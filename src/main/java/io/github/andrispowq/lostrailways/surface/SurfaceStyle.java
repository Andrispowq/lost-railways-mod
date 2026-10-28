package io.github.andrispowq.lostrailways.surface;

public enum SurfaceStyle {
    PLAINS, FOREST, TAIGA, DESERT, JUNGLE, SWAMP, EXCLUDED;

    public boolean wooded() { return this == FOREST || this == TAIGA || this == JUNGLE || this == SWAMP; }
    public SurfacePiece.Material planks() { return this == JUNGLE ? SurfacePiece.Material.JUNGLE_PLANKS : this == TAIGA ? SurfacePiece.Material.SPRUCE_PLANKS : SurfacePiece.Material.OAK_PLANKS; }
    public SurfacePiece.Material log() { return this == JUNGLE ? SurfacePiece.Material.JUNGLE_LOG : this == TAIGA ? SurfacePiece.Material.SPRUCE_LOG : SurfacePiece.Material.OAK_LOG; }
    public SurfacePiece.Material slab() { return this == JUNGLE ? SurfacePiece.Material.JUNGLE_SLAB : this == TAIGA ? SurfacePiece.Material.SPRUCE_SLAB : SurfacePiece.Material.OAK_SLAB; }
    public SurfacePiece.Material fence() { return this == JUNGLE ? SurfacePiece.Material.JUNGLE_FENCE : this == TAIGA ? SurfacePiece.Material.SPRUCE_FENCE : SurfacePiece.Material.OAK_FENCE; }
    public boolean edgeOnly() { return this == JUNGLE || this == SWAMP; }
    public double stationChance() {
        return switch (this) {
            case DESERT, JUNGLE, EXCLUDED -> 0;
            case SWAMP -> 0.05;
            case FOREST, TAIGA -> 0.3;
            case PLAINS -> 0.85;
        };
    }
    public double wear() { return this == JUNGLE ? 2.5 : this == DESERT || this == SWAMP ? 2 : wooded() ? 1.5 : 1; }
    public SurfacePiece.Material bed() {
        return this == DESERT ? SurfacePiece.Material.SANDSTONE : this == SWAMP ? planks()
                : wooded() ? SurfacePiece.Material.MOSSY_COBBLE : SurfacePiece.Material.GRAVEL;
    }
}
