package dev.js.mtrheadlight.forge.platform;

import net.minecraft.core.particles.SimpleParticleType;

/** Fabric {@code FabricParticleTypes} 的等价物。 */
public final class FabricParticleTypes {

    private FabricParticleTypes() {
    }

    /** {@code alwaysShow} = true：原版 CLOUD 两条距离闸门对它失效（与 Fabric 语义一致）。 */
    public static SimpleParticleType simple(final boolean alwaysShow) {
        return new SimpleParticleType(alwaysShow);
    }

    public static SimpleParticleType simple() {
        return new SimpleParticleType(false);
    }
}
