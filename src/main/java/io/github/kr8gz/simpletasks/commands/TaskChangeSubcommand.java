package io.github.kr8gz.simpletasks.commands;

import com.electronwill.nightconfig.core.io.ParsingException;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.github.kr8gz.simpletasks.config.SimpleTasksConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.Random;

class TaskChangeSubcommand extends PlayerTargetCommand {
    SimpleTasksConfig config;

    TaskChangeSubcommand() {
        super("new");
    }

    @Override
    int execute(CommandContext<ServerCommandSource> context, Collection<GameProfile> targetProfiles) {
        try {
            return SimpleTasksConfig.use(config -> {
                this.config = config;
                return super.execute(context, targetProfiles);
            });
        } catch (ParsingException e) {
            throw Commands.createException(e.getMessage());
        }
    }

    @Override
    int executeSingle(ServerCommandSource source, TargetPlayerContext target) {
        var tasks = TaskCommand.getAvailableTasks(config, source);
        tasks.remove(target.playerState.task.get());
        if (tasks.isEmpty()) {
            throw Commands.createException("No more tasks available!");
        }

        var randomTask = tasks.get(new Random().nextInt(tasks.size()));
        target.playerState.task.set(randomTask);
        TaskCommand.notifyPlayerTaskChanged(target.player, target.playerState);

        var infoMessage = Text.literal("%s's new task: ".formatted(target.name)).formatted(Formatting.YELLOW)
                .append(Text.literal(target.playerState.task.get()).formatted(Formatting.GREEN));
        source.sendFeedback(() -> infoMessage, false);

        var feedbackMessage = Text.literal("Changed %s's task".formatted(target.name));
        source.sendFeedback(() -> feedbackMessage, true);
        return Command.SINGLE_SUCCESS;
    }
}
