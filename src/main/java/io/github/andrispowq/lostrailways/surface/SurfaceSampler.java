package io.github.andrispowq.lostrailways.surface;

@FunctionalInterface
public interface SurfaceSampler {
    Column sample(int x, int z);
    record Column(int y, SurfaceStyle style) {
        public int railY() { return y + (style == SurfaceStyle.SWAMP ? 1 : 0); }
    }
}
