package io.github.andrispowq.lostrailways.surface;

import io.github.andrispowq.lostrailways.config.SurfaceConfig;
import io.github.andrispowq.lostrailways.planner.NetworkNode;
import io.github.andrispowq.lostrailways.planner.RegionGraph;
import io.github.andrispowq.lostrailways.planner.RoutePoint;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Cell;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Change;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Material;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Chunk-local refinement. Shared edge geometry is merged before deriving rail shapes and damage. */
public final class SurfaceLayout {
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final Comparator<Point> ORDER = Comparator.comparingInt(Point::x).thenComparingInt(Point::z);

    public List<SurfacePiece> plan(List<RegionGraph> graphs, int chunkX, int chunkZ, long seed,
                                   SurfaceConfig config, SurfaceSampler source) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        Set<Point> geometry = geometry(graphs, minX - 2, minZ - 2, minX + 17, minZ + 17);
        Map<Point, SurfaceSampler.Column> columns = new HashMap<>();
        SurfaceSampler sampler = (x, z) -> columns.computeIfAbsent(new Point(x, z), point -> source.sample(x, z));
        List<SurfacePiece> pieces = new ArrayList<>();
        for (Point point : geometry) {
            if (!owned(point.x(), point.z(), chunkX, chunkZ)) {
                continue;
            }
            var column = sampler.sample(point.x(), point.z());
            if (!suitable(column, config)) {
                continue;
            }
            List<Point> neighbors = neighbors(point, geometry);
            Shape shape = shape(point, neighbors, sampler, config);
            if (shape == null) {
                continue; // No cliff carving, curve-on-slope, or unsupported two-way incline.
            }
            int x = point.x();
            int z = point.z();
            int y = column.y();
            double wear = column.style() == SurfaceStyle.FOREST ? 1.5 : 1;
            boolean bedGap = SurfaceNoise.at(seed, x, z, 11) < config.missingBedChance()
                    || SurfaceNoise.at(seed, Math.floorDiv(x, 8), Math.floorDiv(z, 8), 12) < config.missingBedChance() / 2;
            if (!bedGap) {
                List<Change> blocks = new ArrayList<>();
                Material bed = column.style() == SurfaceStyle.FOREST ? Material.MOSSY_COBBLE : Material.GRAVEL;
                blocks.add(new Change(new Cell(x, y - 1, z), bed));
                // Vanilla cannot represent a four-way switch. Leave an explicit, repairable ruined crossing.
                if (neighbors.size() != 4 && SurfaceNoise.at(seed, x, z, 13) >= config.missingRailChance() * wear) {
                    boolean powered = (shape == Shape.NORTH_SOUTH || shape == Shape.EAST_WEST)
                            && neighbors.size() == 2 && SurfaceNoise.at(seed, x, z, 14) < 0.04;
                    blocks.add(new Change(new Cell(x, y, z), powered ? Material.POWERED_RAIL : Material.RAIL, shape));
                }
                pieces.add(new SurfacePiece(SurfacePiece.Kind.TRACK, blocks,
                        List.of(new Cell(x, y, z), new Cell(x, y + 1, z)),
                        bed == Material.GRAVEL ? List.of(new Cell(x, y - 1, z), new Cell(x, y - 2, z)) : List.of(new Cell(x, y - 1, z))));
            }
            if (neighbors.size() >= 3 && neighbors.stream().allMatch(p -> sampler.sample(p.x(), p.z()).y() == y)) {
                junction(pieces, point, y, geometry, sampler, chunkX, chunkZ);
            }
        }
        graphs.stream().flatMap(graph -> graph.nodes().stream()).distinct()
                .sorted(Comparator.comparing(NetworkNode::id)).forEach(node -> {
                    if (node.kind() != NetworkNode.Kind.BOUNDARY) {
                        station(pieces, node, geometry, sampler, config, seed, chunkX, chunkZ);
                    }
                });
        return List.copyOf(pieces);
    }

    private static Set<Point> geometry(List<RegionGraph> graphs, int minX, int minZ, int maxX, int maxZ) {
        Set<Point> result = new TreeSet<>(ORDER);
        for (RegionGraph graph : graphs) {
            for (var edge : graph.edges()) {
                for (int i = 1; i < edge.path().size(); i++) {
                    RoutePoint a = edge.path().get(i - 1);
                    RoutePoint b = edge.path().get(i);
                    if (a.z() == b.z() && a.z() >= minZ && a.z() <= maxZ) {
                        for (int x = Math.max(minX, Math.min(a.x(), b.x())); x <= Math.min(maxX, Math.max(a.x(), b.x())); x++) {
                            result.add(new Point(x, a.z()));
                        }
                    } else if (a.x() == b.x() && a.x() >= minX && a.x() <= maxX) {
                        for (int z = Math.max(minZ, Math.min(a.z(), b.z())); z <= Math.min(maxZ, Math.max(a.z(), b.z())); z++) {
                            result.add(new Point(a.x(), z));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static Shape shape(Point point, List<Point> neighbors, SurfaceSampler sampler, SurfaceConfig config) {
        int y = sampler.sample(point.x(), point.z()).y();
        for (Point neighbor : neighbors) {
            var next = sampler.sample(neighbor.x(), neighbor.z());
            if (!suitable(next, config) || Math.abs(next.y() - y) > 1) {
                return null;
            }
        }
        boolean east = neighbors.contains(new Point(point.x() + 1, point.z()));
        boolean west = neighbors.contains(new Point(point.x() - 1, point.z()));
        boolean south = neighbors.contains(new Point(point.x(), point.z() + 1));
        boolean north = neighbors.contains(new Point(point.x(), point.z() - 1));
        List<Point> uphill = neighbors.stream().filter(p -> sampler.sample(p.x(), p.z()).y() > y).toList();
        boolean straight = !(east || west) || !(north || south);
        if (!straight && neighbors.stream().anyMatch(p -> sampler.sample(p.x(), p.z()).y() != y) || uphill.size() > 1) {
            return null;
        }
        if (!uphill.isEmpty()) {
            Point next = uphill.getFirst();
            if (next.x() > point.x()) return Shape.ASCENDING_EAST;
            if (next.x() < point.x()) return Shape.ASCENDING_WEST;
            return next.z() > point.z() ? Shape.ASCENDING_SOUTH : Shape.ASCENDING_NORTH;
        }
        if (north && east) return Shape.NORTH_EAST;
        if (north && west) return Shape.NORTH_WEST;
        if (south && east) return Shape.SOUTH_EAST;
        if (south && west) return Shape.SOUTH_WEST;
        return east || west ? Shape.EAST_WEST : Shape.NORTH_SOUTH;
    }

    private static List<Point> neighbors(Point point, Set<Point> geometry) {
        List<Point> result = new ArrayList<>(4);
        for (int[] direction : DIRECTIONS) {
            Point neighbor = new Point(point.x() + direction[0], point.z() + direction[1]);
            if (geometry.contains(neighbor)) result.add(neighbor);
        }
        return result;
    }

    private static void junction(List<SurfacePiece> pieces, Point point, int y, Set<Point> geometry,
                                 SurfaceSampler sampler, int chunkX, int chunkZ) {
        int x = point.x() + 2;
        int z = point.z() + 2;
        if (owned(x, z, chunkX, chunkZ) && !geometry.contains(new Point(x, z)) && sampler.sample(x, z).y() == y
                && sampler.sample(x, z).style() != SurfaceStyle.EXCLUDED) {
            pieces.add(new SurfacePiece(SurfacePiece.Kind.JUNCTION,
                    List.of(new Change(new Cell(x, y - 1, z), Material.MOSSY_COBBLE),
                            new Change(new Cell(x, y, z), Material.OAK_FENCE), new Change(new Cell(x, y + 1, z), Material.OAK_FENCE)),
                    List.of(new Cell(x, y, z), new Cell(x, y + 1, z)), List.of(new Cell(x, y - 1, z))));
        }
    }

    private static void station(List<SurfacePiece> pieces, NetworkNode node, Set<Point> geometry, SurfaceSampler sampler,
                                SurfaceConfig config, long seed, int chunkX, int chunkZ) {
        Point anchor = new Point(node.position().x(), node.position().z());
        if (!geometry.contains(anchor)) return;
        var column = sampler.sample(anchor.x(), anchor.z());
        if (!suitable(column, config) || SurfaceNoise.at(seed, anchor.x(), anchor.z(), 20)
                >= (column.style() == SurfaceStyle.FOREST ? 0.3 : 0.85)) return;
        List<Point> neighbors = neighbors(anchor, geometry);
        if (neighbors.isEmpty()) return;
        Point next = neighbors.getFirst();
        int dx = next.x() - anchor.x();
        int dz = next.z() - anchor.z();
        int sideX = dz != 0 ? 1 : 0;
        int sideZ = dx != 0 ? 1 : 0;
        int y = column.y();
        List<Change> blocks = new ArrayList<>();
        List<Cell> clearance = new ArrayList<>();
        List<Cell> ground = new ArrayList<>();
        for (int along = 2; along <= 6; along++) {
            Point rail = new Point(anchor.x() + dx * along, anchor.z() + dz * along);
            if (!geometry.contains(rail) || sampler.sample(rail.x(), rail.z()).y() != y) return;
            // Include the associated track's support/clearance in the station's atomic safety check.
            if (!owned(rail.x(), rail.z(), chunkX, chunkZ)) return;
            ground.add(new Cell(rail.x(), y - 1, rail.z()));
            clearance.add(new Cell(rail.x(), y, rail.z()));
            clearance.add(new Cell(rail.x(), y + 1, rail.z()));
            for (int side = 1; side <= 5; side++) {
                int x = rail.x() + sideX * side;
                int z = rail.z() + sideZ * side;
                if (!owned(x, z, chunkX, chunkZ) || geometry.contains(new Point(x, z))) return;
                var footing = sampler.sample(x, z);
                if (footing.y() > y || footing.y() < y - 1 || footing.style() != column.style()) return;
                ground.add(new Cell(x, footing.y() - 1, z));
                for (int h = footing.y(); h <= y + 3; h++) clearance.add(new Cell(x, h, z));
                for (int h = footing.y() - 1; h < y; h++) blocks.add(new Change(new Cell(x, h, z), Material.COBBLE));
                Material floor = column.style() == SurfaceStyle.FOREST ? Material.OAK_PLANKS
                        : SurfaceNoise.at(seed, x, z, 21) < 0.3 ? Material.CRACKED_BRICKS : Material.STONE_BRICKS;
                blocks.add(new Change(new Cell(x, y, z), floor));
                if (along >= 3 && along <= 5 && side >= 2 && side <= 4) {
                    if (along != 4 && side != 3) {
                        blocks.add(new Change(new Cell(x, y + 1, z), Material.OAK_LOG));
                        blocks.add(new Change(new Cell(x, y + 2, z), Material.OAK_LOG));
                    }
                    if (!(along == 4 && side == 3 && SurfaceNoise.at(seed, x, z, 22) < 0.5)) {
                        blocks.add(new Change(new Cell(x, y + 3, z), Material.OAK_SLAB));
                    }
                }
            }
        }
        pieces.add(new SurfacePiece(SurfacePiece.Kind.STATION, blocks, clearance, ground));
    }

    private static boolean suitable(SurfaceSampler.Column column, SurfaceConfig config) {
        return column.style() != SurfaceStyle.EXCLUDED && column.y() >= config.minimumRailY();
    }

    private static boolean owned(int x, int z, int chunkX, int chunkZ) {
        return Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ;
    }

    private record Point(int x, int z) { }
}
