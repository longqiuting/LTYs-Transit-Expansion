package dev.js.mtrheadlight.forge.platform;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric {@code ParticleFactoryRegistry} 的等价物：先在客户端入口排队，等
 * {@link RegisterParticleProvidersEvent}（mod 总线）再真正注册。
 *
 * <p>Forge 的 {@code registerSpriteSet} 要的是 {@code ParticleEngine.SpriteParticleRegistration}，
 * 其形状与 Fabric 的 {@code PendingParticleFactory} 完全一致（都是 {@code SpriteSet -> provider}），
 * 所以 lambda 体不用改。泛型在这一层被擦掉（真实注册就是 raw 的），保留 raw 实现避免
 * {@code ParticleType<?>} 的边界噪音。
 */
public final class ParticleFactoryRegistry {

    public interface ParticleFactory<T extends ParticleOptions> {
        Particle create(T effect, ClientLevel world, double x, double y, double z,
                        double velocityX, double velocityY, double velocityZ);
    }

    public interface PendingParticleFactory<T extends ParticleOptions> {
        ParticleFactory<T> create(SpriteSet sprites);
    }

    private static final ParticleFactoryRegistry INSTANCE = new ParticleFactoryRegistry();
    private static final List<Pending> PENDING = new ArrayList<>();

    private ParticleFactoryRegistry() {
    }

    public static ParticleFactoryRegistry getInstance() {
        return INSTANCE;
    }

    public <T extends ParticleOptions> void register(final ParticleType<T> type,
                                                     final PendingParticleFactory<T> factory) {
        if (type != null && factory != null) {
            PENDING.add(new Pending(type, factory));
        }
    }

    /** 由 {@code ClientBootstrap} 挂在 mod 总线上的 {@code RegisterParticleProvidersEvent}。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRegisterProviders(final RegisterParticleProvidersEvent event) {
        for (final Pending pending : PENDING) {
            event.registerSpriteSet(pending.type,
                    (ParticleEngine.SpriteParticleRegistration) sprites -> {
                        final ParticleFactory factory = pending.factory.create((SpriteSet) sprites);
                        return (ParticleProvider) (effect, world, x, y, z, vx, vy, vz) ->
                                factory.create(effect, world, x, y, z, vx, vy, vz);
                    });
        }
    }

    private static final class Pending {
        private final ParticleType<?> type;
        private final PendingParticleFactory<?> factory;

        private Pending(final ParticleType<?> type, final PendingParticleFactory<?> factory) {
            this.type = type;
            this.factory = factory;
        }
    }
}
