package com.osole.controlurdragon.registry;

import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.item.DragonWhistleItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ControlUrDragon.MODID);

    public static final DeferredItem<DragonWhistleItem> DRAGON_WHISTLE = ITEMS.register("dragon_whistle",
            () -> new DragonWhistleItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
