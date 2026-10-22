package com.osole.controlurdragon.network;

import com.osole.controlurdragon.ControlUrDragon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the player's dragons, used to open or refresh the menu.
 *
 * @param debug show the test-only "delete" option
 */
public record DragonListPayload(List<DragonEntry> dragons, long restoreCooldownSeconds, boolean debug) implements CustomPacketPayload {
    public static final Type<DragonListPayload> TYPE = new Type<>(ControlUrDragon.id("dragon_list"));
    public static final StreamCodec<FriendlyByteBuf, DragonListPayload> CODEC =
            StreamCodec.of(DragonListPayload::write, DragonListPayload::read);

    private static void write(FriendlyByteBuf buf, DragonListPayload payload) {
        buf.writeVarInt(payload.dragons.size());
        for (DragonEntry entry : payload.dragons) entry.write(buf);
        buf.writeVarLong(payload.restoreCooldownSeconds);
        buf.writeBoolean(payload.debug);
    }

    private static DragonListPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<DragonEntry> dragons = new ArrayList<>(size);
        for (int i = 0; i < size; i++) dragons.add(DragonEntry.read(buf));
        return new DragonListPayload(dragons, buf.readVarLong(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
