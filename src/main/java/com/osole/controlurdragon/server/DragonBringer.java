package com.osole.controlurdragon.server;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.behavior.DragonCarry;
import com.osole.controlurdragon.behavior.DragonOrders;
import com.osole.controlurdragon.tracking.DragonRecord;
import com.osole.controlurdragon.tracking.DragonRecordsData;
import com.osole.controlurdragon.tracking.DragonRestorer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DragonBringer {
    private static final int SEARCH_TICKS = 200;
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("controlurdragon_bring", Comparator.comparingLong(ChunkPos::toLong), SEARCH_TICKS + 20);

    private record Search(UUID player, UUID dragon, String name, long expires) {
    }

    private static final List<Search> SEARCHES = new ArrayList<>();

    private DragonBringer() {
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static long cooldownSecondsLeft(ServerPlayer player) {
        long cooldown = Config.BRING_COOLDOWN_MINUTES.get() * 1200L;
        if (cooldown <= 0) return 0;
        long last = DragonRecordsData.get(player.server).getLastBring(player.getUUID());
        if (last == Long.MIN_VALUE) return 0;
        long left = cooldown - (now(player.server) - last);
        return left <= 0 ? 0 : (left + 19) / 20;
    }

    public static void bring(ServerPlayer player, DragonRecord record) {
        long left = cooldownSecondsLeft(player);
        if (left > 0) {
            player.displayClientMessage(Component.translatable("controlurdragon.bring.cooldown", left / 60 + 1).withStyle(ChatFormatting.RED), true);
            return;
        }
        for (Search search : SEARCHES) {
            if (search.dragon().equals(record.dragonId)) {
                player.displayClientMessage(Component.translatable("controlurdragon.bring.searching", record.name), true);
                return;
            }
        }

        DragonBaseEntity dragon = DragonActions.findLoaded(player.server, record.dragonId);
        if (dragon != null) {
            if (dragon.isModelDead()) {
                player.displayClientMessage(Component.translatable("controlurdragon.bring.failed", record.name).withStyle(ChatFormatting.RED), true);
                return;
            }
            teleport(player, dragon);
            return;
        }
        if (record.state == DragonRecord.State.LOST || record.state == DragonRecord.State.STORED) {
            player.displayClientMessage(Component.translatable("controlurdragon.bring.lost", record.name).withStyle(ChatFormatting.RED), true);
            return;
        }

        if (!record.snapshot.isEmpty()) {
            DragonRestorer.Spawned spawned = DragonRestorer.spawnFromSnapshot(player, record, landingSpot(player, dragonWidth(record)));
            if (spawned.error() == null) {
                prepare(spawned.dragon());
                DragonRecordsData.get(player.server).setLastBring(player.getUUID(), now(player.server));
                player.displayClientMessage(Component.translatable("controlurdragon.bring.done", record.name).withStyle(ChatFormatting.GREEN), true);
                return;
            }
        }

        ResourceLocation dimension = ResourceLocation.tryParse(record.dimension);
        ServerLevel level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            player.displayClientMessage(Component.translatable("controlurdragon.bring.failed", record.name).withStyle(ChatFormatting.RED), true);
            return;
        }
        ChunkPos chunk = new ChunkPos(BlockPos.containing(record.x, record.y, record.z));
        level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
        SEARCHES.add(new Search(player.getUUID(), record.dragonId, record.name, now(player.server) + SEARCH_TICKS));
        player.displayClientMessage(Component.translatable("controlurdragon.bring.searching", record.name), true);
    }

    public static void tick(MinecraftServer server) {
        if (SEARCHES.isEmpty()) return;
        long time = now(server);
        Iterator<Search> iterator = SEARCHES.iterator();
        while (iterator.hasNext()) {
            Search search = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(search.player());
            if (player == null) {
                iterator.remove();
                continue;
            }
            DragonBaseEntity dragon = DragonActions.findLoaded(server, search.dragon());
            if (dragon != null && !dragon.isModelDead()) {
                iterator.remove();
                teleport(player, dragon);
            } else if (time > search.expires()) {
                iterator.remove();
                player.displayClientMessage(Component.translatable("controlurdragon.bring.not_found", search.name()).withStyle(ChatFormatting.RED), false);
            }
        }
    }

    private static float dragonWidth(DragonRecord record) {
        return 2F + record.stage * 1.5F;
    }

    private static Vec3 landingSpot(ServerPlayer player, float dragonWidth) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        float distance = Mth.clamp(4F + dragonWidth * 1.2F, 6F, 24F) * 0.6F;
        Vec3 spot = player.position().subtract(flat.normalize().scale(distance));
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(spot.x), Mth.floor(spot.z));
        double y = ground >= player.getY() - 4 && ground <= player.getY() + 8 ? ground : player.getY();
        return new Vec3(spot.x, y, spot.z);
    }

    private static void prepare(DragonBaseEntity dragon) {
        DragonCarry.release(dragon);
        dragon.ejectPassengers();
        DragonOrders.clearAll(dragon);
        dragon.setCommand(2);
        dragon.setInSittingPose(false);
        dragon.setFlying(false);
        dragon.setHovering(false);
        dragon.getNavigation().stop();
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.fallDistance = 0;
    }

    private static void teleport(ServerPlayer player, DragonBaseEntity dragon) {
        String name = dragon.getName().getString();
        prepare(dragon);
        ServerLevel level = player.serverLevel();
        Vec3 spot = landingSpot(player, dragon.getBbWidth());
        Level from = dragon.level();
        boolean moved = dragon.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), 0);
        if (!moved) {
            player.displayClientMessage(Component.translatable("controlurdragon.bring.failed", name).withStyle(ChatFormatting.RED), true);
            return;
        }
        DragonRecordsData.get(player.server).setLastBring(player.getUUID(), now(player.server));
        player.displayClientMessage(Component.translatable(from == level ? "controlurdragon.bring.done" : "controlurdragon.bring.done_dimension", name)
                .withStyle(ChatFormatting.GREEN), true);
    }
}
