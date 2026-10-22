package com.osole.controlurdragon.tracking;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.ControlUrDragon;
import net.minecraft.core.registries.BuiltInRegistries;
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

/**
 * Keeps a record and a backup of every tamed Ice and Fire dragon, and detects dragons that are
 * deleted from the world without dying (the bug that made dragons vanish).
 */
@EventBusSubscriber(modid = ControlUrDragon.MODID)
public final class DragonTracker {
    /** Class names that mean "the dragon was put into an item", not lost. */
    private static final String[] STORING_CALLERS = {"DragonHornItem"};

    private DragonTracker() {
    }

    private static boolean isTracked(Entity entity) {
        return entity instanceof DragonBaseEntity dragon && dragon.isTame() && dragon.getOwnerUUID() != null;
    }

    /** Copies the dragon's current data and position into its record. */
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

    // LOWEST + not receiving cancelled events: only counts deaths that really happen.
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

        // The dragon is being destroyed. A corpse being cleaned up or a real death is fine.
        if (alreadyDead || dragon.isModelDead() || dragon.isDeadOrDying()) {
            update(level, dragon, false).state = DragonRecord.State.DEAD;
            return;
        }

        // Deleted while alive: find out who did it.
        Throwable trace = new Throwable("Tamed dragon removed without dying");
        String summary = summarize(trace);
        boolean stored = false;
        for (String caller : STORING_CALLERS) stored |= summary.contains(caller);

        DragonRecord record = update(level, dragon, true);
        record.state = stored ? DragonRecord.State.STORED : DragonRecord.State.LOST;
        record.lostReason = reason.name();
        record.lostTrace = summary;
        record.lostGameTime = level.getGameTime();

        if (Config.LOG_REMOVALS.get() && !stored) {
            ControlUrDragon.LOGGER.warn("[ControlUrDragon] Tamed dragon '{}' ({}) owned by {} was removed ({}) at {} {} without dying. "
                            + "It can be restored with: /cud restore {}",
                    record.name, dragon.getUUID(), record.ownerId, reason, record.dimension, dragon.blockPosition(),
                    dragon.getUUID(), trace);
        }
    }

    /** Keeps the interesting stack frames: who called remove(), skipping Minecraft/NeoForge internals and this mod. */
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
