package aio_plugin.commands.utils;

import aio_plugin.utils.AioMessenger;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.Messenger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.io.*;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class PlayerManager {
    private final PlayerUtils playerUtils;
    private final PlayerEntity player;
    private final Deque<PlayerLocationRecord> locationHistory;
    private short maxLocationHistoryLength = 20;
    private int recordIntervalInSec = 300;
    private boolean enableAutoRecord = true;
    private boolean sendNotificationWhenRecording = true;
    private final Map<String, PlayerLocationRecord> waypoints;
    private final Map<String, TpaWaitingInformation> waitingInformationMap;
    private boolean playerIsOffline = false;
    private final ScheduledRecordTask scheduledRecordTask;
    private final File file;

    public PlayerManager (PlayerUtils playerUtils, PlayerEntity player) {
        this.playerUtils = playerUtils;
        this.player = player;
        this.file = new File("./aio_data/player_data/%s.json".formatted(player.getEntityName()));
        this.locationHistory = new LinkedList<>();
        this.waypoints = new LinkedHashMap<>();
        this.waitingInformationMap = new LinkedHashMap<>();
        this.load();
        this.scheduledRecordTask = new ScheduledRecordTask(this);
        playerUtils.getLocationHistoryTimer().register(this, scheduledRecordTask);
    }

    public void end() throws IOException {
        this.playerIsOffline = true;
        this.playerUtils.getLocationHistoryTimer().remove(this);
        this.save();
        this.locationHistory.clear();
        this.waypoints.clear();
        this.waitingInformationMap.clear();
    }

    public void save() throws IOException {
        Gson gson = new Gson();
        JsonObject a = new JsonObject();
        a.add("locationHistories", gson.toJsonTree(locationHistory));
        a.add("waypoints", gson.toJsonTree(waypoints));
        a.add("waitingInformation", gson.toJsonTree(waitingInformationMap));
        a.add("maxHistoryLength", gson.toJsonTree(maxLocationHistoryLength));
        a.add("recordIntervalInSec", gson.toJsonTree(recordIntervalInSec));
        a.add("enableAutoRecord", gson.toJsonTree(enableAutoRecord));
        a.add("sendNotificationWhenRecording", gson.toJsonTree(sendNotificationWhenRecording));
        file.getParentFile().mkdirs();
        if (!file.exists()) {
            if (!file.createNewFile()) {
                return;
            }
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(gson.toJson(a));
        writer.close();
    }

    public void load() {
        if (!this.file.exists()) {
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder stringBuilder = new StringBuilder();
            String line = null;
            String ls = System.getProperty("line.separator");
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            reader.close();
            JsonObject jsonObject = new Gson().fromJson(stringBuilder.toString(), JsonObject.class);
            //load locationHistories
            for (final JsonElement jsonElement : jsonObject.get("locationHistories").getAsJsonArray().asList()) {
                double x = jsonElement.getAsJsonObject().get("x").getAsDouble();
                double y = jsonElement.getAsJsonObject().get("y").getAsDouble();
                double z = jsonElement.getAsJsonObject().get("z").getAsDouble();
                String dimensionName = jsonElement.getAsJsonObject().get("dimension").getAsString();
                float yaw = jsonElement.getAsJsonObject().get("yaw").getAsFloat();
                float pitch = jsonElement.getAsJsonObject().get("pitch").getAsFloat();
                long time = jsonElement.getAsJsonObject().get("time").getAsLong();
                this.locationHistory.add(new PlayerLocationRecord(new Vec3d(x, y, z), new Identifier(dimensionName), yaw, pitch, time));
            }
            //load waypoints
            for (final String name : jsonObject.get("waypoints").getAsJsonObject().asMap().keySet()) {
                final JsonElement jsonElement = jsonObject.get("waypoints").getAsJsonObject().asMap().get(name);
                double x = jsonElement.getAsJsonObject().get("x").getAsDouble();
                double y = jsonElement.getAsJsonObject().get("y").getAsDouble();
                double z = jsonElement.getAsJsonObject().get("z").getAsDouble();
                String dimensionName = jsonElement.getAsJsonObject().get("dimension").getAsString();
                float yaw = jsonElement.getAsJsonObject().get("yaw").getAsFloat();
                float pitch = jsonElement.getAsJsonObject().get("pitch").getAsFloat();
                long time = jsonElement.getAsJsonObject().get("time").getAsLong();
                this.waypoints.put(name, new PlayerLocationRecord(new Vec3d(x, y, z), new Identifier(dimensionName), yaw, pitch, time));
            }
            //load tpaRequests
            for (final String playerName : jsonObject.get("waitingInformation").getAsJsonObject().keySet()) {
                final JsonElement jsonElement = jsonObject.get("waitingInformation").getAsJsonObject().get(playerName);
                boolean direction = jsonElement.getAsJsonObject().get("direction").getAsBoolean();
                long time = jsonElement.getAsJsonObject().get("timeStamp").getAsLong();
                this.waitingInformationMap.put(playerName, new TpaWaitingInformation(time, direction));
            }
            //load settings
            this.maxLocationHistoryLength = jsonObject.get("maxHistoryLength").getAsShort();
            this.recordIntervalInSec = jsonObject.get("recordIntervalInSec").getAsInt();
            this.enableAutoRecord = jsonObject.get("enableAutoRecord").getAsBoolean();
            this.sendNotificationWhenRecording = jsonObject.get("sendNotificationWhenRecording").getAsBoolean();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long recordLocationHistory() {
        final long timeStamp = System.currentTimeMillis();
        if (this.sendNotificationWhenRecording) {
            this.player.sendMessage(Messenger.s("Location recorded"));
        }
        this.locationHistory.offer(PlayerLocationRecord.of(player));
        while (locationHistory.size() > this.maxLocationHistoryLength) {
            locationHistory.pollLast();
        }
        return timeStamp;
    }

    public void removeLocationHistory(long timeStamp) {
        this.locationHistory.remove(timeStamp);
    }

    public PlayerLocationRecord getLatestRecord() {
        return this.locationHistory.peekFirst();
    }

    public Deque<PlayerLocationRecord> getLocationHistory() {
        return locationHistory;
    }

    public void newTpaRequest(PlayerEntity sender, boolean direction) {
        if (this.isFake()) {
            return;
        }
        this.waitingInformationMap.remove(sender.getEntityName());
        this.waitingInformationMap.put(sender.getEntityName(), new TpaWaitingInformation(System.currentTimeMillis(), direction));
        MutableText text = AioMessenger.text("");
        text.append(
                AioMessenger.text("  [同意]  ")
                        .setStyle(
                                Style.EMPTY
                                        .withColor(Formatting.GREEN)
                                        .withHoverEvent(
                                                HoverEvent.Action.SHOW_TEXT.buildHoverEvent(
                                                        AioMessenger.text("同意这个请求").setStyle(
                                                                Style.EMPTY.withColor(Formatting.WHITE)
                                                        )
                                                )
                                        )
                                        .withClickEvent(
                                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa attitude accept " + sender.getEntityName())
                                        )
                        )
        );
        text.append(
                AioMessenger.text("  [拒绝]  ")
                        .setStyle(
                                Style.EMPTY
                                        .withColor(Formatting.RED)
                                        .withHoverEvent(
                                                HoverEvent.Action.SHOW_TEXT.buildHoverEvent(
                                                        AioMessenger.text("拒绝这个请求").setStyle(
                                                                Style.EMPTY.withColor(Formatting.WHITE)
                                                        )
                                                )
                                        )
                                        .withClickEvent(
                                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa attitude reject " + sender.getEntityName())
                                        )
                        )
        );
        if (direction) {
            this.player.sendMessage(
                    AioMessenger.text(sender.getEntityName() + "发送了传送请求: 他 ==> 你").setStyle(Style.EMPTY.withColor(Formatting.GOLD)).append(text)
            );
        } else {
            this.player.sendMessage(
                    AioMessenger.text(sender.getEntityName() + "发送了传送请求: 你 ==> 他").setStyle(Style.EMPTY.withColor(Formatting.GOLD)).append(text)
            );
        }
    }

    public void removeTpaRequest(String sender) {
        this.waitingInformationMap.remove(sender);
    }

    public Map<String , TpaWaitingInformation> getTpaRequests() {
        return this.waitingInformationMap;
    }

    public void addWaypoint(final String name, final Vec3d pos, final Identifier dimension, final float yaw, final float pitch) {
        if (waypoints.containsKey(name)) {
            return;
        }
        waypoints.put(name, new PlayerLocationRecord(pos, dimension, yaw, pitch, System.currentTimeMillis()));
    }

    public void removeWaypoint(String name) {
        waypoints.remove(name);
    }

    public Map<String, PlayerLocationRecord> getWaypoints() {
        return waypoints;
    }

    public void setRecordInterval(int interval) {
        if (interval < 1) {
            return;
        }
        this.recordIntervalInSec = interval;
    }

    public int getRecordInterval() {
        return this.recordIntervalInSec;
    }

    public void setMaxLocationHistoryLength(short length) {
        this.maxLocationHistoryLength = length;
    }

    public short getMaxLocationHistoryLength() {
        return this.maxLocationHistoryLength;
    }

    public ScheduledRecordTask getScheduledRecordTask() {
        return this.scheduledRecordTask;
    }

    public void enableAutoRecord(boolean state) {
        this.enableAutoRecord = state;
    }

    public boolean autoRecordEnabled() {
        return this.enableAutoRecord;
    }

    public void enableNotificationWhenRecording(boolean state) {
        this.sendNotificationWhenRecording = state;
    }

    public boolean recordingNotificationEnabled() {
        return this.sendNotificationWhenRecording;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public boolean playerIsOffline() {
        return this.playerIsOffline;
    }

    public boolean isFake() {
        return player instanceof EntityPlayerMPFake;
    }

    public static class ScheduledRecordTask {
        private int progress = 0;
        private final PlayerManager playerManager;

        public ScheduledRecordTask(PlayerManager playerManager) {
            this.playerManager = playerManager;
        }

        public void run() {
            if (!playerManager.enableAutoRecord) {
                return;
            }
            playerManager.recordLocationHistory();
        }

        public void tick() {
            progress++;
            if (progress >= playerManager.recordIntervalInSec) {
                this.progress = 0;
                run();
            }
        }
    }
}
