package io.github.andrispowq.lostrailways.surface;

public enum SurfaceStyle {
    PLAINS, FOREST, TAIGA, EXCLUDED;

    public boolean wooded() { return this == FOREST || this == TAIGA; }
    public SurfacePiece.Material planks() { return this == TAIGA ? SurfacePiece.Material.SPRUCE_PLANKS : SurfacePiece.Material.OAK_PLANKS; }
    public SurfacePiece.Material log() { return this == TAIGA ? SurfacePiece.Material.SPRUCE_LOG : SurfacePiece.Material.OAK_LOG; }
    public SurfacePiece.Material slab() { return this == TAIGA ? SurfacePiece.Material.SPRUCE_SLAB : SurfacePiece.Material.OAK_SLAB; }
    public SurfacePiece.Material fence() { return this == TAIGA ? SurfacePiece.Material.SPRUCE_FENCE : SurfacePiece.Material.OAK_FENCE; }
}
