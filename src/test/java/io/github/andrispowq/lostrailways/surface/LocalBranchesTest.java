package io.github.andrispowq.lostrailways.surface;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class LocalBranchesTest {
    private static final SurfacePiece.Cell ORIGIN = new SurfacePiece.Cell(8, 20, 2);
    private static final List<SurfacePiece> SURFACE = List.of(new SurfacePiece(SurfacePiece.Kind.TRACK,
            List.of(new SurfacePiece.Change(new SurfacePiece.Cell(8, 19, 2), SurfacePiece.Material.GRAVEL),
                    new SurfacePiece.Change(ORIGIN, SurfacePiece.Material.RAIL, SurfacePiece.Shape.EAST_WEST)),
            List.of(ORIGIN), List.of(new SurfacePiece.Cell(8, 19, 2))));

    @Test
    void uncommonRepeatableDescendingCaveSpursReplaceOnlyTheirJunction() {
        int branches = 0;
        for (int seed = 0; seed < 1000; seed++) {
            var target = new Terrain(true, cell -> false);
            var result = LocalBranches.add(SURFACE, target, cell -> true, cell -> false, 0, 0, seed);
            assertEquals(result, LocalBranches.add(SURFACE, target, cell -> true, cell -> false, 0, 0, seed));
            if (result.equals(SURFACE)) continue;
            branches++;
            var piece = result.getFirst();
            assertEquals(SurfacePiece.Kind.CAVE_BRANCH, piece.kind());
            assertTrue(piece.blocks().stream().allMatch(change -> change.position().y() >= 12));
            assertTrue(piece.blocks().stream().anyMatch(change -> change.position().equals(ORIGIN)
                    && change.shape() == SurfacePiece.Shape.SOUTH_EAST));
            assertTrue(piece.blocks().stream().anyMatch(change -> change.position().z() == 7 && change.position().y() == 18));
            assertTrue(piece.blocks().size() < 150);
            assertTrue(new SurfacePlacement().place(result, target).blocks() > 0);
        }
        assertTrue(branches > 30 && branches < 140, "Only a small seeded fraction of suitable chunks branch");
    }

    @Test
    void requiresRealDestinationAndRejectsProtectedBiomesBlocksAndDepth() {
        for (int seed = 0; seed < 200; seed++) {
            assertEquals(SURFACE, LocalBranches.add(SURFACE, new Terrain(false, cell -> false), cell -> true, cell -> false, 0, 0, seed));
            assertEquals(SURFACE, LocalBranches.add(SURFACE, new Terrain(true, cell -> cell.z() == 5), cell -> true, cell -> false, 0, 0, seed));
            assertEquals(SURFACE, LocalBranches.add(SURFACE, new Terrain(true, cell -> false), cell -> cell.z() != 5, cell -> false, 0, 0, seed));
            // Restrict every write to Y >= 20; even the source support is below the permitted boundary.
            var highMinimum = new Terrain(true, cell -> false) {
                @Override public boolean canWrite(SurfacePiece.Cell cell) { return super.canWrite(cell) && cell.y() >= 20; }
            };
            assertEquals(SURFACE, LocalBranches.add(SURFACE, highMinimum, cell -> true, cell -> false, 0, 0, seed));
        }
    }

    @Test
    void mineshaftApproachesArePossibleWithoutCarvingIntoTheirBuffer() {
        int found = 0;
        for (int seed = 0; seed < 300; seed++) {
            var target = new Terrain(false, cell -> false) {
                @Override public boolean canWrite(SurfacePiece.Cell cell) { return super.canWrite(cell) && cell.z() < 10; }
            };
            var result = LocalBranches.add(SURFACE, target, cell -> true, cell -> cell.z() >= 6, 0, 0, seed);
            if (result.equals(SURFACE)) continue;
            found++;
            assertEquals(SurfacePiece.Kind.MINESHAFT_BRANCH, result.getFirst().kind());
            assertTrue(result.getFirst().blocks().stream().allMatch(change -> change.position().z() < 10));
        }
        assertTrue(found > 0 && found < 40);
    }

    @Test
    void lowestSupportedBranchKeepsEveryCellAtOrAboveTwelve() {
        var origin = new SurfacePiece.Cell(8, 14, 2);
        var surface = List.of(new SurfacePiece(SurfacePiece.Kind.TRACK,
                List.of(new SurfacePiece.Change(origin, SurfacePiece.Material.RAIL, SurfacePiece.Shape.EAST_WEST)),
                List.of(origin), List.of(new SurfacePiece.Cell(8, 13, 2))));
        var target = new Terrain(true, cell -> false) {
            @Override public SurfacePlacement.Existing existing(SurfacePiece.Cell cell) {
                return super.existing(new SurfacePiece.Cell(cell.x(), cell.y() + 6, cell.z()));
            }
        };
        List<SurfacePiece> result = surface;
        for (int seed = 0; seed < 1000 && result.equals(surface); seed++) {
            result = LocalBranches.add(surface, target, cell -> true, cell -> false, 0, 0, seed);
        }
        assertNotEquals(surface, result);
        assertEquals(12, result.getFirst().blocks().stream().mapToInt(change -> change.position().y()).min().orElseThrow());
        assertTrue(new SurfacePlacement().place(result, target).blocks() > 0);
    }

    private static class Terrain implements SurfacePlacement.Target {
        private final boolean cave;
        private final Predicate<SurfacePiece.Cell> protectedCell;
        private final List<SurfacePiece.Change> writes = new ArrayList<>();

        Terrain(boolean cave, Predicate<SurfacePiece.Cell> protectedCell) {
            this.cave = cave;
            this.protectedCell = protectedCell;
        }
        @Override public boolean canWrite(SurfacePiece.Cell cell) {
            return cell.x() >= 0 && cell.x() < 16 && cell.z() >= 0 && cell.z() < 16 && cell.y() >= 12;
        }
        @Override public SurfacePlacement.Existing existing(SurfacePiece.Cell cell) {
            if (protectedCell.test(cell)) return SurfacePlacement.Existing.PROTECTED;
            if (cave && cell.z() >= 7 && cell.y() == 22) return SurfacePlacement.Existing.NATURAL_GROUND;
            return cell.y() >= 20 || cave && cell.z() >= 7 && cell.y() >= 19
                    ? SurfacePlacement.Existing.AIR : SurfacePlacement.Existing.NATURAL_GROUND;
        }
        @Override public boolean canExcavate(SurfacePiece.Cell cell) { return existing(cell) == SurfacePlacement.Existing.NATURAL_GROUND; }
        @Override public void write(SurfacePiece.Change change) { writes.add(change); }
    }
}
