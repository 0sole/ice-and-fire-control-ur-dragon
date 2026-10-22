package com.osole.controlurdragon.network;

import com.osole.controlurdragon.tracking.DragonRecord;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record DragonEntry(UUID id, String name, String type, int stage, DragonRecord.State state,
                          boolean loaded, int distance, int command, boolean guarding,
                          int stance, String carrying) {
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
        buf.writeVarInt(this.stance);
        buf.writeUtf(this.carrying, 64);
    }

    public static DragonEntry read(FriendlyByteBuf buf) {
        return new DragonEntry(buf.readUUID(), buf.readUtf(64), buf.readUtf(128), buf.readVarInt(),
                buf.readEnum(DragonRecord.State.class), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), buf.readUtf(64));
    }
}
