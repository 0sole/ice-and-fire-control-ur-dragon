package com.osole.controlurdragon;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue FIX_SILENT_KILL = BUILDER
            .comment("Ice and Fire's DragonBaseEntity.kill() deletes a living dragon without a death, corpse or death message.",
                    "When true, kill() on a living dragon makes it die normally instead. Corpses are still removed as before.")
            .define("fixSilentKill", true);

    public static final ModConfigSpec.BooleanValue LOG_REMOVALS = BUILDER
            .comment("Write a warning with a full stack trace to the log whenever a tamed dragon is deleted without dying.",
                    "The stack trace shows which mod removed the dragon.")
            .define("logDragonRemovals", true);

    public static final ModConfigSpec.IntValue SNAPSHOT_INTERVAL = BUILDER
            .comment("How often (in ticks) a backup of each loaded tamed dragon is stored. 600 = 30 seconds.")
            .defineInRange("snapshotIntervalTicks", 600, 20, 72000);

    public static final ModConfigSpec.BooleanValue OWNERS_CAN_RESTORE = BUILDER
            .comment("If true, players can restore their own lost dragons with /cud restore without operator permission.",
                    "If false, only operators (permission level 2) can use it.")
            .define("ownersCanRestore", true);

    public static final ModConfigSpec.IntValue RESTORE_COOLDOWN_MINUTES = BUILDER
            .comment("Cooldown per player between two restores, in minutes. Operators ignore it.")
            .defineInRange("restoreCooldownMinutes", 30, 0, 1440);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
