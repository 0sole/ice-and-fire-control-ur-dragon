package com.osole.controlurdragon.server;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.behavior.DragonCarry;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DragonActions {
    private DragonActions() {
    }

    public static boolean debugEnabled() {
        return !FMLEnvironment.production || Config.DEBUG_DELETE_BUTTON.get();
    }

    public static DragonBaseEntity findLoaded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof DragonBaseEntity dragon && dragon.isAlive()) return dragon;
        }
        return null;
    }

    private static LivingEntity resolveTarget(ServerPlayer player, int entityId) {
        if (entityId < 0) return null;
        Entity entity = player.level().getEntity(entityId);
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return null;
        return living.distanceToSqr(player) <= 160 * 160 ? living : null;
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }

    public static void sendList(ServerPlayer player) {
        if (!DragonWhistleItem.isCarriedBy(player)) {
            tell(player, Component.translatable("controlurdragon.menu.no_whistle"));
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
                Entity carried = DragonCarry.getCarried(dragon);
                entries.add(new DragonEntry(record.dragonId, dragon.getName().getString(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(dragon.getType()).toString(), dragon.getDragonStage(),
                        DragonRecord.State.ALIVE, true, distance, dragon.getCommand(), DragonOrders.getGuard(dragon) != null,
                        DragonOrders.getStance(dragon).ordinal(), carried == null ? "" : carried.getName().getString()));
            } else {
                int distance = record.dimension.equals(playerDimension)
                        ? (int) Math.sqrt(player.distanceToSqr(record.x, record.y, record.z)) : -1;
                DragonRecord.State state = record.state == DragonRecord.State.ALIVE ? DragonRecord.State.UNLOADED : record.state;
                entries.add(new DragonEntry(record.dragonId, record.name, record.entityType, record.stage,
                        state, false, distance, -1, false, 0, ""));
            }
        }
        PacketDistributor.sendToPlayer(player, new DragonListPayload(entries, DragonRestorer.cooldownSecondsLeft(player),
                DragonBringer.cooldownSecondsLeft(player), debugEnabled()));
    }

    public static void handle(ServerPlayer player, DragonActionPayload payload) {
        if (!DragonWhistleItem.isCarriedBy(player)) {
            tell(player, Component.translatable("controlurdragon.menu.no_whistle"));
            return;
        }
        DragonRecordsData data = DragonRecordsData.get(player.server);
        DragonRecord record = data.get(payload.dragon());
        if (record == null || !player.getUUID().equals(record.ownerId)) {
            fail(player, Component.translatable("controlurdragon.restore.no_permission"));
            return;
        }

        if (payload.action() == DragonActionPayload.Action.RESTORE) {
            Component error = DragonRestorer.tryRestore(player, record);
            if (error != null) fail(player, error);
            else tell(player, Component.translatable("controlurdragon.restore.done", record.name).withStyle(ChatFormatting.GREEN));
            return;
        }
        if (payload.action() == DragonActionPayload.Action.BRING) {
            DragonBringer.bring(player, record);
            return;
        }

        DragonBaseEntity dragon = findLoaded(player.server, record.dragonId);
        if (dragon == null || dragon.isModelDead() || !dragon.isOwnedBy(player)) {
            fail(player, Component.translatable("controlurdragon.action.not_loaded", record.name));
            return;
        }

        String name = dragon.getName().getString();
        switch (payload.action()) {
            case SIT -> {
                DragonOrders.clearAll(dragon);
                DragonCarry.release(dragon);
                dragon.setCommand(1);
                tell(player, Component.translatable("controlurdragon.action.sit.done", name));
            }
            case FOLLOW -> {
                DragonOrders.clearAll(dragon);
                dragon.setCommand(2);
                tell(player, Component.translatable("controlurdragon.action.follow.done", name));
            }
            case WANDER -> {
                DragonOrders.clearAll(dragon);
                dragon.setCommand(0);
                tell(player, Component.translatable("controlurdragon.action.wander.done", name));
            }
            case RENAME -> {
                String newName = payload.text().strip();
                if (newName.length() > DragonActionPayload.MAX_NAME_LENGTH)
                    newName = newName.substring(0, DragonActionPayload.MAX_NAME_LENGTH);
                dragon.setCustomName(newName.isEmpty() ? null : Component.literal(newName));
                record.name = dragon.getName().getString();
                data.setDirty();
                tell(player, Component.translatable("controlurdragon.action.rename.done", record.name));
            }
            case ATTACK -> {
                LivingEntity target = resolveTarget(player, payload.value());
                if (target == null || target.level() != dragon.level()) {
                    fail(player, Component.translatable("controlurdragon.action.attack.no_target"));
                } else if (!DragonOrders.canAttack(dragon, target)) {
                    fail(player, Component.translatable("controlurdragon.action.attack.not_allowed", target.getName()));
                } else {
                    DragonOrders.clearGuard(dragon);
                    DragonOrders.attack(dragon, target);
                    tell(player, Component.translatable("controlurdragon.action.attack.done", name, target.getName()));
                }
            }
            case STOP -> {
                DragonOrders.stop(dragon);
                tell(player, Component.translatable("controlurdragon.action.stop.done", name));
            }
            case GUARD -> {
                BlockPos center = dragon.level() == player.level() ? player.blockPosition() : dragon.blockPosition();
                DragonOrders.clearAttackOrder(dragon);
                DragonOrders.guard(dragon, center, Config.GUARD_RADIUS.get());
                tell(player, Component.translatable("controlurdragon.action.guard.done", name, Config.GUARD_RADIUS.get()));
            }
            case STANCE -> {
                DragonOrders.Stance stance = DragonOrders.Stance.byId(payload.value());
                DragonOrders.setStance(dragon, stance);
                if (stance == DragonOrders.Stance.PASSIVE) DragonOrders.stop(dragon);
                tell(player, Component.translatable("controlurdragon.stance.done", name,
                        Component.translatable("controlurdragon.stance." + stance.name().toLowerCase())));
            }
            case CARRY -> {
                LivingEntity target = resolveTarget(player, payload.value());
                carry(player, dragon, target);
            }
            case DROP -> {
                if (DragonCarry.release(dragon)) tell(player, Component.translatable("controlurdragon.carry.dropped", name));
                else fail(player, Component.translatable("controlurdragon.carry.nothing", name));
            }
            case DELETE -> {
                if (!debugEnabled()) {
                    fail(player, Component.translatable("controlurdragon.action.delete.disabled"));
                } else {
                    ControlUrDragon.LOGGER.warn("[ControlUrDragon] TEST: {} deleted dragon '{}' ({}) from the menu",
                            player.getGameProfile().getName(), name, dragon.getUUID());
                    DragonOrders.clearAll(dragon);
                    DragonCarry.release(dragon);
                    DragonTracker.markTestDelete(dragon.getUUID());
                    dragon.discard();
                    tell(player, Component.translatable("controlurdragon.action.delete.done", name).withStyle(ChatFormatting.GOLD));
                }
            }
            default -> {
            }
        }
    }

    private static void carry(ServerPlayer player, DragonBaseEntity dragon, LivingEntity target) {
        String name = dragon.getName().getString();
        if (target == null || target.level() != dragon.level()) {
            fail(player, Component.translatable("controlurdragon.carry.no_target"));
            return;
        }
        switch (DragonCarry.order(dragon, target)) {
            case GRABBED -> tell(player, Component.translatable("controlurdragon.carry.grabbed", name, target.getName()));
            case ON_THE_WAY -> tell(player, Component.translatable("controlurdragon.carry.on_the_way", name, target.getName()));
            case TOO_FAR -> fail(player, Component.translatable("controlurdragon.carry.too_far", name));
            case CANNOT -> fail(player, Component.translatable("controlurdragon.carry.cannot", name, target.getName()));
        }
    }

    private static List<DragonBaseEntity> nearbyDragons(ServerPlayer player) {
        int range = Config.GROUP_RANGE.get();
        return player.serverLevel().getEntitiesOfClass(DragonBaseEntity.class, player.getBoundingBox().inflate(range),
                d -> d.isAlive() && !d.isModelDead() && d.isOwnedBy(player)
                        && d.distanceToSqr(player) <= (double) range * range);
    }

    public static void handleGroup(ServerPlayer player, GroupOrderPayload payload) {
        if (!DragonWhistleItem.isCarriedBy(player)) {
            tell(player, Component.translatable("controlurdragon.menu.no_whistle"));
            return;
        }
        if (payload.order() == GroupOrderPayload.Order.CARRY) {
            groupCarry(player, payload.targetId());
            return;
        }
        List<DragonBaseEntity> dragons = nearbyDragons(player);
        dragons.removeIf(d -> d.getControllingPassenger() != null);
        if (dragons.isEmpty()) {
            fail(player, Component.translatable("controlurdragon.group.none", Config.GROUP_RANGE.get()));
            return;
        }

        switch (payload.order()) {
            case ATTACK -> {
                LivingEntity target = resolveTarget(player, payload.targetId());
                if (target == null) {
                    fail(player, Component.translatable("controlurdragon.action.attack.no_target"));
                    return;
                }
                int count = 0;
                for (DragonBaseEntity dragon : dragons) {
                    if (!DragonOrders.canAttack(dragon, target)) continue;
                    DragonOrders.clearGuard(dragon);
                    DragonOrders.attack(dragon, target);
                    count++;
                }
                if (count == 0) fail(player, Component.translatable("controlurdragon.action.attack.not_allowed", target.getName()));
                else tell(player, Component.translatable("controlurdragon.group.attack", count, target.getName()));
            }
            case FOLLOW -> {
                for (DragonBaseEntity dragon : dragons) {
                    DragonOrders.clearAll(dragon);
                    dragon.setOrderedToSit(false);
                    dragon.setInSittingPose(false);
                    dragon.setCommand(2);
                }
                tell(player, Component.translatable("controlurdragon.group.follow", dragons.size()));
            }
            case GUARD -> {
                BlockPos center = player.blockPosition();
                for (DragonBaseEntity dragon : dragons) {
                    DragonOrders.clearAttackOrder(dragon);
                    if (dragon.getTarget() != null) dragon.setTarget(null);
                    DragonOrders.guard(dragon, center, Config.GUARD_RADIUS.get());
                }
                tell(player, Component.translatable("controlurdragon.group.guard", dragons.size(), Config.GUARD_RADIUS.get()));
            }
            case STOP -> {
                for (DragonBaseEntity dragon : dragons) DragonOrders.stop(dragon);
                tell(player, Component.translatable("controlurdragon.group.stop", dragons.size()));
            }
            default -> {
            }
        }
    }

    private static void groupCarry(ServerPlayer player, int targetId) {
        if (player.getVehicle() instanceof DragonBaseEntity ridden && ridden.isOwnedBy(player)) {
            if (DragonCarry.release(ridden)) {
                tell(player, Component.translatable("controlurdragon.carry.dropped", ridden.getName()));
                return;
            }
            LivingEntity target = resolveTarget(player, targetId);
            if (target == null || !DragonCarry.canCarry(ridden, target)) target = DragonCarry.findBelow(ridden);
            carry(player, ridden, target);
            return;
        }

        List<DragonBaseEntity> dragons = nearbyDragons(player);
        Comparator<DragonBaseEntity> closest = Comparator.comparingDouble(d -> d.distanceToSqr(player));
        DragonBaseEntity carrying = dragons.stream().filter(DragonCarry::isCarrying).min(closest).orElse(null);
        if (carrying != null) {
            DragonCarry.release(carrying);
            tell(player, Component.translatable("controlurdragon.carry.dropped", carrying.getName()));
            return;
        }
        LivingEntity target = resolveTarget(player, targetId);
        if (target == null) {
            fail(player, Component.translatable("controlurdragon.carry.no_target"));
            return;
        }
        DragonBaseEntity dragon = dragons.stream().filter(d -> DragonCarry.canCarry(d, target))
                .min(Comparator.comparingDouble(d -> d.distanceToSqr(target))).orElse(null);
        if (dragon == null) {
            fail(player, dragons.isEmpty() ? Component.translatable("controlurdragon.group.none", Config.GROUP_RANGE.get())
                    : Component.translatable("controlurdragon.carry.cannot", Component.translatable("controlurdragon.carry.your_dragons"), target.getName()));
            return;
        }
        carry(player, dragon, target);
    }
}
