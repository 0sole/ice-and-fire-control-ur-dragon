package com.osole.controlurdragon.server;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.behavior.DragonOrders;
import com.osole.controlurdragon.item.DragonWhistleItem;
import com.osole.controlurdragon.network.DragonActionPayload;
import com.osole.controlurdragon.network.DragonEntry;
import com.osole.controlurdragon.network.DragonListPayload;
import com.osole.controlurdragon.network.GroupOrderPayload;
import com.osole.controlurdragon.tracking.DragonRecord;
import com.osole.controlurdragon.tracking.DragonRecordsData;
import com.osole.controlurdragon.tracking.DragonRestorer;
import com.osole.controlurdragon.tracking.DragonTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server side of the menu: builds the dragon list and carries out the orders.
 */
public final class DragonActions {
    private DragonActions() {
    }

    public static DragonBaseEntity findLoaded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof DragonBaseEntity dragon && dragon.isAlive()) return dragon;
        }
        return null;
    }

    /** The test "delete" button: always in the development environment, otherwise only if enabled in the config. */
    public static boolean debugEnabled() {
        return !FMLEnvironment.production || Config.DEBUG_DELETE_BUTTON.get();
    }

    /** The entity the player aimed at, with dragon body parts resolved to the dragon. */
    private static LivingEntity resolveTarget(ServerPlayer player, int entityId) {
        if (entityId < 0) return null;
        Entity entity = player.level().getEntity(entityId);
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return null;
        return living.distanceToSqr(player) <= 160 * 160 ? living : null;
    }

    public static void sendList(ServerPlayer player) {
        if (!DragonWhistleItem.isCarriedBy(player)) {
            player.displayClientMessage(Component.translatable("controlurdragon.menu.no_whistle"), true);
            return;
        }
        DragonRecordsData data = DragonRecordsData.get(player.server);
        String playerDimension = player.level().dimension().location().toString();
        List<DragonEntry> entries = new ArrayList<>();
        for (DragonRecord record : data.getByOwner(player.getUUID())) {
            if (record.state == DragonRecord.State.DEAD) continue;
            DragonBaseEntity dragon = findLoaded(player.server, record.dragonId);
            if (dragon != null && !dragon.isModelDead()) {
                int distance = dragon.level() == player.level() ? (int) dragon.distanceTo(player) : -1;
                entries.add(new DragonEntry(record.dragonId, dragon.getName().getString(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(dragon.getType()).toString(), dragon.getDragonStage(),
                        DragonRecord.State.ALIVE, true, distance, dragon.getCommand(), DragonOrders.getGuard(dragon) != null));
            } else {
                int distance = record.dimension.equals(playerDimension)
                        ? (int) Math.sqrt(player.distanceToSqr(record.x, record.y, record.z)) : -1;
                DragonRecord.State state = record.state == DragonRecord.State.ALIVE ? DragonRecord.State.UNLOADED : record.state;
                entries.add(new DragonEntry(record.dragonId, record.name, record.entityType, record.stage,
                        state, false, distance, -1, false));
            }
        }
        PacketDistributor.sendToPlayer(player, new DragonListPayload(entries, DragonRestorer.cooldownSecondsLeft(player), debugEnabled()));
    }

    public static void handle(ServerPlayer player, DragonActionPayload payload) {
        if (!DragonWhistleItem.isCarriedBy(player)) {
            player.displayClientMessage(Component.translatable("controlurdragon.menu.no_whistle"), true);
            return;
        }
        DragonRecordsData data = DragonRecordsData.get(player.server);
        DragonRecord record = data.get(payload.dragon());
        if (record == null || !player.getUUID().equals(record.ownerId)) {
            player.displayClientMessage(Component.translatable("controlurdragon.restore.no_permission").withStyle(ChatFormatting.RED), true);
            return;
        }

        if (payload.action() == DragonActionPayload.Action.RESTORE) {
            Component error = DragonRestorer.tryRestore(player, record);
            player.displayClientMessage(error != null ? error.copy().withStyle(ChatFormatting.RED)
                    : Component.translatable("controlurdragon.restore.done", record.name).withStyle(ChatFormatting.GREEN), true);
            return;
        }

        DragonBaseEntity dragon = findLoaded(player.server, record.dragonId);
        if (dragon == null || dragon.isModelDead() || !dragon.isOwnedBy(player)) {
            player.displayClientMessage(Component.translatable("controlurdragon.action.not_loaded", record.name).withStyle(ChatFormatting.RED), true);
            return;
        }

        String name = dragon.getName().getString();
        Component feedback;
        switch (payload.action()) {
            case SIT, FOLLOW, WANDER, COME -> DragonOrders.clearAll(dragon);
            default -> {
            }
        }
        switch (payload.action()) {
            case SIT -> {
                dragon.setCommand(1);
                feedback = Component.translatable("controlurdragon.action.sit.done", name);
            }
            case FOLLOW -> {
                dragon.setCommand(2);
                feedback = Component.translatable("controlurdragon.action.follow.done", name);
            }
            case WANDER -> {
                dragon.setCommand(0);
                feedback = Component.translatable("controlurdragon.action.wander.done", name);
            }
            case COME -> {
                if (dragon.level() != player.level()) {
                    feedback = Component.translatable("controlurdragon.action.come.other_dimension", name).withStyle(ChatFormatting.RED);
                } else {
                    dragon.setCommand(2);
                    dragon.getNavigation().stop();
                    dragon.teleportTo(player.getX(), player.getY(), player.getZ());
                    dragon.setDeltaMovement(0, 0, 0);
                    feedback = Component.translatable("controlurdragon.action.come.done", name);
                }
            }
            case RENAME -> {
                String newName = payload.text().strip();
                if (newName.length() > DragonActionPayload.MAX_NAME_LENGTH)
                    newName = newName.substring(0, DragonActionPayload.MAX_NAME_LENGTH);
                if (newName.isEmpty()) {
                    dragon.setCustomName(null);
                } else {
                    dragon.setCustomName(Component.literal(newName));
                }
                record.name = dragon.getName().getString();
                data.setDirty();
                feedback = Component.translatable("controlurdragon.action.rename.done", record.name);
            }
            case ATTACK -> {
                LivingEntity target = resolveTarget(player, payload.targetId());
                if (target == null || target.level() != dragon.level()) {
                    feedback = Component.translatable("controlurdragon.action.attack.no_target").withStyle(ChatFormatting.RED);
                } else if (!DragonOrders.canAttack(dragon, target)) {
                    feedback = Component.translatable("controlurdragon.action.attack.not_allowed", target.getName()).withStyle(ChatFormatting.RED);
                } else {
                    DragonOrders.clearGuard(dragon);
                    DragonOrders.attack(dragon, target);
                    feedback = Component.translatable("controlurdragon.action.attack.done", name, target.getName());
                }
            }
            case GUARD -> {
                BlockPos center = dragon.level() == player.level() ? player.blockPosition() : dragon.blockPosition();
                DragonOrders.clearAttackOrder(dragon);
                DragonOrders.guard(dragon, center, Config.GUARD_RADIUS.get());
                feedback = Component.translatable("controlurdragon.action.guard.done", name, Config.GUARD_RADIUS.get());
            }
            case DELETE -> {
                if (!debugEnabled()) {
                    feedback = Component.translatable("controlurdragon.action.delete.disabled").withStyle(ChatFormatting.RED);
                } else {
                    // Test only: removes the dragon the same way the bug did (no death), so the tracker and
                    // the chat alert can be checked. It can be brought back with the restore option.
                    ControlUrDragon.LOGGER.warn("[ControlUrDragon] TEST: {} deleted dragon '{}' ({}) from the menu",
                            player.getGameProfile().getName(), name, dragon.getUUID());
                    DragonOrders.clearAll(dragon);
                    DragonTracker.markTestDelete(dragon.getUUID());
                    dragon.discard();
                    feedback = Component.translatable("controlurdragon.action.delete.done", name).withStyle(ChatFormatting.GOLD);
                }
            }
            default -> feedback = Component.empty();
        }
        player.displayClientMessage(feedback, true);
    }

    /** Hotkey orders for every loaded dragon of the player within {@link Config#GROUP_RANGE}. */
    public static void handleGroup(ServerPlayer player, GroupOrderPayload payload) {
        if (!DragonWhistleItem.isCarriedBy(player)) {
            player.displayClientMessage(Component.translatable("controlurdragon.menu.no_whistle"), true);
            return;
        }
        int range = Config.GROUP_RANGE.get();
        List<DragonBaseEntity> dragons = player.serverLevel().getEntitiesOfClass(DragonBaseEntity.class,
                player.getBoundingBox().inflate(range),
                d -> d.isAlive() && !d.isModelDead() && d.isOwnedBy(player) && d.getControllingPassenger() == null
                        && d.distanceToSqr(player) <= (double) range * range);
        if (dragons.isEmpty()) {
            player.displayClientMessage(Component.translatable("controlurdragon.group.none", range).withStyle(ChatFormatting.RED), true);
            return;
        }

        Component feedback;
        switch (payload.order()) {
            case ATTACK -> {
                LivingEntity target = resolveTarget(player, payload.targetId());
                if (target == null) {
                    feedback = Component.translatable("controlurdragon.action.attack.no_target").withStyle(ChatFormatting.RED);
                    break;
                }
                int count = 0;
                for (DragonBaseEntity dragon : dragons) {
                    if (!DragonOrders.canAttack(dragon, target)) continue;
                    DragonOrders.clearGuard(dragon);
                    DragonOrders.attack(dragon, target);
                    count++;
                }
                feedback = count == 0
                        ? Component.translatable("controlurdragon.action.attack.not_allowed", target.getName()).withStyle(ChatFormatting.RED)
                        : Component.translatable("controlurdragon.group.attack", count, target.getName());
            }
            case FOLLOW -> {
                for (DragonBaseEntity dragon : dragons) {
                    DragonOrders.clearAll(dragon);
                    dragon.setOrderedToSit(false);
                    dragon.setInSittingPose(false);
                    dragon.setCommand(2);
                }
                feedback = Component.translatable("controlurdragon.group.follow", dragons.size());
            }
            case GUARD -> {
                BlockPos center = player.blockPosition();
                for (DragonBaseEntity dragon : dragons) {
                    DragonOrders.clearAttackOrder(dragon);
                    if (dragon.getTarget() != null) dragon.setTarget(null);
                    DragonOrders.guard(dragon, center, Config.GUARD_RADIUS.get());
                }
                feedback = Component.translatable("controlurdragon.group.guard", dragons.size(), Config.GUARD_RADIUS.get());
            }
            default -> feedback = Component.empty();
        }
        player.displayClientMessage(feedback, true);
    }
}
