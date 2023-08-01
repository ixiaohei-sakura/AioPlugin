package aio_plugin.commands;

import aio_plugin.commands.utils.*;
import aio_plugin.utils.AioMessenger;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.sql.Timestamp;
import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TpaCommands extends Command{
    private static final SimpleCommandExceptionType INVALID_POSITION_EXCEPTION = new SimpleCommandExceptionType(AioMessenger.errorMsg("指定的传送位置无效！"));
    private static final SimpleCommandExceptionType NOT_PLAYER_EXCEPTION = new SimpleCommandExceptionType(AioMessenger.errorMsg("两项参数必须全部为玩家"));
    private static final SimpleCommandExceptionType PLAYER_NOT_EXIST = new SimpleCommandExceptionType(AioMessenger.errorMsg("目标玩家不存在"));
    private static final SimpleCommandExceptionType REQUEST_NOT_EXIST = new SimpleCommandExceptionType(AioMessenger.errorMsg("请求不存在，使用 \"/tpa list [页数]\" 列出请求."));
    private static final SimpleCommandExceptionType PLAYER_OFFLINE = new SimpleCommandExceptionType(AioMessenger.errorMsg("请求失效: 玩家下线"));

    private final PlayerUtils playerUtils;

    public TpaCommands(PlayerUtils playerUtils) {
        this.playerUtils = playerUtils;
    }

    public static <T> T getTarget(T obj1, T obj2, boolean direction) {
        if (direction)
            return obj1;
        return obj2;
    }

    public static <T> T getDestination(T obj1, T obj2, boolean direction) {
        if (direction)
            return obj2;
        return obj1;
    }

    @Override
    protected List<LiteralArgumentBuilder<ServerCommandSource>> registration() {
        List<LiteralArgumentBuilder<ServerCommandSource>> literalArgumentBuilders = new LinkedList<>();
        LiteralArgumentBuilder<ServerCommandSource> tpaRoot = literal("tpa");
        tpaRoot.then(
                        argument("player", EntityArgumentType.player()).executes(this::tpa)
        );
        try {
            tpaRoot.then(
                    literal("attitude").then(
                            argument("attitude", StringArgumentType.word()).suggests(StringSuggestionProvider.of(Arrays.asList("accept", "reject")))
                                    .then(
                                            argument("player", StringArgumentType.word()).suggests(((context, builder) -> {
                                                return WaitingPlayerSuggestionProvider.of(playerUtils.getPlayerManager(context.getSource().getPlayer()).getTpaRequests().keySet().stream().toList()).getSuggestions(context, builder);
                                                    }))
                                                        .executes(this::tpaReply)
                                    )
                    )
            );
        } catch (CommandSyntaxException e) {
                e.printStackTrace();
        }
        tpaRoot.then(
                literal("crd")
                        .then(
                                argument("coordinate", Vec3ArgumentType.vec3())
                                        .executes(this::tpaCrd).then(
                                                argument("dimension", DimensionArgumentType.dimension()).executes(this::tpaCrd)
                                        )
                        )
        );
        tpaRoot.then(
                literal("list").executes(this::tpaListRequests)
                        .then(argument("page", IntegerArgumentType.integer(1)).executes(this::tpaListRequests))
        );
        literalArgumentBuilders.add(tpaRoot);
        literalArgumentBuilders.add(
                literal("tpah").then(
                                argument("player", EntityArgumentType.players())
                                        .executes(this::tpah)
                        )
        );
        LiteralArgumentBuilder<ServerCommandSource> waypointRoot = literal("waypoint");
        try {
            waypointRoot.then(
                    literal("go").then(
                            argument("name", StringArgumentType.word()).suggests(((context, builder) -> {
                                return StringSuggestionProvider.of(playerUtils.getPlayerManager(context.getSource().getPlayer()).getWaypoints().keySet().stream().toList()).getSuggestions(context, builder);
                            })).executes(this::waypointGo)
                    )
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        waypointRoot.then(
                literal("add").then(
                        argument("name", StringArgumentType.word()).executes(this::addWaypoint).then(
                                argument("pos", Vec3ArgumentType.vec3()).executes(this::addWaypoint)
                                        .then(argument("dimension", DimensionArgumentType.dimension()))
                        )
                )
        );
        waypointRoot.then(
                literal("remove").then(
                        argument("name", StringArgumentType.word()).executes(this::removeWaypoint)
                )
        );
        waypointRoot.then(
                literal("list").executes(this::listWaypoints)
                        .then(
                                argument("page", IntegerArgumentType.integer(1)).executes(this::listWaypoints)
                        )
        );
        literalArgumentBuilders.add(waypointRoot);
        LiteralArgumentBuilder<ServerCommandSource> backRoot = literal("back");
        backRoot.executes((this::back));
        backRoot.then(
                literal("list").executes(this::listHistory)
                        .then(
                                argument("page", IntegerArgumentType.integer(1))
                        )
        );
        backRoot.then(
                literal("slot")
                        .then(
                                argument("slot", IntegerArgumentType.integer(0, 100))
                                        .executes(this::back)
                        )
        );
        backRoot.then(
                literal("settings")
                        .then(
                                literal("enableAutoRecord").executes(this::setAutoRecordState).then(
                                        argument("state", BoolArgumentType.bool()).executes(this::setAutoRecordState)
                                )
                        )
                        .then(
                                literal("enableRecordNotification").executes(this::setRecordingNotificationState).then(
                                        argument("state", BoolArgumentType.bool()).executes(this::setRecordingNotificationState)
                                )
                        )
                        .then(
                                literal("setMaxHistoryLength").executes(this::setMaxHistoryLength).then(
                                        argument("maxLength", IntegerArgumentType.integer(1, 100)).executes(this::setMaxHistoryLength)
                                )
                        )
                        .then(
                                literal("setRecordInterval").executes(this::setRecordInterval).then(
                                        argument("intervalInSec", IntegerArgumentType.integer(1)).executes(this::setRecordInterval)
                                )
                        )
        );
        literalArgumentBuilders.add(backRoot);
        literalArgumentBuilders.add(
                literal("home").executes(this::home)
        );
        return literalArgumentBuilders;
    }

    private int back(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        if (this.playerUtils.getPlayerManager(context.getSource().getPlayer()) == null) {
            return -1;
        }
        try {
            return this.back(context, context.getArgument("slot", Integer.class));
        } catch (Exception e) {

        }
        return this.back(context, this.playerUtils.getPlayerManager(context.getSource().getPlayer()).getLocationHistory().size() - 1);
    }

    private int back(final CommandContext<ServerCommandSource> context, int slot) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        if (this.playerUtils.getPlayerManager(context.getSource().getPlayer()) == null) {
            return -1;
        }
        PlayerManager playerManager = this.playerUtils.getPlayerManager(context.getSource().getPlayer());
        if (playerManager.getLocationHistory().isEmpty()) {
            context.getSource().getPlayer().sendMessage(AioMessenger.goldenText("没有玩家位置记录！"));
            return 0;
        }
        PlayerLocationRecord record = ((List<PlayerLocationRecord>)playerManager.getLocationHistory()).get(slot);
        context.getSource().getPlayer()
                .sendMessage(
                        AioMessenger.goldenText("正在返回 ")
                                .append(AioMessenger.aqueousText("[x:%d y:%d z:%d w:%s]".formatted((int)record.getPos().x, (int)record.getPos().y, (int)record.getPos().z,record.getDimension().toString())))
                                .append(AioMessenger.text("(slot)=" + slot).setStyle(Style.EMPTY.withColor(Formatting.GRAY)))
                );
        teleport(context.getSource().getPlayer(), record.getWorld(), record.getPos().x, record.getPos().y, record.getPos().z, record.getYaw(), record.getPitch());
        return 0;
    }

    private int setRecordInterval(final CommandContext<ServerCommandSource> context) {
        try {
            context.getArgument("intervalInSec", Integer.class);
        } catch (Exception e) {
            context.getSource().getPlayer().sendMessage(AioMessenger.goldenText("当前的记录间隔为: %d秒".formatted(playerUtils.getPlayerManager(context.getSource().getPlayer()).getRecordInterval())));
            return 0;
        }
        playerUtils.getPlayerManager(context.getSource().getPlayer()).setRecordInterval(context.getArgument("intervalInSec", Integer.class));
        return 0;
    }

    private int setAutoRecordState(final CommandContext<ServerCommandSource> context) {
        try {
            context.getArgument("state", Boolean.class);
        } catch (Exception e) {
            context.getSource().getPlayer().sendMessage(AioMessenger.goldenText("当前的自动记录启用状态为: " + playerUtils.getPlayerManager(context.getSource().getPlayer()).autoRecordEnabled()));
            return 0;
        }
        playerUtils.getPlayerManager(context.getSource().getPlayer()).enableAutoRecord(context.getArgument("state", Boolean.class));
        return 0;
    }

    private int setRecordingNotificationState(final CommandContext<ServerCommandSource> context) {
        try {
            context.getArgument("state", Boolean.class);
        } catch (Exception e) {
            context.getSource().getPlayer().sendMessage(AioMessenger.goldenText("当前的记录通知启用状态为: " + playerUtils.getPlayerManager(context.getSource().getPlayer()).autoRecordEnabled()));
            return 0;
        }
        playerUtils.getPlayerManager(context.getSource().getPlayer()).enableAutoRecord(context.getArgument("state", Boolean.class));
        return 0;
    }

    private int setMaxHistoryLength(final CommandContext<ServerCommandSource> context) {
        try {
            context.getArgument("maxLength", Integer.class);
        } catch (Exception e) {
            context.getSource().getPlayer().sendMessage(AioMessenger.goldenText("当前的记录长度为: %d秒".formatted(playerUtils.getPlayerManager(context.getSource().getPlayer()).getRecordInterval())));
        }
        playerUtils.getPlayerManager(context.getSource().getPlayer()).setRecordInterval(context.getArgument("maxLength", Integer.class));
        return 0;
    }

    private int listHistory(final CommandContext<ServerCommandSource> context) {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        if (this.playerUtils.getPlayerManager(context.getSource().getPlayer()) == null) {
            return -1;
        }
        PlayerManager playerManager = this.playerUtils.getPlayerManager(context.getSource().getPlayer());
        List<MutableText> lines = new LinkedList<>();
        int count = 0;
        for (final PlayerLocationRecord record : playerManager.getLocationHistory()) {
            count++;
            lines.add(
                    AioMessenger.text("%02d. ".formatted(count)).setStyle(Style.EMPTY.withColor(Formatting.GREEN)).append(AioMessenger.goldenText(new Timestamp(record.getTime()).toString())).append(AioMessenger.text(" ")).append(AioMessenger.teleportableText("[x:%d y:%d z:%d w:%s]".formatted((int)record.getPos().x, (int)record.getPos().y, (int)record.getPos().z, record.getDimension().toString()), record.getPos(), record.getDimension().toString())).append(AioMessenger.text("\n"))
            );
        }
        short page = 1;
        try {
            page = context.getArgument("page", Short.class);
        } catch (Exception e) {

        }
        context.getSource().getPlayer().sendMessage(AioMessenger.formTable(lines, "/back list [<页码>]").get(page-1));
        return 0;
    }

    private int addWaypoint(final CommandContext<ServerCommandSource> context) {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        if (this.playerUtils.getPlayerManager(context.getSource().getPlayer()) == null) {
            return -1;
        }
        Vec3d pos = null;
        ServerWorld world = null;
        try {
            pos = Vec3ArgumentType.getVec3(context, "pos");
        } catch (Exception e) {
            pos = context.getSource().getPlayer().getPos();
        }
        try {
            world = DimensionArgumentType.getDimensionArgument(context, "dimension");
        } catch (Exception e) {
            world = (ServerWorld) context.getSource().getPlayer().getWorld();
        }
        PlayerManager playerManager = playerUtils.getPlayerManager(context.getSource().getPlayer());
        playerManager.addWaypoint(context.getArgument("name", String.class), pos, world.getDimensionKey().getValue(), playerManager.getPlayer().getYaw(), playerManager.getPlayer().getPitch());
        playerManager.getPlayer().sendMessage(AioMessenger.goldenText("成功添加了路径点: ").append(AioMessenger.teleportableText(context.getArgument("name", String.class)+"[x:%d y:%d z:%d w:%s]".formatted((int)pos.x, (int)pos.y, (int)pos.z, world.getDimensionKey().getValue().toString()), pos, world.getDimensionKey().getValue().toString())));
        return 0;
    }

    private int removeWaypoint(final CommandContext<ServerCommandSource> context) {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        if (this.playerUtils.getPlayerManager(context.getSource().getPlayer()) == null) {
            return -1;
        }
        PlayerManager playerManager = playerUtils.getPlayerManager(context.getSource().getPlayer());
        if (!playerManager.getWaypoints().containsKey(context.getArgument("name", String.class))){
            playerManager.getPlayer().sendMessage(AioMessenger.goldenText("没有这个路径点！请使用这个命令添加路径点: ").append(AioMessenger.suggestionText("/waypoint add [名称] [x] [y] [z] [维度]", "/waypoint add ")));
        }
        playerManager.removeWaypoint(context.getArgument("name", String.class));
        playerManager.getPlayer().sendMessage(AioMessenger.goldenText("路径点已删除"));
        return 0;
    }

    private int listWaypoints(final CommandContext<ServerCommandSource> context) {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        if (this.playerUtils.getPlayerManager(context.getSource().getPlayer()) == null) {
            return -1;
        }
        PlayerManager playerManager = playerUtils.getPlayerManager(context.getSource().getPlayer());
        if (playerManager.getWaypoints().size() == 0) {
            playerManager.getPlayer().sendMessage(AioMessenger.goldenText("还没有路径点, 添加一个吧! ").append(AioMessenger.suggestionText("/waypoint add [名称] [x] [y] [z] [维度]", "/waypoint add ")));
            return 0;
        }
        List<MutableText> lines = new LinkedList<>();
        Map<String, PlayerLocationRecord> waypoints = playerManager.getWaypoints();
        int count = 0;
        for (final String name : waypoints.keySet()) {
            count++;
            lines.add(
                    AioMessenger.text("%02d. ".formatted(count)).setStyle(Style.EMPTY.withColor(Formatting.GREEN)).append(AioMessenger.goldenText(new Timestamp(waypoints.get(name).getTime()).toString())).append(AioMessenger.text(" ")).append(AioMessenger.teleportableText(name + "[x:%d y:%d z:%d w:%s]".formatted((int)waypoints.get(name).getPos().x, (int)waypoints.get(name).getPos().y, (int)waypoints.get(name).getPos().z, waypoints.get(name).getDimension().toString()), waypoints.get(name).getPos(), waypoints.get(name).getDimension().toString())).append(AioMessenger.text("\n"))
            );
        }
        return 0;
    }

    private int waypointGo(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        if (this.playerUtils.getPlayerManager(context.getSource().getPlayer()) == null) {
            return -1;
        }
        teleportToWaypoint(context.getSource().getPlayer(), context.getArgument("name", String.class));
        return 0;
    }

    private int home(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        if (this.playerUtils.getPlayerManager(context.getSource().getPlayer()) == null) {
            return -1;
        }
        teleportToWaypoint(context.getSource().getPlayer(), "home");
        return 0;
    }

    private void teleportToWaypoint(final PlayerEntity player, final String name) throws CommandSyntaxException {
        PlayerManager playerManager = playerUtils.getPlayerManager(player);
        if (!playerManager.getWaypoints().containsKey(name)) {
            player.sendMessage(AioMessenger.goldenText("还没有这个路径点! 使用: ").append(AioMessenger.suggestionText("/waypoint add [名称]", "/waypoint add " + name)).append(AioMessenger.goldenText("来创建一个路径点！")));
            return;
        }
        PlayerLocationRecord record = playerManager.getWaypoints().get(name);
        player.sendMessage(AioMessenger.goldenText("正在传送到路径点: ").append(AioMessenger.aqueousText(name)).append(AioMessenger.teleportableText("[x:%d y:%d z:%d w:%s]".formatted((int)record.getPos().x, (int)record.getPos().y, (int)record.getPos().z, record.getDimension().toString()), record.getPos(), record.getDimension().toString())));
        teleport(player, record.getWorld(), record.getPos().x, record.getPos().y, record.getPos().z, player.getYaw(), player.getPitch());
    }

    private int tpa(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        makeRequest(context, true);
        return 0;
    }

    private int tpah(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        makeRequest(context, false);
        return 0;
    }

    private int tpaCrd(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        final Vec3d pos = Vec3ArgumentType.getVec3(context, "coordinate");
        if (pos == null) {
            return -1;
        }
        ServerWorld world = null;
        try {
            world = DimensionArgumentType.getDimensionArgument(context, "dimension");
        } catch (Exception e) {

        }
        if (world != null) {
            context.getSource()
                    .getPlayer()
                    .sendMessage(
                            AioMessenger.goldenText("正在传送到 ")
                                    .append(AioMessenger.aqueousText("[x:%d y:%d z:%d w:%s]".formatted((int)pos.x, (int)pos.y, (int)pos.z, world.getRegistryKey().getValue().toString())))
                                    .append(AioMessenger.goldenText("..."))
                    );
            teleport(context.getSource()
                            .getPlayer(), world, pos.x, pos.y, pos.z,
                    context.getSource().getPlayer().getYaw(), context.getSource().getPlayer().getPitch());
            return 0;
        }
        context.getSource()
                .getPlayer()
                .sendMessage(
                        AioMessenger.goldenText("正在传送到 ")
                                .append(AioMessenger.aqueousText("[x:%d y:%d z:%d]".formatted((int)pos.x, (int)pos.y, (int)pos.z)))
                                .append(AioMessenger.goldenText("..."))
                );
        teleport(context.getSource().getPlayer(),
                context.getSource().getWorld(),
                pos.x, pos.y, pos.z,
                context.getSource().getPlayer().getYaw(), context.getSource().getPlayer().getPitch());
        return 0;
    }

    private int tpaListRequests(final CommandContext<ServerCommandSource> context) {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }
        final PlayerManager playerManager = playerUtils.getPlayerManager(context.getSource().getPlayer());
        Map<String, TpaWaitingInformation> informationMap = playerManager.getTpaRequests();
        if (informationMap.size() == 0) {
            context.getSource().getPlayer().sendMessage(AioMessenger.goldenText("没有传送请求！"));
            return 0;
        }
        final List<MutableText> lines = new LinkedList<>();
        int count = 0;
        for (final String playerName : informationMap.keySet()) {
            count += 1;
            String a;
            if (informationMap.get(playerName).getDirection()) {
                a = " 请求传送到你这里";
            } else {
                a = " 请求你传送到他那里";
            }
            lines.add(
                    AioMessenger.text("%02d. ".formatted(count)).setStyle(Style.EMPTY.withColor(Formatting.GREEN)).append(AioMessenger.goldenText(" " + new Timestamp(informationMap.get(playerName).getTimeStamp()) + " "))
                            .append(AioMessenger.aqueousText(playerName))
                            .append(AioMessenger.goldenText(a))
                            .append(AioMessenger.text("\n"))
            );
        }
        short page = 1;
        try {
            page = context.getArgument("page", Short.class);
        } catch (Exception e) {

        }
        context.getSource().getPlayer().sendMessage(AioMessenger.formTable(lines, "/tpa list [<页码>]").get(page-1));
        return 0;
    }

    private int tpaReply(final CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            return -1;
        }

        PlayerManager playerManager1 = playerUtils.getPlayerManager(context.getSource().getPlayer());
        PlayerEntity player2 = context.getSource()
                .getServer()
                .getPlayerManager().getPlayer(context.getArgument("player", String.class));
        if (playerManager1 == null) {
            return -1;
        }
        if (!playerManager1.getTpaRequests().containsKey(context.getArgument("player", String.class))) {
            throw REQUEST_NOT_EXIST.create();
        }
        if (player2 == null) {
            playerManager1.getTpaRequests().remove(context.getArgument("player", String.class));
            throw PLAYER_OFFLINE.create();
        }
        TpaWaitingInformation information = playerManager1.getTpaRequests().get(player2.getEntityName());
        if (Objects.equals(context.getArgument("attitude", String.class), "accept")) {
            PlayerEntity target, destination;
            target = getTarget(playerManager1.getPlayer(), player2, information.getDirection());
            destination = getDestination(playerManager1.getPlayer(), player2, information.getDirection());
            target.sendMessage(AioMessenger.goldenText("正在传送到 ").append(AioMessenger.aqueousText(playerManager1.getPlayer().getEntityName())).append(AioMessenger.goldenText(" ...")));
            destination.sendMessage(AioMessenger.goldenText("正在将 ").append(AioMessenger.aqueousText(playerManager1.getPlayer().getEntityName())).append(AioMessenger.goldenText(" 传送到你的位置...")));
            teleport(target, destination);
        } else if (Objects.equals(context.getArgument("attitude", String.class), "reject")){
            playerManager1.getPlayer().sendMessage(AioMessenger.goldenText("拒绝了 ").append(AioMessenger.aqueousText(player2.getEntityName())).append(AioMessenger.goldenText(" 的传送请求")));
            player2.sendMessage(AioMessenger.goldenText("你发给 ").append(AioMessenger.aqueousText(playerManager1.getPlayer().getEntityName())).append(AioMessenger.goldenText(" 的传送请求被拒绝了")));
        } else {
            return -1;
        }
        playerManager1.removeTpaRequest(player2.getEntityName());
        return 0;
    }

    private void makeRequest(final CommandContext<ServerCommandSource> context,
                             final boolean direction) throws CommandSyntaxException {
        if(!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() == null) {
            throw NOT_PLAYER_EXCEPTION.create();
        }
        Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "player");                //Get selected players
        if (players == null) {
            throw PLAYER_NOT_EXIST.create();
        }
        final List<String> targets = new LinkedList<>();
        for (final PlayerEntity p : players) {

//            if (p == context.getSource().getPlayer()) {
//                p.sendMessage(AioMessenger.errorMsg("不能给自己发送请求！"));
//                continue;
//            }

            if (playerUtils.getPlayerManager(p).isFake()) {                                                             //Target is bot
                if (!direction) {
                    context.getSource()
                            .getPlayer()
                            .sendMessage(
                                    AioMessenger.goldenText("正在将 BOT ")
                                            .append(AioMessenger.aqueousText(p.getEntityName()))
                                            .append(AioMessenger.goldenText(" 传送到你..."))
                            );
                } else {
                    context.getSource()
                            .getPlayer()
                            .sendMessage(
                                    AioMessenger.goldenText("正在传送到 BOT ")
                                            .append(AioMessenger.aqueousText(p.getEntityName()))
                                            .append(AioMessenger.goldenText(" ..."))
                            );
                }
                teleport(getTarget(context.getSource().getPlayer(), p, direction),
                        getDestination(context.getSource().getPlayer(), p, direction));
                continue;
            }
            if (targets.size() < 10) {
                targets.add(p.getEntityName());
            }
            playerUtils.getPlayerManager(p).newTpaRequest(context.getSource().getPlayer(), direction);
        }
        if (players.size() == 0 || targets.size() == 0) {
            return;
        }
        if (targets.size() == 1) {
            context.getSource().getPlayer().sendMessage(
                    AioMessenger.goldenText("正在向玩家 ")
                            .append(AioMessenger.aqueousText(targets.get(0)))
                            .append(AioMessenger.goldenText( " 发送传送请求..."))
            );
            return;
        }
        MutableText a = AioMessenger.goldenText("正在向 ");
        if (players.size() > 10) {
            for (final String b : targets) {
                a.append(AioMessenger.aqueousText(b)).append(" ");
            }
            a.append(AioMessenger.goldenText(" 等玩家发送传送请求..."));
        } else {
            for (final String b : targets) {
                a.append(AioMessenger.aqueousText(b)).append(" ");
            }
            a.append(AioMessenger.goldenText(" 发送请求..."));
        }
        context.getSource().getPlayer().sendMessage(a);
    }

    private void teleport(final PlayerEntity target, final PlayerEntity destination) throws CommandSyntaxException {
        teleport(target, (ServerWorld) destination.getWorld(), destination.getX(), destination.getY(), destination.getZ(), destination.getYaw(), destination.getPitch());
    }

    private void teleport(final PlayerEntity target, final ServerWorld world,
                          final double x, final double y, final double z,
                          final float yaw, final float pitch) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.ofFloored(x, y, x);
        if (!World.isValid(blockPos)) {
            throw INVALID_POSITION_EXCEPTION.create();
        } else {
            Set<PositionFlag> set = EnumSet.noneOf(PositionFlag.class);
            final long timeStamp = playerUtils.getPlayerManager(target).recordLocationHistory();
            if (target.teleport(world, x, y, z, set, yaw, pitch)) {
                target.setVelocity(target.getVelocity().multiply(1.0D, 0.0D, 1.0D));
                target.setOnGround(true);
            } else {
                playerUtils.getPlayerManager(target).removeLocationHistory(timeStamp);
            }
        }
    }
}

