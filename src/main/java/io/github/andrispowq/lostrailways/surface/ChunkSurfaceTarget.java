package io.github.andrispowq.lostrailways.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Set;
import java.util.function.Predicate;

/** Deliberately narrow replacement whitelist: no excavation, fluids, trees, ores, or block entities. */
public final class ChunkSurfaceTarget implements SurfacePlacement.Target {
    private final java.util.List<BlockPos> writtenPositions = new java.util.ArrayList<>();
    private final ChunkAccess chunk;
    private final int minimumY;
    private final Predicate<BlockPos> allowed;
    private final Set<BlockPos> blockEntities;

    public ChunkSurfaceTarget(ChunkAccess chunk, int minimumY, Predicate<BlockPos> allowed) {
        this.chunk = chunk;
        this.minimumY = minimumY;
        this.allowed = allowed;
        this.blockEntities = Set.copyOf(chunk.getBlockEntitiesPos());
    }

    @Override
    public boolean canWrite(SurfacePiece.Cell cell) {
        return Math.floorDiv(cell.x(), 16) == chunk.getPos().x() && Math.floorDiv(cell.z(), 16) == chunk.getPos().z()
                && cell.y() >= minimumY && !chunk.isOutsideBuildHeight(cell.y()) && allowed.test(pos(cell));
    }

    @Override
    public SurfacePlacement.Existing existing(SurfacePiece.Cell cell) {
        BlockPos pos = pos(cell);
        BlockState state = chunk.getBlockState(pos);
        if (blockEntities.contains(pos) || state.hasBlockEntity()) return SurfacePlacement.Existing.PROTECTED;
        if (!state.getFluidState().isEmpty()) return SurfacePlacement.Existing.FLUID;
        if (state.isAir()) return SurfacePlacement.Existing.AIR;
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.DEAD_BUSH)) return SurfacePlacement.Existing.VEGETATION;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.SANDSTONE) || state.is(Blocks.MUD)) return SurfacePlacement.Existing.NATURAL_GROUND;
        return SurfacePlacement.Existing.SOLID;
    }

    @Override
    public void write(SurfacePiece.Change change) {
        if (!canWrite(change.position())) throw new IllegalArgumentException("Cross-chunk or protected surface write");
        chunk.setBlockState(pos(change.position()), state(change), 2);
        writtenPositions.add(pos(change.position()));
    }

    public java.util.List<BlockPos> writtenPositions() { return java.util.List.copyOf(writtenPositions); }

    public static BlockState state(SurfacePiece.Change change) {
        return switch (change.material()) {
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
            case MOSSY_COBBLE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case STONE_BRICKS -> Blocks.STONE_BRICKS.defaultBlockState();
            case CRACKED_BRICKS -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case OAK_PLANKS -> Blocks.OAK_PLANKS.defaultBlockState();
            case OAK_LOG -> Blocks.OAK_LOG.defaultBlockState();
            case OAK_SLAB -> Blocks.OAK_SLAB.defaultBlockState();
            case OAK_FENCE -> Blocks.OAK_FENCE.defaultBlockState();
            case SPRUCE_PLANKS -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case SPRUCE_LOG -> Blocks.SPRUCE_LOG.defaultBlockState();
            case SPRUCE_SLAB -> Blocks.SPRUCE_SLAB.defaultBlockState();
            case SPRUCE_FENCE -> Blocks.SPRUCE_FENCE.defaultBlockState();
            case JUNGLE_PLANKS -> Blocks.JUNGLE_PLANKS.defaultBlockState();
            case JUNGLE_LOG -> Blocks.JUNGLE_LOG.defaultBlockState();
            case JUNGLE_SLAB -> Blocks.JUNGLE_SLAB.defaultBlockState();
            case JUNGLE_FENCE -> Blocks.JUNGLE_FENCE.defaultBlockState();
            case SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
            case RAIL -> Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.valueOf(change.shape().name()));
            case POWERED_RAIL -> Blocks.POWERED_RAIL.defaultBlockState()
                    .setValue(PoweredRailBlock.SHAPE, RailShape.valueOf(change.shape().name())).setValue(PoweredRailBlock.POWERED, false);
        };
    }

    private static BlockPos pos(SurfacePiece.Cell cell) { return new BlockPos(cell.x(), cell.y(), cell.z()); }
}
