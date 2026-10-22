package com.osole.controlurdragon.network;

import com.osole.controlurdragon.ControlUrDragon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: "send me my dragons" (the menu key was pressed).
 */
public record RequestDragonListPayload() implements CustomPacketPayload {
    public static final RequestDragonListPayload INSTANCE = new RequestDragonListPayload();
    public static final Type<RequestDragonListPayload> TYPE = new Type<>(ControlUrDragon.id("request_dragon_list"));
    public static final StreamCodec<ByteBuf, RequestDragonListPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
