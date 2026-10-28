package io.github.andrispowq.lostrailways.planner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One worker, two queued requests, sixteen cached regions. No idle planning. */
public final class PlannerService implements AutoCloseable {
    private final WorldPlanSettings settings;
    private final Path debugDirectory;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), runnable -> {
                Thread thread = new Thread(runnable, "lost-railways-planner");
                thread.setDaemon(true);
                return thread;
            });
    private final java.util.Set<CompletableFuture<Inspection>> requests = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<CacheKey, RegionGraph> cache = new LinkedHashMap<>(16, 0.75f, true);
    private volatile boolean closed;

    public PlannerService(WorldPlanSettings settings, Path debugDirectory) {
        this.settings = settings;
        this.debugDirectory = debugDirectory;
    }

    public WorldPlanSettings settings() {
        return settings;
    }

    public boolean isClosed() {
        return closed;
    }

    public CompletableFuture<Inspection> inspect(long seed, String dimension, RegionPos region, TerrainSampler sampler) {
        if (closed || !settings.generationEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("World planning is disabled or stopping"));
        }
        try {
            CompletableFuture<Inspection> request = CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                CacheKey key = new CacheKey(seed, dimension, region);
                RegionGraph graph = cache.get(key);
                if (graph == null) {
                    graph = new NetworkPlanner().plan(seed, dimension, region, settings.config(), sampler);
                    cache.put(key, graph);
                    if (cache.size() > 16) {
                        cache.remove(cache.keySet().iterator().next());
                    }
                }
                if (closed) {
                    throw new java.util.concurrent.CancellationException("Planner stopped");
                }
                Path output = debugDirectory.resolve("region_" + region.x() + "_" + region.z() + ".json");
                try {
                    WorldPlanStore.writeAtomic(output, graph);
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
                return new Inspection(graph, output, (System.nanoTime() - start) / 1_000_000);
            }, worker);
            requests.add(request);
            request.whenComplete((result, failure) -> requests.remove(request));
            if (closed) {
                request.cancel(false);
            }
            return request;
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new IllegalStateException("Planner busy; retry after the queued requests finish"));
        }
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
        requests.forEach(request -> request.cancel(false));
    }

    private record CacheKey(long seed, String dimension, RegionPos region) { }

    public record Inspection(RegionGraph graph, Path file, long elapsedMillis) { }
}
