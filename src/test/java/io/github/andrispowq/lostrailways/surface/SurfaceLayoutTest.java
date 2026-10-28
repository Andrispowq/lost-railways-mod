package io.github.andrispowq.lostrailways.surface;

import io.github.andrispowq.lostrailways.config.SurfaceConfig;
import io.github.andrispowq.lostrailways.planner.NetworkEdge;
import io.github.andrispowq.lostrailways.planner.NetworkNode;
import io.github.andrispowq.lostrailways.planner.RegionGraph;
import io.github.andrispowq.lostrailways.planner.RegionPos;
import io.github.andrispowq.lostrailways.planner.RoutePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceLayoutTest {
    private static final SurfaceConfig INTACT = new SurfaceConfig(12, 0, 0);
    private static final SurfaceSampler FLAT = (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.PLAINS);
    private final SurfaceLayout layout = new SurfaceLayout();

    @Test
    void chunkTraversalOrderAndParallelGenerationProduceIdenticalDamage() throws Exception {
        RegionGraph graph = graph(false, List.of(p(-40, 8), p(70, 8)));
        List<Integer> chunks = new ArrayList<>(List.of(-3, -2, -1, 0, 1, 2, 3, 4));
        Map<SurfacePiece.Cell, SurfacePiece.Change> expected = collect(graph, chunks, 42, SurfaceConfig.defaults());
        Collections.reverse(chunks);
        assertEquals(expected, collect(graph, chunks, 42, SurfaceConfig.defaults()));
        assertNotEquals(expected, collect(graph, chunks, 43, SurfaceConfig.defaults()));
        try (var workers = Executors.newFixedThreadPool(4)) {
            var jobs = chunks.stream().map(chunk -> workers.submit(() -> layout.plan(List.of(graph), chunk, 0, 42, SurfaceConfig.defaults(), FLAT))).toList();
            Map<SurfacePiece.Cell, SurfacePiece.Change> actual = new HashMap<>();
            for (var job : jobs) job.get().forEach(piece -> piece.blocks().forEach(change -> actual.put(change.position(), change)));
            assertEquals(expected, actual);
        }
        long rails = expected.values().stream().filter(SurfaceLayoutTest::rail).count();
        assertTrue(rails > 40 && rails < 100, "Abandoned line retains useful, repairable rail fragments");
    }

    @Test
    void negativeAndPositiveChunkBordersHaveContinuousCorrectlyShapedRails() {
        RegionGraph graph = graph(false, List.of(p(-20, 8), p(16, 8), p(16, 30)));
        Map<SurfacePiece.Cell, SurfacePiece.Change> blocks = new HashMap<>();
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = 0; cz <= 1; cz++) {
                for (var piece : layout.plan(List.of(graph), cx, cz, 7, INTACT, FLAT)) {
                    for (var change : piece.blocks()) {
                        assertEquals(cx, Math.floorDiv(change.position().x(), 16));
                        assertEquals(cz, Math.floorDiv(change.position().z(), 16));
                        blocks.put(change.position(), change);
                    }
                }
            }
        }
        for (int x = -20; x < 16; x++) assertEquals(SurfacePiece.Shape.EAST_WEST, blocks.get(new SurfacePiece.Cell(x, 64, 8)).shape());
        assertEquals(SurfacePiece.Shape.SOUTH_WEST, blocks.get(new SurfacePiece.Cell(16, 64, 8)).shape());
        for (int z = 9; z <= 30; z++) assertEquals(SurfacePiece.Shape.NORTH_SOUTH, blocks.get(new SurfacePiece.Cell(16, 64, z)).shape());
    }

    @Test
    void junctionsMergeOverlappingCorridorsWithoutDuplicateWrites() {
        RegionGraph graph = graph(false, List.of(p(0, 8), p(15, 8)), List.of(p(8, 0), p(8, 8)));
        var pieces = layout.plan(List.of(graph, graph), 0, 0, 1, INTACT, FLAT);
        assertTrue(pieces.stream().anyMatch(piece -> piece.kind() == SurfacePiece.Kind.JUNCTION));
        var rails = pieces.stream().flatMap(piece -> piece.blocks().stream()).filter(SurfaceLayoutTest::rail).toList();
        assertEquals(24, rails.size());
        assertEquals(rails.size(), rails.stream().map(SurfacePiece.Change::position).distinct().count());
        assertEquals(SurfacePiece.Shape.NORTH_EAST, rails.stream().filter(change -> change.position().x() == 8 && change.position().z() == 8).findFirst().orElseThrow().shape());
    }

    @Test
    void gradientsAreRefinedAndCliffsExcludedAtBlockResolution() {
        RegionGraph line = graph(false, List.of(p(0, 8), p(15, 8)));
        var slope = layout.plan(List.of(line), 0, 0, 1, INTACT,
                (x, z) -> new SurfaceSampler.Column(x >= 8 ? 65 : 64, SurfaceStyle.PLAINS));
        assertTrue(slope.stream().flatMap(piece -> piece.blocks().stream()).anyMatch(change -> change.shape() == SurfacePiece.Shape.ASCENDING_EAST));
        var cliff = layout.plan(List.of(line), 0, 0, 1, INTACT,
                (x, z) -> new SurfaceSampler.Column(x >= 8 ? 70 : 64, SurfaceStyle.PLAINS));
        assertFalse(cliff.stream().flatMap(piece -> piece.blocks().stream()).anyMatch(change -> change.position().x() == 7 || change.position().x() == 8));
        assertTrue(layout.plan(List.of(line), 0, 0, 1, INTACT, (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.EXCLUDED)).isEmpty());
        assertTrue(layout.plan(List.of(line), 0, 0, 1, INTACT, (x, z) -> new SurfaceSampler.Column(0, SurfaceStyle.PLAINS)).isEmpty());
    }

    @Test
    void stationsAreSmallChunkOwnedAndForestStationsAreRarer() {
        RegionGraph graph = graph(true, List.of(p(0, 0), p(15, 0)));
        int plains = 0;
        int forest = 0;
        for (int seed = 0; seed < 100; seed++) {
            var pieces = layout.plan(List.of(graph), 0, 0, seed, INTACT, FLAT);
            for (var piece : pieces) {
                if (piece.kind() == SurfacePiece.Kind.STATION) {
                    plains++;
                    assertTrue(piece.blocks().size() <= 100);
                    assertTrue(piece.blocks().stream().allMatch(change -> change.position().x() >= 0 && change.position().x() < 16
                            && change.position().z() >= 0 && change.position().z() < 16));
                }
            }
            forest += (int) layout.plan(List.of(graph), 0, 0, seed, INTACT,
                    (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.FOREST)).stream().filter(piece -> piece.kind() == SurfacePiece.Kind.STATION).count();
        }
        assertTrue(plains > 50 && forest > 0 && forest < plains / 2);
    }

    @Test
    void refinementSamplingIsBoundedAndFourWayCrossingsAreExplicitRuins() {
        RegionGraph graph = graph(false, List.of(p(0, 8), p(15, 8)), List.of(p(8, 0), p(8, 15)));
        AtomicInteger samples = new AtomicInteger();
        var pieces = layout.plan(List.of(graph), 0, 0, 1, INTACT, (x, z) -> { samples.incrementAndGet(); return FLAT.sample(x, z); });
        assertTrue(samples.get() <= 400);
        assertTrue(pieces.stream().anyMatch(piece -> piece.kind() == SurfacePiece.Kind.JUNCTION));
        assertFalse(pieces.stream().flatMap(piece -> piece.blocks().stream()).filter(SurfaceLayoutTest::rail)
                .anyMatch(change -> change.position().x() == 8 && change.position().z() == 8));
    }

    @Test
    void taigaUsesSpruceForStationsAndJunctionsWithForestWear() {
        var graph = graph(true, List.of(p(0, 0), p(15, 0)), List.of(p(8, 0), p(8, 15)));
        int stations = 0;
        for (int seed = 0; seed < 100; seed++) {
            var taiga = layout.plan(List.of(graph), 0, 0, seed, SurfaceConfig.defaults(),
                    (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.TAIGA));
            var forest = layout.plan(List.of(graph), 0, 0, seed, SurfaceConfig.defaults(),
                    (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.FOREST));
            assertEquals(forest.stream().filter(piece -> piece.kind() == SurfacePiece.Kind.TRACK).toList(),
                    taiga.stream().filter(piece -> piece.kind() == SurfacePiece.Kind.TRACK).toList());
            assertEquals(taiga, layout.plan(List.of(graph), 0, 0, seed, SurfaceConfig.defaults(),
                    (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.TAIGA)));
            for (var piece : taiga) {
                assertTrue(piece.blocks().stream().noneMatch(change -> change.material().name().startsWith("OAK_")));
                if (piece.kind() == SurfacePiece.Kind.STATION) {
                    stations++;
                    for (var material : List.of(SurfacePiece.Material.SPRUCE_PLANKS, SurfacePiece.Material.SPRUCE_LOG, SurfacePiece.Material.SPRUCE_SLAB)) {
                        assertTrue(piece.blocks().stream().anyMatch(change -> change.material() == material));
                    }
                }
                if (piece.kind() == SurfacePiece.Kind.JUNCTION) {
                    assertTrue(piece.blocks().stream().anyMatch(change -> change.material() == SurfacePiece.Material.SPRUCE_FENCE));
                }
            }
        }
        assertTrue(stations > 0 && stations < 100, "Two candidate stations per seed should remain occasional");
    }

    @Test
    void remainingBiomesRespectEdgesPalettesAndStationRestrictions() {
        var graph = graph(true, List.of(p(0, 0), p(15, 0)));
        for (var style : List.of(SurfaceStyle.DESERT, SurfaceStyle.JUNGLE, SurfaceStyle.SWAMP)) {
            SurfaceSampler edge = (x, z) -> new SurfaceSampler.Column(64, z >= 16 ? SurfaceStyle.PLAINS : style);
            var pieces = layout.plan(List.of(graph), 0, 0, 42, INTACT, edge);
            assertFalse(pieces.isEmpty());
            assertEquals(pieces, layout.plan(List.of(graph), 0, 0, 42, INTACT, edge));
            assertTrue(pieces.stream().filter(piece -> piece.kind() == SurfacePiece.Kind.TRACK)
                    .flatMap(piece -> piece.blocks().stream()).anyMatch(change -> change.material() == style.bed()));
            if (style == SurfaceStyle.SWAMP) {
                assertTrue(pieces.stream().flatMap(piece -> piece.blocks().stream()).filter(SurfaceLayoutTest::rail)
                        .allMatch(change -> change.position().y() == 65));
            } else {
                for (int seed = 0; seed < 100; seed++) {
                    assertTrue(layout.plan(List.of(graph), 0, 0, seed, INTACT, edge).stream()
                            .noneMatch(piece -> piece.kind() == SurfacePiece.Kind.STATION));
                }
            }
            if (style.edgeOnly()) {
                assertTrue(layout.plan(List.of(graph), 0, 0, 42, INTACT,
                        (x, z) -> new SurfaceSampler.Column(64, style)).isEmpty());
                assertTrue(layout.plan(List.of(graph), 0, 0, 42, INTACT,
                        (x, z) -> new SurfaceSampler.Column(64, z >= 16 ? SurfaceStyle.EXCLUDED : style)).isEmpty());
            }
        }
    }

    @Test
    void raisedSwampTransitionUsesAscendingRailAndEdgeSamplingIsBounded() {
        var graph = graph(false, List.of(p(0, 8), p(31, 8)));
        AtomicInteger samples = new AtomicInteger();
        SurfaceSampler terrain = (x, z) -> {
            samples.incrementAndGet();
            return new SurfaceSampler.Column(64, x < 8 || z >= 16 ? SurfaceStyle.PLAINS : SurfaceStyle.SWAMP);
        };
        var pieces = layout.plan(List.of(graph), 0, 0, 1, INTACT, terrain);
        assertTrue(pieces.stream().flatMap(piece -> piece.blocks().stream()).anyMatch(change ->
                change.position().x() == 7 && change.shape() == SurfacePiece.Shape.ASCENDING_EAST));
        assertTrue(samples.get() <= 400, "Edge queries stay bounded and never request chunks");
    }

    @SafeVarargs
    static RegionGraph graph(boolean stations, List<RoutePoint>... paths) {
        List<NetworkNode> nodes = new ArrayList<>();
        List<NetworkEdge> edges = new ArrayList<>();
        for (List<RoutePoint> path : paths) {
            String from = "node" + nodes.size();
            String to = "node" + (nodes.size() + 1);
            nodes.add(new NetworkNode(from, stations ? NetworkNode.Kind.STATION : NetworkNode.Kind.BOUNDARY, path.getFirst()));
            nodes.add(new NetworkNode(to, NetworkNode.Kind.BOUNDARY, path.getLast()));
            edges.add(new NetworkEdge(from + to, from, to, path, 1));
        }
        return new RegionGraph(new RegionPos(0, 0), nodes, edges, 0, 0);
    }

    private Map<SurfacePiece.Cell, SurfacePiece.Change> collect(RegionGraph graph, List<Integer> chunks, long seed, SurfaceConfig config) {
        Map<SurfacePiece.Cell, SurfacePiece.Change> result = new HashMap<>();
        for (int cx : chunks) layout.plan(List.of(graph), cx, 0, seed, config, FLAT).forEach(piece -> piece.blocks().forEach(change -> result.put(change.position(), change)));
        return result;
    }

    static RoutePoint p(int x, int z) { return new RoutePoint(x, 64, z); }
    static boolean rail(SurfacePiece.Change change) { return change.material() == SurfacePiece.Material.RAIL || change.material() == SurfacePiece.Material.POWERED_RAIL; }
}
