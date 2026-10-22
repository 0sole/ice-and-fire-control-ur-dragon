package com.osole.controlurdragon.network;

import com.osole.controlurdragon.behavior.DragonSettings;
import com.osole.controlurdragon.tracking.DragonRecord;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record DragonEntry(UUID id, String name, String type, int stage, DragonRecord.State state,
                          boolean loaded, int distance, int command, boolean guarding,
                          int[] settings, String carrying) {
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
        buf.writeVarIntArray(this.settings);
        buf.writeUtf(this.carrying, 64);
    }

    public static DragonEntry read(FriendlyByteBuf buf) {
        return new DragonEntry(buf.readUUID(), buf.readUtf(64), buf.readUtf(128), buf.readVarInt(),
                buf.readEnum(DragonRecord.State.class), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarIntArray(), buf.readUtf(64));
    }

    public int setting(DragonSettings.Setting setting) {
        return setting.ordinal() < this.settings.length ? this.settings[setting.ordinal()] : 0;
    }
}
