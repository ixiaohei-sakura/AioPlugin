package aio_plugin.commands;

import carpet.utils.Messenger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.ServerCommandSource;

import java.util.LinkedList;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public class RemakeCommand extends Command{
    private static final SimpleCommandExceptionType NOT_PLAYER_EXCEPTION = new SimpleCommandExceptionType(Messenger.s("target and destination must be players"));

    @Override
    protected List<LiteralArgumentBuilder<ServerCommandSource>> registration() {
        List<LiteralArgumentBuilder<ServerCommandSource>> literalArgumentBuilders = new LinkedList<>();
        literalArgumentBuilders.add(
                                    literal("remake")
                                    .executes(this::execute)
        );
        return literalArgumentBuilders;
    }

    private int execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            throw NOT_PLAYER_EXCEPTION.create();
        }
        context.getSource().getPlayer().kill();
        context.getSource().getServer().sendMessage(Messenger.s(context.getSource().getPlayer().getEntityName() + "重开了"));
        return 0;
    }
}
