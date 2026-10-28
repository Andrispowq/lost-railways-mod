package io.github.andrispowq.lostrailways.surface;

import io.github.andrispowq.lostrailways.LostRailwaysMod;
import io.github.andrispowq.lostrailways.planner.MinecraftTerrain;
import io.github.andrispowq.lostrailways.planner.NetworkNode;
import io.github.andrispowq.lostrailways.planner.NetworkPlanner;
import io.github.andrispowq.lostrailways.planner.RegionGraph;
import io.github.andrispowq.lostrailways.planner.RegionPos;
import io.github.andrispowq.lostrailways.planner.WorldPlanSettings;
import io.github.andrispowq.lostrailways.planner.WorldPlanStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Lazy initialization is required before SERVER_STARTED: spawn terrain is already generating then. */
public final class RailwayWorlds {
    private static final Map<MinecraftServer, Optional<Context>> WORLDS = new ConcurrentHashMap<>();

    private RailwayWorlds() { }

    public static Context get(ServerLevel level) {
        return WORLDS.computeIfAbsent(level.getServer(), server -> {
            try {
                var data = server.getWorldPath(LevelResource.DATA);
                var settings = WorldPlanStore.loadOrCreate(data.resolve("lost_railways_planner.json"), () ->
                        new WorldPlanSettings(1, LostRailwaysMod.config().generationEnabled(), LostRailwaysMod.config().planner(),
                                MinecraftTerrain.resolveBiomes(level, LostRailwaysMod.config().planner())));
                SurfaceSettings surface = SurfaceSettings.loadOrCreate(data.resolve("lost_railways_surface.json"), level,
                        LostRailwaysMod.config().surface());
                return Optional.of(new Context(settings, surface));
            } catch (Exception exception) {
                org.slf4j.LoggerFactory.getLogger("lost_railways").error("Railway generation disabled; preserved world settings", exception);
                return Optional.empty();
            }
        }).orElse(null);
    }

    public static void release(MinecraftServer server) { WORLDS.remove(server); }

    public static final class Context {
        private final WorldPlanSettings settings;
        private final SurfaceSettings surface;
        private final Map<RegionPos, RegionGraph> graphs = new LinkedHashMap<>(16, 0.75f, true);

        private Context(WorldPlanSettings settings, SurfaceSettings surface) {
            this.settings = settings;
            this.surface = surface;
        }

        public WorldPlanSettings settings() { return settings; }
        public SurfaceSettings surface() { return surface; }

        private synchronized RegionGraph graph(ServerLevel level, RegionPos region) {
            RegionGraph graph = graphs.get(region);
            if (graph == null) {
                graph = new NetworkPlanner().plan(level.getSeed(), level.dimension().identifier().toString(), region,
                        settings.config(), MinecraftTerrain.capture(level, settings));
                graphs.put(region, graph);
                if (graphs.size() > 16) graphs.remove(graphs.keySet().iterator().next());
            }
            return graph;
        }

        public List<RegionGraph> graphsForChunk(ServerLevel level, int chunkX, int chunkZ) {
            int size = settings.config().regionSizeBlocks();
            int x = chunkX * 16;
            int z = chunkZ * 16;
            RegionPos region = RegionPos.containing(x, z, size);
            RegionGraph own = graph(level, region);
            List<RegionGraph> result = new ArrayList<>();
            result.add(own);
            // Only shared gateways near this chunk require the neighboring graph; ordinary boundary chunks do not.
            for (NetworkNode node : own.nodes()) {
                var p = node.position();
                if (node.kind() != NetworkNode.Kind.BOUNDARY || p.x() < x - 2 || p.x() > x + 17 || p.z() < z - 2 || p.z() > z + 17) continue;
                RegionPos neighbor;
                if (p.x() == region.x() * size) neighbor = new RegionPos(region.x() - 1, region.z());
                else if (p.x() == (region.x() + 1) * size) neighbor = new RegionPos(region.x() + 1, region.z());
                else if (p.z() == region.z() * size) neighbor = new RegionPos(region.x(), region.z() - 1);
                else neighbor = new RegionPos(region.x(), region.z() + 1);
                RegionGraph adjacent = graph(level, neighbor);
                if (!result.contains(adjacent)) result.add(adjacent);
            }
            return List.copyOf(result);
        }
    }
}
