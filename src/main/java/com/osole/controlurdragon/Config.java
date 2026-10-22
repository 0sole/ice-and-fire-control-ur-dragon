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

    public static final ModConfigSpec.IntValue BRING_COOLDOWN_MINUTES = BUILDER
            .comment("Cooldown per player for the 'Bring dragon' order, which teleports a dragon to you from anywhere. 0 disables it.")
            .defineInRange("bringCooldownMinutes", 30, 0, 1440);

    public static final ModConfigSpec.IntValue LOW_HEALTH_PERCENT = BUILDER
            .comment("Health (percent of max) at which a dragon's 'when hurt badly' behaviour kicks in.")
            .defineInRange("lowHealthPercent", 30, 5, 90);

    public static final ModConfigSpec.BooleanValue IMPROVED_FOLLOW = BUILDER
            .comment("Replace Ice and Fire's follow behaviour: dragons keep a distance from you, run/fly at their own speed",
                    "and land next to you instead of trying to stand on top of you.")
            .define("improvedFollow", true);

    public static final ModConfigSpec.IntValue GUARD_RADIUS = BUILDER
            .comment("Radius (blocks) of the area a dragon protects with the 'Guard area' order.")
            .defineInRange("guardRadius", 24, 8, 128);

    public static final ModConfigSpec.IntValue GROUP_RANGE = BUILDER
            .comment("Group orders (hotkeys) reach your loaded dragons within this many blocks.")
            .defineInRange("groupOrderRange", 128, 16, 512);

    public static final ModConfigSpec.BooleanValue CHAT_REMOVAL_ALERTS = BUILDER
            .comment("Tell the owner in chat when one of their dragons is deleted without dying, with the code that removed it.")
            .define("chatRemovalAlerts", true);

    public static final ModConfigSpec.BooleanValue DEBUG_DELETE_BUTTON = BUILDER
            .comment("Show a 'Delete (test)' option in the dragon menu that deletes the dragon without killing it,",
                    "to test the lost-dragon detection and restore. Always shown in the development environment.")
            .define("debugDeleteButton", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
