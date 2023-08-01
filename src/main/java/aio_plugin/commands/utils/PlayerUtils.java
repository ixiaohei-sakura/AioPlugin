package aio_plugin.commands.utils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class PlayerUtils {
    private final List<PlayerManager> playerManagers;
    private LocationHistoryTimer locationHistoryTimer;

    public PlayerUtils() {
        playerManagers = new LinkedList<>();
        locationHistoryTimer = new LocationHistoryTimer();
        locationHistoryTimer.start();
        registerListener();
    }

    private void registerListener() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerPlayConnectionEvents.JOIN.register(this::onPlayReady);
        ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayDisconnect);
    }

    private void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        final PlayerManager playerManager = new PlayerManager(this, handler.getPlayer());
        playerManager.load();
        playerManagers.add(playerManager);
    }

    private void onPlayDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        PlayerManager playerManager = getPlayerManager(handler.getPlayer());
        if (playerManager == null) {
            return;
        }
        try {
            playerManager.end();
        } catch (IOException e) {
            e.printStackTrace();
        }
        playerManagers.remove(playerManager);
    }

    private void onServerStarting(MinecraftServer server) {
        if (!locationHistoryTimer.isInterrupted()) {
            locationHistoryTimer.interrupt();
        }
        locationHistoryTimer = new LocationHistoryTimer();
        locationHistoryTimer.start();
    }

    private void onServerStopping(MinecraftServer server) {
        this.locationHistoryTimer.interrupt();
        this.saveAll();
        this.endAll();
    }

    public void saveAll() {
        for (final PlayerManager playerManager : playerManagers) {
            try {
                playerManager.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void endAll() {
        for (final PlayerManager playerManager : playerManagers) {
            try {
                playerManager.end();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public LocationHistoryTimer getLocationHistoryTimer() {
        return locationHistoryTimer;
    }

    public List<PlayerManager> getPlayerManagers() {
        return playerManagers;
    }

    public PlayerManager getPlayerManager(PlayerEntity player) {
        for (final PlayerManager playerManager : playerManagers) {
            if (playerManager.getPlayer() == player) {
                return playerManager;
            }
        }
        return null;
    }

    public void removePlayerManager(PlayerManager playerManager) {
        playerManagers.remove(playerManager);
    }

    public void removePlayerManager(PlayerEntity player) {
        removePlayerManager(getPlayerManager(player));
    }
}
