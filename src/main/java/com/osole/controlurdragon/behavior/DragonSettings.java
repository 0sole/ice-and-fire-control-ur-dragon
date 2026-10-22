package com.osole.controlurdragon.behavior;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.entity.util.dragon.IafDragonAttacks;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

public final class DragonSettings {
    private static final String ATTACK_STYLE = "controlurdragon_attack_style";
    private static final String LOW_HEALTH = "controlurdragon_low_health";
    private static final String STAY_SEATED = "controlurdragon_stay_seated";
    private static final String NO_GRIEF = "controlurdragon_no_grief";

    public enum AttackStyle {
        MIXED, BREATH, BITE
    }

    public enum LowHealth {
        FIGHT, FLEE, COME, HORN
    }

    public enum Setting {
        STANCE, ATTACK_STYLE, LOW_HEALTH, WAKE_ON_HIT, BREAK_BLOCKS
    }

    private DragonSettings() {
    }

    private static <E extends Enum<E>> E byId(Class<E> type, int id) {
        E[] values = type.getEnumConstants();
        return id >= 0 && id < values.length ? values[id] : values[0];
    }

    public static AttackStyle attackStyle(DragonBaseEntity dragon) {
        return byId(AttackStyle.class, dragon.getPersistentData().getInt(ATTACK_STYLE));
    }

    public static LowHealth lowHealth(DragonBaseEntity dragon) {
        return byId(LowHealth.class, dragon.getPersistentData().getInt(LOW_HEALTH));
    }

    public static boolean wakesOnHit(DragonBaseEntity dragon) {
        return !dragon.getPersistentData().getBoolean(STAY_SEATED);
    }

    public static boolean breaksBlocks(DragonBaseEntity dragon) {
        return !dragon.getPersistentData().getBoolean(NO_GRIEF);
    }

    public static int get(DragonBaseEntity dragon, Setting setting) {
        return switch (setting) {
            case STANCE -> DragonOrders.getStance(dragon).ordinal();
            case ATTACK_STYLE -> attackStyle(dragon).ordinal();
            case LOW_HEALTH -> lowHealth(dragon).ordinal();
            case WAKE_ON_HIT -> wakesOnHit(dragon) ? 1 : 0;
            case BREAK_BLOCKS -> breaksBlocks(dragon) ? 1 : 0;
        };
    }

    public static void set(DragonBaseEntity dragon, Setting setting, int value) {
        CompoundTag data = dragon.getPersistentData();
        switch (setting) {
            case STANCE -> {
                DragonOrders.Stance stance = DragonOrders.Stance.byId(value);
                DragonOrders.setStance(dragon, stance);
                if (stance == DragonOrders.Stance.PASSIVE) DragonOrders.stop(dragon);
            }
            case ATTACK_STYLE -> {
                data.putInt(ATTACK_STYLE, byId(AttackStyle.class, value).ordinal());
                dragon.randomizeAttacks();
            }
            case LOW_HEALTH -> data.putInt(LOW_HEALTH, byId(LowHealth.class, value).ordinal());
            case WAKE_ON_HIT -> data.putBoolean(STAY_SEATED, value == 0);
            case BREAK_BLOCKS -> data.putBoolean(NO_GRIEF, value == 0);
        }
    }

    private static AttackStyle effectiveStyle(DragonBaseEntity dragon) {
        AttackStyle style = attackStyle(dragon);
        return style == AttackStyle.BREATH && dragon.getDragonStage() < 2 ? AttackStyle.MIXED : style;
    }

    public static void applyAttackStyle(DragonBaseEntity dragon) {
        switch (effectiveStyle(dragon)) {
            case BREATH -> {
                dragon.groundAttack = IafDragonAttacks.Ground.FIRE;
                if (dragon.airAttack == IafDragonAttacks.Air.TACKLE) {
                    dragon.airAttack = dragon.getRandom().nextBoolean() ? IafDragonAttacks.Air.SCORCH_STREAM : IafDragonAttacks.Air.HOVER_BLAST;
                }
            }
            case BITE -> {
                dragon.groundAttack = IafDragonAttacks.Ground.BITE;
                dragon.airAttack = IafDragonAttacks.Air.TACKLE;
            }
            default -> {
            }
        }
    }

    public static boolean matchesAttackStyle(DragonBaseEntity dragon) {
        return switch (effectiveStyle(dragon)) {
            case MIXED -> true;
            case BREATH -> dragon.groundAttack == IafDragonAttacks.Ground.FIRE && dragon.airAttack != IafDragonAttacks.Air.TACKLE;
            case BITE -> dragon.groundAttack == IafDragonAttacks.Ground.BITE && dragon.airAttack == IafDragonAttacks.Air.TACKLE;
        };
    }

    public static int optionCount(Setting setting) {
        return switch (setting) {
            case STANCE -> DragonOrders.Stance.values().length;
            case ATTACK_STYLE -> AttackStyle.values().length;
            case LOW_HEALTH -> LowHealth.values().length;
            case WAKE_ON_HIT, BREAK_BLOCKS -> 2;
        };
    }

    public static String nameKey(Setting setting) {
        return "controlurdragon.setting." + setting.name().toLowerCase(Locale.ROOT);
    }

    public static String valueKey(Setting setting, int value) {
        String name = switch (setting) {
            case STANCE -> DragonOrders.Stance.byId(value).name();
            case ATTACK_STYLE -> byId(AttackStyle.class, value).name();
            case LOW_HEALTH -> byId(LowHealth.class, value).name();
            case WAKE_ON_HIT, BREAK_BLOCKS -> value == 0 ? "OFF" : "ON";
        };
        return nameKey(setting) + "." + name.toLowerCase(Locale.ROOT);
    }
}
