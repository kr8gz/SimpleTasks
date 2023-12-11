package io.github.kr8gz.questcraft.data;

import net.minecraft.nbt.NbtCompound;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;

public class PlayerData {
    private final Set<Entry<?>> entries = new HashSet<>();

    public class Entry<T> {
        private final String key;
        private final T initialValue;
        private T value;

        private final TriConsumer<NbtCompound, String, T> writer;
        private final BiFunction<NbtCompound, String, T> reader;

        Entry(String key, T initialValue, TriConsumer<NbtCompound, String, T> writer, BiFunction<NbtCompound, String, T> reader) {
            this.key = key;
            this.value = this.initialValue = initialValue;

            this.writer = writer;
            this.reader = reader;

            entries.add(this);
        }

        public T get() {
            return this.value;
        }

        public void set(@NotNull T value) {
            this.value = Objects.requireNonNull(value, "NBT data must not be null");
        }

        void writeNbt(NbtCompound tag) {
            writer.accept(tag, key, value);
        }

        void readNbt(NbtCompound tag) {
            value = tag.contains(key) ? reader.apply(tag, key) : initialValue;
        }
    }

    public NbtCompound toNbt() {
        var tag = new NbtCompound();
        for (Entry<?> entry : this.entries) {
            entry.writeNbt(tag);
        }
        return tag;
    }

    public static PlayerData fromNbt(NbtCompound tag) {
        var playerData = new PlayerData();
        for (Entry<?> entry : playerData.entries) {
            entry.readNbt(tag);
        }
        return playerData;
    }

    public Entry<String> task = new Entry<>("task", "", NbtCompound::putString, NbtCompound::getString);

    public Entry<Boolean> hasSeenTask = new Entry<>("hasSeenTask", true, NbtCompound::putBoolean, NbtCompound::getBoolean);
}
