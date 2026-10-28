package io.github.andrispowq.lostrailways.surface;

import io.github.andrispowq.lostrailways.config.SurfaceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.andrispowq.lostrailways.surface.SurfaceLayoutTest.graph;
import static io.github.andrispowq.lostrailways.surface.SurfaceLayoutTest.p;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceCrossingsTest {
    private static final SurfaceConfig INTACT = new SurfaceConfig(12, 0, 0);
    private final SurfaceLayout layout = new SurfaceLayout();

    @Test
    void createsLevelChunkOwnedBridgeAndThreeBlockHighTunnel() {
        for (int height : List.of(60, 68)) {
            var pieces = plan(height, 0, INTACT);
            var crossing = pieces.stream().filter(SurfaceCrossingsTest::crossing).findFirst().orElseThrow();
            assertEquals(height < 64 ? SurfacePiece.Kind.BRIDGE : SurfacePiece.Kind.TUNNEL, crossing.kind());
            assertTrue(crossing.blocks().stream().filter(change -> change.material() == SurfacePiece.Material.RAIL)
                    .allMatch(change -> change.position().y() == 64 && change.shape() == SurfacePiece.Shape.EAST_WEST));
            assertTrue(crossing.blocks().stream().allMatch(change -> change.position().x() >= 0 && change.position().x() < 16
                    && change.position().z() >= 0 && change.position().z() < 16));
            assertEquals(height > 64, !crossing.excavation().isEmpty());
            if (height > 64) assertTrue(crossing.blocks().stream().anyMatch(change -> change.material() == SurfacePiece.Material.AIR
                    && change.position().y() == 65));
            assertTrue(pieces.stream().filter(piece -> !crossing(piece)).flatMap(piece -> piece.blocks().stream())
                    .noneMatch(change -> change.position().x() >= 3 && change.position().x() <= 8));
        }
    }

    @Test
    void damageIsDeterministicAndNeverLeavesRailsOverMissingDeck() {
        var first = plan(60, 42, SurfaceConfig.defaults());
        assertEquals(first, plan(60, 42, SurfaceConfig.defaults()));
        assertNotEquals(first, plan(60, 77, SurfaceConfig.defaults()));
        for (int seed = 0; seed < 100; seed++) {
            var bridge = plan(60, seed, SurfaceConfig.defaults()).stream().filter(SurfaceCrossingsTest::crossing).findFirst().orElseThrow();
            for (var rail : bridge.blocks().stream().filter(change -> change.material() == SurfacePiece.Material.RAIL).toList()) {
                assertTrue(bridge.blocks().stream().anyMatch(change -> change.position().equals(new SurfacePiece.Cell(
                        rail.position().x(), rail.position().y() - 1, rail.position().z()))));
            }
        }
    }

    @Test
    void rejectsLongDeepExcludedAndCrossChunkSpansAndCanRetainLegacyLayout() {
        var graph = graph(false, List.of(p(0, 8), p(31, 8)));
        for (int height : List.of(50, 72)) assertTrue(plan(height, 1, INTACT).stream().noneMatch(SurfaceCrossingsTest::crossing));
        for (int[] range : List.of(new int[]{4, 12}, new int[]{14, 18})) {
            SurfaceSampler terrain = (x, z) -> new SurfaceSampler.Column(x >= range[0] && x <= range[1] ? 60 : 64, SurfaceStyle.PLAINS);
            for (int chunk : List.of(0, 1)) assertTrue(layout.plan(List.of(graph), chunk, 0, 1, INTACT, terrain).stream().noneMatch(SurfaceCrossingsTest::crossing));
        }
        SurfaceSampler excluded = (x, z) -> new SurfaceSampler.Column(x >= 4 && x <= 7 ? 60 : 64,
                x >= 4 && x <= 7 ? SurfaceStyle.EXCLUDED : SurfaceStyle.PLAINS);
        assertTrue(layout.plan(List.of(graph), 0, 0, 1, INTACT, excluded).stream().noneMatch(SurfaceCrossingsTest::crossing));
        assertTrue(layout.plan(List.of(graph), 0, 0, 1, INTACT, terrain(60), false).stream().noneMatch(SurfaceCrossingsTest::crossing));
    }

    @Test
    void supportsNorthSouthAtNegativeCoordinatesButRejectsSlopedApproaches() {
        var graph = graph(false, List.of(p(8, -16), p(8, -1)));
        var pieces = layout.plan(List.of(graph), 0, -1, 1, INTACT,
                (x, z) -> new SurfaceSampler.Column(z >= -12 && z <= -9 ? 60 : 64, SurfaceStyle.TAIGA));
        var bridge = pieces.stream().filter(SurfaceCrossingsTest::crossing).findFirst().orElseThrow();
        assertTrue(bridge.blocks().stream().filter(change -> change.material() == SurfacePiece.Material.RAIL)
                .allMatch(change -> change.shape() == SurfacePiece.Shape.NORTH_SOUTH));
        assertTrue(bridge.blocks().stream().anyMatch(change -> change.material() == SurfacePiece.Material.SPRUCE_PLANKS));
        assertTrue(bridge.blocks().stream().allMatch(change -> Math.floorDiv(change.position().z(), 16) == -1));
        var line = graph(false, List.of(p(0, 8), p(15, 8)));
        assertTrue(layout.plan(List.of(line), 0, 0, 1, INTACT,
                (x, z) -> new SurfaceSampler.Column(x == 2 ? 65 : x >= 4 && x <= 7 ? 60 : 64, SurfaceStyle.PLAINS))
                .stream().noneMatch(SurfaceCrossingsTest::crossing));
    }

    private List<SurfacePiece> plan(int height, long seed, SurfaceConfig config) {
        return layout.plan(List.of(graph(false, List.of(p(0, 8), p(15, 8)))), 0, 0, seed, config, terrain(height));
    }

    private static SurfaceSampler terrain(int height) {
        return (x, z) -> new SurfaceSampler.Column(x >= 4 && x <= 7 ? height : 64, SurfaceStyle.PLAINS);
    }

    private static boolean crossing(SurfacePiece piece) {
        return piece.kind() == SurfacePiece.Kind.BRIDGE || piece.kind() == SurfacePiece.Kind.TUNNEL;
    }
}
