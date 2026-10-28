package io.github.andrispowq.lostrailways.planner;

public record RegionPos(int x, int z) {
    public static RegionPos containing(int blockX, int blockZ, int regionSize) {
        return new RegionPos(Math.floorDiv(blockX, regionSize), Math.floorDiv(blockZ, regionSize));
    }
}
