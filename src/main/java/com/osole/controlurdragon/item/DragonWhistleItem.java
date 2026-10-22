package com.osole.controlurdragon.item;

import com.osole.controlurdragon.registry.ModItems;
import com.osole.controlurdragon.server.DragonActions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The control item. While it is anywhere in the inventory, the menu key opens the dragon menu.
 * Using it (right click) opens the menu too.
 */
public class DragonWhistleItem extends Item {
    public DragonWhistleItem(Properties properties) {
        super(properties);
    }

    public static boolean isCarriedBy(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.DRAGON_WHISTLE.get())) return true;
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) DragonActions.sendList(serverPlayer);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.controlurdragon.dragon_whistle.tooltip"));
    }
}
