package com.osole.controlurdragon;

import com.mojang.logging.LogUtils;
import com.osole.controlurdragon.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(ControlUrDragon.MODID)
public class ControlUrDragon {
    public static final String MODID = "controlurdragon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ControlUrDragon(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        modBus.addListener(ControlUrDragon::addToCreativeTabs);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            event.accept(ModItems.DRAGON_WHISTLE.get());
    }
}
