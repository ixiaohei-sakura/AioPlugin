package aio_plugin.utils;

import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedList;
import java.util.List;

public class AioMessenger {
    private final static Style EMPTY_STYLE = Style.EMPTY;

    public static MutableText text(String text) {
        return Text.literal(text);
    }

    public static MutableText goldenText(String text) {
        return text(text).setStyle(EMPTY_STYLE.withColor(Formatting.GOLD));
    }

    public static MutableText aqueousText(String text) {
        return text(text).setStyle(EMPTY_STYLE.withColor(Formatting.AQUA));
    }

    public static MutableText errorMsg(String msg) {
        return text(msg).setStyle(EMPTY_STYLE.withColor(Formatting.RED));
    }

    public static List<MutableText> formTable(List<MutableText> lines, String command) {
        List<MutableText> texts = new LinkedList<>();
        String tableFrame1 = "=====================================================\n";
        String tableFrame2 = "========================%02d/%02d========================\n";
        int n;
        for (int i=1; i<=(int)Math.ceil(((double)lines.size())/7); i++) {
            final MutableText text = goldenText(tableFrame1);
            if (i*7 > lines.size()) {
                n = lines.size()-7*(i-1);
            } else {
                n = 7;
            }
            for (int cursor = 7*(i-1); cursor <= n+7*(i-1)-1; cursor ++) {
                text.append(lines.get(cursor));
            }
            text.append(goldenText("Command: ").append(text(command).setStyle(Style.EMPTY.withColor(Formatting.AQUA).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("命令建议"))).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)))));
            text.append(text("\n"));
            text.append(goldenText(tableFrame2.formatted(i, (int) Math.ceil(((double)lines.size())/7))));
            texts.add(text);
        }
        return texts;
    }

    public static MutableText teleportableText(String text, Vec3d pos, String dim) {
        return text(text).setStyle(EMPTY_STYLE.withColor(Formatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa crd %s %s %s %s".formatted(pos.x, pos.y, pos.z, dim))).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("点击传送"))));
    }

    public static MutableText runnableText(String text, String command) {
        return text(text).setStyle(EMPTY_STYLE.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("点击运行"))).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)).withColor(Formatting.AQUA));
    }

    public static MutableText suggestionText(String text, String command) {
        return text(text).setStyle(EMPTY_STYLE.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("点击运行"))).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)).withColor(Formatting.AQUA));
    }
}
