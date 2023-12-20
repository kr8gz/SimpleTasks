package io.github.kr8gz.simpletasks.commands.task;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
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

import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.literal;

public class TaskCommand implements CommandRegistrationCallback {
    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("task")
                .executes(Subcommands.VIEW::execute)
                .then(Subcommands.VIEW.commandNode)
                .then(Subcommands.CHANGE.commandNode)
                .then(Subcommands.CLEAR.commandNode)
                .then(Subcommands.LIST.commandNode));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var playerState = StateManager.getPlayerState(server, handler.player.getUuid());
            if (!playerState.lastSeenTask.get().equals(playerState.currentTask.get())) {
                notifyPlayerTaskChanged(handler.player, playerState);
            }
        });
    }

    public static void notifyPlayerTaskChanged(@Nullable PlayerEntity player, PlayerState playerState) {
        var isPlayerOnline = player != null;
        var newTask = playerState.currentTask.get();

        if (isPlayerOnline && !newTask.equals(playerState.lastSeenTask.get())) {
            playerState.lastSeenTask.set(newTask);
            // TODO configurable notification sounds
            if (newTask.isEmpty()) {
                player.sendMessage(Text.literal("Your task was cleared.").formatted(Formatting.YELLOW));
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), SoundCategory.MASTER, 1.0f, 2.0f);
            } else {
                player.sendMessage(Text.literal("Your task was changed! New task: ").formatted(Formatting.YELLOW)
                        .append(Text.literal(newTask).formatted(Formatting.GREEN)));
                player.playSound(SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    public static List<String> getAvailableTasks(SimpleTasksConfig config, ServerCommandSource source) {
        var tasks = config.tasks.get();
        if (config.assignUniqueTasks.get()) {
            var playerStates = StateManager.getServerState(source.getServer()).playerStates;
            var assignedTasks = playerStates.values().stream()
                    .map(playerState -> playerState.currentTask.get())
                    .collect(Collectors.toSet());

            tasks.removeAll(assignedTasks);
        }
        return tasks;
    }

    static abstract class Subcommand {
        final CommandNode<ServerCommandSource> commandNode;

        Subcommand(String name) {
            var builder = literal(name);
            buildCommandNode(builder);
            this.commandNode = builder.build();
        }

        abstract void buildCommandNode(LiteralArgumentBuilder<ServerCommandSource> commandNodeBuilder);
    }
}
