package io.github.kr8gz.simpletasks.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.kr8gz.simpletasks.state.PlayerState;
import io.github.kr8gz.simpletasks.state.StateManager;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Collection;
import java.util.Collections;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

abstract class TaskPlayerTargetSubcommand {
    static final String ARGUMENT_PLAYER = "player";

    final String name;

    public TaskPlayerTargetSubcommand(String name) {
        this.name = name;
    }

    LiteralArgumentBuilder<ServerCommandSource> buildCommandNode() {
        var profileSelectorArgument = argument(ARGUMENT_PLAYER, GameProfileArgumentType.gameProfile())
                .executes(context -> execute(context, GameProfileArgumentType.getProfileArgument(context, ARGUMENT_PLAYER)));

        var allServerProfilesArgument = literal("*")
                .executes(context -> execute(context, StateManager.getAllServerProfiles(context.getSource().getServer())));

        return literal(name)
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> execute(context, Collections.singleton(context.getSource().getPlayerOrThrow().getGameProfile())))
                .then(profileSelectorArgument)
                .then(allServerProfilesArgument);
    }

    int execute(CommandContext<ServerCommandSource> context, Collection<GameProfile> targetProfiles) {
        return targetProfiles.stream()
                .mapToInt(profile -> executeSingle(context.getSource(), new TargetPlayerContext(context, profile)))
                .sum();
    }

    abstract int executeSingle(ServerCommandSource source, TargetPlayerContext target);

    static class TargetPlayerContext {
        final PlayerEntity player;
        final PlayerState playerState;
        final String name;
        final boolean isCommandSource;

        TargetPlayerContext(CommandContext<ServerCommandSource> commandContext, GameProfile targetProfile) {
            var commandSource = commandContext.getSource();
            var server = commandSource.getServer();

            this.name = targetProfile.getName();
            var targetUuid = targetProfile.getId();

            this.player = server.getPlayerManager().getPlayer(targetUuid);
            this.playerState = StateManager.getPlayerState(server, targetUuid);

            var sourcePlayer = commandSource.getPlayer();
            this.isCommandSource = sourcePlayer != null && sourcePlayer.getGameProfile() == targetProfile;
        }
    }
}