package aio_plugin.commands.utils;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

public class WaitingPlayerSuggestionProvider extends StringSuggestionProvider{
    private final PlayerUtils playerUtils;

    public WaitingPlayerSuggestionProvider(PlayerUtils playerUtils) {
        super();
        this.playerUtils = playerUtils;
    }

    public static SuggestionProvider of(PlayerUtils playerUtils) {
        return new WaitingPlayerSuggestionProvider(playerUtils);
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        suggestions = new LinkedList<>(playerUtils.getPlayerManager(context.getSource().getPlayer()).getTpaRequests().keySet());
        return super.getSuggestions(context, builder);
    }
}
