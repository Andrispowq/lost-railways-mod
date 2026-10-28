package io.github.andrispowq.lostrailways.minecart;

import java.util.function.BiPredicate;

public final class ChunkSafety {
    private ChunkSafety() {
    }

    /** A bounded collision/rail-query halo; the predicate must never load or generate chunks. */
    public static boolean hasMovementArea(int blockX, int blockZ, double maximumSpeed,
                                          BiPredicate<Integer, Integer> isLoaded) {
        int radius = (int) Math.ceil(maximumSpeed / 20.0) + 1;
        for (int x = Math.floorDiv(blockX - radius, 16); x <= Math.floorDiv(blockX + radius, 16); x++) {
            for (int z = Math.floorDiv(blockZ - radius, 16); z <= Math.floorDiv(blockZ + radius, 16); z++) {
                if (!isLoaded.test(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
