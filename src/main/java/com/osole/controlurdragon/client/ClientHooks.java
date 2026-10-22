package com.osole.controlurdragon.client;

import com.osole.controlurdragon.network.DragonListPayload;
import net.minecraft.client.Minecraft;

/**
 * Client-only reactions to packets from the server.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void onDragonList(DragonListPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DragonWheelScreen wheel) {
            wheel.setData(payload);
        } else if (minecraft.screen == null) {
            // Remember what the player looks at right now, for the menu's attack option.
            minecraft.setScreen(new DragonWheelScreen(payload,
                    minecraft.player == null ? null : ClientEvents.lookedAt(minecraft.player)));
        }
    }
}
