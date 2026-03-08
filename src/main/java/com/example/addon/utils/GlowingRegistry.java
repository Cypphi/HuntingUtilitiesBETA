package com.example.addon.utils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of entity IDs that should render with a glow outline.
 * Populated by Mobanom; read by EntityGlowingMixin.
 */
public final class GlowingRegistry {
    private GlowingRegistry() {}

    private static final Set<Integer> GLOWING_IDS = ConcurrentHashMap.newKeySet();

    public static void add(int entityId) {
        GLOWING_IDS.add(entityId);
    }

    public static void remove(int entityId) {
        GLOWING_IDS.remove(entityId);
    }

    public static boolean isGlowing(int entityId) {
        return GLOWING_IDS.contains(entityId);
    }

    public static void clear() {
        GLOWING_IDS.clear();
    }
}