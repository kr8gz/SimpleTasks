package io.github.kr8gz.simpletasks.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigSpec;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.ConfigParser;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.yaml.YamlFormat;
import io.github.kr8gz.simpletasks.SimpleTasks;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SimpleTasksConfig {

    private static final Path PATH = Paths.get("config", SimpleTasks.MOD_ID + ".yml");
    private static final List<Entry<?>> ENTRIES = new ArrayList<>();
    private static final String TEMPLATE;

    static {
        var templateBuilder = new StringBuilder("# SimpleTasks generated template\n");
        ENTRIES.stream()
                .map(Entry::getTemplate)
                .forEach(templateBuilder::append);

        TEMPLATE = templateBuilder.toString();
    }

    private static final ConfigParser<?> yamlParser = YamlFormat.defaultInstance().createParser();
    private static final ConfigSpec spec = new ConfigSpec();

    private final Config config;

    private SimpleTasksConfig() throws ParsingException {
        try {
            Files.createDirectories(PATH.getParent());
            var templateStream = new ByteArrayInputStream(TEMPLATE.getBytes(StandardCharsets.UTF_8));
            this.config = yamlParser.parse(PATH, FileNotFoundAction.copyData(templateStream));

            if (!spec.isCorrect(config)) {
                throw new ParsingException("Config file is incorrect");
            }
        }
        catch (ParsingException e) {
            var message = "Couldn't parse %s: %s".formatted(PATH, e.getMessage());
            SimpleTasks.LOGGER.error(message);
            throw new ParsingException(message);
        }
        catch (Exception e) {
            SimpleTasks.LOGGER.error("Exception while getting tasks config:", e);
            throw new RuntimeException(e);
        }
    }

    public class Entry<T> {
        final String key;
        final T defaultValue;
        final Predicate<Object> validator;

        private Entry(String key, T defaultValue, Predicate<Object> validator) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.validator = validator;

            this.defineSpec();
            ENTRIES.add(this);
        }

        void defineSpec() {
            spec.define(key, defaultValue, validator);
        }

        public T get() {
            return config.get(key);
        }

        String getTemplate() {
            return "\n%s: %s\n".formatted(key, defaultValue);
        }
    }

    public class ListEntry<T> extends Entry<List<T>> {
        private ListEntry(String key, List<T> defaultValue, Predicate<Object> validator) {
            super(key, defaultValue, validator);
        }

        @Override
        void defineSpec() {
            spec.defineList(key, defaultValue, validator);
        }

        @Override
        public List<T> get() {
            return new ArrayList<>(super.get());
        }

        @Override
        String getTemplate() {
            var list = defaultValue.isEmpty() ? "\n#  - ..." : defaultValue.stream()
                    .map("\n  - %s"::formatted)
                    .collect(Collectors.joining());

            return "\n%s:%s\n".formatted(key, list);
        }
    }

    public final ListEntry<String> tasks = new ListEntry<>("tasks", new ArrayList<>(List.of("Example task")), v -> v instanceof String task && !task.isEmpty());

    public final Entry<Boolean> assignUniqueTasks = new Entry<>("assignUniqueTasks", false, v -> v instanceof Boolean);

    // TODO
    // notificationSounds:
    //   - change:
    //       - sound: block.conduit.activate
    //       - volume: 1.0
    //       - pitch: 1.0
    //   - clear:
    //       - sound: block.note_block.harp
    //       - volume: 1.0
    //       - pitch: 2.0

    // or a command that should be run on notification, /execute at <target> run [config.runOnNotification]

    // TODO redo the entire whole fucking config system ig

    public static <T> T use(Function<SimpleTasksConfig, T> function) throws ParsingException {
        return function.apply(new SimpleTasksConfig());
    }
}
