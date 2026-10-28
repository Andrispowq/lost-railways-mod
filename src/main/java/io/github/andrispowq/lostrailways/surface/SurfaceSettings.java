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

public record SurfaceSettings(int version, SurfaceConfig config, Map<String, SurfaceStyle> styles) {
    public SurfaceSettings {
        if (version != 1) throw new IllegalArgumentException("Unsupported surface version: " + version);
        Objects.requireNonNull(config, "config");
        styles = Map.copyOf(styles);
        if (styles.isEmpty()) throw new IllegalArgumentException("Missing frozen surface biome styles");
    }

    public static SurfaceSettings loadOrCreate(Path path, ServerLevel level, SurfaceConfig config) throws IOException {
        if (Files.exists(path)) {
            if (Files.size(path) > 1_048_576) throw new IOException("Surface settings exceed 1 MiB");
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            Map<String, SurfaceStyle> styles = new HashMap<>();
            json.getAsJsonObject("styles").entrySet().forEach(entry -> styles.put(entry.getKey(), SurfaceStyle.valueOf(entry.getValue().getAsString())));
            return new SurfaceSettings(PlannerConfigJson.integer(json, "version"), SurfaceConfig.read(json.getAsJsonObject("config"), false), styles);
        }
        TagKey<Biome> taiga = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:taiga"));
        TagKey<Biome> forest = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:forest"));
        TagKey<Biome> plains = TagKey.create(Registries.BIOME, Identifier.parse("lost_railways:preferred"));
        Map<String, SurfaceStyle> styles = new HashMap<>();
        level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach(biome -> styles.put(
                biome.key().identifier().toString(), biome.is(taiga) ? SurfaceStyle.TAIGA : biome.is(forest) ? SurfaceStyle.FOREST : biome.is(plains) ? SurfaceStyle.PLAINS : SurfaceStyle.EXCLUDED));
        SurfaceSettings settings = new SurfaceSettings(1, config, styles);
        WorldPlanStore.writeAtomic(path, settings);
        return settings;
    }
}
