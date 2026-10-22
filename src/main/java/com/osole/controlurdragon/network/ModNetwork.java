package com.osole.controlurdragon.network;

import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.client.ClientHooks;
import com.osole.controlurdragon.server.DragonActions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ControlUrDragon.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToServer(RequestDragonListPayload.TYPE, RequestDragonListPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) DragonActions.sendList(player);
                }));

        registrar.playToServer(DragonActionPayload.TYPE, DragonActionPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) DragonActions.handle(player, payload);
                }));

        registrar.playToServer(GroupOrderPayload.TYPE, GroupOrderPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) DragonActions.handleGroup(player, payload);
                }));

        // ClientHooks is only loaded when this runs, which only happens on the client.
        registrar.playToClient(DragonListPayload.TYPE, DragonListPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHooks.onDragonList(payload)));
    }
}
