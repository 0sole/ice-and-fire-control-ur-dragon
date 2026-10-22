package com.osole.controlurdragon.network;

import com.osole.controlurdragon.ControlUrDragon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GroupOrderPayload(Order order, int targetId) implements CustomPacketPayload {
    public enum Order {
        ATTACK, FOLLOW, GUARD, STOP, CARRY, SIT
    }

    public static final Type<GroupOrderPayload> TYPE = new Type<>(ControlUrDragon.id("group_order"));
    public static final StreamCodec<FriendlyByteBuf, GroupOrderPayload> CODEC =
            StreamCodec.of(GroupOrderPayload::write, GroupOrderPayload::read);

    private static void write(FriendlyByteBuf buf, GroupOrderPayload payload) {
        buf.writeEnum(payload.order);
        buf.writeVarInt(payload.targetId);
    }

    private static GroupOrderPayload read(FriendlyByteBuf buf) {
        return new GroupOrderPayload(buf.readEnum(Order.class), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
