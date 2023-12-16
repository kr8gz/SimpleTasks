package io.github.kr8gz.simpletasks.state;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerState {
    private final Set<Entry<?>> entries = new HashSet<>();
    private final StateManager stateManager;

    PlayerState(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    public class Entry<T> {
        private final String key;
        private final T defaultValue;
        private T value;

        @FunctionalInterface
        private interface NbtWriter<T> {
            void write(NbtCompound tag, String key, T value);
        }

        private final NbtWriter<T> writer;

        @FunctionalInterface
        private interface NbtReader<T> {
            T read(NbtCompound tag, String key);
        }

        private final NbtReader<T> reader;

        Entry(String key, T defaultValue, NbtWriter<T> writer, NbtReader<T> reader) {
            this.key = key;
            this.value = this.defaultValue = defaultValue;

            this.writer = writer;
            this.reader = reader;

            entries.add(this);
        }

        public T get() {
            return this.value;
        }

        public void set(@NotNull T value) {
            this.value = Objects.requireNonNull(value, "NBT data must not be null");
            stateManager.markDirty();
        }

        void writeNbt(NbtCompound tag) {
            writer.write(tag, key, value);
        }

        void readNbt(NbtCompound tag) {
            value = tag.contains(key) ? reader.read(tag, key) : defaultValue;
        }
    }

    public NbtCompound toNbt() {
        var tag = new NbtCompound();
        for (Entry<?> entry : this.entries) {
            entry.writeNbt(tag);
        }
        return tag;
    }

    public static PlayerState fromNbt(StateManager stateManager, NbtCompound tag) {
        var playerState = new PlayerState(stateManager);
        for (Entry<?> entry : playerState.entries) {
            entry.readNbt(tag);
        }
        return playerState;
    }

    public Entry<String> task = new Entry<>("task", "", NbtCompound::putString, NbtCompound::getString);
    public Entry<Boolean> hasSeenTask = new Entry<>("hasSeenTask", true, NbtCompound::putBoolean, NbtCompound::getBoolean);
}
