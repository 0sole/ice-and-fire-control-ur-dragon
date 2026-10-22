package com.osole.controlurdragon.server;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.item.DragonWhistleItem;
import com.osole.controlurdragon.network.DragonActionPayload;
import com.osole.controlurdragon.network.DragonEntry;
import com.osole.controlurdragon.network.DragonListPayload;
import com.osole.controlurdragon.tracking.DragonRecord;
import com.osole.controlurdragon.tracking.DragonRecordsData;
import com.osole.controlurdragon.tracking.DragonRestorer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
                        DragonRecord.State.ALIVE, true, distance, dragon.getCommand()));
            } else {
                int distance = record.dimension.equals(playerDimension)
                        ? (int) Math.sqrt(player.distanceToSqr(record.x, record.y, record.z)) : -1;
                DragonRecord.State state = record.state == DragonRecord.State.ALIVE ? DragonRecord.State.UNLOADED : record.state;
                entries.add(new DragonEntry(record.dragonId, record.name, record.entityType, record.stage,
                        state, false, distance, -1));
            }
        }
        PacketDistributor.sendToPlayer(player, new DragonListPayload(entries, DragonRestorer.cooldownSecondsLeft(player)));
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
            default -> feedback = Component.empty();
        }
        player.displayClientMessage(feedback, true);
    }
}
