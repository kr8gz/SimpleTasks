package io.github.kr8gz.simpletasks.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.github.kr8gz.simpletasks.config.SimpleTasksConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.Random;

public class TaskChangeSubcommand extends PlayerTargetSubcommand {
    TaskChangeSubcommand() {
        super("new");
    }

    @Override
    int execute(CommandContext<ServerCommandSource> context, Collection<GameProfile> targetProfiles) {
        SimpleTasksConfig.reload();
        return super.execute(context, targetProfiles);
    }

    @Override
    int executeSingle(ServerCommandSource source, TargetPlayerContext target) {
        var tasks = TaskCommand.getAvailableTasks(source);
        tasks.remove(target.playerState.task.get());
        if (tasks.isEmpty()) {
            TaskCommand.errorMessage("Couldn't change %s's task! No tasks available.".formatted(target.name));
        }

        var randomTask = tasks.get(new Random().nextInt(tasks.size()));
        target.playerState.task.set(randomTask);
        TaskCommand.notifyPlayerTaskChanged(target.player, target.playerState);

        var message = Text.literal("Changed %s's task: ".formatted(target.name)).formatted(Formatting.YELLOW)
                .append(Text.literal(target.playerState.task.get()).formatted(Formatting.GREEN));
        source.sendFeedback(() -> message, true);
        return Command.SINGLE_SUCCESS;
    }
}
