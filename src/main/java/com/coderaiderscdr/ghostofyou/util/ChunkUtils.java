package com.coderaiderscdr.ghostofyou.util;

import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class ChunkUtils {

    private ChunkUtils() {}

    public static List<GhostEntity> getGhostsInChunk(ServerLevel level, BlockPos pos) {

        int chunkX = pos.getX() & ~15;
        int chunkZ = pos.getZ() & ~15;
        AABB chunkBox = new AABB(
                chunkX,                    level.getMinBuildHeight(),  chunkZ,
                chunkX + 16, level.getMaxBuildHeight(), chunkZ + 16);
        return level.getEntitiesOfClass(GhostEntity.class, chunkBox, GhostEntity::isAlive);
    }

    public static List<GhostEntity> getGhostsByOwnerName(ServerLevel level, String ownerName) {
        AABB everywhere = AABB.ofSize(
                net.minecraft.world.phys.Vec3.ZERO, 60000, 2048, 60000);
        return level.getEntitiesOfClass(GhostEntity.class, everywhere,
                g -> ownerName.equals(g.getOwnerName()));
    }

    public static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos);
    }
}
