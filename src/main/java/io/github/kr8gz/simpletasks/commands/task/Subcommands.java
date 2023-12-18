package io.github.kr8gz.simpletasks.commands.task;

import com.electronwill.nightconfig.core.io.ParsingException;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.kr8gz.simpletasks.commands.Commands;
import io.github.kr8gz.simpletasks.config.SimpleTasksConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.List;
import java.util.Random;

class Subcommands {

    static final PlayerTargetSubcommand VIEW = new PlayerTargetSubcommand("view") {
        @Override
        int executeForSingleProfile(ServerCommandSource source, TargetPlayerContext target) {
            var noTaskMessage = Text.literal(target.isCommandSource ? "You don't currently have a task!" : "%s currently has no task!".formatted(target.name)).formatted(Formatting.RED);
            var currentTaskMessage = Text.literal(target.isCommandSource ? "Your current task: " : "%s's current task: ".formatted(target.name)).formatted(Formatting.YELLOW)
                    .append(Text.literal(target.playerState.currentTask.get()).formatted(Formatting.GREEN));

            var message = target.playerState.currentTask.get().isEmpty() ? noTaskMessage : currentTaskMessage;
            source.sendFeedback(() -> message, false);
            return Command.SINGLE_SUCCESS;
        }
    };

    static final PlayerTargetSubcommand CHANGE = new PlayerTargetSubcommand("new") {
        SimpleTasksConfig config;

        @Override
        int executeForProfiles(CommandContext<ServerCommandSource> context, Collection<GameProfile> targetProfiles) {
            try {
                return SimpleTasksConfig.use(config -> {
                    this.config = config;
                    return super.executeForProfiles(context, targetProfiles);
                });
            } catch (ParsingException e) {
                throw Commands.createException(e.getMessage());
            }
        }

        @Override
        int executeForSingleProfile(ServerCommandSource source, TargetPlayerContext target) {
            var tasks = TaskCommand.getAvailableTasks(config, source);
            tasks.remove(target.playerState.currentTask.get());
            if (tasks.isEmpty()) {
                throw Commands.createException("No more tasks available!");
            }

            var newTask = tasks.get(new Random().nextInt(tasks.size()));
            target.playerState.currentTask.set(newTask);
            TaskCommand.notifyPlayerTaskChanged(target.player, target.playerState);

            var infoMessage = Text.literal("%s's new task: ".formatted(target.name)).formatted(Formatting.YELLOW)
                    .append(Text.literal(target.playerState.currentTask.get()).formatted(Formatting.GREEN));
            source.sendFeedback(() -> infoMessage, false);

            var feedbackMessage = Text.literal("Changed %s's task".formatted(target.name));
            source.sendFeedback(() -> feedbackMessage, true);
            return Command.SINGLE_SUCCESS;
        }
    };

    static final PlayerTargetSubcommand CLEAR = new PlayerTargetSubcommand("clear") {
        @Override
        int executeForSingleProfile(ServerCommandSource source, TargetPlayerContext target) {
            if (target.playerState.currentTask.get().isEmpty()) {
                var message = Text.literal(target.isCommandSource ? "You already have no task!" : "%s already has no task!".formatted(target.name));
                source.sendFeedback(() -> message.formatted(Formatting.RED), false);
                return 0;
            }

            target.playerState.currentTask.set("");
            TaskCommand.notifyPlayerTaskChanged(target.player, target.playerState);

            var message = Text.literal("Cleared %s's task".formatted(target.name));
            source.sendFeedback(() -> message, true);
            return Command.SINGLE_SUCCESS;
        }
    };

    static final TaskCommand.Subcommand LIST = new TaskCommand.Subcommand("list") {
        @Override
        public void buildCommandNode(LiteralArgumentBuilder<ServerCommandSource> commandNodeBuilder) {
            commandNodeBuilder
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(this::execute);
        }

        private int execute(CommandContext<ServerCommandSource> context) {
            var source = context.getSource();

            List<String> tasks;
            try {
                tasks = SimpleTasksConfig.use(config -> TaskCommand.getAvailableTasks(config, source));
            } catch (ParsingException e) {
                throw Commands.createException(e.getMessage());
            }

            MutableText message;
            if (tasks.isEmpty()) {
                message = Text.literal("No tasks available!").formatted(Formatting.RED);
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
    };
}
