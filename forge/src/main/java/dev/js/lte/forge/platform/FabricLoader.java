package dev.js.lte.forge.platform;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Fabric {@code FabricLoader} 的 Forge 等价物。
 *
 * <p>方法名刻意与 Fabric 一致（{@code getConfigDir} / {@code getGameDir} / {@code isModLoaded} /
 * {@code getEnvironmentType} / {@code getModContainer}），所以源码里 {@code FabricLoader.getInstance()}
 * 只被替换成 {@code Env.getInstance()}，调用点一行不用改。
 */
public final class FabricLoader {

    private static final FabricLoader INSTANCE = new FabricLoader();

    private FabricLoader() {
    }

    public static FabricLoader getInstance() {
        return INSTANCE;
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public boolean isModLoaded(final String id) {
        return id != null && ModList.get().isLoaded(id);
    }

    public EnvType getEnvironmentType() {
        return FMLLoader.getDist().isClient() ? EnvType.CLIENT : EnvType.SERVER;
    }

    public Optional<ModContainer> getModContainer(final String id) {
        return ModList.get().getModContainerById(id).map(c -> new ModContainer(c.getModInfo()));
    }

    /** {@code FabricLoader#getModContainer(id).get().getMetadata().getVersion().getFriendlyString()} 的形状。 */
    public static final class ModContainer {
        private final IModInfo info;

        ModContainer(final IModInfo info) {
            this.info = info;
        }

        public ModMetadata getMetadata() {
            return new ModMetadata(info);
        }
    }

    public static final class ModMetadata {
        private final IModInfo info;

        ModMetadata(final IModInfo info) {
            this.info = info;
        }

        public Version getVersion() {
            return new Version(info.getVersion().toString());
        }

        public String getId() {
            return info.getModId();
        }
    }

    public static final class Version {
        private final String friendly;

        Version(final String friendly) {
            this.friendly = friendly;
        }

        public String getFriendlyString() {
            return friendly;
        }

        @Override
        public String toString() {
            return friendly;
        }
    }
}
