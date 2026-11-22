package dev.js.lte.forge.platform;

/** 收发都不需要「回包给发起方」时的空 {@link PacketSender}。 */
final class NoPacketSender implements PacketSender {

    static final NoPacketSender INSTANCE = new NoPacketSender();

    private NoPacketSender() {
    }
}
