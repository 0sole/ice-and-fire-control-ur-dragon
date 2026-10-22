package com.osole.controlurdragon.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

public final class CuriosCompat {
    private static Boolean loaded;

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("curios");
        return loaded;
    }

    public static boolean isEquipped(LivingEntity entity, Item item) {
        return isLoaded() && Equipped.check(entity, item);
    }

    private static final class Equipped {
        static boolean check(LivingEntity entity, Item item) {
            return CuriosApi.getCuriosInventory(entity).map(handler -> handler.isEquipped(item)).orElse(false);
        }
    }
}
