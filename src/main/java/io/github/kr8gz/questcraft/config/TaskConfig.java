package io.github.kr8gz.questcraft.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigSpec;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.ConfigParser;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.yaml.YamlFormat;
import io.github.kr8gz.questcraft.QuestCraft;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TaskConfig {
    private static final Path PATH = Paths.get("config", "tasks.yml");

    private static final String KEY_TASKS = "tasks";
    private static final String TEMPLATE_TASKS = KEY_TASKS + ":\n  - ";

    private static final ConfigSpec spec = new ConfigSpec();
    private static final ConfigParser<?> parser = YamlFormat.defaultInstance().createParser();

    private static Config config = YamlFormat.newConfig();

    static {
        spec.defineList(KEY_TASKS, new ArrayList<>(), e -> e instanceof String);
    }

    public static void reload() {
        try {
            Files.createDirectories(PATH.getParent());
            var templateStream = new ByteArrayInputStream(TEMPLATE_TASKS.getBytes(StandardCharsets.UTF_8));
            config = parser.parse(PATH, FileNotFoundAction.copyData(templateStream));
            spec.correct(config);
        } catch (ParsingException e) {
            QuestCraft.LOGGER.warn("Parsing %s failed. Continuing with previous (or empty) task list".formatted(PATH));
        } catch (Exception e) {
            QuestCraft.LOGGER.error("Exception while getting tasks config:", e);
        }
    }

    public static List<String> getTasks(boolean reload) {
        if (reload) reload();
        return new ArrayList<>(config.get(KEY_TASKS));
    }
}
