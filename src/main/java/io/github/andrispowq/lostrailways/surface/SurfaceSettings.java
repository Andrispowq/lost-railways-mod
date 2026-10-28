package io.github.andrispowq.lostrailways.surface;

import com.google.gson.JsonParser;
import io.github.andrispowq.lostrailways.config.PlannerConfigJson;
import io.github.andrispowq.lostrailways.config.SurfaceConfig;
import io.github.andrispowq.lostrailways.planner.WorldPlanStore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record SurfaceSettings(int version, SurfaceConfig config, Map<String, SurfaceStyle> styles, boolean crossingsEnabled) {
    public SurfaceSettings {
        if (version != 1) throw new IllegalArgumentException("Unsupported surface version: " + version);
        Objects.requireNonNull(config, "config");
        styles = Map.copyOf(styles);
        if (styles.isEmpty()) throw new IllegalArgumentException("Missing frozen surface biome styles");
    }

    private static boolean readCrossings(com.google.gson.JsonObject json) {
        // Older snapshots deliberately retain their original surface-only behavior.
        if (!json.has("crossingsEnabled")) return false;
        var value = json.get("crossingsEnabled");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("crossingsEnabled must be boolean");
        }
        return value.getAsBoolean();
    }

    public static SurfaceSettings loadOrCreate(Path path, ServerLevel level, SurfaceConfig config) throws IOException {
        if (Files.exists(path)) {
            if (Files.size(path) > 1_048_576) throw new IOException("Surface settings exceed 1 MiB");
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            Map<String, SurfaceStyle> styles = new HashMap<>();
            json.getAsJsonObject("styles").entrySet().forEach(entry -> styles.put(entry.getKey(), SurfaceStyle.valueOf(entry.getValue().getAsString())));
            return new SurfaceSettings(PlannerConfigJson.integer(json, "version"), SurfaceConfig.read(json.getAsJsonObject("config"), false), styles, readCrossings(json));
        }
        TagKey<Biome> desert = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:desert"));
        TagKey<Biome> jungle = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:jungle"));
        TagKey<Biome> swamp = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:swamp"));
        TagKey<Biome> taiga = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:taiga"));
        TagKey<Biome> forest = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:forest"));
        TagKey<Biome> plains = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:preferred"));
        Map<String, SurfaceStyle> styles = new HashMap<>();
        level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach(biome -> styles.put(
                biome.key().identifier().toString(), biome.is(swamp) ? SurfaceStyle.SWAMP : biome.is(jungle) ? SurfaceStyle.JUNGLE
                        : biome.is(desert) ? SurfaceStyle.DESERT : biome.is(taiga) ? SurfaceStyle.TAIGA : biome.is(forest) ? SurfaceStyle.FOREST : biome.is(plains) ? SurfaceStyle.PLAINS : SurfaceStyle.EXCLUDED));
        SurfaceSettings settings = new SurfaceSettings(1, config, styles, true);
        WorldPlanStore.writeAtomic(path, settings);
        return settings;
    }
}
