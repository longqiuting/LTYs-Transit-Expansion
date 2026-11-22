package dev.js.lte.forge.platform;

import io.netty.buffer.Unpooled;
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
        return connection != null && channel(channelId).isRemotePresent(connection);
    }

    public static boolean canSend(final ResourceLocation channelId) {
        final Minecraft client = Minecraft.getInstance();
        final ClientPacketListener listener = client == null ? null : client.getConnection();
        if (listener == null || listener.getConnection() == null || !listener.getConnection().isConnected()) {
            return false;
        }
        return channel(channelId).isRemotePresent(listener.getConnection());
    }

    public static void send(final ServerPlayer player, final ResourceLocation channelId,
                            final FriendlyByteBuf data) {
        if (player == null || data == null) {
            return;
        }
        channel(channelId).send(PacketDistributor.PLAYER.with(() -> player), new ToClient(bytes(data)));
    }

    public static void sendToServer(final ResourceLocation channelId, final FriendlyByteBuf data) {
        if (data == null) {
            return;
        }
        channel(channelId).sendToServer(new ToServer(bytes(data)));
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
        return CHANNELS.computeIfAbsent(channelId, id ->
                NetworkRegistry.newSimpleChannel(id, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals));
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
