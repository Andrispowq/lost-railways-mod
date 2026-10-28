package io.github.andrispowq.lostrailways.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Exact mineshaft bounds are the sole exception to the conservative whole-chunk structure veto. */
public final class BranchStructures {
    private final List<BoundingBox> protectedBoxes;
    private final List<BoundingBox> approachBoxes;

    public BranchStructures(List<BoundingBox> bounds) {
        protectedBoxes = bounds.stream().map(box -> box.inflatedBy(4)).toList();
        approachBoxes = bounds.stream().map(box -> box.inflatedBy(8)).toList();
    }

    /** Null means an unrelated structure or unresolved reference: fail closed without requesting chunks. */
    public static BranchStructures inspect(List<ChunkAccess> neighbors, Function<ChunkPos, ChunkAccess> cachedStart) {
        Set<StructureStart> starts = new HashSet<>();
        for (var chunk : neighbors) {
            for (var entry : chunk.getAllStarts().entrySet()) {
                if (!entry.getValue().isValid()) continue;
                if (!(entry.getKey() instanceof MineshaftStructure)) return null;
                starts.add(entry.getValue());
            }
            for (var entry : chunk.getAllReferences().entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                if (!(entry.getKey() instanceof MineshaftStructure)) return null;
                for (long position : entry.getValue()) {
                    var origin = cachedStart.apply(ChunkPos.unpack(position));
                    var start = origin == null ? null : origin.getAllStarts().get(entry.getKey());
                    if (start == null || !start.isValid()) return null;
                    starts.add(start);
                }
            }
        }
        return new BranchStructures(starts.stream().map(StructureStart::getBoundingBox).toList());
    }

    public boolean allows(BlockPos pos) {
        // Mineshaft support pillars can extend below the nominal box. Protect its horizontal footprint at every height.
        return protectedBoxes.stream().noneMatch(box -> pos.getX() >= box.minX() && pos.getX() <= box.maxX()
                && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ());
    }
    public boolean near(SurfacePiece.Cell pos) {
        BlockPos block = new BlockPos(pos.x(), pos.y(), pos.z());
        return allows(block) && approachBoxes.stream().anyMatch(box -> box.isInside(block));
    }
}
