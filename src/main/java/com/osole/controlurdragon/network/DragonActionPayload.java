package com.osole.controlurdragon.network;

import com.osole.controlurdragon.ControlUrDragon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record DragonActionPayload(UUID dragon, Action action, String text, int value) implements CustomPacketPayload {
    public static final int MAX_NAME_LENGTH = 32;

    public enum Action {
        SIT, FOLLOW, WANDER, BRING, RENAME, RESTORE, ATTACK, STOP, GUARD, SETTING, CARRY, DROP, DELETE
    }

    public DragonActionPayload(UUID dragon, Action action, String text) {
        this(dragon, action, text, -1);
    }

    public static final Type<DragonActionPayload> TYPE = new Type<>(ControlUrDragon.id("dragon_action"));
    public static final StreamCodec<FriendlyByteBuf, DragonActionPayload> CODEC =
            StreamCodec.of(DragonActionPayload::write, DragonActionPayload::read);

    private static void write(FriendlyByteBuf buf, DragonActionPayload payload) {
        buf.writeUUID(payload.dragon);
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.text, MAX_NAME_LENGTH * 4);
        buf.writeVarInt(payload.value);
    }

    private static DragonActionPayload read(FriendlyByteBuf buf) {
        return new DragonActionPayload(buf.readUUID(), buf.readEnum(Action.class), buf.readUtf(MAX_NAME_LENGTH * 4), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
