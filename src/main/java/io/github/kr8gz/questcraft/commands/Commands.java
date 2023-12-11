package io.github.kr8gz.questcraft.commands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class Commands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TaskCommand.register(dispatcher));
    }
}
