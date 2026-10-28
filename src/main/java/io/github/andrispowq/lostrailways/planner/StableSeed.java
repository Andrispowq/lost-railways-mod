package io.github.andrispowq.lostrailways.planner;

final class StableSeed {
    private StableSeed() { }

    static long key(long seed, String text) {
        long hash = seed ^ 0x4c4f53545241494cL; // Fixed planner-v1 feature salt.
        for (int i = 0; i < text.length(); i++) {
            hash = mix(hash ^ text.charAt(i));
        }
        return hash;
    }

    static long mix(long value) {
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    static double fraction(long key) {
        return (key >>> 11) * 0x1.0p-53;
    }
}
