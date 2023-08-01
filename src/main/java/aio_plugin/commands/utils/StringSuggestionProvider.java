package aio_plugin.commands.utils;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StringSuggestionProvider implements SuggestionProvider<ServerCommandSource> {
    protected List<String> suggestions = new LinkedList<>();

    public StringSuggestionProvider(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public StringSuggestionProvider() {

    }

    public static SuggestionProvider of(List<String> suggestions) throws CommandSyntaxException {
        return new StringSuggestionProvider(suggestions);
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        for (final String s : suggestions) {
            builder.suggest(s);
        }
        return builder.buildFuture();
    }
}
