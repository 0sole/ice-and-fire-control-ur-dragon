package com.osole.controlurdragon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.osole.controlurdragon.Config;
import com.osole.controlurdragon.ControlUrDragon;
import com.osole.controlurdragon.tracking.DragonRecord;
import com.osole.controlurdragon.tracking.DragonRecordsData;
import com.osole.controlurdragon.tracking.DragonRestorer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.UUID;

/**
 * /cud list            - your dragons and their state
 * /cud info <uuid>     - details, including which mod removed a lost dragon
 * /cud restore <uuid>  - bring a lost dragon back from its backup
 */
@EventBusSubscriber(modid = ControlUrDragon.MODID)
public final class CudCommands {
    private CudCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("cud")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("info")
                        .then(Commands.argument("dragon", UuidArgument.uuid()).executes(CudCommands::info)))
                .then(Commands.literal("restore")
                        .then(Commands.argument("dragon", UuidArgument.uuid()).executes(CudCommands::restore))));
    }

    public static int list(ServerPlayer player) {
        DragonRecordsData data = DragonRecordsData.get(player.server);
        List<DragonRecord> dragons = data.getByOwner(player.getUUID());
        if (dragons.isEmpty()) {
            player.sendSystemMessage(Component.translatable("controlurdragon.list.empty"));
            return 0;
        }
        player.sendSystemMessage(Component.translatable("controlurdragon.list.header", dragons.size()).withStyle(ChatFormatting.GOLD));
        for (DragonRecord record : dragons) player.sendSystemMessage(line(record));
        return dragons.size();
    }

    private static MutableComponent line(DragonRecord record) {
        String id = record.dragonId.toString();
        MutableComponent line = Component.literal(" - ").append(Component.literal(record.name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" [" + record.stage + "] ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("controlurdragon.state." + record.state.name().toLowerCase()).withStyle(stateColor(record.state)))
                .append(Component.literal(String.format(" %s %.0f %.0f %.0f", record.dimension, record.x, record.y, record.z)).withStyle(ChatFormatting.DARK_GRAY));
        // Click to get the info command; hover shows the id.
        line.withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cud info " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(id))));
        if (record.state == DragonRecord.State.LOST) {
            line.append(Component.literal(" ")).append(Component.translatable("controlurdragon.list.restore_button")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cud restore " + id))));
        }
        return line;
    }

    private static ChatFormatting stateColor(DragonRecord.State state) {
        return switch (state) {
            case ALIVE -> ChatFormatting.GREEN;
            case UNLOADED -> ChatFormatting.AQUA;
            case STORED -> ChatFormatting.YELLOW;
            case LOST -> ChatFormatting.RED;
            case DEAD -> ChatFormatting.DARK_GRAY;
        };
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        UUID id = UuidArgument.getUuid(ctx, "dragon");
        DragonRecord record = DragonRecordsData.get(source.getServer()).get(id);
        if (record == null) {
            source.sendFailure(Component.translatable("controlurdragon.unknown_dragon"));
            return 0;
        }
        source.sendSuccess(() -> line(record), false);
        if (!record.lostReason.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("controlurdragon.info.lost", record.lostReason).withStyle(ChatFormatting.RED), false);
            source.sendSuccess(() -> Component.literal(record.lostTrace).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        UUID id = UuidArgument.getUuid(ctx, "dragon");
        DragonRecordsData data = DragonRecordsData.get(source.getServer());
        DragonRecord record = data.get(id);
        if (record == null) {
            source.sendFailure(Component.translatable("controlurdragon.unknown_dragon"));
            return 0;
        }

        boolean operator = source.hasPermission(2);
        if (!operator) {
            if (!Config.OWNERS_CAN_RESTORE.get() || !player.getUUID().equals(record.ownerId)) {
                source.sendFailure(Component.translatable("controlurdragon.restore.no_permission"));
                return 0;
            }
            long cooldownTicks = Config.RESTORE_COOLDOWN_MINUTES.get() * 60L * 20L;
            long last = data.getLastRestore(player.getUUID());
            long now = player.serverLevel().getGameTime();
            if (last != Long.MIN_VALUE && now - last < cooldownTicks) {
                long minutesLeft = (cooldownTicks - (now - last)) / 1200 + 1;
                source.sendFailure(Component.translatable("controlurdragon.restore.cooldown", minutesLeft));
                return 0;
            }
        }

        Component error = DragonRestorer.restore(player, record);
        if (error != null) {
            source.sendFailure(error);
            return 0;
        }
        if (!operator) data.setLastRestore(player.getUUID(), player.serverLevel().getGameTime());
        source.sendSuccess(() -> Component.translatable("controlurdragon.restore.done", record.name).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
