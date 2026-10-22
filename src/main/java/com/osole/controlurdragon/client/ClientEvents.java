package com.osole.controlurdragon.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.item.DragonWhistleItem;
import com.osole.controlurdragon.network.RequestDragonListPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The menu key (default V). Changeable in Options → Controls → "IaF: Control Ur Dragon".
 */
public final class ClientEvents {
    public static final String CATEGORY = "key.categories.controlurdragon";
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.controlurdragon.menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private ClientEvents() {
    }

    @EventBusSubscriber(modid = ControlUrDragon.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
        }
    }

    @EventBusSubscriber(modid = ControlUrDragon.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_MENU.consumeClick()) {
                if (minecraft.player == null || minecraft.screen != null) continue;
                if (!DragonWhistleItem.isCarriedBy(minecraft.player)) {
                    minecraft.player.displayClientMessage(Component.translatable("controlurdragon.menu.no_whistle"), true);
                    continue;
                }
                // The server answers with the dragon list, which opens the menu (ClientHooks).
                PacketDistributor.sendToServer(RequestDragonListPayload.INSTANCE);
            }
        }
    }
}
