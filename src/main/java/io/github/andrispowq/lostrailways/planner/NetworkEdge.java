package io.github.andrispowq.lostrailways.planner;

import java.util.List;

/** Coarse route intent; placement must refine and validate terrain between these points. */
public record NetworkEdge(String id, String from, String to, List<RoutePoint> path, double cost) {
    public NetworkEdge {
        path = List.copyOf(path);
        if (path.size() < 2 || !Double.isFinite(cost) || cost <= 0) {
            throw new IllegalArgumentException("A route needs a positive cost and at least two points");
        }
    }
}
