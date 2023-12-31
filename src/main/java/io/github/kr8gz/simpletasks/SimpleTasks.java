package io.github.kr8gz.simpletasks;

import io.github.kr8gz.simpletasks.commands.Commands;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleTasks implements ModInitializer {
    public static final String MOD_ID = "simple_tasks";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Commands.register();
    }
}
