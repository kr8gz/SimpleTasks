package io.github.kr8gz.simpletasks.commands;

import io.github.kr8gz.simpletasks.commands.task.TaskCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandException;
import net.minecraft.text.Text;

public class Commands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register(new TaskCommand());
    }

    public static CommandException createException(String message) {
        return new CommandException(Text.of(message));
    }
}
