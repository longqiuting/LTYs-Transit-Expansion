package dev.js.lte.forge.platform;

import io.netty.buffer.Unpooled;
import dev.js.lte.LteLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络 shim：Fabric 的 {@code ServerPlayNetworking} / {@code ClientPlayNetworking} /
 * {@code PlayerLookup} → 每个逻辑频道一个 Forge {@link SimpleChannel}。
 *
 * <p><b>字节格式</b>：消息体不解析、不重组，发送端把 {@link FriendlyByteBuf} 里剩下的字节原样
 * 塞进 payload，接收端再包回一个 {@link FriendlyByteBuf} 交给 java-gen 的 lambda。所以
 * {@code lte:hello} / {@code lte:signal_link} 的包体（kind 字节 / BlockPos / 变长字段）与
 * Fabric 侧逐字节一致，只有 Forge 频道外层的分帧不同。
 *
 * <p>每个频道注册两条消息：{@code 0} = PLAY_TO_SERVER，{@code 1} = PLAY_TO_CLIENT。
 */
public final class Net {

    private static final String PROTOCOL = "1";
    private static final int ID_TO_SERVER = 0;
    private static final int ID_TO_CLIENT = 1;

    private static final Map<ResourceLocation, SimpleChannel> CHANNELS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> SERVER_REGISTERED = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> CLIENT_REGISTERED = ConcurrentHashMap.newKeySet();

    private static boolean commonHooksInstalled;
    private static boolean clientHooksInstalled;
    /** 「对端没装 LTE」的一次性 INFO 只打一条，别刷日志。 */
    private static volatile boolean warnedMissingPeer;

    private Net() {
    }

    // ---------------------------------------------------------------- 注册

    public static void registerServer(final ResourceLocation channelId,
                                      final ServerPlayNetworking.PlayChannelHandler receiver) {
        if (!SERVER_REGISTERED.add(channelId)) {
            return;
        }
        channel(channelId)
                .messageBuilder(ToServer.class, ID_TO_SERVER, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToServer::encode)
                .decoder(ToServer::decode)
                .consumerMainThread((payload, context) -> {
                    final ServerPlayer sender = context.get().getSender();
                    if (sender == null || sender.server == null) {
                        return;
                    }
                    try {
                        receiver.receive(sender.server, sender, sender.connection, payload.open(),
                                NO_SENDER);
                    } catch (Throwable ignored) {
                        // 坏包不能把服务端弄掉线。
                    }
                })
                .add();
    }

    public static void registerClient(final ResourceLocation channelId,
                                      final ClientPlayNetworking.PlayChannelHandler receiver) {
        if (!CLIENT_REGISTERED.add(channelId)) {
            return;
        }
        channel(channelId)
                .messageBuilder(ToClient.class, ID_TO_CLIENT, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ToClient::encode)
                .decoder(ToClient::decode)
                .consumerMainThread((payload, context) -> {
                    final Minecraft client = Minecraft.getInstance();
                    try {
                        receiver.receive(client, client.getConnection(), payload.open(), NO_SENDER);
                    } catch (Throwable ignored) {
                        // 同上：坏包只丢这一条。
                    }
                })
                .add();
    }

    // ---------------------------------------------------------------- 发送

    public static boolean canSend(final ServerPlayer player, final ResourceLocation channelId) {
        if (player == null || player.connection == null) {
            return false;
        }
        final Connection connection = player.connection.connection;
        // 只查已注册的频道，不在这里惰性建频道：注册期已过再 createInstance 会抛
        // IllegalArgumentException，而 canSend 是在玩家进服的 tick 上调用的。
        final SimpleChannel channel = connection == null ? null : CHANNELS.get(channelId);
        if (channel == null || channel.isRemotePresent(connection)) {
            return channel != null;
        }
        // 本地有这条频道、对端没有 → 对端没装 LTE。留一条一次性 INFO，别静默失效。
        noteMissingPeer(player, channelId);
        return false;
    }

    public static boolean canSend(final ResourceLocation channelId) {
        final Minecraft client = Minecraft.getInstance();
        final ClientPacketListener listener = client == null ? null : client.getConnection();
        if (listener == null || listener.getConnection() == null || !listener.getConnection().isConnected()) {
            return false;
        }
        final SimpleChannel channel = CHANNELS.get(channelId);
        if (channel == null || channel.isRemotePresent(listener.getConnection())) {
            return channel != null;
        }
        noteMissingPeer(null, channelId);
        return false;
    }

    public static void send(final ServerPlayer player, final ResourceLocation channelId,
                            final FriendlyByteBuf data) {
        if (player == null || data == null) {
            return;
        }
        // 对端没装 LTE 就没有这条频道：发出去只会变成对端的未知 payload。
        // 所有 S2C 一律在这里收口判断，调用点漏判也越不过去。
        if (!canSend(player, channelId)) {
            noteMissingPeer(player, channelId);
            return;
        }
        try {
            channel(channelId).send(PacketDistributor.PLAYER.with(() -> player), new ToClient(bytes(data)));
        } catch (Throwable ignored) {
            // 玩家正好在这一 tick 掉线：丢这一条，别把服务端 tick 打挂。
        }
    }

    public static void sendToServer(final ResourceLocation channelId, final FriendlyByteBuf data) {
        if (data == null) {
            return;
        }
        if (!canSend(channelId)) {
            noteMissingPeer(null, channelId);
            return;
        }
        try {
            channel(channelId).sendToServer(new ToServer(bytes(data)));
        } catch (Throwable ignored) {
            // 同上：连接刚好断掉时静默丢弃。
        }
    }

