package dev.js.mtrheadlight.forge.platform;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/** Fabric {@code PacketByteBufs} 的等价物。 */
public final class PacketByteBufs {

    private PacketByteBufs() {
    }

    public static FriendlyByteBuf create() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    public static FriendlyByteBuf empty() {
        return new FriendlyByteBuf(Unpooled.EMPTY_BUFFER);
    }

    /** 把一整块 payload 包成可读缓冲（网络层收到字节数组后用它还原）。 */
    public static FriendlyByteBuf wrap(final byte[] data) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }
}
