package aio_plugin.mixins;

import aio_plugin.utils.AioMessenger;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.GameRules;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class MixinServerWorld extends World {
    private boolean someoneSleeping = false;

    protected MixinServerWorld(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, Supplier<Profiler> profiler, boolean isClient, boolean debugWorld, long biomeAccess, int maxChainedNeighborUpdates) {
        super(properties, registryRef,registryManager, dimensionEntry, profiler, isClient, debugWorld, biomeAccess, maxChainedNeighborUpdates);
    }

    @Shadow
    @Final
    private List<ServerPlayerEntity> players;

    @Shadow
    private void wakeSleepingPlayers() {}

    @Shadow
    public void setTimeOfDay(long timeOfDay) {}

    @Shadow
    private void resetWeather() {}

    @Shadow @Final private MinecraftServer server;

    @Inject(method="tick", at=@At("HEAD"))
    public void tick(CallbackInfo ci) {
        if (this.someoneSleeping && this.players.stream().anyMatch((player) -> !player.isSpectator() && player.canResetTimeBySleeping())) {
            this.someoneSleeping = false;
            if (this.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
                long l = this.properties.getTimeOfDay() + 24000L;
                this.setTimeOfDay(l - l % 24000L);
                this.server.getPlayerManager().broadcast(AioMessenger.text("跳过夜晚").setStyle(Style.EMPTY.withColor(Formatting.GOLD)), false);
            }
            this.wakeSleepingPlayers();
            if (this.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
                this.resetWeather();
            }
        }
    }

    @Inject(method="updateSleepingPlayers", at=@At("HEAD"), cancellable = true)
    public void updateSleepingPlayers(CallbackInfo ci) {
        this.someoneSleeping = false;
        for (ServerPlayerEntity player : this.players) {
            if (!player.isSpectator() && player.isSleeping()) {
                this.someoneSleeping = true;
                this.server.getPlayerManager().broadcast(AioMessenger.text(player.getEntityName() + " 正在睡觉！").setStyle(Style.EMPTY.withColor(Formatting.GOLD)), false);
            }
        }
        ci.cancel();
    }
}
