package io.github.kr8gz.questcraft.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.kr8gz.questcraft.config.TaskConfig;
import io.github.kr8gz.questcraft.data.PlayerState;
import io.github.kr8gz.questcraft.data.StateManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.ToIntFunction;

import static net.minecraft.server.command.CommandManager.*;

public class TaskCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("task")
                .executes(context -> new TaskCommand(context).executeView())
                .then(buildPlayerTargetArgument("view", TaskCommand::executeView))
                .then(buildPlayerTargetArgument("new", TaskCommand::executeNew, true))
                .then(buildPlayerTargetArgument("clear", TaskCommand::executeClear))
                .then(literal("list")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(TaskCommand::executeList))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var playerState = StateManager.getPlayerState(server, handler.player.getUuid());
            if (!playerState.hasSeenTask.get()) {
                notifyTargetPlayer(handler.player, playerState);
            }
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPlayerTargetArgument(String argumentName, ToIntFunction<TaskCommand> executeFunction) {
        return buildPlayerTargetArgument(argumentName, executeFunction, false);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPlayerTargetArgument(String argumentName, ToIntFunction<TaskCommand> executeFunction, boolean groupReloadTasks) {
        return literal(argumentName)
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> executeFunction.applyAsInt(new TaskCommand(context)))
                .then(argument("player", GameProfileArgumentType.gameProfile())
                        .executes(context -> multipleTargets(context, GameProfileArgumentType.getProfileArgument(context, "player"), executeFunction, groupReloadTasks)))
                .then(literal("*")
                        .executes(context -> multipleTargets(context, StateManager.getAllServerProfiles(context.getSource().getServer()), executeFunction, groupReloadTasks)));
    }

    private static final Random random = new Random();

    private final ServerCommandSource source;

    private final String targetName;
    private final PlayerEntity targetPlayer;
    private final PlayerState playerState;
    private final boolean isTargetSource;

    private final boolean reloadTasks;

    private TaskCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        this(context, context.getSource().getPlayerOrThrow().getGameProfile(), true);
    }

    private TaskCommand(CommandContext<ServerCommandSource> context, GameProfile gameProfile, boolean reloadTasks) {
        this.source = context.getSource();
        var server = source.getServer();

        this.targetName = gameProfile.getName();
        var targetUuid = gameProfile.getId();

        this.targetPlayer = server.getPlayerManager().getPlayer(targetUuid);
        this.playerState = StateManager.getPlayerState(server, targetUuid);

        var sourcePlayer = source.getPlayer();
        this.isTargetSource = sourcePlayer != null && sourcePlayer.getGameProfile() == gameProfile;

        this.reloadTasks = reloadTasks;
    }

    private static int multipleTargets(CommandContext<ServerCommandSource> context, Collection<GameProfile> gameProfiles, ToIntFunction<TaskCommand> executeFunction, boolean groupReloadTasks) {
        if (groupReloadTasks) TaskConfig.reload();
        return gameProfiles.stream()
                .map(profile -> new TaskCommand(context, profile, !groupReloadTasks))
                .mapToInt(executeFunction)
                .sum();
    }

    private static void notifyTargetPlayer(@Nullable PlayerEntity targetPlayer, PlayerState playerState) {
        if (targetPlayer == null) { // player offline
            playerState.hasSeenTask.set(false);
        } else {
            if (playerState.task.get().isEmpty()) {
                targetPlayer.sendMessage(Text.literal("Your task was cleared.").formatted(Formatting.YELLOW));
                targetPlayer.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), SoundCategory.MASTER, 1.0f, 2.0f);
            } else {
                targetPlayer.sendMessage(Text.literal("Your task was changed! New task: ").formatted(Formatting.YELLOW)
                        .append(Text.literal(playerState.task.get()).formatted(Formatting.GREEN)));
                targetPlayer.playSound(SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    private int errorMessage(String message) {
        source.sendFeedback(() -> Text.literal(message).formatted(Formatting.RED), false);
        return 0;
    }

    private int executeView() {
        var messageNoTask = Text.literal(isTargetSource ? "You don't currently have a task!" : "%s currently has no task!".formatted(targetName)).formatted(Formatting.RED);
        var messageCurrentTask = Text.literal(isTargetSource ? "Your current task: " : "%s's current task: ".formatted(targetName)).formatted(Formatting.YELLOW)
                .append(Text.literal(playerState.task.get()).formatted(Formatting.GREEN));

        var message = playerState.task.get().isEmpty() ? messageNoTask : messageCurrentTask;
        source.sendFeedback(() -> message, false);
        return 1;
    }

    private int executeNew() {
        var tasks = TaskConfig.getTasks(reloadTasks);
        if (tasks.isEmpty()) {
            return errorMessage("No tasks found! Please check the config file.");
        }

        tasks.remove(playerState.task.get());
        if (tasks.isEmpty()) {
            return errorMessage("Couldn't change %s's task! No other tasks available.".formatted(targetName));
        }

        var randomTask = tasks.get(random.nextInt(tasks.size()));
        playerState.task.set(randomTask);
        notifyTargetPlayer(targetPlayer, playerState);

        var message = Text.literal("Changed %s's task: ".formatted(targetName)).formatted(Formatting.YELLOW)
                .append(Text.literal(playerState.task.get()).formatted(Formatting.GREEN));
        source.sendFeedback(() -> message, true);
        return 1;
    }

    private int executeClear() {
        if (playerState.task.get().isEmpty()) {
            return errorMessage(isTargetSource ? "You already have no task!" : "%s already has no task!".formatted(targetName));
        }

        playerState.task.set("");
        notifyTargetPlayer(targetPlayer, playerState);

        var message = Text.literal("Cleared %s's task".formatted(targetName)).formatted(Formatting.YELLOW);
        source.sendFeedback(() -> message, true);
        return 1;
    }

    private static int executeList(CommandContext<ServerCommandSource> context) {
        var tasks = TaskConfig.getTasks(true);
        MutableText message;

        if (tasks.isEmpty()) {
            message = Text.literal("No tasks found. Please check the config file").formatted(Formatting.RED);
        } else {
            message = Text.literal("Available tasks:").formatted(Formatting.YELLOW);
            for (String task : tasks) {
                message.append(Text.literal("\n- ").formatted(Formatting.YELLOW))
                        .append(Text.literal(task).formatted(Formatting.GREEN));
            }
        }

        context.getSource().sendFeedback(() -> message, false);
        return tasks.size();
    }
}
