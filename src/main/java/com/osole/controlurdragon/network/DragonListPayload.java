package com.osole.controlurdragon.network;

import com.osole.controlurdragon.ControlUrDragon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record DragonListPayload(List<DragonEntry> dragons, long restoreCooldownSeconds, long bringCooldownSeconds) implements CustomPacketPayload {
    public static final Type<DragonListPayload> TYPE = new Type<>(ControlUrDragon.id("dragon_list"));
    public static final StreamCodec<FriendlyByteBuf, DragonListPayload> CODEC =
            StreamCodec.of(DragonListPayload::write, DragonListPayload::read);

    private static void write(FriendlyByteBuf buf, DragonListPayload payload) {
        buf.writeVarInt(payload.dragons.size());
        for (DragonEntry entry : payload.dragons) entry.write(buf);
        buf.writeVarLong(payload.restoreCooldownSeconds);
        buf.writeVarLong(payload.bringCooldownSeconds);
    }

    private static DragonListPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<DragonEntry> dragons = new ArrayList<>(size);
        for (int i = 0; i < size; i++) dragons.add(DragonEntry.read(buf));
        return new DragonListPayload(dragons, buf.readVarLong(), buf.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
