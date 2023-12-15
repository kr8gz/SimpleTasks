package io.github.kr8gz.simpletasks.command;

import com.mojang.brigadier.Command;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TaskClearSubcommand extends PlayerTargetSubcommand {
    TaskClearSubcommand() {
        super("clear");
    }

    @Override
    int executeSingle(ServerCommandSource source, TargetPlayerContext target) {
        if (target.playerState.task.get().isEmpty()) {
            TaskCommand.errorMessage(target.isCommandSource ? "You already have no task!" : "%s already has no task!".formatted(target.name));
        }

        target.playerState.task.set("");
        TaskCommand.notifyPlayerTaskChanged(target.player, target.playerState);

        var message = Text.literal("Cleared %s's task".formatted(target.name)).formatted(Formatting.YELLOW);
        source.sendFeedback(() -> message, true);
        return Command.SINGLE_SUCCESS;
    }
}
