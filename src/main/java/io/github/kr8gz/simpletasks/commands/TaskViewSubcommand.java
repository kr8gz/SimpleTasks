package io.github.kr8gz.simpletasks.commands;

import com.mojang.brigadier.Command;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

class TaskViewSubcommand extends PlayerTargetCommand {
    TaskViewSubcommand() {
        super("view");
    }

    @Override
    int executeSingle(ServerCommandSource source, TargetPlayerContext target) {
        var noTaskMessage = Text.literal(target.isCommandSource ? "You don't currently have a task!" : "%s currently has no task!".formatted(target.name)).formatted(Formatting.RED);
        var currentTaskMessage = Text.literal(target.isCommandSource ? "Your current task: " : "%s's current task: ".formatted(target.name)).formatted(Formatting.YELLOW)
                .append(Text.literal(target.playerState.task.get()).formatted(Formatting.GREEN));

        var message = target.playerState.task.get().isEmpty() ? noTaskMessage : currentTaskMessage;
        source.sendFeedback(() -> message, false);
        return Command.SINGLE_SUCCESS;
    }
}
