package io.github.kr8gz.simpletasks.commands;

import com.mojang.brigadier.Command;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

class TaskClearSubcommand extends TaskPlayerTargetSubcommand {
    TaskClearSubcommand() {
        super("clear");
    }

    @Override
    int executeSingle(ServerCommandSource source, TargetPlayerContext target) {
        if (target.playerState.task.get().isEmpty()) {
            var message = Text.literal(target.isCommandSource ? "You already have no task!" : "%s already has no task!".formatted(target.name));
            source.sendFeedback(() -> message.formatted(Formatting.RED), false);
            return 0;
        }

        target.playerState.task.set("");
        TaskCommand.notifyPlayerTaskChanged(target.player, target.playerState);

        var message = Text.literal("Cleared %s's task".formatted(target.name));
        source.sendFeedback(() -> message, true);
        return Command.SINGLE_SUCCESS;
    }
}
