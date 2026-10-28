package io.github.andrispowq.lostrailways.planner;

import io.github.andrispowq.lostrailways.config.PlannerConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stateless planner-v1. No global random state, chunk access, or traversal-order dependencies. */
public final class NetworkPlanner {
    public RegionGraph plan(long worldSeed, String dimension, RegionPos region, PlannerConfig config, TerrainSampler sampler) {
        int limit = 29_999_000 / config.regionSizeBlocks() - 1;
        if (Math.abs((long) region.x()) > limit || Math.abs((long) region.z()) > limit) {
            throw new IllegalArgumentException("Region is outside the supported world bounds");
        }
        long seed = StableSeed.key(worldSeed, dimension);
        RouteGrid grid = new RouteGrid(region, config, sampler);
        List<NodeAt> nodes = new ArrayList<>();
        List<Integer> candidates = grid.stationCandidates();
        long regionSeed = StableSeed.key(seed, region.x() + ":" + region.z());
        candidates.sort(Comparator.<Integer>comparingLong(i -> StableSeed.mix(regionSeed + i)).thenComparingInt(i -> i));
        for (int index : candidates) {
            boolean separated = nodes.stream().allMatch(node -> {
                int dx = index % RouteGrid.WIDTH - node.index() % RouteGrid.WIDTH;
                int dz = index / RouteGrid.WIDTH - node.index() / RouteGrid.WIDTH;
                return dx * dx + dz * dz >= 64;
            });
            if (separated) {
                String id = dimension + ":node:" + region.x() + ":" + region.z() + ":" + nodes.size();
                nodes.add(new NodeAt(new NetworkNode(id, NetworkNode.Kind.STATION,
                        grid.point(index)), index));
                if (nodes.size() == 3) {
                    break;
                }
            }
        }
        gateway(nodes, grid, seed, dimension, region.x(), region.z(), true, false, config);
        gateway(nodes, grid, seed, dimension, region.x() + 1, region.z(), true, true, config);
        gateway(nodes, grid, seed, dimension, region.x(), region.z(), false, false, config);
        gateway(nodes, grid, seed, dimension, region.x(), region.z() + 1, false, true, config);
        nodes.sort(Comparator.comparing(node -> node.node().id()));

        // A minimum-cost spanning forest preserves disconnected terrain components instead of forcing crossings.
        List<Candidate> routes = new ArrayList<>();
        for (int a = 0; a < nodes.size(); a++) {
            for (int b = a + 1; b < nodes.size(); b++) {
                RouteGrid.Route route = grid.route(nodes.get(a).index(), nodes.get(b).index());
                if (route != null) {
                    String id = nodes.get(a).node().id() + "->" + nodes.get(b).node().id();
                    routes.add(new Candidate(a, b, new NetworkEdge(id, nodes.get(a).node().id(), nodes.get(b).node().id(),
                            route.path(), route.cost())));
                }
            }
        }
        routes.sort(Comparator.comparingDouble((Candidate route) -> route.edge().cost()).thenComparing(route -> route.edge().id()));
        int[] component = new int[nodes.size()];
        for (int i = 0; i < component.length; i++) {
            component[i] = i;
        }
        List<NetworkEdge> edges = new ArrayList<>();
        for (Candidate route : routes) {
            int from = component[route.from()];
            int to = component[route.to()];
            if (from != to) {
                edges.add(route.edge());
                for (int i = 0; i < component.length; i++) {
                    if (component[i] == to) {
                        component[i] = from;
                    }
                }
            }
        }
        List<NetworkNode> classified = nodes.stream().map(NodeAt::node).map(node -> {
            long degree = edges.stream().filter(edge -> edge.from().equals(node.id()) || edge.to().equals(node.id())).count();
            return node.kind() != NetworkNode.Kind.BOUNDARY && degree >= 3
                    ? new NetworkNode(node.id(), NetworkNode.Kind.JUNCTION, node.position()) : node;
        }).toList();
        return new RegionGraph(region, classified, edges, RouteGrid.COUNT, grid.expanded);
    }

    private static void gateway(List<NodeAt> nodes, RouteGrid grid, long seed, String dimension,
                                int boundaryX, int boundaryZ, boolean vertical, boolean far, PlannerConfig config) {
        // Both neighboring regions derive exactly the same ID, chance and position from this boundary key.
        String id = dimension + ":boundary:" + (vertical ? "x:" : "z:") + boundaryX + ":" + boundaryZ;
        long key = StableSeed.key(seed, id);
        if (StableSeed.fraction(key) >= config.boundaryConnectionChance()) {
            return;
        }
        int offset = 4 + (int) Math.floorMod(StableSeed.mix(key), 25);
        int edge = far ? RouteGrid.CELLS : 0;
        int index = vertical ? offset * RouteGrid.WIDTH + edge : edge * RouteGrid.WIDTH + offset;
        if (grid.samples[index].rule().cost() > 0) {
            nodes.add(new NodeAt(new NetworkNode(id, NetworkNode.Kind.BOUNDARY, grid.point(index)), index));
        }
    }

    private record NodeAt(NetworkNode node, int index) { }
    private record Candidate(int from, int to, NetworkEdge edge) { }
}
