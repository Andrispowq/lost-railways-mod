package io.github.andrispowq.lostrailways.planner;

import io.github.andrispowq.lostrailways.config.PlannerConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;

/** Fixed-size Dijkstra search: at most 1089 expanded cells per candidate edge. */
final class RouteGrid {
    static final int CELLS = 32;
    static final int WIDTH = CELLS + 1;
    static final int COUNT = WIDTH * WIDTH;
    final TerrainSampler.Sample[] samples = new TerrainSampler.Sample[COUNT];
    private final RegionPos region;
    private final PlannerConfig config;
    private final int step;
    int expanded;

    RouteGrid(RegionPos region, PlannerConfig config, TerrainSampler sampler) {
        this.region = region;
        this.config = config;
        this.step = config.regionSizeBlocks() / CELLS;
        for (int index = 0; index < COUNT; index++) {
            checkCancelled();
            RoutePoint point = point(index, 0);
            samples[index] = sampler.sample(point.x(), point.z());
        }
    }

    RoutePoint point(int index) {
        return point(index, samples[index].height());
    }

    private RoutePoint point(int index, int height) {
        return new RoutePoint(region.x() * config.regionSizeBlocks() + index % WIDTH * step,
                height, region.z() * config.regionSizeBlocks() + index / WIDTH * step);
    }

    static boolean boundary(int index) {
        int x = index % WIDTH;
        int z = index / WIDTH;
        return x == 0 || x == CELLS || z == 0 || z == CELLS;
    }

    /** Prefer a viable local cluster over isolated station candidates scattered across excluded terrain. */
    List<Integer> stationCandidates() {
        boolean[] seen = new boolean[COUNT];
        List<Integer> largest = List.of();
        for (int start = 0; start < COUNT; start++) {
            if (seen[start] || boundary(start) || samples[start].rule().cost() == 0) {
                continue;
            }
            List<Integer> candidates = new ArrayList<>();
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int x = current % WIDTH;
                int z = current / WIDTH;
                if (x >= 4 && x <= 28 && z >= 4 && z <= 28 && samples[current].rule().stations()) {
                    candidates.add(current);
                }
                for (int next : new int[]{current - 1, current + 1, current - WIDTH, current + WIDTH}) {
                    if (!boundary(next) && !seen[next] && traversable(current, next)) {
                        seen[next] = true;
                        queue.add(next);
                    }
                }
            }
            if (candidates.size() > largest.size()) {
                largest = candidates;
            }
        }
        return new ArrayList<>(largest);
    }

    private boolean traversable(int from, int to) {
        return samples[to].rule().cost() > 0
                && (double) Math.abs(samples[from].height() - samples[to].height()) / step <= config.maximumSlope();
    }

    Route route(int start, int end) {
        double[] distance = new double[COUNT];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        int[] parent = new int[COUNT];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[COUNT];
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::cost).thenComparingInt(Visit::index));
        distance[start] = 0;
        queue.add(new Visit(start, 0));
        while (!queue.isEmpty()) {
            checkCancelled();
            int current = queue.remove().index();
            if (visited[current]) {
                continue;
            }
            visited[current] = true;
            expanded++;
            if (current == end) {
                List<RoutePoint> path = new ArrayList<>();
                for (int i = end; i != -1; i = parent[i]) {
                    path.add(point(i));
                }
                Collections.reverse(path);
                return new Route(List.copyOf(path), distance[end]);
            }
            int x = current % WIDTH;
            int z = current / WIDTH;
            int[] neighbors = {x > 0 ? current - 1 : -1, x < CELLS ? current + 1 : -1,
                    z > 0 ? current - WIDTH : -1, z < CELLS ? current + WIDTH : -1};
            for (int next : neighbors) {
                // Only negotiated gateways may touch the region border.
                if (next < 0 || visited[next] || boundary(next) && next != end
                        || !traversable(current, next)) {
                    continue;
                }
                int rise = Math.abs(samples[current].height() - samples[next].height());
                double cost = step * (samples[current].rule().cost() + samples[next].rule().cost()) / 2.0 + rise * 8.0;
                double candidate = distance[current] + cost;
                if (candidate < distance[next]) {
                    distance[next] = candidate;
                    parent[next] = current;
                    queue.add(new Visit(next, candidate));
                }
            }
        }
        return null;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Planning cancelled");
        }
    }

    record Route(List<RoutePoint> path, double cost) { }
    private record Visit(int index, double cost) { }
}
