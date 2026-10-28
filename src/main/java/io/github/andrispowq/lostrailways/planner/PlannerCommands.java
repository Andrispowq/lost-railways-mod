package io.github.andrispowq.lostrailways.planner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.andrispowq.lostrailways.LostRailwaysMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class PlannerCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("lost_railways_planner");
    private static final Map<MinecraftServer, PlannerService> SERVICES = new ConcurrentHashMap<>();

    private PlannerCommands() { }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var data = server.getWorldPath(LevelResource.DATA);
                var settings = WorldPlanStore.loadOrCreate(data.resolve("lost_railways_planner.json"), () ->
                        new WorldPlanSettings(1, LostRailwaysMod.config().generationEnabled(), LostRailwaysMod.config().planner(),
                                MinecraftTerrain.resolveBiomes(server.overworld(), LostRailwaysMod.config().planner())));
                SERVICES.put(server, new PlannerService(settings, data.resolve("lost_railways_debug")));
                LOGGER.info("World planner v{} initialized; generation enabled: {}", settings.plannerVersion(), settings.generationEnabled());
            } catch (Exception exception) {
                // Fail closed: never replace a damaged snapshot with defaults and silently rearrange routes.
                LOGGER.error("World railway planner unavailable; preserved its settings file", exception);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PlannerService service = SERVICES.remove(server);
            if (service != null) {
                service.close();
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                Commands.literal("lostrailways").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("graph")
                                .executes(context -> inspect(context.getSource(), null))
                                .then(Commands.argument("regionX", IntegerArgumentType.integer(-30_000, 30_000))
                                        .then(Commands.argument("regionZ", IntegerArgumentType.integer(-30_000, 30_000))
                                                .executes(context -> inspect(context.getSource(), new RegionPos(
                                                        IntegerArgumentType.getInteger(context, "regionX"),
                                                        IntegerArgumentType.getInteger(context, "regionZ")))))))));
    }

    private static int inspect(CommandSourceStack source, RegionPos requested) {
        PlannerService service = SERVICES.get(source.getServer());
        if (service == null || !service.settings().generationEnabled()) {
            source.sendFailure(Component.literal("Railway planning is disabled or unavailable for this world; check its snapshot and server log."));
            return 0;
        }
        if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Planner v1 currently supports the Overworld only."));
            return 0;
        }
        try {
            RegionPos region = requested == null ? RegionPos.containing((int) Math.floor(source.getPosition().x),
                    (int) Math.floor(source.getPosition().z), service.settings().config().regionSizeBlocks()) : requested;
            var sampler = MinecraftTerrain.capture(source.getLevel(), service.settings());
            service.inspect(source.getLevel().getSeed(), source.getLevel().dimension().identifier().toString(), region, sampler)
                    .whenComplete((inspection, failure) -> {
                        if (!service.isClosed()) {
                            source.getServer().execute(() -> {
                                if (failure != null) {
                                    LOGGER.warn("Region inspection failed", failure);
                                    source.sendFailure(Component.literal("Planner failed: " + failure.getMessage()));
                                    return;
                                }
                                RegionGraph graph = inspection.graph();
                                source.sendSuccess(() -> Component.literal("Region " + region.x() + ", " + region.z() + ": "
                                        + graph.nodes().size() + " nodes, " + graph.edges().size() + " routes; "
                                        + graph.terrainSamples() + " terrain samples, " + graph.expandedCells() + " search expansions, "
                                        + inspection.elapsedMillis() + " ms. No blocks placed."), false);
                                for (NetworkNode node : graph.nodes()) {
                                    source.sendSuccess(() -> Component.literal(node.kind() + " at " + node.position().x() + " "
                                            + node.position().y() + " " + node.position().z()), false);
                                }
                                for (NetworkEdge edge : graph.edges()) {
                                    RoutePoint start = edge.path().getFirst();
                                    RoutePoint end = edge.path().getLast();
                                    source.sendSuccess(() -> Component.literal("Route " + start.x() + "," + start.z() + " -> "
                                            + end.x() + "," + end.z() + ": " + edge.path().size() + " coarse waypoints"), false);
                                }
                                long isolated = graph.nodes().stream().filter(node -> graph.edges().stream()
                                        .noneMatch(edge -> edge.from().equals(node.id()) || edge.to().equals(node.id()))).count();
                                source.sendSuccess(() -> Component.literal("Unconnected candidate nodes: " + isolated
                                        + ". Full graph: " + inspection.file()), false);
                            });
                        }
                    });
            source.sendSuccess(() -> Component.literal("Railway graph inspection queued."), false);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }
}
