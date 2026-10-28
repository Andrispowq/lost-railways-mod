package io.github.andrispowq.lostrailways.planner;

import java.util.List;

public record RegionGraph(RegionPos region, List<NetworkNode> nodes, List<NetworkEdge> edges,
                          int terrainSamples, int expandedCells) {
    public RegionGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
