package io.github.andrispowq.lostrailways.planner;

import io.github.andrispowq.lostrailways.config.PlannerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;

import java.util.HashMap;
import java.util.Map;

public final class MinecraftTerrain {
    private MinecraftTerrain() { }

    /** Resolve tag membership once on the server thread, then persist plain values. Exclusions win. */
    public static Map<String, BiomeRule> resolveBiomes(ServerLevel level, PlannerConfig config) {
        Map<TagKey<Biome>, BiomeRule> tags = new HashMap<>();
        config.biomeRules().forEach((tag, rule) -> tags.put(TagKey.create(Registries.BIOME, Identifier.parse(tag)), rule));
        Map<String, BiomeRule> result = new HashMap<>();
        for (var biome : level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()) {
            BiomeRule selected = null;
            for (var entry : tags.entrySet()) {
                if (biome.is(entry.getKey())) {
                    BiomeRule rule = entry.getValue();
                    if (selected == null || rule.cost() < selected.cost()) {
                        selected = rule;
                    } else if (rule.cost() == selected.cost()) {
                        selected = new BiomeRule(rule.cost(), selected.stations() && rule.stations());
                    }
                }
            }
            result.put(biome.unwrapKey().orElseThrow().identifier().toString(), selected == null ? BiomeRule.EXCLUDED : selected);
        }
        return Map.copyOf(result);
    }

    /** Capture generator inputs on the server thread. The returned sampler never accesses the level/chunk source. */
    public static TerrainSampler capture(ServerLevel level, WorldPlanSettings settings) {
        var generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator) && !(generator instanceof FlatLevelSource)) {
            throw new IllegalArgumentException("Planner v1 supports vanilla noise and flat generators only");
        }
        var randomState = level.getChunkSource().randomState();
        var heights = LevelHeightAccessor.create(level.getMinY(), level.getHeight());
        var biomes = generator.getBiomeSource().createCachingResolver(randomState);
        int seaLevel = generator.getSeaLevel();
        return (x, z) -> {
            int y = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, randomState);
            var biome = biomes.getNoiseBiome(Math.floorDiv(x, 4), Math.floorDiv(y, 4), Math.floorDiv(z, 4));
            String id = biome.unwrapKey().orElseThrow().identifier().toString();
            BiomeRule rule = settings.resolvedBiomes().getOrDefault(id, BiomeRule.EXCLUDED);
            return new TerrainSampler.Sample(y, y < seaLevel ? BiomeRule.EXCLUDED : rule);
        };
    }
}
