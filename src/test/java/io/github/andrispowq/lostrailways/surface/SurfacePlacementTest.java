package io.github.andrispowq.lostrailways.surface;

import io.github.andrispowq.lostrailways.config.SurfaceConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfacePlacementTest {
    @Test
    void everyProtectionRejectsTheWholeStationBeforeAnyStationBlockChanges() {
        var graph = SurfaceLayoutTest.graph(true, List.of(SurfaceLayoutTest.p(0, 0), SurfaceLayoutTest.p(15, 0)));
        List<SurfacePiece> pieces = List.of();
        for (int seed = 0; seed < 100; seed++) {
            pieces = new SurfaceLayout().plan(List.of(graph), 0, 0, seed, new SurfaceConfig(12, 0, 0),
                    (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.PLAINS));
            if (pieces.stream().anyMatch(piece -> piece.kind() == SurfacePiece.Kind.STATION)) break;
        }
        var station = pieces.stream().filter(piece -> piece.kind() == SurfacePiece.Kind.STATION).findFirst().orElseThrow();
        for (var obstruction : List.of(SurfacePlacement.Existing.PROTECTED, SurfacePlacement.Existing.FLUID, SurfacePlacement.Existing.SOLID)) {
            Target target = new Target();
            var cell = station.blocks().stream().filter(change -> change.position().y() == 64).findFirst().orElseThrow().position();
            target.original.put(cell, obstruction);
            var result = new SurfacePlacement().place(pieces, target);
            assertTrue(result.rejectedPieces() >= 1);
            assertTrue(result.blocks() > 0, "Independent safe track columns may still be placed");
            assertFalse(station.blocks().stream().anyMatch(change -> target.writes.containsKey(change.position())), "No partially written station");
            assertEquals(obstruction, target.original.get(cell));
        }
    }

    @Test
    void voidSupportsWorldBorderAndForeignChunksRejectWithoutWrites() {
        var pieces = new SurfaceLayout().plan(List.of(SurfaceLayoutTest.graph(false,
                List.of(SurfaceLayoutTest.p(0, 8), SurfaceLayoutTest.p(15, 8)))), 0, 0, 0, new SurfaceConfig(12, 0, 0),
                (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.PLAINS));
        Target unsupported = new Target();
        for (int x = 0; x < 16; x++) unsupported.original.put(new SurfacePiece.Cell(x, 63, 8), SurfacePlacement.Existing.AIR);
        assertEquals(0, new SurfacePlacement().place(pieces, unsupported).blocks());
        Target forbidden = new Target();
        forbidden.writable = false;
        assertEquals(0, new SurfacePlacement().place(pieces, forbidden).blocks());
        assertTrue(forbidden.writes.isEmpty());
        var foreign = new SurfacePiece(SurfacePiece.Kind.TRACK,
                List.of(new SurfacePiece.Change(new SurfacePiece.Cell(16, 64, 0), SurfacePiece.Material.RAIL, SurfacePiece.Shape.EAST_WEST)),
                List.of(new SurfacePiece.Cell(16, 64, 0)), List.of());
        assertEquals(0, new SurfacePlacement().place(List.of(foreign), new Target()).blocks());
    }

    @Test
    void conflictingTemplateWritesNeverPartiallyApply() {
        var cell = new SurfacePiece.Cell(5, 64, 5);
        var first = new SurfacePiece(SurfacePiece.Kind.STATION, List.of(new SurfacePiece.Change(cell, SurfacePiece.Material.OAK_PLANKS)), List.of(cell), List.of());
        var other = new SurfacePiece.Cell(6, 64, 5);
        var second = new SurfacePiece(SurfacePiece.Kind.JUNCTION, List.of(new SurfacePiece.Change(cell, SurfacePiece.Material.COBBLE),
                new SurfacePiece.Change(other, SurfacePiece.Material.OAK_FENCE)), List.of(cell, other), List.of());
        Target target = new Target();
        var result = new SurfacePlacement().place(List.of(second, first), target);
        assertEquals(1, result.acceptedPieces());
        assertEquals(SurfacePiece.Material.OAK_PLANKS, target.writes.get(cell).material());
        assertFalse(target.writes.containsKey(other));
    }

    private static final class Target implements SurfacePlacement.Target {
        final Map<SurfacePiece.Cell, SurfacePlacement.Existing> original = new HashMap<>();
        final Map<SurfacePiece.Cell, SurfacePiece.Change> writes = new HashMap<>();
        boolean writable = true;

        @Override public boolean canWrite(SurfacePiece.Cell cell) { return writable && cell.x() >= 0 && cell.x() < 16 && cell.z() >= 0 && cell.z() < 16 && cell.y() >= 12; }
        @Override public SurfacePlacement.Existing existing(SurfacePiece.Cell cell) {
            assertTrue(canWrite(cell), "Must not query foreign/protected chunks");
            return original.getOrDefault(cell, cell.y() <= 63 ? SurfacePlacement.Existing.NATURAL_GROUND : SurfacePlacement.Existing.AIR);
        }
        @Override public void write(SurfacePiece.Change change) { writes.put(change.position(), change); }
    }
}
