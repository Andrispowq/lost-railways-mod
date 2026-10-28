package io.github.andrispowq.lostrailways.minecart;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSafetyTest {
    @Test
    void requiresTheNextChunkBeforeCrossingPositiveAndNegativeBoundaries() {
        assertFalse(ChunkSafety.hasMovementArea(14, 8, 35, (x, z) -> x == 0 && z == 0));
        assertFalse(ChunkSafety.hasMovementArea(-2, 8, 35, (x, z) -> x == -1 && z == 0));
        assertTrue(ChunkSafety.hasMovementArea(8, 8, 35, (x, z) -> x == 0 && z == 0));
    }

    @Test
    void checksOnlyTheBoundedHaloIncludingDiagonalCollisions() {
        Set<String> requested = new HashSet<>();
        assertTrue(ChunkSafety.hasMovementArea(-1, -1, 60, (x, z) -> {
            requested.add(x + "," + z);
            return true;
        }));
        assertEquals(Set.of("-1,-1", "-1,0", "0,-1", "0,0"), requested);
        assertFalse(ChunkSafety.hasMovementArea(15, 15, 35, (x, z) -> x != 1 || z != 1));
    }
}
