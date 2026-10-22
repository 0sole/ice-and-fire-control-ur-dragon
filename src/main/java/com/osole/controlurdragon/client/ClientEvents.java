package com.osole.controlurdragon.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.item.DragonWhistleItem;
import com.osole.controlurdragon.network.GroupOrderPayload;
import com.osole.controlurdragon.network.RequestDragonListPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Keys, all changeable in Options → Controls → "IaF: Control Ur Dragon":
 * V menu, G all dragons attack what you look at, H all dragons follow, J all dragons guard here.
 */
public final class ClientEvents {
    public static final String CATEGORY = "key.categories.controlurdragon";
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.controlurdragon.menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping ATTACK_ALL = new KeyMapping("key.controlurdragon.attack_all",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping FOLLOW_ALL = new KeyMapping("key.controlurdragon.follow_all",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping GUARD_ALL = new KeyMapping("key.controlurdragon.guard_all",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    /** How far the "attack what I look at" ray reaches. */
    private static final double TARGET_RANGE = 96D;

    private ClientEvents() {
    }

    @EventBusSubscriber(modid = ControlUrDragon.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
            event.register(ATTACK_ALL);
            event.register(FOLLOW_ALL);
            event.register(GUARD_ALL);
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
            while (ATTACK_ALL.consumeClick()) {
                if (!canOrder(minecraft)) continue;
                LivingEntity target = lookedAt(minecraft.player);
                if (target == null) {
                    minecraft.player.displayClientMessage(Component.translatable("controlurdragon.action.attack.no_target"), true);
                    continue;
                }
                PacketDistributor.sendToServer(new GroupOrderPayload(GroupOrderPayload.Order.ATTACK, target.getId()));
            }
            while (FOLLOW_ALL.consumeClick()) {
                if (canOrder(minecraft))
                    PacketDistributor.sendToServer(new GroupOrderPayload(GroupOrderPayload.Order.FOLLOW, -1));
            }
            while (GUARD_ALL.consumeClick()) {
                if (canOrder(minecraft))
                    PacketDistributor.sendToServer(new GroupOrderPayload(GroupOrderPayload.Order.GUARD, -1));
            }
        }

        private static boolean canOrder(Minecraft minecraft) {
            if (minecraft.player == null || minecraft.screen != null) return false;
            if (!DragonWhistleItem.isCarriedBy(minecraft.player)) {
                minecraft.player.displayClientMessage(Component.translatable("controlurdragon.menu.no_whistle"), true);
                return false;
            }
            return true;
        }
    }

    /** The living thing the player is looking at, up to {@link #TARGET_RANGE} blocks, not through walls. */
    static LivingEntity lookedAt(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        double range = TARGET_RANGE;
        HitResult block = player.pick(range, 1F, false);
        if (block.getType() != HitResult.Type.MISS) range = block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e != player.getVehicle(), range * range);
        if (hit == null) return null;
        Entity entity = hit.getEntity();
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }
}
