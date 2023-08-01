package aio_plugin.commands.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class LocationHistoryTimer extends Thread{
    final private Map<PlayerManager, PlayerManager.ScheduledRecordTask> scheduledRecordTaskMap;

    public LocationHistoryTimer() {
        super("LocationHistoryTimer");
        this.setDaemon(true);
        scheduledRecordTaskMap = new LinkedHashMap<>();
    }

    public void register(PlayerManager playerManager, PlayerManager.ScheduledRecordTask task) {
        this.scheduledRecordTaskMap.put(playerManager, task);
    }

    public void remove(PlayerManager playerManager) {
        this.scheduledRecordTaskMap.remove(playerManager);
    }

    @Override
    public void run() {
        super.run();
        while (!this.isInterrupted()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            for (final PlayerManager playerManager : scheduledRecordTaskMap.keySet()) {
                if (playerManager.playerIsOffline()) {
                    continue;
                }
                scheduledRecordTaskMap.get(playerManager).tick();
            }
        }
    }
}
