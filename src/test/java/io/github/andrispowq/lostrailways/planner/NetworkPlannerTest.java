package io.github.andrispowq.lostrailways.planner;

import io.github.andrispowq.lostrailways.config.PlannerConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPlannerTest {
    private final PlannerConfig config = new PlannerConfig(128, 1, 0.25, PlannerConfig.defaults().biomeRules());
    private final TerrainSampler plains = (x, z) -> new TerrainSampler.Sample(70, new BiomeRule(1, true));
    private final NetworkPlanner planner = new NetworkPlanner();

    @Test
    void plannerVersionOneTopologyRemainsStable() throws Exception {
        RegionGraph graph = planner.plan(42, "minecraft:overworld", new RegionPos(-2, 3), config, plains);
        byte[] topology = (graph.nodes().toString() + graph.edges().toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(topology));
        assertEquals("d9bc4ccfb88a2b05bfa034dbf0ef938b4604f134abb2933617ee37d7f8a399fe", fingerprint,
                "Changing persisted-world topology requires an explicit planner-version/migration decision");
    }

    @Test
    void deterministicAcrossTraversalOrderAndConcurrentRequests() throws Exception {
        RegionPos region = new RegionPos(-2, 3);
        RegionGraph expected = planner.plan(42, "minecraft:overworld", region, config, plains);
        planner.plan(42, "minecraft:overworld", new RegionPos(-1, 3), config, plains);
        assertEquals(expected, planner.plan(42, "minecraft:overworld", region, config, plains));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<RegionGraph>>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> planner.plan(42, "minecraft:overworld", region, config, plains)));
            }
            for (var future : futures) {
                assertEquals(expected, future.get());
            }
        }
        assertNotEquals(expected.nodes(), planner.plan(43, "minecraft:overworld", region, config, plains).nodes());
        assertNotEquals(expected.nodes(), planner.plan(42, "other:dimension", region, config, plains).nodes());
    }

    @Test
    void neighboringRegionsAgreeOnBoundaryIdsCoordinatesAndHeights() {
        for (int x = -2; x <= 1; x++) {
            RegionGraph west = planner.plan(7, "minecraft:overworld", new RegionPos(x, -1), config, plains);
            RegionGraph east = planner.plan(7, "minecraft:overworld", new RegionPos(x + 1, -1), config, plains);
            int boundary = (x + 1) * config.regionSizeBlocks();
            assertEquals(boundaryNodes(west, boundary, true), boundaryNodes(east, boundary, true));
            assertEquals(1, boundaryNodes(west, boundary, true).size());
            RegionGraph south = planner.plan(7, "minecraft:overworld", new RegionPos(x, 0), config, plains);
            assertEquals(boundaryNodes(west, 0, false), boundaryNodes(south, 0, false));
            assertEquals(1, boundaryNodes(west, 0, false).size());
        }
        assertEquals(new RegionPos(-1, -2), RegionPos.containing(-1, -2049, 2048));
    }

    @Test
    void flatFriendlyTerrainProducesConnectedImmutableBoundedGraph() {
        AtomicInteger samples = new AtomicInteger();
        RegionGraph graph = planner.plan(42, "minecraft:overworld", new RegionPos(0, 0), config, (x, z) -> {
            samples.incrementAndGet();
            return plains.sample(x, z);
        });
        assertEquals(7, graph.nodes().size());
        assertEquals(graph.nodes().size() - 1, graph.edges().size());
        assertEquals(1089, samples.get());
        assertTrue(graph.expandedCells() <= 21 * 1089);
        var connected = new java.util.HashSet<String>();
        connected.add(graph.nodes().getFirst().id());
        for (int pass = 0; pass < graph.nodes().size(); pass++) {
            for (NetworkEdge edge : graph.edges()) {
                if (connected.contains(edge.from()) || connected.contains(edge.to())) {
                    connected.add(edge.from());
                    connected.add(edge.to());
                }
                assertEquals(graph.nodes().stream().filter(node -> node.id().equals(edge.from())).findFirst().orElseThrow().position(), edge.path().getFirst());
                assertEquals(graph.nodes().stream().filter(node -> node.id().equals(edge.to())).findFirst().orElseThrow().position(), edge.path().getLast());
                for (int i = 1; i < edge.path().size(); i++) {
                    RoutePoint a = edge.path().get(i - 1);
                    RoutePoint b = edge.path().get(i);
                    assertEquals(64, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()));
                }
            }
        }
        assertEquals(graph.nodes().size(), connected.size());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.edges().getFirst().path().clear());
    }

    @Test
    void exclusionsAndCliffsSplitNetworksWithoutForcedCrossings() {
        for (boolean cliff : new boolean[]{false, true}) {
            TerrainSampler terrain = (x, z) -> new TerrainSampler.Sample(cliff && x >= 1024 ? 200 : 70,
                    !cliff && x == 1024 ? BiomeRule.EXCLUDED : new BiomeRule(1, true));
            RegionGraph graph = planner.plan(4, "minecraft:overworld", new RegionPos(0, 0), config, terrain);
            assertTrue(graph.edges().size() < graph.nodes().size() - 1);
            for (NetworkEdge edge : graph.edges()) {
                boolean west = edge.path().getFirst().x() < 1024;
                assertTrue(edge.path().stream().allMatch(point -> (point.x() < 1024) == west));
            }
        }
        RegionGraph ocean = planner.plan(1, "minecraft:overworld", new RegionPos(0, 0), config,
                (x, z) -> new TerrainSampler.Sample(40, BiomeRule.EXCLUDED));
        assertTrue(ocean.nodes().isEmpty());
        assertTrue(ocean.edges().isEmpty());
    }

    @Test
    void weightedRoutingAvoidsExpensiveBiomeAndRespectsSearchBounds() {
        RouteGrid grid = new RouteGrid(new RegionPos(0, 0), config,
                (x, z) -> new TerrainSampler.Sample(70, new BiomeRule(x == 1024 && z < 1024 ? 100 : 1, true)));
        RouteGrid.Route route = grid.route(8 * 33 + 8, 8 * 33 + 24);
        assertNotNull(route);
        assertTrue(route.path().stream().anyMatch(point -> point.z() >= 1024));
        assertTrue(grid.expanded <= 1089);
        assertThrows(IllegalArgumentException.class, () -> planner.plan(0, "minecraft:overworld",
                new RegionPos(Integer.MIN_VALUE, 0), config, plains));
    }

    @Test
    void stationsClusterInTheLargestSuitableConnectedArea() {
        PlannerConfig local = new PlannerConfig(128, 0, 0.25, config.biomeRules());
        RegionGraph graph = planner.plan(42, "minecraft:overworld", new RegionPos(0, 0), local, (x, z) -> {
            boolean suitable = x >= 1024 || x >= 256 && x <= 320 && z >= 256 && z <= 320;
            return new TerrainSampler.Sample(70, suitable ? new BiomeRule(1, true) : BiomeRule.EXCLUDED);
        });
        assertEquals(3, graph.nodes().size());
        assertEquals(2, graph.edges().size());
        assertTrue(graph.nodes().stream().allMatch(node -> node.position().x() >= 1024));
    }

    @Test
    void disabledBoundaryChanceLeavesLocalNetworkOnly() {
        PlannerConfig local = new PlannerConfig(128, 0, 0.25, config.biomeRules());
        RegionGraph graph = planner.plan(5, "minecraft:overworld", new RegionPos(0, 0), local, plains);
        assertEquals(3, graph.nodes().size());
        assertEquals(2, graph.edges().size());
    }

    private static List<NetworkNode> boundaryNodes(RegionGraph graph, int coordinate, boolean vertical) {
        return graph.nodes().stream().filter(node -> node.kind() == NetworkNode.Kind.BOUNDARY
                && (vertical ? node.position().x() : node.position().z()) == coordinate).toList();
    }
}
