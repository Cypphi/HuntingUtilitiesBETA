package com.example.addon.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

public class Tunnelers extends Module {

    public enum TunnelType {
        TUNNEL_1x1,
        TUNNEL_1x2,
        TUNNEL_2x2,
        HOLE,
        ABNORMAL_TUNNEL,
        LADDER_SHAFT
    }

    // ------------------------------------------------------------------ //
    //  Setting Groups                                                      //
    // ------------------------------------------------------------------ //

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sg1x1      = settings.createGroup("1x1 Tunnels");
    private final SettingGroup sg1x2      = settings.createGroup("1x2 Tunnels");
    private final SettingGroup sg2x2      = settings.createGroup("2x2 Tunnels");
    private final SettingGroup sgHoles    = settings.createGroup("Holes");
    private final SettingGroup sgAbnormal = settings.createGroup("Abnormal Tunnels");
    private final SettingGroup sgLadder   = settings.createGroup("Ladder Shafts");
    private final SettingGroup sgRender   = settings.createGroup("Render");

    // ------------------------------------------------------------------ //
    //  General                                                             //
    // ------------------------------------------------------------------ //

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Scan range in chunks.")
        .defaultValue(8).min(1).sliderMax(32)
        .build());

    private final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder()
        .name("scan-delay")
        .description("Ticks between out-of-range pruning passes.")
        .defaultValue(40).min(10).sliderMax(200)
        .build());

    // ------------------------------------------------------------------ //
    //  1x1 Tunnels                                                         //
    // ------------------------------------------------------------------ //

    private final Setting<Boolean> find1x1 = sg1x1.add(new BoolSetting.Builder()
        .name("find-1x1-tunnels")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> color1x1 = sg1x1.add(new ColorSetting.Builder()
        .name("color-1x1")
        .defaultValue(new SettingColor(255, 255, 0, 75))
        .visible(find1x1::get)
        .build());

    // ------------------------------------------------------------------ //
    //  1x2 Tunnels                                                         //
    // ------------------------------------------------------------------ //

    private final Setting<Boolean> find1x2 = sg1x2.add(new BoolSetting.Builder()
        .name("find-1x2-tunnels")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> color1x2 = sg1x2.add(new ColorSetting.Builder()
        .name("color-1x2")
        .defaultValue(new SettingColor(255, 200, 0, 75))
        .visible(find1x2::get)
        .build());

    // ------------------------------------------------------------------ //
    //  2x2 Tunnels                                                         //
    // ------------------------------------------------------------------ //

    private final Setting<Boolean> find2x2 = sg2x2.add(new BoolSetting.Builder()
        .name("find-2x2-tunnels")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> color2x2 = sg2x2.add(new ColorSetting.Builder()
        .name("color-2x2")
        .defaultValue(new SettingColor(255, 165, 0, 75))
        .visible(find2x2::get)
        .build());

    // ------------------------------------------------------------------ //
    //  Holes                                                               //
    // ------------------------------------------------------------------ //

    private final Setting<Boolean> findHoles = sgHoles.add(new BoolSetting.Builder()
        .name("find-holes")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minHoleHeight = sgHoles.add(new IntSetting.Builder()
        .name("min-hole-height")
        .description("Minimum shaft depth to be detected as a hole.")
        .defaultValue(4).min(2).sliderMax(20)
        .visible(findHoles::get)
        .build());

    private final Setting<SettingColor> colorHoles = sgHoles.add(new ColorSetting.Builder()
        .name("color-holes")
        .defaultValue(new SettingColor(0, 255, 255, 75))
        .visible(findHoles::get)
        .build());

    // ------------------------------------------------------------------ //
    //  Abnormal Tunnels                                                    //
    // ------------------------------------------------------------------ //

    private final Setting<Boolean> findAbnormalTunnels = sgAbnormal.add(new BoolSetting.Builder()
        .name("find-abnormal-tunnels")
        .description("Finds 3x3, 4x4, and 5x5 tunnels.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> colorAbnormalTunnels = sgAbnormal.add(new ColorSetting.Builder()
        .name("color-abnormal")
        .defaultValue(new SettingColor(255, 0, 255, 75))
        .visible(findAbnormalTunnels::get)
        .build());

    // ------------------------------------------------------------------ //
    //  Ladder Shafts                                                       //
    // ------------------------------------------------------------------ //

    private final Setting<Boolean> findLadderShafts = sgLadder.add(new BoolSetting.Builder()
        .name("find-ladder-shafts")
        .description("Finds vertical 1x1 shafts with ladders on the wall.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minLadderHeight = sgLadder.add(new IntSetting.Builder()
        .name("min-ladder-height")
        .description("Minimum consecutive ladder blocks to count as a shaft.")
        .defaultValue(4).min(2).sliderMax(20)
        .visible(findLadderShafts::get)
        .build());

    private final Setting<SettingColor> colorLadderShafts = sgLadder.add(new ColorSetting.Builder()
        .name("color-ladder-shafts")
        .defaultValue(new SettingColor(0, 255, 0, 75))
        .visible(findLadderShafts::get)
        .build());

    // ------------------------------------------------------------------ //
    //  Render                                                              //
    // ------------------------------------------------------------------ //

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> fadeWithDistance = sgRender.add(new BoolSetting.Builder()
        .name("fade-with-distance")
        .description("Reduces opacity of highlights that are further away.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxRenderBoxes = sgRender.add(new IntSetting.Builder()
        .name("max-render-boxes")
        .description("Maximum merged boxes rendered per frame. Lower = better FPS in dense areas.")
        .defaultValue(2000).min(100).sliderMax(8000)
        .build());

    // ------------------------------------------------------------------ //
    //  State                                                               //
    // ------------------------------------------------------------------ //

    /** Live block→type map. Written by main thread (flush), read by merge task. */
    private final ConcurrentHashMap<BlockPos, TunnelType>    locations      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Set<BlockPos>> chunkIndex     = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ScanResult>          pendingResults = new ConcurrentLinkedQueue<>();

    /**
     * The finished, sorted list of merged boxes handed to the render thread.
     * Written only by the merge task via a single volatile store.
     * The render thread reads it with a single volatile load — zero locking.
     */
    private volatile List<MergedBox> renderSnapshot = Collections.emptyList();

    /**
     * Gate that ensures at most ONE merge task is queued at any time.
     * compareAndSet(false→true) to schedule; the task resets it to false when done.
     * This prevents a burst of flushes from stacking up O(N) merge jobs.
     */
    private final AtomicBoolean mergeScheduled = new AtomicBoolean(false);

    /**
     * Player position snapshot taken on the main thread when a merge is
     * scheduled, so the background merge task can compute distances without
     * touching any Minecraft state.
     */
    private volatile int snapPX, snapPY, snapPZ;

    // Chunk scanning state — main-thread only.
    private final Set<ChunkPos>          scannedChunks = new HashSet<>();
    private final LinkedHashSet<ChunkPos> snapshotQueue = new LinkedHashSet<>();
    private final Set<ChunkPos>          inFlight       = ConcurrentHashMap.newKeySet();

    /**
     * Single-thread executor shared by chunk scanners (3 threads) AND the
     * merge task (1 thread). The merge task is cheap compared to scanning so
     * sharing is fine; it prevents needing a second pool.
     */
    private ExecutorService executor;

    private String lastDimension           = "";
    private int    dimensionChangeCooldown = 0;
    private int    pruneTimer              = 0;

    private static final int  MAX_QUEUE_PER_TICK    = 32;
    private static final int  MAX_BATCHES_PER_FLUSH = 4;
    private static final int  MAX_IN_FLIGHT         = 6;
    private static final int  DRAIN_PER_TICK        = 4;
    private static final long TIME_BUDGET_NS        = 500_000L;

    // ------------------------------------------------------------------ //
    //  Lifecycle                                                           //
    // ------------------------------------------------------------------ //

    public Tunnelers() {
        super(HuntingUtilities.CATEGORY, "tunnelers", "Highlights player-made tunnels and holes.");
    }

    @Override
    public void onActivate() {
        locations.clear();
        chunkIndex.clear();
        pendingResults.clear();
        scannedChunks.clear();
        snapshotQueue.clear();
        inFlight.clear();
        renderSnapshot = Collections.emptyList();
        mergeScheduled.set(false);
        pruneTimer = 0;
        dimensionChangeCooldown = 0;
        if (mc.world != null) lastDimension = mc.world.getRegistryKey().getValue().toString();
        // 3 scanner threads + headroom for the occasional merge task.
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Tunnelers-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    @Override
    public void onDeactivate() {
        if (executor != null) { executor.shutdownNow(); executor = null; }
        locations.clear();
        chunkIndex.clear();
        pendingResults.clear();
        scannedChunks.clear();
        snapshotQueue.clear();
        inFlight.clear();
        renderSnapshot = Collections.emptyList();
        mergeScheduled.set(false);
    }

    // ------------------------------------------------------------------ //
    //  Tick — main thread work is now O(batches) only, never O(blocks)    //
    // ------------------------------------------------------------------ //

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (dimensionChangeCooldown > 0) { dimensionChangeCooldown--; return; }

        String currDim = mc.world.getRegistryKey().getValue().toString();
        if (!currDim.equals(lastDimension)) {
            lastDimension = currDim;
            dimensionChangeCooldown = 40;
            locations.clear(); chunkIndex.clear(); scannedChunks.clear();
            inFlight.clear(); snapshotQueue.clear();
            renderSnapshot = Collections.emptyList();
            mergeScheduled.set(false);
            return;
        }

        // 1. Merge completed scan results into the live map — O(batches × entries).
        boolean newData = flushPendingResults();

        // 2. Schedule a background merge if the map changed.
        //    The main thread only snapshots player position and does a CAS —
        //    the expensive O(N log N) merge runs entirely on the worker pool.
        if (newData) scheduleMerge();

        // 3. Periodically prune out-of-range chunks.
        if (++pruneTimer >= scanDelay.get()) {
            pruneTimer = 0;
            if (pruneOutOfRange()) scheduleMerge(); // re-merge after eviction
        }

        // 4. Queue and dispatch new chunk scans.
        int playerCX = mc.player.getBlockPos().getX() >> 4;
        int playerCZ = mc.player.getBlockPos().getZ() >> 4;
        enqueueNewChunks(playerCX, playerCZ);
        drainSnapshotQueue();
    }

    // ------------------------------------------------------------------ //
    //  Merge scheduling — main thread sets up, worker does the work       //
    // ------------------------------------------------------------------ //

    /**
     * Schedules one merge task if none is already queued.
     * The AtomicBoolean gate means that no matter how many flushes happen
     * in a single tick (up to MAX_BATCHES_PER_FLUSH) only ONE merge is ever
     * queued. The task resets the gate when it finishes so the next dirty
     * tick can queue another.
     *
     * Main-thread cost: one CAS + two int volatile writes. Essentially free.
     */
    private void scheduleMerge() {
        if (!mergeScheduled.compareAndSet(false, true)) return; // already queued

        // Snapshot player position now, on the main thread, so the worker
        // task never needs to touch mc.player.
        snapPX = mc.player.getBlockPos().getX();
        snapPY = mc.player.getBlockPos().getY();
        snapPZ = mc.player.getBlockPos().getZ();

        // Take an immutable snapshot of the current locations map for the
        // worker. ConcurrentHashMap.entrySet() iteration is weakly consistent
        // and safe to do from another thread, but we want a stable snapshot
        // so the merge result is coherent. A single HashMap copy here is
        // O(N) on the main thread but it's just pointer copies — no GC-heavy
        // object creation — and it's bounded by the map size.
        //
        // We pass the snapshot to the lambda to avoid capturing the live map.
        final Map<BlockPos, TunnelType> locSnapshot = new HashMap<>(locations);
        final int px = snapPX, py = snapPY, pz = snapPZ;
        final double maxDistSq = (double)(range.get() * 16) * (range.get() * 16);

        executor.submit(() -> {
            try {
                List<MergedBox> merged = buildMergedBoxes(locSnapshot, px, py, pz, maxDistSq);
                renderSnapshot = merged; // single volatile write — render thread sees it immediately
            } finally {
                mergeScheduled.set(false); // gate open for the next dirty tick
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Greedy Mesh — runs entirely on the worker thread, never main thread //
    // ------------------------------------------------------------------ //

    /**
     * Collapses adjacent same-type blocks into the minimum number of AABBs
     * using greedy expansion: X → Z → Y.
     *
     * Key correctness fix vs previous version:
     *   The outerY loop previously used a labelled break inside a nested
     *   for-for, which means any single missing block in the XZ-slab would
     *   break out of the OUTER loop — correct — but only after already having
     *   incremented y2. This caused boxes to be one block too tall whenever
     *   the slab extension failed after the first step.
     *   Fixed by checking the full slab BEFORE incrementing y2.
     *
     * @param locs      immutable snapshot of the live locations map
     * @param px/py/pz  player position at scheduling time
     * @param maxDistSq squared render range, used for distSq clamping
     */
    private static List<MergedBox> buildMergedBoxes(
            Map<BlockPos, TunnelType> locs,
            int px, int py, int pz,
            double maxDistSq
    ) {
        if (locs.isEmpty()) return Collections.emptyList();

        // Group positions by type. Merging must stay within the same type.
        EnumMap<TunnelType, Set<Long>>   remaining   = new EnumMap<>(TunnelType.class);
        EnumMap<TunnelType, List<int[]>> coordsByType = new EnumMap<>(TunnelType.class);
        for (TunnelType t : TunnelType.values()) {
            remaining.put(t, new HashSet<>());
            coordsByType.put(t, new ArrayList<>());
        }

        for (Map.Entry<BlockPos, TunnelType> e : locs.entrySet()) {
            BlockPos p = e.getKey();
            TunnelType t = e.getValue();
            remaining.get(t).add(pack(p.getX(), p.getY(), p.getZ()));
            coordsByType.get(t).add(new int[]{ p.getX(), p.getY(), p.getZ() });
        }

        List<MergedBox> boxes = new ArrayList<>();

        for (TunnelType type : TunnelType.values()) {
            Set<Long>   rem    = remaining.get(type);
            List<int[]> coords = coordsByType.get(type);
            if (rem.isEmpty()) continue;

            for (int[] origin : coords) {
                int ox = origin[0], oy = origin[1], oz = origin[2];
                if (!rem.contains(pack(ox, oy, oz))) continue; // already consumed

                // --- Extend along +X ---
                int x2 = ox;
                while (rem.contains(pack(x2 + 1, oy, oz))) x2++;

                // --- Extend along +Z (entire X-row must be present) ---
                int z2 = oz;
                while (canExtendZ(rem, ox, x2, oy, z2 + 1)) z2++;

                // --- Extend along +Y (entire XZ-slab must be present) ---
                // FIX: test BEFORE incrementing y2, not after, to avoid
                // the off-by-one that produced boxes 1 block too tall.
                int y2 = oy;
                while (canExtendY(rem, ox, x2, y2 + 1, oz, z2)) y2++;

                // Consume all blocks in this merged box.
                for (int x = ox; x <= x2; x++)
                    for (int y = oy; y <= y2; y++)
                        for (int z = oz; z <= z2; z++)
                            rem.remove(pack(x, y, z));

                // Squared distance from box centre to player.
                double cx = (ox + x2) * 0.5 + 0.5;
                double cy = (oy + y2) * 0.5 + 0.5;
                double cz = (oz + z2) * 0.5 + 0.5;
                double ddx = cx - px, ddy = cy - py, ddz = cz - pz;
                double distSq = Math.min(ddx * ddx + ddy * ddy + ddz * ddz, maxDistSq);

                boxes.add(new MergedBox(ox, oy, oz, x2 + 1, y2 + 1, z2 + 1, type, distSq));
            }
        }

        // Nearest-first so the render cap drops distant geometry first.
        boxes.sort(Comparator.comparingDouble(b -> b.distSq));
        return boxes;
    }

    /** Returns true if every block in the row (ox..x2, y, z) is present in rem. */
    private static boolean canExtendZ(Set<Long> rem, int ox, int x2, int y, int z) {
        for (int x = ox; x <= x2; x++)
            if (!rem.contains(pack(x, y, z))) return false;
        return true;
    }

    /** Returns true if every block in the slab (ox..x2, y, oz..z2) is present in rem. */
    private static boolean canExtendY(Set<Long> rem, int ox, int x2, int y, int oz, int z2) {
        for (int x = ox; x <= x2; x++)
            for (int z = oz; z <= z2; z++)
                if (!rem.contains(pack(x, y, z))) return false;
        return true;
    }

    // ------------------------------------------------------------------ //
    //  Coordinate packing                                                  //
    // ------------------------------------------------------------------ //

    private static long pack(int x, int y, int z) {
        // x, z: ±33 554 432 blocks (26-bit unsigned after offset)
        // y:    ±2 048 blocks      (12-bit unsigned after offset)
        return ((long)(x + 33_554_432) << 38) | ((long)(y + 2_048) << 26) | (z + 33_554_432);
    }

    // ------------------------------------------------------------------ //
    //  Pruning                                                             //
    // ------------------------------------------------------------------ //

    /** @return true if any chunks were evicted (caller should re-merge). */
    private boolean pruneOutOfRange() {
        if (mc.player == null) return false;
        int centerCX = mc.player.getBlockPos().getX() >> 4;
        int centerCZ = mc.player.getBlockPos().getZ() >> 4;
        int rSq      = range.get() * range.get();
        boolean evicted = false;

        Iterator<ChunkPos> it = scannedChunks.iterator();
        while (it.hasNext()) {
            ChunkPos cp = it.next();
            int dx = cp.x - centerCX, dz = cp.z - centerCZ;
            if (dx * dx + dz * dz > rSq) {
                evictChunk(cp);
                it.remove();
                evicted = true;
            }
        }
        return evicted;
    }

    // ------------------------------------------------------------------ //
    //  Queue building (main thread, O(range²) but cheap coord-only work)  //
    // ------------------------------------------------------------------ //

    private void enqueueNewChunks(int centerCX, int centerCZ) {
        int r = range.get(), rSq = r * r, added = 0;
        long t0 = System.nanoTime();

        outer:
        for (int d = 0; d <= r; d++) {
            for (int x = -d; x <= d; x++) {
                if (tryEnqueue(centerCX + x, centerCZ - d, rSq, centerCX, centerCZ)) added++;
                if (added >= MAX_QUEUE_PER_TICK || System.nanoTime() - t0 > TIME_BUDGET_NS) break outer;
                if (d != 0) {
                    if (tryEnqueue(centerCX + x, centerCZ + d, rSq, centerCX, centerCZ)) added++;
                    if (added >= MAX_QUEUE_PER_TICK || System.nanoTime() - t0 > TIME_BUDGET_NS) break outer;
                }
            }
            for (int z = -d + 1; z < d; z++) {
                if (tryEnqueue(centerCX - d, centerCZ + z, rSq, centerCX, centerCZ)) added++;
                if (added >= MAX_QUEUE_PER_TICK || System.nanoTime() - t0 > TIME_BUDGET_NS) break outer;
                if (d != 0) {
                    if (tryEnqueue(centerCX + d, centerCZ + z, rSq, centerCX, centerCZ)) added++;
                    if (added >= MAX_QUEUE_PER_TICK || System.nanoTime() - t0 > TIME_BUDGET_NS) break outer;
                }
            }
        }
    }

    private boolean tryEnqueue(int cx, int cz, int rSq, int centerCX, int centerCZ) {
        int dx = cx - centerCX, dz = cz - centerCZ;
        if (dx * dx + dz * dz > rSq) return false;
        ChunkPos cp = new ChunkPos(cx, cz);
        if (scannedChunks.contains(cp) || inFlight.contains(cp)) return false;
        if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) return false;
        return snapshotQueue.add(cp);
    }

    // ------------------------------------------------------------------ //
    //  Scan dispatch                                                       //
    // ------------------------------------------------------------------ //

    private void drainSnapshotQueue() {
        for (int i = 0; i < DRAIN_PER_TICK; i++) {
            if (inFlight.size() >= MAX_IN_FLIGHT || snapshotQueue.isEmpty()) break;

            Iterator<ChunkPos> it = snapshotQueue.iterator();
            ChunkPos cp = it.next(); it.remove();

            if (!mc.world.getChunkManager().isChunkLoaded(cp.x, cp.z)) continue;
            WorldChunk chunk = mc.world.getChunk(cp.x, cp.z);
            if (chunk == null) continue;

            inFlight.add(cp);
            ScanConfig config = new ScanConfig(
                find1x1.get(), find1x2.get(), find2x2.get(),
                findHoles.get(), findAbnormalTunnels.get(), findLadderShafts.get(),
                minHoleHeight.get(), minLadderHeight.get(),
                mc.world.getBottomY(), mc.world.getBottomY() + mc.world.getHeight()
            );
            int bottomCoord = config.minY >> 4;

            executor.submit(() -> {
                try {
                    BlockState[][] snapshot = snapshotChunk(chunk);
                    Map<BlockPos, TunnelType> results = scanSnapshot(cp, snapshot, bottomCoord, config);
                    pendingResults.add(new ScanResult(cp, results));
                } finally {
                    inFlight.remove(cp);
                }
            });
        }
    }

    // ------------------------------------------------------------------ //
    //  Flush — O(batches × entries), main thread                          //
    // ------------------------------------------------------------------ //

    /** @return true if any results were merged. */
    private boolean flushPendingResults() {
        ScanResult batch;
        int n = 0;
        while (n < MAX_BATCHES_PER_FLUSH && (batch = pendingResults.poll()) != null) {
            scannedChunks.add(batch.chunkPos);
            Set<BlockPos> index = chunkIndex.computeIfAbsent(
                batch.chunkPos, k -> ConcurrentHashMap.newKeySet());
            for (Map.Entry<BlockPos, TunnelType> e : batch.results.entrySet()) {
                locations.put(e.getKey(), e.getValue());
                index.add(e.getKey());
            }
            n++;
        }
        return n > 0;
    }

    // ------------------------------------------------------------------ //
    //  Chunk snapshot (worker thread)                                      //
    // ------------------------------------------------------------------ //

    private BlockState[][] snapshotChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        BlockState[][] out = new BlockState[sections.length][];
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) continue;
            BlockState[] data = new BlockState[16 * 16 * 16];
            for (int lx = 0; lx < 16; lx++)
                for (int ly = 0; ly < 16; ly++)
                    for (int lz = 0; lz < 16; lz++)
                        data[lx + lz * 16 + ly * 256] = sec.getBlockState(lx, ly, lz);
            out[si] = data;
        }
        return out;
    }

    // ------------------------------------------------------------------ //
    //  Off-thread block scan                                               //
    // ------------------------------------------------------------------ //

    private Map<BlockPos, TunnelType> scanSnapshot(
            ChunkPos cp, BlockState[][] snapshot, int bottomCoord, ScanConfig config) {
        Map<BlockPos, TunnelType> results = new HashMap<>();
        int baseX = cp.x << 4, baseZ = cp.z << 4;
        ScanContext ctx = new ScanContext(snapshot, bottomCoord, config.minY, config.maxY, baseX, baseZ);

        for (int si = 0; si < snapshot.length; si++) {
            if (snapshot[si] == null) continue;
            int sMinY = (bottomCoord + si) << 4, sMaxY = sMinY + 16;
            if (sMaxY <= config.minY || sMinY >= config.maxY) continue;
            for (int lx = 0; lx < 16; lx++)
                for (int ly = 0; ly < 16; ly++) {
                    int wy = sMinY + ly;
                    if (wy < config.minY || wy >= config.maxY) continue;
                    for (int lz = 0; lz < 16; lz++)
                        classifyBlock(baseX + lx, wy, baseZ + lz, ctx, config, results);
                }
        }
        return results;
    }

    private void classifyBlock(int wx, int wy, int wz,
            ScanContext ctx, ScanConfig config, Map<BlockPos, TunnelType> results) {

        if (config.doHoles && isHole(wx, wy, wz, ctx, config.holeDepth)) {
            for (int i = 0; i < config.holeDepth; i++)
                results.put(new BlockPos(wx, wy - i, wz), TunnelType.HOLE);
            return;
        }
        if (config.doLadder && isLadderShaft(wx, wy, wz, ctx, config.ladderMin))
            for (int i = 0; i < config.ladderMin; i++)
                results.put(new BlockPos(wx, wy + i, wz), TunnelType.LADDER_SHAFT);

        if (config.do1x1 && is1x1Tunnel(wx, wy, wz, ctx))
            results.put(new BlockPos(wx, wy + 1, wz), TunnelType.TUNNEL_1x1);

        if (config.do1x2 && is1x2Tunnel(wx, wy, wz, ctx)) {
            results.put(new BlockPos(wx, wy + 1, wz), TunnelType.TUNNEL_1x2);
            results.put(new BlockPos(wx, wy + 2, wz), TunnelType.TUNNEL_1x2);
        }
        if (config.doAbnormal) {
            int sz = getAbnormalTunnelSize(wx, wy, wz, ctx);
            if (sz > 0)
                for (int dx = 0; dx < sz; dx++) for (int dy = 1; dy <= sz; dy++) for (int dz = 0; dz < sz; dz++)
                    results.put(new BlockPos(wx + dx, wy + dy, wz + dz), TunnelType.ABNORMAL_TUNNEL);
        }
        if (config.do2x2 && is2x2Tunnel(wx, wy, wz, ctx))
            for (int dx = 0; dx < 2; dx++) for (int dy = 1; dy <= 2; dy++) for (int dz = 0; dz < 2; dz++)
                results.put(new BlockPos(wx + dx, wy + dy, wz + dz), TunnelType.TUNNEL_2x2);
    }

    // ------------------------------------------------------------------ //
    //  Block tests                                                         //
    // ------------------------------------------------------------------ //

    private boolean isHole(int x, int y, int z, ScanContext ctx, int depth) {
        if (!ctx.isAir(x, y, z)) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0) && !ctx.isSolid(x + dx, y, z + dz)) return false;
        for (int i = 1; i < depth; i++) {
            int sy = y - i;
            if (!ctx.isAir(x,sy,z) || !ctx.isSolid(x-1,sy,z) || !ctx.isSolid(x+1,sy,z)
                    || !ctx.isSolid(x,sy,z-1) || !ctx.isSolid(x,sy,z+1)) return false;
        }
        return true;
    }

    private boolean is1x1Tunnel(int x, int y, int z, ScanContext ctx) {
        if (!ctx.isSolid(x,y,z) || !ctx.isAir(x,y+1,z) || !ctx.isSolid(x,y+2,z)) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0) && !ctx.isSolid(x+dx,y+1,z+dz)) return false;
        return true;
    }

    private boolean is1x2Tunnel(int x, int y, int z, ScanContext ctx) {
        if (!is1x2Slice(x, y, z, ctx)) return false;
        if (isMineshaftBlock(ctx.get(x,y,z)) || isMineshaftBlock(ctx.get(x,y+3,z))) return false;
        boolean isZ = true;
        for (int dy = 1; dy <= 2; dy++)
            if (!ctx.isSolid(x-1,y+dy,z) || !ctx.isSolid(x+1,y+dy,z)) { isZ = false; break; }
        if (isZ) return is1x2Slice(x, y, z-1, ctx) && is1x2Slice(x, y, z+1, ctx);
        for (int dy = 1; dy <= 2; dy++)
            if (!ctx.isSolid(x,y+dy,z-1) || !ctx.isSolid(x,y+dy,z+1)) return false;
        return is1x2Slice(x-1, y, z, ctx) && is1x2Slice(x+1, y, z, ctx);
    }

    private boolean is1x2Slice(int x, int y, int z, ScanContext ctx) {
        return ctx.isSolid(x,y,z) && ctx.isAir(x,y+1,z) && ctx.isAir(x,y+2,z) && ctx.isSolid(x,y+3,z);
    }

    private boolean is2x2Tunnel(int x, int y, int z, ScanContext ctx) {
        for (int fx = 0; fx < 2; fx++) for (int fz = 0; fz < 2; fz++)
            if (!ctx.isSolid(x+fx,y,z+fz) || !ctx.isSolid(x+fx,y+3,z+fz)) return false;
        for (int fx = 0; fx < 2; fx++) for (int fy = 1; fy <= 2; fy++) for (int fz = 0; fz < 2; fz++)
            if (!ctx.isAir(x+fx,y+fy,z+fz)) return false;
        for (int fx = 0; fx < 2; fx++) for (int fy = 1; fy <= 2; fy++)
            if (!ctx.isSolid(x+fx,y+fy,z-1) || !ctx.isSolid(x+fx,y+fy,z+2)) return false;
        for (int fz = 0; fz < 2; fz++) for (int fy = 1; fy <= 2; fy++)
            if (!ctx.isSolid(x-1,y+fy,z+fz) || !ctx.isSolid(x+2,y+fy,z+fz)) return false;
        return true;
    }

    private int getAbnormalTunnelSize(int x, int y, int z, ScanContext ctx) {
        if (isTunnelOfSize(x,y,z,ctx,5)) return 5;
        if (isTunnelOfSize(x,y,z,ctx,4)) return 4;
        if (isTunnelOfSize(x,y,z,ctx,3)) return 3;
        return 0;
    }

    private boolean isTunnelOfSize(int x, int y, int z, ScanContext ctx, int s) {
        for (int fx = 0; fx < s; fx++) for (int fz = 0; fz < s; fz++)
            if (!ctx.isSolid(x+fx,y,z+fz) || !ctx.isSolid(x+fx,y+s+1,z+fz)) return false;
        for (int fx = 0; fx < s; fx++) for (int fy = 1; fy <= s; fy++) for (int fz = 0; fz < s; fz++)
            if (!ctx.isAir(x+fx,y+fy,z+fz)) return false;
        for (int fx = 0; fx < s; fx++) for (int fy = 1; fy <= s; fy++)
            if (!ctx.isSolid(x+fx,y+fy,z-1) || !ctx.isSolid(x+fx,y+fy,z+s)) return false;
        for (int fz = 0; fz < s; fz++) for (int fy = 1; fy <= s; fy++)
            if (!ctx.isSolid(x-1,y+fy,z+fz) || !ctx.isSolid(x+s,y+fy,z+fz)) return false;
        return true;
    }

    private boolean isMineshaftBlock(BlockState s) {
        if (s == null) return false;
        Block b = s.getBlock();
        return b == Blocks.OAK_PLANKS || b == Blocks.DARK_OAK_PLANKS;
    }

    private boolean isLadderShaft(int x, int y, int z, ScanContext ctx, int minH) {
        if (!ctx.isSolid(x,y-1,z)) return false;
        for (int i = 0; i < minH; i++) {
            int cy = y + i;
            if (!ctx.isAir(x,cy,z)) return false;
            if (!ctx.isLadder(x-1,cy,z) && !ctx.isLadder(x+1,cy,z)
                    && !ctx.isLadder(x,cy,z-1) && !ctx.isLadder(x,cy,z+1)) return false;
            int walls = 0;
            if (ctx.isSolid(x-1,cy,z)) walls++;
            if (ctx.isSolid(x+1,cy,z)) walls++;
            if (ctx.isSolid(x,cy,z-1)) walls++;
            if (ctx.isSolid(x,cy,z+1)) walls++;
            if (walls < 3) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ //
    //  ScanContext                                                         //
    // ------------------------------------------------------------------ //

    private static final class ScanContext {
        private final BlockState[][] snapshot;
        private final int bottomCoord, minY, maxY, baseX, baseZ;

        ScanContext(BlockState[][] s, int bc, int minY, int maxY, int bx, int bz) {
            snapshot = s; bottomCoord = bc;
            this.minY = minY; this.maxY = maxY; baseX = bx; baseZ = bz;
        }

        BlockState get(int x, int y, int z) {
            if (y < minY || y >= maxY) return null;
            int lx = x - baseX, lz = z - baseZ;
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return null;
            int si = (y >> 4) - bottomCoord;
            if (si < 0 || si >= snapshot.length) return null;
            BlockState[] sec = snapshot[si];
            return sec == null ? null : sec[lx + lz * 16 + (y & 15) * 256];
        }

        boolean isSolid (int x, int y, int z) { BlockState s = get(x,y,z); return s != null && s.isOpaque(); }
        boolean isAir   (int x, int y, int z) { BlockState s = get(x,y,z); return s == null || s.isAir(); }
        boolean isLadder(int x, int y, int z) { BlockState s = get(x,y,z); return s != null && s.isOf(Blocks.LADDER); }
    }

    // ------------------------------------------------------------------ //
    //  Eviction                                                            //
    // ------------------------------------------------------------------ //

    private void evictChunk(ChunkPos cp) {
        Set<BlockPos> idx = chunkIndex.remove(cp);
        if (idx != null) idx.forEach(locations::remove);
    }

    // ------------------------------------------------------------------ //
    //  Render — main thread cost is now: one volatile read + N box calls  //
    // ------------------------------------------------------------------ //

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        List<MergedBox> snapshot = renderSnapshot; // single volatile read
        if (snapshot.isEmpty()) return;

        boolean   doFade    = fadeWithDistance.get();
        double    maxDistSq = (double)(range.get() * 16) * (range.get() * 16);
        int       limit     = maxRenderBoxes.get();
        ShapeMode sm        = shapeMode.get();

        SettingColor reusable = new SettingColor(0, 0, 0, 0);

        int drawn = 0;
        for (MergedBox box : snapshot) {
            if (drawn >= limit) break;
            SettingColor base = getColor(box.type);
            if (base == null) continue;

            SettingColor c;
            if (doFade) {
                float frac   = (float) Math.max(0.0, 1.0 - box.distSq / maxDistSq);
                int   fadedA = Math.max(8, (int)(base.a * frac));
                reusable.r = base.r; reusable.g = base.g; reusable.b = base.b; reusable.a = fadedA;
                c = reusable;
            } else {
                c = base;
            }

            event.renderer.box(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2, c, c, sm, 0);
            drawn++;
        }
    }

    private SettingColor getColor(TunnelType type) {
        if (type == null) return null;
        return switch (type) {
            case TUNNEL_1x1      -> find1x1.get()            ? color1x1.get()             : null;
            case TUNNEL_1x2      -> find1x2.get()            ? color1x2.get()             : null;
            case TUNNEL_2x2      -> find2x2.get()            ? color2x2.get()             : null;
            case HOLE            -> findHoles.get()           ? colorHoles.get()           : null;
            case ABNORMAL_TUNNEL -> findAbnormalTunnels.get() ? colorAbnormalTunnels.get() : null;
            case LADDER_SHAFT    -> findLadderShafts.get()    ? colorLadderShafts.get()    : null;
        };
    }

    // ------------------------------------------------------------------ //
    //  Data records                                                        //
    // ------------------------------------------------------------------ //

    private record ScanConfig(
        boolean do1x1, boolean do1x2, boolean do2x2, boolean doHoles, boolean doAbnormal, boolean doLadder,
        int holeDepth, int ladderMin, int minY, int maxY
    ) {}

    private static final class ScanResult {
        final ChunkPos chunkPos;
        final Map<BlockPos, TunnelType> results;
        ScanResult(ChunkPos cp, Map<BlockPos, TunnelType> r) { chunkPos = cp; results = r; }
    }

    /**
     * An AABB covering one or more adjacent same-type blocks after greedy merging.
     * x1/y1/z1 = inclusive min corner.
     * x2/y2/z2 = exclusive max corner (x2 = lastX + 1, etc.).
     * distSq   = squared distance from box centre to player, clamped to maxDistSq.
     */
    private static final class MergedBox {
        final int x1, y1, z1, x2, y2, z2;
        final TunnelType type;
        final double distSq;
        MergedBox(int x1, int y1, int z1, int x2, int y2, int z2, TunnelType t, double d) {
            this.x1=x1; this.y1=y1; this.z1=z1; this.x2=x2; this.y2=y2; this.z2=z2; type=t; distSq=d;
        }
    }
}