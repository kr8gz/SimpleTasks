package io.github.kr8gz.simpletasks.commands.task;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.kr8gz.simpletasks.state.PlayerState;
import io.github.kr8gz.simpletasks.state.StateManager;
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
    void buildCommandNode(LiteralArgumentBuilder<ServerCommandSource> commandNodeBuilder) {
        var profileSelectorArgument = argument(ARGUMENT_PLAYER, GameProfileArgumentType.gameProfile())
                .executes(context -> executeForProfiles(context, GameProfileArgumentType.getProfileArgument(context, ARGUMENT_PLAYER)));

        var allServerProfilesArgument = literal("*")
                .executes(context -> executeForProfiles(context, StateManager.getAllServerProfiles(context.getSource().getServer())));

        commandNodeBuilder
                .requires(source -> source.hasPermissionLevel(2))
                .executes(this::execute)
                .then(profileSelectorArgument)
                .then(allServerProfilesArgument);
    }

    int execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return executeForProfiles(context, Collections.singleton(context.getSource().getPlayerOrThrow().getGameProfile()));
    }

    int executeForProfiles(CommandContext<ServerCommandSource> context, Collection<GameProfile> targetProfiles) {
        return targetProfiles.stream()
                .mapToInt(profile -> executeForSingleProfile(context.getSource(), new TargetPlayerContext(context, profile)))
                .sum();
    }

    abstract int executeForSingleProfile(ServerCommandSource source, TargetPlayerContext target);

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