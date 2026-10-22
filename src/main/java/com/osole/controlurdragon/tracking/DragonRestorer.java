package com.osole.controlurdragon.tracking;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public final class DragonRestorer {
    private DragonRestorer() {
    }

    private static long cooldownTicks() {
        return Config.RESTORE_COOLDOWN_MINUTES.get() * 60L * 20L;
    }

    public static long cooldownSecondsLeft(ServerPlayer player) {
        if (player.hasPermissions(2)) return 0;
        long last = DragonRecordsData.get(player.server).getLastRestore(player.getUUID());
        if (last == Long.MIN_VALUE) return 0;
        long left = cooldownTicks() - (player.serverLevel().getGameTime() - last);
        return Math.max(0, (left + 19) / 20);
    }

    public static Component tryRestore(ServerPlayer player, DragonRecord record) {
        boolean operator = player.hasPermissions(2);
        if (!operator) {
            if (!Config.OWNERS_CAN_RESTORE.get() || !player.getUUID().equals(record.ownerId))
                return Component.translatable("controlurdragon.restore.no_permission");
            long secondsLeft = cooldownSecondsLeft(player);
            if (secondsLeft > 0)
                return Component.translatable("controlurdragon.restore.cooldown", secondsLeft / 60 + 1);
        }
        Component error = restore(player, record);
        if (error == null && !operator)
            DragonRecordsData.get(player.server).setLastRestore(player.getUUID(), player.serverLevel().getGameTime());
        return error;
    }

    public static final String GENERATION_TAG = "controlurdragon_generation";

    public static int generationOf(Entity entity) {
        return entity.getPersistentData().getInt(GENERATION_TAG);
    }

    public static boolean isLoadedAnywhere(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) != null) return true;
        }
        return false;
    }

    public static Component restore(ServerPlayer player, DragonRecord record) {
        if (record.state != DragonRecord.State.LOST)
            return Component.translatable("controlurdragon.restore.not_lost", record.state.name());
        Spawned result = spawnFromSnapshot(player, record, player.position().add(0, 1, 0));
        if (result.error() != null) return result.error();
        DragonBaseEntity dragon = result.dragon();
        dragon.setHealth(Math.max(dragon.getHealth(), dragon.getMaxHealth() * 0.25F));
        return null;
    }

    public record Spawned(DragonBaseEntity dragon, Component error) {
        static Spawned failed(Component error) {
            return new Spawned(null, error);
        }
    }

    public static Spawned spawnFromSnapshot(ServerPlayer player, DragonRecord record, Vec3 position) {
        if (record.snapshot.isEmpty()) return Spawned.failed(Component.translatable("controlurdragon.restore.no_snapshot"));
        if (isLoadedAnywhere(player.server, record.dragonId)) return Spawned.failed(Component.translatable("controlurdragon.restore.still_exists"));

        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(record.entityType));
        if (type.isEmpty()) return Spawned.failed(Component.translatable("controlurdragon.restore.unknown_type", record.entityType));

        ServerLevel level = player.serverLevel();
        Entity entity = type.get().create(level);
        if (!(entity instanceof DragonBaseEntity dragon))
            return Spawned.failed(Component.translatable("controlurdragon.restore.unknown_type", record.entityType));

        dragon.load(record.snapshot.copy());
        dragon.setUUID(record.dragonId);
        record.generation++;
        dragon.getPersistentData().putInt(GENERATION_TAG, record.generation);
        dragon.moveTo(position.x, position.y, position.z, player.getYRot(), 0);
        dragon.setDeltaMovement(0, 0, 0);
        dragon.fallDistance = 0;

        if (!level.addFreshEntity(dragon)) {
            record.generation--;
            return Spawned.failed(Component.translatable("controlurdragon.restore.spawn_failed"));
        }
        record.state = DragonRecord.State.ALIVE;
        DragonRecordsData.get(player.server).setDirty();
        return new Spawned(dragon, null);
    }
}
