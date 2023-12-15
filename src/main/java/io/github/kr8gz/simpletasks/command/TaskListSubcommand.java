package io.github.kr8gz.simpletasks.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.kr8gz.simpletasks.config.SimpleTasksConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TaskListSubcommand extends TaskCommand.Subcommand {
    TaskListSubcommand() {
        super("list");
    }

    @Override
    LiteralArgumentBuilder<ServerCommandSource> buildSubcommandNode(LiteralArgumentBuilder<ServerCommandSource> subcommandNode) {
        return subcommandNode
                .requires(source -> source.hasPermissionLevel(2))
                .executes(TaskListSubcommand::execute);
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        var source = context.getSource();

        SimpleTasksConfig.reload();
        var tasks = TaskCommand.getAvailableTasks(source);

        MutableText message;
        if (tasks.isEmpty()) {
            message = Text.literal("No tasks found! Please check the config file.").formatted(Formatting.RED);
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
}
