package io.github.kr8gz.simpletasks.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.kr8gz.simpletasks.data.PlayerState;
import io.github.kr8gz.simpletasks.data.StateManager;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Collection;
import java.util.Collections;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

abstract class PlayerTargetSubcommand extends TaskCommand.Subcommand {
    static final String ARGUMENT_PLAYER = "player";

    PlayerTargetSubcommand(String name) {
        super(name);
    }

    @Override
    LiteralArgumentBuilder<ServerCommandSource> buildSubcommandNode(LiteralArgumentBuilder<ServerCommandSource> subcommandNode) {
        var profileSelectorArgument = argument(ARGUMENT_PLAYER, GameProfileArgumentType.gameProfile())
                .executes(context -> execute(context, GameProfileArgumentType.getProfileArgument(context, ARGUMENT_PLAYER)));

        var allServerProfilesArgument = literal("*")
                .executes(context -> execute(context, StateManager.getAllServerProfiles(context.getSource().getServer())));

        return subcommandNode
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

    abstract int executeSingle(ServerCommandSource source, TargetPlayerContext targetPlayerContext);

    static class TargetPlayerContext {
        final PlayerEntity player;
        final PlayerState playerState;
        final String name;
        final boolean isCommandSource;

        TargetPlayerContext(CommandContext<ServerCommandSource> context, GameProfile targetProfile) {
            var source = context.getSource();
            var server = source.getServer();

            this.name = targetProfile.getName();
            var targetUuid = targetProfile.getId();

            this.player = server.getPlayerManager().getPlayer(targetUuid);
            this.playerState = StateManager.getPlayerState(server, targetUuid);

            var sourcePlayer = source.getPlayer();
            this.isCommandSource = sourcePlayer != null && sourcePlayer.getGameProfile() == targetProfile;
        }
    }
}