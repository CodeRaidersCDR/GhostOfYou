package com.coderaiderscdr.ghostofyou.util;

import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Utility helpers for chunk-level ghost queries.
 */
public final class ChunkUtils {

    private ChunkUtils() {}

    /**
     * Return all live {@link GhostEntity} objects whose block position falls
     * inside the 16³ chunk that contains {@code pos}.
     *
     * @param level the server level to search
     * @param pos   any block position inside the target chunk
     * @return mutable list of ghost entities in that chunk
     */
    public static List<GhostEntity> getGhostsInChunk(ServerLevel level, BlockPos pos) {
        // Snap to chunk origin (rounding down to 16-block boundary)
        int chunkX = pos.getX() & ~15;
        int chunkZ = pos.getZ() & ~15;
        AABB chunkBox = new AABB(
                chunkX,                    level.getMinBuildHeight(),  chunkZ,
                chunkX + 16, level.getMaxBuildHeight(), chunkZ + 16);
        return level.getEntitiesOfClass(GhostEntity.class, chunkBox, GhostEntity::isAlive);
    }

    /**
     * Return all live {@link GhostEntity} objects owned by {@code ownerName}
     * within the given level.
     *
     * @param level     the server level to search
     * @param ownerName the player display name to match
     * @return mutable list of matching ghost entities
     */
    public static List<GhostEntity> getGhostsByOwnerName(ServerLevel level, String ownerName) {
        AABB everywhere = AABB.ofSize(
                net.minecraft.world.phys.Vec3.ZERO, 60000, 2048, 60000);
        return level.getEntitiesOfClass(GhostEntity.class, everywhere,
                g -> ownerName.equals(g.getOwnerName()));
    }

    /**
     * Check whether the chunk containing {@code pos} is loaded.
     *
     * @param level the server level
     * @param pos   any block position
     * @return {@code true} if the chunk is fully loaded
     */
    public static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos);
    }
}
