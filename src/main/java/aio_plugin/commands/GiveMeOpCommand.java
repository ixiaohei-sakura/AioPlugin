package aio_plugin.commands;

import carpet.utils.Messenger;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

public class GiveMeOpCommand extends Command{
    private final List<GameProfile> opedPlayers = new LinkedList<>();
    private MinecraftServer server = null;

    @Override
    protected List<LiteralArgumentBuilder<ServerCommandSource>> registration() {
        ServerLifecycleEvents.SERVER_STOPPING.register(this::deOpAll);
        List<LiteralArgumentBuilder<ServerCommandSource>> literalArgumentBuilders = new LinkedList<>();
        literalArgumentBuilders.add(
                literal("giveMeOp").executes((context -> {
                    GameProfile profile = Objects.requireNonNull(context.getSource().getPlayer()).getGameProfile();
                    if (profile == null) {
                        return -1;
                    }
                    opedPlayers.add(profile);
                    deopPlayer(context.getSource().getServer(), profile);
                    context.getSource().getServer().getPlayerManager().addToOperators(profile);
                    context.getSource().sendMessage(Messenger.s("Made " + context.getSource().getPlayer().getEntityName() + " an operator for 5 minutes."));
                    return 0;
                }))
        );
        return literalArgumentBuilders;
    }

    private void deopPlayer(final MinecraftServer server, final GameProfile profile) {
        if (this.server == null) {
            this.server = server;
        }
        Timer timer = new Timer(profile.getName() + "-deop-timer", false);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                server.getPlayerManager().removeFromOperators(profile);
                opedPlayers.remove(profile);
                server.sendMessage(Messenger.s(profile.getName() + " is no longer an operator"));
                for (final PlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.sendMessage(Messenger.s(profile.getName() + " is no longer an operator."));
                }
            }
        }, 300000);
    }

    private void deOpAll(MinecraftServer server) {
        if (this.server == null) {
            this.server = server;
        }
        if (opedPlayers.size() == 0) {
            return;
        }
        for (GameProfile gameProfile : opedPlayers) {
            server.getPlayerManager().removeFromOperators(gameProfile);
        }
    }
}
