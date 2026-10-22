package com.osole.controlurdragon.network;

import com.osole.controlurdragon.tracking.DragonRecord;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * What the menu shows about one dragon.
 *
 * @param distance blocks from the player, or -1 if it is in another dimension
 * @param command  Ice and Fire command (0 wander, 1 sit, 2 follow), or -1 if the dragon is not loaded
 * @param guarding the dragon has a guard-area order
 */
public record DragonEntry(UUID id, String name, String type, int stage, DragonRecord.State state,
                          boolean loaded, int distance, int command, boolean guarding) {

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeUtf(this.name, 64);
        buf.writeUtf(this.type, 128);
        buf.writeVarInt(this.stage);
        buf.writeEnum(this.state);
        buf.writeBoolean(this.loaded);
        buf.writeVarInt(this.distance);
        buf.writeVarInt(this.command);
        buf.writeBoolean(this.guarding);
    }

    public static DragonEntry read(FriendlyByteBuf buf) {
        return new DragonEntry(buf.readUUID(), buf.readUtf(64), buf.readUtf(128), buf.readVarInt(),
                buf.readEnum(DragonRecord.State.class), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
