package io.github.kr8gz.questcraft.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.kr8gz.questcraft.config.TaskConfig;
import io.github.kr8gz.questcraft.data.PlayerData;
import io.github.kr8gz.questcraft.data.StateManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

import static net.minecraft.server.command.CommandManager.*;

public class TaskCommand {

    private static final Random random = new Random();

    private enum TaskAction {
        CLEAR,
        CHANGE,
        VIEW,
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("task")
                .executes(context -> runAction(context.getSource(), TaskAction.VIEW))
                .then(playerArgument("clear", TaskAction.CLEAR))
                .then(playerArgument("new", TaskAction.CHANGE))
                .then(playerArgument("view", TaskAction.VIEW))
                .then(literal("list")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> displayList(context.getSource())))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var playerData = StateManager.getPlayerState(server, handler.player.getUuid());
            if (!playerData.hasSeenTask.get()) {
                notifyPlayer(handler.player, playerData);
            }
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playerArgument(String name, TaskAction action) {
        return literal(name)
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> runAction(context.getSource(), action))
                .then(argument("player", GameProfileArgumentType.gameProfile())
                        .executes(context -> {
                            var profiles = GameProfileArgumentType.getProfileArgument(context, "player");
                            return runMultiple(context.getSource(), action, profiles);
                        }))
                .then(literal("*")
                        .executes(context -> {
                            var profiles = StateManager.getAllServerProfiles(context.getSource().getServer());
                            return runMultiple(context.getSource(), action, profiles);
                        }));
    }

    private static int runMultiple(ServerCommandSource source, TaskAction action, Collection<GameProfile> gameProfiles) {
        TaskConfig.reload();
        var count = 0;
        for (GameProfile gameProfile : gameProfiles) {
            count += runAction(source, action, gameProfile, false);
        }
        return count;
    }

    private static int runAction(ServerCommandSource source, TaskAction action) throws CommandSyntaxException {
        return runAction(source, action, source.getPlayerOrThrow().getGameProfile(), true);
    }

    private static int runAction(ServerCommandSource source, TaskAction action, GameProfile gameProfile, boolean reloadTasks) {
        var server = source.getServer();

        var sourcePlayer = source.getPlayer();
        var targetPlayer = server.getPlayerManager().getPlayer(gameProfile.getId());
        var playerData = StateManager.getPlayerState(server, gameProfile.getId());

        var targetsSelf = sourcePlayer != null && sourcePlayer.getGameProfile() == gameProfile;

        switch (action) {
            case CLEAR -> {
                if (playerData.task.get().isEmpty()) {
                    var message = Text.literal(targetsSelf ? "You already have no task!" : gameProfile.getName() + " already has no task!");
                    source.sendFeedback(() -> message.formatted(Formatting.RED), false);
                    return 0;
                }

                playerData.task.set("");
                notifyPlayer(targetPlayer, playerData);

                var message = Text.literal("Cleared " + gameProfile.getName() + "'s task");
                source.sendFeedback(() -> message.formatted(Formatting.YELLOW), true);
            }

            case CHANGE -> {
                var tasks = TaskConfig.getTasks(reloadTasks);

                if (tasks.isEmpty()) {
                    var message = Text.literal("No tasks found! Please check the config file.");
                    source.sendFeedback(() -> message.formatted(Formatting.RED), false);
                    return 0;
                }

                tasks.remove(playerData.task.get());

                if (tasks.isEmpty()) {
                    var message = Text.literal("Couldn't change " + gameProfile.getName() + "'s task! No other tasks available.");
                    source.sendFeedback(() -> message.formatted(Formatting.RED), false);
                    return 0;
                }

                var randomTask = tasks.get(random.nextInt(tasks.size()));
                playerData.task.set(randomTask);
                notifyPlayer(targetPlayer, playerData);

                var message = Text.literal("Changed " + gameProfile.getName() + "'s task: ").formatted(Formatting.YELLOW)
                        .append(Text.literal(playerData.task.get()).formatted(Formatting.GREEN));
                source.sendFeedback(() -> message, true);
            }

            case VIEW -> {
                var messageNoTask = Text.literal(targetsSelf ? "You don't currently have a task!" : gameProfile.getName() + " currently has no task!").formatted(Formatting.RED);
                var messageCurrentTask = Text.literal(targetsSelf ? "Your current task: " : gameProfile.getName() + "'s current task: ").formatted(Formatting.YELLOW)
                        .append(Text.literal(playerData.task.get()).formatted(Formatting.GREEN));

                var message = playerData.task.get().isEmpty() ? messageNoTask : messageCurrentTask;

                if (targetsSelf) {
                    sourcePlayer.sendMessage(message);
                } else {
                    source.sendFeedback(() -> message, false);
                }
            }
        }

        return 1;
    }

    private static void notifyPlayer(PlayerEntity player, PlayerData playerData) {
        var shouldNotify = player != null;
        playerData.hasSeenTask.set(shouldNotify);

        if (shouldNotify) {
            if (playerData.task.get().isEmpty()) {
                player.sendMessage(Text.literal("Your task was cleared.").formatted(Formatting.YELLOW));
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), SoundCategory.MASTER, 1.0f, 2.0f);
            } else {
                player.sendMessage(Text.literal("Your task was changed! New task: ").formatted(Formatting.YELLOW)
                        .append(Text.literal(playerData.task.get()).formatted(Formatting.GREEN)));
                player.playSound(SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    private static int displayList(ServerCommandSource source) {
        var tasks = TaskConfig.getTasks(true);
        if (tasks.isEmpty()) {
            var feedback = Text.literal("No tasks found. Please check the config file");
            source.sendFeedback(() -> feedback.formatted(Formatting.RED), false);
            return 0;
        }

        var list = Text.literal("Available tasks:").formatted(Formatting.YELLOW);
        for (String task : tasks) {
            list.append(Text.literal("\n- ").formatted(Formatting.YELLOW))
                    .append(Text.literal(task).formatted(Formatting.GREEN));
        }
        source.sendFeedback(() -> list, false);
        return 1;
    }
}
