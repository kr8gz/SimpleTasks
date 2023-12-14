package io.github.kr8gz.simpletasks.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.kr8gz.simpletasks.config.SimpleTasksConfig;
import io.github.kr8gz.simpletasks.data.PlayerState;
import io.github.kr8gz.simpletasks.data.StateManager;
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
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.*;

public class TaskCommand {
    private static final String ARGUMENT_PLAYER = "player";

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("task")
                .executes(context -> new TaskCommand(context).executeView())
                .then(buildPlayerTargetArgument("view", TaskCommand::executeView, false))
                .then(buildPlayerTargetArgument("new", TaskCommand::executeNew, true))
                .then(buildPlayerTargetArgument("clear", TaskCommand::executeClear, false))
                .then(literal("list")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(TaskCommand::executeList))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var playerState = StateManager.getPlayerState(server, handler.player.getUuid());
            if (!playerState.hasSeenTask.get()) {
                notifyPlayerTaskChanged(handler.player, playerState);
            }
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildPlayerTargetArgument(String argumentName,
                                                                                         ToIntFunction<TaskCommand> executeFunction,
                                                                                         boolean reloadTasks) {
        return literal(argumentName)
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> executeFunction.applyAsInt(new TaskCommand(context)))
                .then(argument(ARGUMENT_PLAYER, GameProfileArgumentType.gameProfile())
                        .executes(context -> {
                            var targetProfiles = GameProfileArgumentType.getProfileArgument(context, ARGUMENT_PLAYER);
                            return multipleTargets(context, targetProfiles, executeFunction, reloadTasks);
                        }))
                .then(literal("*")
                        .executes(context -> {
                            var targetProfiles = StateManager.getAllServerProfiles(context.getSource().getServer());
                            return multipleTargets(context, targetProfiles, executeFunction, reloadTasks);
                        }));
    }

    private static final Random random = new Random();

    private final ServerCommandSource source;

    private final PlayerEntity targetPlayer;
    private final PlayerState targetPlayerState;
    private final String targetName;
    private final boolean isTargetSource;

    private final boolean reloadTasks;

    private TaskCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        this(context, context.getSource().getPlayerOrThrow().getGameProfile(), true);
    }

    private TaskCommand(CommandContext<ServerCommandSource> context, GameProfile targetProfile, boolean reloadTasks) {
        this.source = context.getSource();
        var server = source.getServer();

        this.targetName = targetProfile.getName();
        var targetUuid = targetProfile.getId();

        this.targetPlayer = server.getPlayerManager().getPlayer(targetUuid);
        this.targetPlayerState = StateManager.getPlayerState(server, targetUuid);

        var sourcePlayer = source.getPlayer();
        this.isTargetSource = sourcePlayer != null && sourcePlayer.getGameProfile() == targetProfile;

        this.reloadTasks = reloadTasks;
    }

    private static int multipleTargets(CommandContext<ServerCommandSource> context,
                                       Collection<GameProfile> targetProfiles,
                                       ToIntFunction<TaskCommand> executeFunction,
                                       boolean reloadTasks) {
        if (reloadTasks) SimpleTasksConfig.reload();
        return targetProfiles.stream()
                .map(profile -> new TaskCommand(context, profile, false))
                .mapToInt(executeFunction)
                .sum();
    }

    private static List<String> getAvailableTasks(ServerCommandSource source, boolean reloadTasks) {
        var tasks = SimpleTasksConfig.TASKS.get(reloadTasks);

        if (SimpleTasksConfig.ASSIGN_UNIQUE_TASKS.get(reloadTasks)) {
            var playerStates = StateManager.getServerState(source.getServer()).playerStates;
            var assignedTasks = playerStates.values().stream()
                    .map(playerState -> playerState.task.get())
                    .collect(Collectors.toSet());

            tasks.removeAll(assignedTasks);
        }

        return tasks;
    }

    private List<String> getAvailableTasksForTarget() {
        var tasks = getAvailableTasks(source, reloadTasks);
        if (!SimpleTasksConfig.ASSIGN_UNIQUE_TASKS.get(false)) {
            tasks.remove(targetPlayerState.task.get());
        }
        return tasks;
    }

    private static int errorMessage(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message).formatted(Formatting.RED), false);
        return 0;
    }

    private static void notifyPlayerTaskChanged(@Nullable PlayerEntity player, PlayerState playerState) {
        var isPlayerOnline = player != null;
        if (isPlayerOnline) {
            if (playerState.task.get().isEmpty()) {
                player.sendMessage(Text.literal("Your task was cleared.").formatted(Formatting.YELLOW));
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), SoundCategory.MASTER, 1.0f, 2.0f);
            } else {
                player.sendMessage(Text.literal("Your task was changed! New task: ").formatted(Formatting.YELLOW)
                        .append(Text.literal(playerState.task.get()).formatted(Formatting.GREEN)));
                player.playSound(SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }

        playerState.hasSeenTask.set(isPlayerOnline);
    }

    private int executeView() {
        var messageNoTask = Text.literal(isTargetSource ? "You don't currently have a task!" : "%s currently has no task!".formatted(targetName)).formatted(Formatting.RED);
        var messageCurrentTask = Text.literal(isTargetSource ? "Your current task: " : "%s's current task: ".formatted(targetName)).formatted(Formatting.YELLOW)
                .append(Text.literal(targetPlayerState.task.get()).formatted(Formatting.GREEN));

        var message = targetPlayerState.task.get().isEmpty() ? messageNoTask : messageCurrentTask;
        source.sendFeedback(() -> message, false);
        return 1;
    }

    private int executeNew() {
        var tasks = getAvailableTasksForTarget();
        if (tasks.isEmpty()) {
            return errorMessage(source, "Couldn't change %s's task! No tasks available.".formatted(targetName));
        }

        var randomTask = tasks.get(random.nextInt(tasks.size()));
        targetPlayerState.task.set(randomTask);
        notifyPlayerTaskChanged(targetPlayer, targetPlayerState);

        var message = Text.literal("Changed %s's task: ".formatted(targetName)).formatted(Formatting.YELLOW)
                .append(Text.literal(targetPlayerState.task.get()).formatted(Formatting.GREEN));
        source.sendFeedback(() -> message, true);
        return 1;
    }

    private int executeClear() {
        if (targetPlayerState.task.get().isEmpty()) {
            return errorMessage(source, isTargetSource ? "You already have no task!" : "%s already has no task!".formatted(targetName));
        }

        targetPlayerState.task.set("");
        notifyPlayerTaskChanged(targetPlayer, targetPlayerState);

        var message = Text.literal("Cleared %s's task".formatted(targetName)).formatted(Formatting.YELLOW);
        source.sendFeedback(() -> message, true);
        return 1;
    }

    private static int executeList(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();
        var tasks = getAvailableTasks(source, true);

        MutableText message;
        if (tasks.isEmpty()) {
            message = Text.literal("No tasks found! Please check the config file.").formatted(Formatting.RED);
        } else {
            message = Text.literal("Available tasks:").formatted(Formatting.YELLOW);
            for (String task : tasks) {
                message.append(Text.literal("\n- ").formatted(Formatting.YELLOW))
                        .append(Text.literal(task).formatted(Formatting.GREEN));
            }
        }

        source.sendFeedback(() -> message, false);
        return tasks.size();
    }
}
