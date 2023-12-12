package io.github.kr8gz.questcraft;

import io.github.kr8gz.questcraft.commands.TaskCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QuestCraft implements ModInitializer {
    public static final String MOD_ID = "questcraft";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TaskCommand.register(dispatcher));
    }
}
