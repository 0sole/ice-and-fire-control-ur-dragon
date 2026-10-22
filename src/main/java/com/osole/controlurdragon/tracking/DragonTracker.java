package com.osole.controlurdragon.tracking;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.ControlUrDragon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = ControlUrDragon.MODID)
public final class DragonTracker {
    public static final String TEST_PREFIX = "TEST_";
    private static final Set<UUID> TEST_DELETES = ConcurrentHashMap.newKeySet();
    private static final String[] STORING_CALLERS = {"DragonHornItem"};

    public static void markTestDelete(UUID dragon) {
        TEST_DELETES.add(dragon);
    }

    private DragonTracker() {
    }

    private static boolean isTracked(Entity entity) {
        return entity instanceof DragonBaseEntity dragon && dragon.isTame() && dragon.getOwnerUUID() != null;
    }

    private static DragonRecord update(ServerLevel level, DragonBaseEntity dragon, boolean takeSnapshot) {
        DragonRecordsData data = DragonRecordsData.get(level.getServer());
        DragonRecord record = data.getOrCreate(dragon.getUUID());
        record.ownerId = dragon.getOwnerUUID();
        record.entityType = BuiltInRegistries.ENTITY_TYPE.getKey(dragon.getType()).toString();
        record.name = dragon.getName().getString();
        record.dimension = level.dimension().location().toString();
        record.x = dragon.getX();
        record.y = dragon.getY();
        record.z = dragon.getZ();
        record.stage = dragon.getDragonStage();
        record.lastSeenGameTime = level.getGameTime();
        if (takeSnapshot && !dragon.isModelDead()) {
            CompoundTag snapshot = new CompoundTag();
            dragon.saveWithoutId(snapshot);
            record.snapshot = snapshot;
        }
        data.setDirty();
        return record;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isTracked(event.getEntity())) return;
        DragonBaseEntity dragon = (DragonBaseEntity) event.getEntity();
        DragonRecord record = update(level, dragon, true);
        record.state = dragon.isModelDead() ? DragonRecord.State.DEAD : DragonRecord.State.ALIVE;
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !isTracked(event.getEntity())) return;
        DragonBaseEntity dragon = (DragonBaseEntity) event.getEntity();
        if ((level.getGameTime() + dragon.getId()) % Config.SNAPSHOT_INTERVAL.get() != 0) return;
        DragonRecord record = update(level, dragon, true);
        if (record.state != DragonRecord.State.DEAD) record.state = DragonRecord.State.ALIVE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !isTracked(event.getEntity())) return;
        DragonRecord record = update(level, (DragonBaseEntity) event.getEntity(), false);
        record.state = DragonRecord.State.DEAD;
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isTracked(event.getEntity())) return;
        DragonBaseEntity dragon = (DragonBaseEntity) event.getEntity();
        Entity.RemovalReason reason = dragon.getRemovalReason();
        if (reason == null || reason == Entity.RemovalReason.CHANGED_DIMENSION) return;

        DragonRecordsData data = DragonRecordsData.get(level.getServer());
        DragonRecord existing = data.get(dragon.getUUID());
        boolean alreadyDead = existing != null && existing.state == DragonRecord.State.DEAD;

        if (!reason.shouldDestroy()) {
            DragonRecord record = update(level, dragon, true);
            if (!alreadyDead) record.state = DragonRecord.State.UNLOADED;
            return;
        }

        if (alreadyDead || dragon.isModelDead() || dragon.isDeadOrDying()) {
            update(level, dragon, false).state = DragonRecord.State.DEAD;
            return;
        }

        Throwable trace = new Throwable("Tamed dragon removed without dying");
        String summary = summarize(trace);
        boolean stored = false;
        for (String caller : STORING_CALLERS) stored |= summary.contains(caller);

        DragonRecord record = update(level, dragon, true);
        record.state = stored ? DragonRecord.State.STORED : DragonRecord.State.LOST;
        record.lostReason = (TEST_DELETES.remove(dragon.getUUID()) ? TEST_PREFIX : "") + reason.name();
        record.lostTrace = summary;
        record.lostGameTime = level.getGameTime();

        if (Config.LOG_REMOVALS.get() && !stored) {
            ControlUrDragon.LOGGER.warn("[ControlUrDragon] Tamed dragon '{}' ({}) owned by {} was removed ({}) at {} {} without dying. "
                            + "It can be restored with: /cud restore {}",
                    record.name, dragon.getUUID(), record.ownerId, reason, record.dimension, dragon.blockPosition(),
                    dragon.getUUID(), trace);
        }
        if (Config.CHAT_REMOVAL_ALERTS.get() && !stored) alertOwner(level, record, reason);
    }

    private static void alertOwner(ServerLevel level, DragonRecord record, Entity.RemovalReason reason) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(record.ownerId);
        if (owner == null) return;

        String trace = record.lostTrace == null || record.lostTrace.isEmpty() ? "?" : record.lostTrace;
        String[] lines = trace.split("\n");
        MutableComponent message = Component.translatable("controlurdragon.alert.lost", record.name, record.lostReason,
                        record.dimension, (int) record.x, (int) record.y, (int) record.z)
                .withStyle(ChatFormatting.RED);
        owner.sendSystemMessage(message);

        MutableComponent where = Component.translatable("controlurdragon.alert.trace").withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < Math.min(4, lines.length); i++) {
            where.append(Component.literal("\n  " + lines[i]).withStyle(ChatFormatting.DARK_GRAY));
        }
        where.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(trace))));
        owner.sendSystemMessage(where);

        String command = "/cud restore " + record.dragonId;
        owner.sendSystemMessage(Component.translatable("controlurdragon.alert.restore")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))));
    }

    private static String summarize(Throwable trace) {
        StringBuilder builder = new StringBuilder();
        int kept = 0;
        for (StackTraceElement element : trace.getStackTrace()) {
            String cls = element.getClassName();
            if (cls.startsWith("com.osole.controlurdragon") || cls.startsWith("net.neoforged.bus")
                    || cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.")
                    || cls.startsWith("net.minecraft.world.level.entity.")) continue;
            builder.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(element.getMethodName())
                    .append(':').append(element.getLineNumber()).append('\n');
            if (++kept >= 12) break;
        }
        return builder.toString().trim();
    }
}
