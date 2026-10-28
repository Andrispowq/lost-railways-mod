package io.github.andrispowq.lostrailways.planner;

import io.github.andrispowq.lostrailways.config.PlannerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerServiceTest {
    @TempDir Path directory;

    private WorldPlanSettings settings(boolean enabled) {
        return new WorldPlanSettings(1, enabled, PlannerConfig.defaults(), Map.of("minecraft:plains", new BiomeRule(1, true)));
    }

    @Test
    void runsOffCallerThreadAndCachesWithoutResampling() throws Exception {
        Thread caller = Thread.currentThread();
        try (var service = new PlannerService(settings(true), directory)) {
            var first = service.inspect(7, "minecraft:overworld", new RegionPos(0, 0), (x, z) -> {
                assertNotSame(caller, Thread.currentThread());
                return new TerrainSampler.Sample(70, new BiomeRule(1, true));
            }).get(10, TimeUnit.SECONDS);
            var cached = service.inspect(7, "minecraft:overworld", new RegionPos(0, 0), (x, z) -> {
                throw new AssertionError("Cached region must not be sampled again");
            }).get(10, TimeUnit.SECONDS);
            assertEquals(first.graph(), cached.graph());
            assertTrue(Files.readString(first.file()).contains("minecraft:overworld"));
        }
    }

    @Test
    void boundsQueueAndCancelsOutstandingWorkOnShutdown() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var service = new PlannerService(settings(true), directory);
        TerrainSampler blocking = (x, z) -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new TerrainSampler.Sample(70, new BiomeRule(1, true));
        };
        try {
            var first = service.inspect(1, "minecraft:overworld", new RegionPos(0, 0), blocking);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = service.inspect(1, "minecraft:overworld", new RegionPos(1, 0), blocking);
            var third = service.inspect(1, "minecraft:overworld", new RegionPos(2, 0), blocking);
            var rejected = service.inspect(1, "minecraft:overworld", new RegionPos(3, 0), blocking);
            assertTrue(rejected.isCompletedExceptionally());
            service.close();
            assertTrue(first.isDone());
            assertTrue(second.isCancelled());
            assertTrue(third.isCancelled());
        } finally {
            release.countDown();
            service.close();
        }
    }

    @Test
    void disabledWorldDoesNotSampleOrExport() throws Exception {
        try (var service = new PlannerService(settings(false), directory)) {
            assertTrue(service.inspect(0, "minecraft:overworld", new RegionPos(0, 0), (x, z) -> {
                throw new AssertionError("Disabled world must not plan");
            }).isCompletedExceptionally());
        }
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }
}
