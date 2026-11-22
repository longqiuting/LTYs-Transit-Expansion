package dev.js.mtrheadlight.forge.platform;

/**
 * Fabric {@code PacketSender} 的占位实现。
 *
 * <p>本模组的收发都不需要「回包给发起方」，Fabric 的 handler 形参里也只是接收方的返回值，
 * 所以这里给一个空接口；lambda 形参不需要显式类型，源码里也没有任何地方引用它。
 */
public interface PacketSender {
}
