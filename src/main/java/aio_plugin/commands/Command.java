package aio_plugin.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;

public abstract class Command {
    public void register() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> {
            for (LiteralArgumentBuilder<ServerCommandSource> l : registration()) {
                dispatcher.register(l);
            }
        }));
    }

    protected abstract List<LiteralArgumentBuilder<ServerCommandSource>> registration();
}
