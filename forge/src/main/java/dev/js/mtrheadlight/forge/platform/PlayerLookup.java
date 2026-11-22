package dev.js.mtrheadlight.forge.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

/** Fabric {@code PlayerLookup} 的等价物。 */
public final class PlayerLookup {

    private PlayerLookup() {
    }

    public static List<ServerPlayer> all(final MinecraftServer server) {
        return Net.allPlayers(server);
    }

    public static List<ServerPlayer> tracking(final ServerLevel level, final BlockPos pos) {
        return Net.tracking(level, pos);
    }

    public static List<ServerPlayer> tracking(final BlockEntity blockEntity) {
        if (blockEntity == null || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return List.of();
        }
        return Net.tracking(level, blockEntity.getBlockPos());
    }

    public static List<ServerPlayer> tracking(final ServerLevel level, final LevelChunk chunk) {
        if (level == null || chunk == null) {
            return List.of();
        }
        return level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
    }
}
