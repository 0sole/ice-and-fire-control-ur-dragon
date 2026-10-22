package com.osole.controlurdragon.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.item.DragonWhistleItem;
import com.osole.controlurdragon.network.GroupOrderPayload;
import com.osole.controlurdragon.network.RequestDragonListPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

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
    public static final KeyMapping STOP_ALL = new KeyMapping("key.controlurdragon.stop_all",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping CARRY = new KeyMapping("key.controlurdragon.carry",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private static final double TARGET_RANGE = 96D;
    private static final double AIM_ASSIST_DEGREES = 6D;

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
            event.register(STOP_ALL);
            event.register(CARRY);
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
            while (STOP_ALL.consumeClick()) {
                if (canOrder(minecraft))
                    PacketDistributor.sendToServer(new GroupOrderPayload(GroupOrderPayload.Order.STOP, -1));
            }
            while (CARRY.consumeClick()) {
                if (!canOrder(minecraft)) continue;
                LivingEntity target = lookedAt(minecraft.player);
                PacketDistributor.sendToServer(new GroupOrderPayload(GroupOrderPayload.Order.CARRY, target == null ? -1 : target.getId()));
            }
            while (GUARD_ALL.consumeClick()) {
                if (canOrder(minecraft))
                    PacketDistributor.sendToServer(new GroupOrderPayload(GroupOrderPayload.Order.GUARD, -1));
            }
        }

        @SubscribeEvent
        public static void onMovementInput(MovementInputUpdateEvent event) {
            if (Minecraft.getInstance().screen instanceof DragonWheelScreen) walkWhileOpen(event.getEntity(), event.getInput());
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

    private static boolean isCandidate(Player player, Entity entity) {
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) return false;
        if (living == player || living == player.getVehicle()) return false;
        return !(living instanceof TamableAnimal pet && pet.isOwnedBy(player));
    }

    private static boolean canSee(Player player, Vec3 eye, Vec3 point) {
        return player.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    static LivingEntity lookedAt(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        double range = TARGET_RANGE;
        BlockHitResult wall = player.level().clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) range = wall.getLocation().distanceTo(eye);

        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(range)), box,
                e -> e.isPickable() && isCandidate(player, e), range * range);
        if (hit != null) {
            Entity entity = hit.getEntity();
            if (entity instanceof PartEntity<?> part) entity = part.getParent();
            if (entity instanceof LivingEntity living) return living;
        }

        LivingEntity best = null;
        double bestAngle = AIM_ASSIST_DEGREES * Mth.DEG_TO_RAD;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(TARGET_RANGE),
                e -> isCandidate(player, e))) {
            Vec3 center = entity.getBoundingBox().getCenter();
            Vec3 toEntity = center.subtract(eye);
            double distance = toEntity.length();
            if (distance < 0.5 || distance > TARGET_RANGE) continue;
            double size = Math.atan2(entity.getBbWidth() * 0.5, distance);
            double angle = Math.acos(Mth.clamp(look.dot(toEntity.scale(1 / distance)), -1, 1)) - size;
            if (angle < bestAngle && canSee(player, eye, center)) {
                bestAngle = angle;
                best = entity;
            }
        }
        return best;
    }

    private static boolean held(long window, KeyMapping key) {
        InputConstants.Key bound = key.getKey();
        if (bound.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        return bound.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, bound.getValue());
    }

    static void walkWhileOpen(Player player, Input input) {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        Options options = minecraft.options;
        input.up = held(window, options.keyUp);
        input.down = held(window, options.keyDown);
        input.left = held(window, options.keyLeft);
        input.right = held(window, options.keyRight);
        input.jumping = held(window, options.keyJump);
        input.shiftKeyDown = held(window, options.keyShift);
        input.forwardImpulse = input.up == input.down ? 0F : input.up ? 1F : -1F;
        input.leftImpulse = input.left == input.right ? 0F : input.left ? 1F : -1F;
        if (input.shiftKeyDown) {
            input.forwardImpulse *= 0.3F;
            input.leftImpulse *= 0.3F;
        }
        if (input.up && held(window, options.keySprint) && !player.isSprinting()) player.setSprinting(true);
    }
}
