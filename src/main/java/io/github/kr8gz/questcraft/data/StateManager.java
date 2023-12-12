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
    private final HashMap<UUID, PlayerState> playerStates = new HashMap<>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        var playersNbt = new NbtCompound();

        playerStates.forEach((uuid, playerState) -> playersNbt.put(uuid.toString(), playerState.toNbt()));
        nbt.put("players", playersNbt);

        return nbt;
    }

    public static StateManager fromNbt(NbtCompound nbt) {
        var stateManager = new StateManager();
        var playersNbt = nbt.getCompound("players");

        for (var key : playersNbt.getKeys()) {
            var playerState = PlayerState.fromNbt(stateManager, playersNbt.getCompound(key));
            stateManager.playerStates.put(UUID.fromString(key), playerState);
        }

        return stateManager;
    }

    public static Set<GameProfile> getAllServerProfiles(MinecraftServer server) {
        var userCache = Objects.requireNonNull(server.getUserCache());

        // using STATS path instead of PLAYERDATA, since the latter contains an additional backup file for each player
        try (var playerFilePaths = Files.list(server.getSavePath(WorldSavePath.STATS))) {
            return playerFilePaths
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
        return world.getPersistentStateManager().getOrCreate(StateManager::fromNbt, StateManager::new, QuestCraft.MOD_ID);
    }

    public static PlayerState getPlayerState(MinecraftServer server, UUID uuid) {
        var stateManager = getServerState(server);
        return stateManager.playerStates.computeIfAbsent(uuid, id -> new PlayerState(stateManager));
    }
}
