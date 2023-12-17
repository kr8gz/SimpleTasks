package io.github.kr8gz.simpletasks.commands;

import com.mojang.brigadier.CommandDispatcher;
import io.github.kr8gz.simpletasks.config.SimpleTasksConfig;
import io.github.kr8gz.simpletasks.state.PlayerState;
import io.github.kr8gz.simpletasks.state.StateManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.literal;

class TaskCommand implements CommandRegistrationCallback {
    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("task")
                .executes(context -> new TaskViewSubcommand().execute(context, Collections.singleton(context.getSource().getPlayerOrThrow().getGameProfile())))
                .then(new TaskViewSubcommand().buildCommandNode())
                .then(new TaskChangeSubcommand().buildCommandNode())
                .then(new TaskClearSubcommand().buildCommandNode())
                .then(new TaskListSubcommand().buildCommandNode()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var playerState = StateManager.getPlayerState(server, handler.player.getUuid());
            if (!playerState.hasSeenTask.get()) {
                notifyPlayerTaskChanged(handler.player, playerState);
            }
        });
    }

    public static void notifyPlayerTaskChanged(@Nullable PlayerEntity player, PlayerState playerState) {
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

    public static List<String> getAvailableTasks(SimpleTasksConfig config, ServerCommandSource source) {
        var tasks = config.tasks.get();
        if (config.assignUniqueTasks.get()) {
            var playerStates = StateManager.getServerState(source.getServer()).playerStates;
            var assignedTasks = playerStates.values().stream()
                    .map(playerState -> playerState.task.get())
                    .collect(Collectors.toSet());

            tasks.removeAll(assignedTasks);
        }
        return tasks;
    }
}