    /**
     * 对端没装 LTE 时留一条一次性 INFO，免得功能静默失效到没人能查。
     * 只在连接确实是活的、且频道确实缺失时才说话（不把「还没连上」当成「没装」）。
     */
    private static void noteMissingPeer(final ServerPlayer player, final ResourceLocation channelId) {
        if (warnedMissingPeer) {
            return;
        }
        final String whose;
        if (player != null) {
            final Connection connection = player.connection == null ? null : player.connection.connection;
            if (connection == null || !connection.isConnected()) {
                return;
            }
            whose = "server -> " + player.getGameProfile().getName();
        } else {
            final Minecraft client = Minecraft.getInstance();
            final ClientPacketListener listener = client == null ? null : client.getConnection();
            if (listener == null || listener.getConnection() == null || !listener.getConnection().isConnected()) {
                return;
            }
            whose = "client -> server";
        }
        warnedMissingPeer = true;
        LteLog.LOGGER.info("[lte] peer did not register the {} channel (LTE not installed on the other side)"
                + " - LTE network features are skipped for {}; this is logged once", channelId, whose);
    }

    public static List<ServerPlayer> allPlayers(final MinecraftServer server) {
        return server == null ? List.of() : server.getPlayerList().getPlayers();
    }

    public static List<ServerPlayer> tracking(final ServerLevel level, final BlockPos pos) {
        if (level == null || pos == null) {
            return List.of();
        }
        return level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
    }

    // ---------------------------------------------------------------- 钩子

    /** 由 {@code LteForge} 在构造阶段调用：把「玩家进服」转成 Fabric 的 JOIN 语义。 */
    public static void installCommonHooks() {
        if (commonHooksInstalled) {
            return;
        }
        commonHooksInstalled = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(Net::onPlayerLoggedIn);
    }

    public static void installClientHooks() {
        if (clientHooksInstalled) {
            return;
        }
        clientHooksInstalled = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(Net::onClientLoggingIn);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(Net::onClientLoggingOut);
    }

    private static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.server == null) {
            return;
        }
        ServerPlayConnectionEvents.fireJoin(player.connection, player.server);
    }

    private static void onClientLoggingIn(final ClientPlayerNetworkEvent.LoggingIn event) {
        final Minecraft client = Minecraft.getInstance();
        ClientPlayConnectionEvents.fireJoin(client.getConnection(), client);
    }

    private static void onClientLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        final Minecraft client = Minecraft.getInstance();
        ClientPlayConnectionEvents.fireDisconnect(client.getConnection(), client);
    }

    private static SimpleChannel channel(final ResourceLocation channelId) {
        return CHANNELS.computeIfAbsent(channelId, id -> NetworkRegistry.ChannelBuilder
                .named(id)
                .networkProtocolVersion(() -> PROTOCOL)
                // 双端可选的关键：对端没装 LTE 时它根本不会出现在握手列表里，Forge 拿
                // ABSENT 哨兵（"ABSENT 🤔"）去问 serverAcceptedVersions / clientAcceptedVersions。
                // 原来两侧都是 PROTOCOL::equals，ABSENT 必然被拒 → Forge 在 LOGIN 阶段就
                // "Terminating connection with client, mismatched mod list" 断开。
                // 1.20.1 的 ACCEPTVANILLA 是 String 常量（不是 Predicate，不能 .test()），
                // 官方给的是 acceptMissingOr(...) = check.or(ABSENT.version()::equals).or(ACCEPTVANILLA::equals)。
                .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL::equals))
                .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL::equals))
                .simpleChannel());
    }

    private static byte[] bytes(final FriendlyByteBuf data) {
        final byte[] out = new byte[data.readableBytes()];
        data.readBytes(out);
        return out;
    }

    private static final PacketSender NO_SENDER = new PacketSender() {
    };

    // ---------------------------------------------------------------- 消息体

    /** 原始字节 payload：不做任何解析。 */
    public abstract static class Wire {

        private final byte[] data;

        private Wire(final byte[] data) {
            this.data = data;
        }

        final FriendlyByteBuf open() {
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        }

        final void write(final FriendlyByteBuf out) {
            out.writeBytes(data);
        }

        static byte[] readAll(final FriendlyByteBuf in) {
            final byte[] data = new byte[in.readableBytes()];
            in.readBytes(data);
            return data;
        }
    }

    /**
     * C2S 消息体。必须和 {@link ToClient} 分成两个类：Forge 的 {@code IndexedMessageCodec}
     * 用 {@code Class} 做 handler 的键，同一个类挂两个方向会互相覆盖。
     */
    public static final class ToServer extends Wire {

        ToServer(final byte[] data) {
            super(data);
        }

        static void encode(final ToServer payload, final FriendlyByteBuf out) {
            payload.write(out);
        }

        static ToServer decode(final FriendlyByteBuf in) {
            return new ToServer(readAll(in));
        }
    }

    /** S2C 消息体（与 {@link ToServer} 同字节布局）。 */
    public static final class ToClient extends Wire {

        ToClient(final byte[] data) {
            super(data);
        }

        static void encode(final ToClient payload, final FriendlyByteBuf out) {
            payload.write(out);
        }

        static ToClient decode(final FriendlyByteBuf in) {
            return new ToClient(readAll(in));
        }
    }
}
