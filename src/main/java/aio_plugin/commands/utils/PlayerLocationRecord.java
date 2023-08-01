package aio_plugin.commands.utils;

import aio_plugin.ixiaohei;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Objects;

public class PlayerLocationRecord {
    private double x, y, z;
    private String dimension;
    private float yaw;
    private float pitch;
    private long time;

    public PlayerLocationRecord(Vec3d pos, Identifier dimension, float yaw, float pitch, long time) {
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.dimension = dimension.toString();
        this.yaw = yaw;
        this.pitch = pitch;
        this.time = time;
    }

    public static PlayerLocationRecord of(PlayerEntity player) {
        return new PlayerLocationRecord(player.getPos(), player.getWorld().getRegistryKey().getValue(), player.getYaw(), player.getPitch(), System.currentTimeMillis());
    }

    public void setPos(Vec3d pos) {
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    public void setWorld(ServerWorld serverWorld) {
        this.dimension = serverWorld.getRegistryKey().getRegistry().toString();
    }

    public void setYaw(float yaw) {
        this.yaw = MathHelper.wrapDegrees(yaw);
    }

    public void setPitch(float pitch) {
        this.pitch = MathHelper.wrapDegrees(pitch);
    }

    public void setTime(long time) {
        this.time = time;
    }

    public Vec3d getPos() {
        return new Vec3d(x, y, z);
    }

    public ServerWorld getWorld() {
        return getWorld(ixiaohei.server);
    }

    public ServerWorld getWorld(MinecraftServer server) {
        for (final RegistryKey<World> worldRegistryKey : server.getWorldRegistryKeys()) {
            if (Objects.equals(worldRegistryKey.getValue().toString(), dimension)) {
                return server.getWorld(worldRegistryKey);
            }
        }
        return null;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public Identifier getDimension() {
        return new Identifier(dimension);
    }

    public long getTime() {
        return time;
    }
}
