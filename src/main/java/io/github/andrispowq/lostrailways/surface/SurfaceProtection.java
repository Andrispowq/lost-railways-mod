package io.github.andrispowq.lostrailways.surface;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;

/** Generation-only protection survives saving partially generated terrain; ordinary player edits remain vanilla. */
@SuppressWarnings("UnstableApiUsage")
public final class SurfaceProtection {
    public static final AttachmentType<List<Long>> CELLS = AttachmentRegistry.createPersistent(
            Identifier.parse("lost_railways:surface_cells_v1"), Codec.LONG.listOf(0, 4096));

    private SurfaceProtection() { }
    public static void initialize() { }

    public static void remember(ChunkAccess chunk, List<BlockPos> cells) {
        ((AttachmentTarget) chunk).setAttached(CELLS, cells.stream().map(BlockPos::asLong).distinct().sorted().toList());
    }

    public static boolean contains(ChunkAccess chunk, BlockPos pos) {
        List<Long> cells = ((AttachmentTarget) chunk).getAttached(CELLS);
        return cells != null && java.util.Collections.binarySearch(cells, pos.asLong()) >= 0;
    }
}
