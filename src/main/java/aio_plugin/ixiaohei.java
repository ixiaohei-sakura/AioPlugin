package aio_plugin;

import aio_plugin.commands.GiveMeOpCommand;
import aio_plugin.commands.RemakeCommand;
import aio_plugin.commands.TpaCommands;
import aio_plugin.commands.utils.PlayerUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.Objects;

import static net.minecraft.server.command.CommandManager.literal;

public class ixiaohei implements ModInitializer {
    public static MinecraftServer server;
    private TpaCommands tpaCommands;
    private RemakeCommand remakeCommand;
    private GiveMeOpCommand giveMeOpCommand;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        PlayerUtils playerUtils = new PlayerUtils();
        tpaCommands = new TpaCommands(playerUtils);
        remakeCommand = new RemakeCommand();
        giveMeOpCommand = new GiveMeOpCommand();
        register();
    }

    private void onServerStarted(MinecraftServer server) {
        ixiaohei.server = server;
    }

    private void register() {
        tpaCommands.register();
        remakeCommand.register();
        giveMeOpCommand.register();
        CommandRegistrationCallback.EVENT.register(
                ((dispatcher, registryAccess, environment) -> {
                    dispatcher.register(
                            literal("aio_debug").requires((context) -> {return Objects.equals(context.getPlayer().getEntityName(), "ixiaohei");}).executes((context) -> {
                                context.getSource().sendFeedback((() -> {return Text.literal("Result: " + (context.getSource().getPlayer().getWorld().getRegistryKey().getValue()));}), true);
                                return 0;
                            }));
                })
        );
    }
}
