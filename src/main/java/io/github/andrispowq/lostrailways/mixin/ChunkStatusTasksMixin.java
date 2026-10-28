package io.github.andrispowq.lostrailways.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.andrispowq.lostrailways.LostRailwaysMod;
import io.github.andrispowq.lostrailways.surface.SurfaceGeneration;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {
    // A decoration feature is too late: neighboring vegetation could already have written into this chunk.
    // TERRAIN must complete here before any dependent FEATURES task starts, including those of neighbors.
    @ModifyReturnValue(method = "buildTerrain", at = @At("RETURN"))
    private static CompletableFuture<ChunkAccess> lostRailways$surface(CompletableFuture<ChunkAccess> terrain,
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        if (!LostRailwaysMod.config().generationEnabled() || !context.level().dimension().equals(Level.OVERWORLD)) return terrain;
        return terrain.thenApplyAsync(generated -> {
            SurfaceGeneration.generate(context, step, cache, generated);
            return generated;
        }, SurfaceGeneration.executor());
    }
}
