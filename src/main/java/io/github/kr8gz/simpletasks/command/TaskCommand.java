package io.github.kr8gz.simpletasks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.kr8gz.simpletasks.config.SimpleTasksConfig;
import io.github.kr8gz.simpletasks.data.PlayerState;
import io.github.kr8gz.simpletasks.data.StateManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.*;

public class TaskCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("task")
                .then(new TaskViewSubcommand().getSubcommandNode())
                .then(new TaskChangeSubcommand().getSubcommandNode())
                .then(new TaskClearSubcommand().getSubcommandNode())
                .then(new TaskListSubcommand().getSubcommandNode()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var playerState = StateManager.getPlayerState(server, handler.player.getUuid());
            if (!playerState.hasSeenTask.get()) {
                notifyPlayerTaskChanged(handler.player, playerState);
            }
        });
    }

    static void notifyPlayerTaskChanged(@Nullable PlayerEntity player, PlayerState playerState) {
        var isPlayerOnline = player != null;
        if (isPlayerOnline) {
            // TODO configurable notification sounds
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

    static List<String> getAvailableTasks(ServerCommandSource source) {
        var tasks = SimpleTasksConfig.TASKS.get();
        if (SimpleTasksConfig.ASSIGN_UNIQUE_TASKS.get()) {
            var playerStates = StateManager.getServerState(source.getServer()).playerStates;
            var assignedTasks = playerStates.values().stream()
                    .map(playerState -> playerState.task.get())
                    .collect(Collectors.toSet());

            tasks.removeAll(assignedTasks);
        }
        return tasks;
    }

    static void errorMessage(String message) {
        throw new CommandException(Text.of(message));
        // FIXME if a PlayerTargetSubcommand throws an exception it will not continue with the remaining players.
        //       revert to sending a red feedback message or check if .fork() can do anything helpful here (see ExecuteCommand?)
    }

    static abstract class Subcommand {
        private final LiteralArgumentBuilder<ServerCommandSource> subcommandNode;

        Subcommand(String name) {
            subcommandNode = buildSubcommandNode(literal(name));
        }

        abstract LiteralArgumentBuilder<ServerCommandSource> buildSubcommandNode(LiteralArgumentBuilder<ServerCommandSource> subcommandNode);

        public LiteralArgumentBuilder<ServerCommandSource> getSubcommandNode() {
            return subcommandNode;
        }
    }
}
