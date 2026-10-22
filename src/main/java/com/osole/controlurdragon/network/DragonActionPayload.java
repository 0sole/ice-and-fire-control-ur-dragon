package com.osole.controlurdragon.network;

import com.osole.controlurdragon.ControlUrDragon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client → server: an order for one dragon chosen in the menu.
 *
 * @param text the new name for {@link Action#RENAME}, empty otherwise
 */
public record DragonActionPayload(UUID dragon, Action action, String text) implements CustomPacketPayload {
    public static final int MAX_NAME_LENGTH = 32;

    public enum Action {
        SIT, FOLLOW, WANDER, COME, RENAME, RESTORE
    }

    public static final Type<DragonActionPayload> TYPE = new Type<>(ControlUrDragon.id("dragon_action"));
    public static final StreamCodec<FriendlyByteBuf, DragonActionPayload> CODEC =
            StreamCodec.of(DragonActionPayload::write, DragonActionPayload::read);

    private static void write(FriendlyByteBuf buf, DragonActionPayload payload) {
        buf.writeUUID(payload.dragon);
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.text, MAX_NAME_LENGTH * 4);
    }

    private static DragonActionPayload read(FriendlyByteBuf buf) {
        return new DragonActionPayload(buf.readUUID(), buf.readEnum(Action.class), buf.readUtf(MAX_NAME_LENGTH * 4));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
