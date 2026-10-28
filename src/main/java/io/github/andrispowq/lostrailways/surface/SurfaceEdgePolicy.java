package io.github.andrispowq.lostrailways.surface;

import java.util.HashMap;
import java.util.Map;

/** Limit wet/dense-biome remnants to a narrow landward fringe using immutable generator samples. */
public final class SurfaceEdgePolicy {
    private static final int[][] OFFSETS = {{16, 0}, {-16, 0}, {0, 16}, {0, -16},
            {16, 16}, {16, -16}, {-16, 16}, {-16, -16}};

    private SurfaceEdgePolicy() { }

    public static SurfaceSampler filter(SurfaceSampler source) {
        Map<Point, SurfaceSampler.Column> samples = new HashMap<>();
        SurfaceSampler cached = (x, z) -> samples.computeIfAbsent(new Point(x, z), point -> source.sample(x, z));
        return (x, z) -> {
            var column = cached.sample(x, z);
            if (!column.style().edgeOnly()) return column;
            for (int[] offset : OFFSETS) {
                var neighbor = cached.sample(x + offset[0], z + offset[1]);
                if (neighbor.style() != SurfaceStyle.EXCLUDED && !neighbor.style().edgeOnly()
                        && Math.abs(neighbor.y() - column.y()) <= 2) return column;
            }
            return new SurfaceSampler.Column(column.y(), SurfaceStyle.EXCLUDED);
        };
    }

    private record Point(int x, int z) { }
}
