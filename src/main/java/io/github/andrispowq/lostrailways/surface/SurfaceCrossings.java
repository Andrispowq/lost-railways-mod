package io.github.andrispowq.lostrailways.surface;

import io.github.andrispowq.lostrailways.config.SurfaceConfig;
import io.github.andrispowq.lostrailways.surface.SurfaceLayout.Point;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Cell;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Change;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Short, level crossings with both abutments and the full safety footprint owned by one chunk. */
final class SurfaceCrossings {
    private SurfaceCrossings() { }

    static List<SurfacePiece> plan(Set<Point> route, SurfaceSampler terrain, int chunkX, int chunkZ,
                                    long seed, SurfaceConfig config) {
        List<SurfacePiece> result = new ArrayList<>();
        Set<Point> claimed = new HashSet<>();
        for (Point start : route) {
            if (claimed.contains(start)) continue;
            for (int[] axis : new int[][]{{1, 0}, {0, 1}}) {
                int dx = axis[0];
                int dz = axis[1];
                var bank = terrain.sample(start.x(), start.z());
                if (bank.style() == SurfaceStyle.EXCLUDED || bank.style() == SurfaceStyle.SWAMP
                        || bank.y() - 1 < config.minimumRailY()) continue;
                int firstRise = terrain.sample(start.x() + dx, start.z() + dz).y() - bank.y();
                boolean bridge = firstRise <= -2;
                if (!bridge && firstRise < 4) continue;
                for (int length = 3; length <= 7; length++) {
                    Point end = offset(start, dx, dz, length, 0);
                    if (terrain.sample(end.x(), end.z()).y() != bank.y()) continue;
                    var before = terrain.sample(start.x() - dx, start.z() - dz);
                    var after = terrain.sample(end.x() + dx, end.z() + dz);
                    boolean valid = before.railY() == bank.y() && after.railY() == bank.y()
                            && before.style() == bank.style() && after.style() == bank.style();
                    for (int i = 0; i <= length && valid; i++) {
                        Point center = offset(start, dx, dz, i, 0);
                        // No curves, junctions, competing pieces, biome boundaries, or cross-chunk safety reads.
                        if (!route.contains(center) || claimed.contains(center)
                                || !route.contains(offset(center, dx, dz, -1, 0))
                                || !route.contains(offset(center, dx, dz, 1, 0))) { valid = false; break; }
                        for (int side = -1; side <= 1; side++) {
                            Point point = offset(start, dx, dz, i, side);
                            var column = terrain.sample(point.x(), point.z());
                            int rise = column.y() - bank.y();
                            if (Math.floorDiv(point.x(), 16) != chunkX || Math.floorDiv(point.z(), 16) != chunkZ
                                    || column.style() != bank.style() || (side != 0 && route.contains(point))
                                    || ((i == 0 || i == length) ? rise != 0
                                    : bridge ? rise > -2 || rise < -6 : rise < 4 || rise > 6)) {
                                valid = false;
                                break;
                            }
                        }
                    }
                    if (valid) {
                        result.add(piece(start, dx, dz, length, bank.y(), bank.style(), bridge, seed, config));
                        for (int i = 0; i <= length; i++) {
                            for (int side = -1; side <= 1; side++) claimed.add(offset(start, dx, dz, i, side));
                        }
                        break;
                    }
                }
                if (claimed.contains(start)) break;
            }
        }
        return List.copyOf(result);
    }

    private static SurfacePiece piece(Point start, int dx, int dz, int length, int y, SurfaceStyle style,
                                       boolean bridge, long seed, SurfaceConfig config) {
        List<Change> blocks = new ArrayList<>();
        List<Cell> ground = new ArrayList<>();
        List<Cell> clearance = new ArrayList<>();
        List<Cell> excavation = new ArrayList<>();
        for (int i = 0; i <= length; i++) {
            boolean bank = i == 0 || i == length;
            Point center = offset(start, dx, dz, i, 0);
            boolean gap = bridge && !bank && SurfaceNoise.at(seed, center.x(), center.z(), 31) < config.missingBedChance();
            for (int side = -1; side <= 1; side++) {
                Point point = offset(start, dx, dz, i, side);
                Cell floor = new Cell(point.x(), y - 1, point.z());
                if (bank) {
                    ground.add(floor);
                    clearance.add(new Cell(point.x(), y, point.z()));
                    clearance.add(new Cell(point.x(), y + 1, point.z()));
                } else if (bridge) {
                    for (int h = y - 1; h <= y + 1; h++) clearance.add(new Cell(point.x(), h, point.z()));
                } else {
                    // Stable natural material only, including walls and roof: no cave opening or ore excavation.
                    for (int h = y - 1; h <= y + 3; h++) excavation.add(new Cell(point.x(), h, point.z()));
                }
                if (bridge) {
                    if (!gap) {
                        blocks.add(new Change(floor, bank ? Material.COBBLE : style.planks()));
                        if (side != 0 && SurfaceNoise.at(seed, point.x(), point.z(), 32) > 0.4) {
                            blocks.add(new Change(new Cell(point.x(), y, point.z()), style.fence()));
                        }
                    }
                } else if (side == 0) {
                    blocks.add(new Change(floor, Material.COBBLE));
                    blocks.add(new Change(new Cell(point.x(), y + 1, point.z()), Material.AIR));
                    if (!bank) blocks.add(new Change(new Cell(point.x(), y + 2, point.z()), Material.AIR));
                    if (!bank) blocks.add(new Change(new Cell(point.x(), y + 3, point.z()), Material.CRACKED_BRICKS));
                } else if (!bank) {
                    for (int h = y; h <= y + 3; h++) blocks.add(new Change(new Cell(point.x(), h, point.z()),
                            h < y + 3 && (i == 1 || i == length - 1)
                                    ? style == SurfaceStyle.DESERT ? Material.SANDSTONE : style.log()
                                    : Material.MOSSY_COBBLE));
                }
            }
            Cell rail = new Cell(center.x(), y, center.z());
            if (!gap && SurfaceNoise.at(seed, center.x(), center.z(), 33) >= config.missingRailChance() * style.wear()) {
                blocks.add(new Change(rail, Material.RAIL, dx == 1 ? SurfacePiece.Shape.EAST_WEST : SurfacePiece.Shape.NORTH_SOUTH));
            } else if (!bridge) blocks.add(new Change(rail, Material.AIR));
        }
        return new SurfacePiece(bridge ? SurfacePiece.Kind.BRIDGE : SurfacePiece.Kind.TUNNEL, blocks, clearance, ground, excavation);
    }

    private static Point offset(Point start, int dx, int dz, int along, int side) {
        return new Point(start.x() + dx * along - dz * side, start.z() + dz * along + dx * side);
    }
}
