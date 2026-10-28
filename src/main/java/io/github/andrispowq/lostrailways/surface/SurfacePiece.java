package io.github.andrispowq.lostrailways.surface;

import java.util.List;

/** A piece is validated in full before any of its blocks are written, and belongs to one chunk. */
public record SurfacePiece(Kind kind, List<Change> blocks, List<Cell> clearance, List<Cell> ground) {
    public SurfacePiece {
        blocks = List.copyOf(blocks);
        clearance = List.copyOf(clearance);
        ground = List.copyOf(ground);
    }

    public enum Kind { TRACK, STATION, JUNCTION }
    public enum Material { GRAVEL, COBBLE, MOSSY_COBBLE, STONE_BRICKS, CRACKED_BRICKS, OAK_PLANKS, OAK_LOG, OAK_SLAB, OAK_FENCE, SPRUCE_PLANKS, SPRUCE_LOG, SPRUCE_SLAB, SPRUCE_FENCE, JUNGLE_PLANKS, JUNGLE_LOG, JUNGLE_SLAB, JUNGLE_FENCE, SANDSTONE, RAIL, POWERED_RAIL }
    public enum Shape { NORTH_SOUTH, EAST_WEST, ASCENDING_EAST, ASCENDING_WEST, ASCENDING_NORTH, ASCENDING_SOUTH,
        NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST }
    public record Cell(int x, int y, int z) { }
    public record Change(Cell position, Material material, Shape shape) {
        public Change(Cell position, Material material) { this(position, material, null); }
    }
}
