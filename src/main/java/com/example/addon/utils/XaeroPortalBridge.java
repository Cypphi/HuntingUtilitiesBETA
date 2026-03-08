package com.example.addon.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Soft-dependency bridge between PortalTracker and Xaero's mods.
 *
 * Does NOT import any Xaero classes — safe to load whether or not Xaero
 * is installed. Uses polling via PortalTracker's existing tick event rather
 * than Fabric API's ClientChunkEvents, so no extra dependencies are needed.
 *
 * Each tick, watched positions whose chunk is now loaded are checked for
 * the portal block. If gone → callback fires → PortalTracker handles removal.
 *
 * Usage:
 *   XaeroPortalBridge.tick();                          // call every tick from PortalTracker
 *   XaeroPortalBridge.watchPosition(pos, callback);    // register a portal to watch
 *   XaeroPortalBridge.unwatch(pos);                    // remove a single position
 *   XaeroPortalBridge.clearWatched();                  // call on dimension change / disable
 */
public class XaeroPortalBridge {

    public static final boolean WORLD_MAP_PRESENT =
        FabricLoader.getInstance().isModLoaded("xaeroworldmap");
    public static final boolean MINIMAP_PRESENT =
        FabricLoader.getInstance().isModLoaded("xaerominimapfair")
        || FabricLoader.getInstance().isModLoaded("xaerominimap");
    public static final boolean XAERO_PRESENT = WORLD_MAP_PRESENT || MINIMAP_PRESENT;

    /**
     * Positions we are watching.
     * Key   = BlockPos of the portal block.
     * Value = callback: (pos, stillExists) called once when the chunk is loaded.
     *         stillExists=true  = portal block is present.
     *         stillExists=false = portal block is gone, trigger removal logic.
     */
    private static final Map<BlockPos, BiConsumer<BlockPos, Boolean>> watched
        = new ConcurrentHashMap<>();

    /** Positions already resolved this session — never re-fired. */
    private static final Set<BlockPos> checked = ConcurrentHashMap.newKeySet();

    // ─────────────────────────────────────────────────────────────────────────
    // API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call this every tick from PortalTracker's onTick().
     * Checks all watched positions whose chunk is currently loaded.
     */
    public static void tick() {
        if (watched.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        watched.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();

            // Skip if already resolved.
            if (checked.contains(pos)) return true;

            // Only check if the chunk is loaded.
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) return false;

            // Chunk is loaded — check for the portal block.
            checked.add(pos);

            boolean present = isPortalBlock(mc, pos);

            // Also check a few blocks above in case Y was stored at portal base.
            if (!present) {
                for (int dy = 1; dy <= 3; dy++) {
                    if (isPortalBlock(mc, pos.up(dy))) {
                        present = true;
                        break;
                    }
                }
            }

            entry.getValue().accept(pos, present);
            return true; // remove from watched map
        });
    }

    /**
     * Register a portal position to watch. The callback fires once when the
     * chunk containing pos is loaded and the block state is readable.
     */
    public static void watchPosition(BlockPos pos, BiConsumer<BlockPos, Boolean> onResolved) {
        if (checked.contains(pos)) return;
        watched.put(pos.toImmutable(), onResolved);
    }

    /** Remove a single watched position without firing its callback. */
    public static void unwatch(BlockPos pos) {
        watched.remove(pos);
        checked.add(pos); // prevent re-registration
    }

    /**
     * Clear all watched positions and the checked set.
     * Call on dimension change or module deactivate.
     */
    public static void clearWatched() {
        watched.clear();
        checked.clear();
    }

    /** Returns how many positions are currently being watched. */
    public static int watchedCount() {
        return watched.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isPortalBlock(MinecraftClient mc, BlockPos pos) {
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.NETHER_PORTAL
            || block == Blocks.END_PORTAL
            || block == Blocks.END_GATEWAY;
    }
}