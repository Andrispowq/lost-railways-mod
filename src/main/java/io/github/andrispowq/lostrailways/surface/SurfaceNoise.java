package io.github.andrispowq.lostrailways.surface;

/** Coordinate-keyed damage also agrees on shared corridors; never consumes Minecraft's feature RNG. */
final class SurfaceNoise {
    private SurfaceNoise() { }

    static double at(long seed, int x, int z, long salt) {
        long value = seed ^ ((long) x * 0x632be59bd9b4e019L) ^ ((long) z * 0x9e3779b97f4a7c15L) ^ salt;
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
