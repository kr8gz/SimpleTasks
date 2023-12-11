package io.github.kr8gz.questcraft.data;

import com.mojang.authlib.GameProfile;
import io.github.kr8gz.questcraft.QuestCraft;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class StateManager extends PersistentState {
    public HashMap<UUID, PlayerData> players = new HashMap<>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        var playersNbt = new NbtCompound();

        players.forEach((uuid, playerData) -> playersNbt.put(uuid.toString(), playerData.toNbt()));
        nbt.put("players", playersNbt);

        return nbt;
    }

    public static StateManager fromNbt(NbtCompound nbt) {
        var state = new StateManager();
        var playersNbt = nbt.getCompound("players");
        
        playersNbt.getKeys().forEach(key -> {
            var playerData = PlayerData.fromNbt(playersNbt.getCompound(key));
            state.players.put(UUID.fromString(key), playerData);
        });

        return state;
    }

    public static Set<GameProfile> getAllServerProfiles(MinecraftServer server) {
        var userCache = Objects.requireNonNull(server.getUserCache());
        try (var pathStream = Files.list(server.getSavePath(WorldSavePath.STATS))) {
            return pathStream
                    .map(Path::toFile)
                    .map(File::getName)
                    .map(FilenameUtils::removeExtension)
                    .map(UUID::fromString)
                    .map(userCache::getByUuid)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            QuestCraft.LOGGER.error("Exception while getting server game profiles:", e);
            throw new RuntimeException(e);
        }
    }

    public static StateManager getServerState(MinecraftServer server) {
        var world = Objects.requireNonNull(server.getWorld(World.OVERWORLD));
        var state = world.getPersistentStateManager().getOrCreate(StateManager::fromNbt, StateManager::new, QuestCraft.MOD_ID);

        state.markDirty();
        return state;
    }

    public static PlayerData getPlayerState(MinecraftServer server, UUID uuid) {
        return getServerState(server).players.computeIfAbsent(uuid, u -> new PlayerData());
    }
}
