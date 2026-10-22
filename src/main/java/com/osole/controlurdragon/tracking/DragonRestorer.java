package com.osole.controlurdragon.tracking;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * Recreates a LOST dragon from its last backup, next to a player.
 */
public final class DragonRestorer {
    private DragonRestorer() {
    }

    /** @return null on success, otherwise the reason it failed. */
    public static Component restore(ServerPlayer player, DragonRecord record) {
        if (record.state != DragonRecord.State.LOST)
            return Component.translatable("controlurdragon.restore.not_lost", record.state.name());
        if (record.snapshot.isEmpty())
            return Component.translatable("controlurdragon.restore.no_snapshot");

        // Never create a second copy: refuse if the dragon still exists anywhere.
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level.getEntity(record.dragonId) != null)
                return Component.translatable("controlurdragon.restore.still_exists");
        }

        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(record.entityType));
        if (type.isEmpty()) return Component.translatable("controlurdragon.restore.unknown_type", record.entityType);

        ServerLevel level = player.serverLevel();
        Entity entity = type.get().create(level);
        if (!(entity instanceof DragonBaseEntity dragon))
            return Component.translatable("controlurdragon.restore.unknown_type", record.entityType);

        dragon.load(record.snapshot.copy());
        dragon.setUUID(record.dragonId);
        dragon.moveTo(player.getX(), player.getY() + 1, player.getZ(), player.getYRot(), 0);
        dragon.setHealth(Math.max(dragon.getHealth(), dragon.getMaxHealth() * 0.25F));
        dragon.setDeltaMovement(0, 0, 0);
        dragon.fallDistance = 0;

        if (!level.addFreshEntity(dragon))
            return Component.translatable("controlurdragon.restore.spawn_failed");

        record.state = DragonRecord.State.ALIVE;
        DragonRecordsData.get(player.server).setDirty();
        return null;
    }
}
