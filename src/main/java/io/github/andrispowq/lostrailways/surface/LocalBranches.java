package io.github.andrispowq.lostrailways.surface;

import io.github.andrispowq.lostrailways.surface.SurfacePiece.Cell;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Change;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Material;
import io.github.andrispowq.lostrailways.surface.SurfacePiece.Shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** A bounded local spur, derived from a real surface rail and original terrain, never a remote search. */
public final class LocalBranches {
    private LocalBranches() { }

    public static List<SurfacePiece> add(List<SurfacePiece> surface, SurfacePlacement.Target target,
                                         Predicate<Cell> biomeAllowed, Predicate<Cell> nearMineshaft,
                                         int chunkX, int chunkZ, long seed) {
        if (SurfaceNoise.at(seed, chunkX, chunkZ, 40) >= 0.08) return surface;
        var origins = surface.stream().filter(piece -> piece.kind() == SurfacePiece.Kind.TRACK)
                .flatMap(piece -> piece.blocks().stream()).filter(change -> change.material() == Material.RAIL
                        && (change.shape() == Shape.EAST_WEST || change.shape() == Shape.NORTH_SOUTH))
                .sorted(Comparator.comparingDouble(change -> SurfaceNoise.at(seed, change.position().x(), change.position().z(), 41)))
                .limit(4).toList();
        Set<Column> occupied = new HashSet<>();
        surface.forEach(piece -> piece.blocks().forEach(change -> occupied.add(column(change.position()))));
        for (Change origin : origins) {
            for (int sign : new int[]{1, -1}) {
                int dx = origin.shape() == Shape.NORTH_SOUTH ? sign : 0;
                int dz = origin.shape() == Shape.EAST_WEST ? sign : 0;
                SurfacePiece branch = attempt(origin, dx, dz, target, biomeAllowed, nearMineshaft, occupied, seed);
                if (branch != null) {
                    List<SurfacePiece> result = new ArrayList<>(surface);
                    result.removeIf(piece -> piece.kind() == SurfacePiece.Kind.TRACK && piece.blocks().contains(origin));
                    result.add(branch);
                    return List.copyOf(result);
                }
            }
        }
        return surface;
    }

    private static SurfacePiece attempt(Change origin, int dx, int dz, SurfacePlacement.Target target,
                                         Predicate<Cell> biomeAllowed, Predicate<Cell> nearMineshaft,
                                         Set<Column> occupied, long seed) {
        List<Change> blocks = new ArrayList<>();
        List<Cell> ground = new ArrayList<>();
        List<Cell> clearance = new ArrayList<>();
        List<Cell> excavation = new ArrayList<>();
        int buriedColumns = 0;
        for (int step = 0; step <= 10; step++) {
            int x = origin.position().x() + dx * step;
            int z = origin.position().z() + dz * step;
            int y = origin.position().y() - step / 3;
            Cell floor = new Cell(x, y - 1, z);
            if (!safe(floor, target, biomeAllowed) || !target.canExcavate(floor)) return null;
            if (step > 0 && occupied.contains(column(floor))) return null;
            ground.add(floor);
            excavation.add(floor);
            blocks.add(new Change(floor, Material.COBBLE));
            boolean open = true;
            for (int h = y; h <= y + 3; h++) {
                Cell cell = new Cell(x, h, z);
                if (!classify(cell, target, biomeAllowed, clearance, excavation)) return null;
                boolean solid = target.canExcavate(cell);
                if (h <= y + 2) {
                    if (solid) open = false;
                    if (h > y) blocks.add(new Change(cell, Material.AIR));
                } else if (solid) blocks.add(new Change(cell, Material.MOSSY_COBBLE));
            }
            if (!open) buriedColumns++;
            // Validate and retain natural walls; never clear roots, ores, cave decorations, or fluids.
            if (step > 0) for (int side : new int[]{-1, 1}) {
                for (int h = y; h <= y + 3; h++) {
                    Cell wall = new Cell(x - dz * side, h, z + dx * side);
                    if (occupied.contains(column(wall)) || !classify(wall, target, biomeAllowed, clearance, excavation)) return null;
                    if (!open && target.canExcavate(wall)) blocks.add(new Change(wall, Material.MOSSY_COBBLE));
                }
            }
            Shape shape = step == 0 ? junction(origin.shape(), dx, dz)
                    : step % 3 == 0 ? ascending(-dx, -dz) : dx == 0 ? Shape.NORTH_SOUTH : Shape.EAST_WEST;
            Cell rail = new Cell(x, y, z);
            boolean missing = step > 0 && SurfaceNoise.at(seed, x, z, 42) < 0.25;
            blocks.add(new Change(rail, missing ? Material.AIR : Material.RAIL, missing ? null : shape));
            boolean cave = open && target.canExcavate(new Cell(x, y + 3, z));
            boolean mine = nearMineshaft.test(rail) && SurfaceNoise.at(seed, x, z, 43) < 0.25;
            if (step >= 4 && buriedColumns >= 2 && (cave || mine)) {
                return new SurfacePiece(mine ? SurfacePiece.Kind.MINESHAFT_BRANCH : SurfacePiece.Kind.CAVE_BRANCH,
                        blocks, clearance, ground, excavation);
            }
        }
        return null;
    }

    private static boolean safe(Cell cell, SurfacePlacement.Target target, Predicate<Cell> biomeAllowed) {
        return target.canWrite(cell) && biomeAllowed.test(cell);
    }

    private static boolean classify(Cell cell, SurfacePlacement.Target target, Predicate<Cell> biomeAllowed,
                                    List<Cell> clearance, List<Cell> excavation) {
        if (!safe(cell, target, biomeAllowed)) return false;
        if (target.canExcavate(cell)) {
            excavation.add(cell);
            return true;
        }
        var existing = target.existing(cell);
        if (existing == SurfacePlacement.Existing.AIR || existing == SurfacePlacement.Existing.VEGETATION) {
            clearance.add(cell);
            return true;
        }
        return false;
    }

    private static Shape ascending(int dx, int dz) {
        return dx > 0 ? Shape.ASCENDING_EAST : dx < 0 ? Shape.ASCENDING_WEST
                : dz > 0 ? Shape.ASCENDING_SOUTH : Shape.ASCENDING_NORTH;
    }

    private static Shape junction(Shape main, int dx, int dz) {
        return main == Shape.EAST_WEST ? dz > 0 ? Shape.SOUTH_EAST : Shape.NORTH_EAST
                : dx > 0 ? Shape.NORTH_EAST : Shape.NORTH_WEST;
    }

    private static Column column(Cell cell) { return new Column(cell.x(), cell.z()); }
    private record Column(int x, int z) { }
}
