package com.example.addon.modules;

import com.example.addon.HuntingUtilities;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
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

import java.util.*;
import java.util.concurrent.*;

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

    // ------------------------------------------------------------------ //
    //  State                                                               //
    // ------------------------------------------------------------------ //

    private final ConcurrentHashMap<BlockPos, TunnelType>    locations  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Set<BlockPos>> chunkIndex = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ScanResult>          pendingResults = new ConcurrentLinkedQueue<>();

    /**
     * FIX #1 — render snapshot.
     * The render thread iterates this plain ArrayList instead of the live
     * ConcurrentHashMap, eliminating per-frame CHM iteration overhead and
     * the associated lock contention. Rebuilt on the main thread only when
     * new results are flushed (i.e. at most once per tick, not once per frame).
     * Declared volatile so the render thread always sees the latest reference.
     */
    private volatile List<RenderEntry> renderSnapshot = new ArrayList<>();

    /** Whether the snapshot needs to be rebuilt after a flush. */
    private boolean renderSnapshotDirty = false;

    /** Chunks whose results are already live in {@code locations}. Main-thread only. */
    private final Set<ChunkPos> scannedChunks = new HashSet<>();

    /**
     * Chunks queued for snapshotting. LinkedHashSet gives O(1) contains()
     * AND preserves insertion order so we drain in spiral order.
     * Main-thread only.
     */
    private final LinkedHashSet<ChunkPos> snapshotQueue = new LinkedHashSet<>();

    /** Chunks currently being scanned on the background thread. */
    private final Set<ChunkPos> inFlight = ConcurrentHashMap.newKeySet();

    private ExecutorService executor;

    private String lastDimension           = "";
    private int    dimensionChangeCooldown = 0;
    private int    pruneTimer              = 0;

    // How many chunks we *queue* per tick (cheap – just adds to a set).
    private static final int MAX_QUEUE_PER_TICK = 32;
    /**
     * FIX #2 — count *batches* consumed per flush, not individual entries.
     * The old MAX_MERGE_PER_TICK=200 counted entries, but each batch can carry
     * hundreds of entries, so a burst of completed scans could insert thousands
     * of map entries in one tick. Limiting to 4 batches per tick spreads the
     * work evenly and bounds the worst-case main-thread time per flush call.
     */
    private static final int MAX_BATCHES_PER_FLUSH = 4;
    // Max concurrent background scan jobs.
    private static final int MAX_IN_FLIGHT         = 6;
    // How many jobs we drain from the queue each tick.
    private static final int DRAIN_PER_TICK        = 4;
    // Time budget for the queue-building loop (does NOT include snapshot work).
    private static final long TIME_BUDGET_NS       = 500_000L; // 0.5 ms

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
        renderSnapshot = new ArrayList<>();
        renderSnapshotDirty = false;
        pruneTimer = 0;
        dimensionChangeCooldown = 0;
        if (mc.world != null) lastDimension = mc.world.getRegistryKey().getValue().toString();
        executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "Tunnelers-Scanner");
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
        renderSnapshot = new ArrayList<>();
    }

    // ------------------------------------------------------------------ //
    //  Tick                                                                //
    // ------------------------------------------------------------------ //

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (dimensionChangeCooldown > 0) {
            dimensionChangeCooldown--;
            return;
        }

        String currDim = mc.world.getRegistryKey().getValue().toString();
        if (!currDim.equals(lastDimension)) {
            lastDimension = currDim;
            dimensionChangeCooldown = 40;
            locations.clear();
            chunkIndex.clear();
            scannedChunks.clear();
            inFlight.clear();
            snapshotQueue.clear();
            renderSnapshot = new ArrayList<>();
            renderSnapshotDirty = false;
            return;
        }

        // 1. Merge a bounded slice of completed results.
        flushPendingResults();

        // 2. Rebuild the render snapshot if anything was flushed this tick.
        //    Done once per tick on the main thread — not once per frame.
        if (renderSnapshotDirty) {
            rebuildRenderSnapshot();
            renderSnapshotDirty = false;
        }

        // 3. Periodically prune out-of-range chunks.
        pruneTimer++;
        if (pruneTimer >= scanDelay.get()) {
            pruneTimer = 0;
            pruneOutOfRange();
        }

        // 4. Discover new chunks to scan.
        int playerCX = mc.player.getBlockPos().getX() >> 4;
        int playerCZ = mc.player.getBlockPos().getZ() >> 4;
        enqueueNewChunks(playerCX, playerCZ);

        // 5. Hand pending snapshot jobs to the background thread.
        drainSnapshotQueue();
    }

    // ------------------------------------------------------------------ //
    //  Render snapshot                                                     //
    // ------------------------------------------------------------------ //

    /**
     * FIX #1 — rebuilds the render list from the live map once per tick.
     * Pre-bakes the BlockPos as three ints to avoid repeated field accesses
     * in the hot render loop.
     */
    private void rebuildRenderSnapshot() {
        List<RenderEntry> next = new ArrayList<>(locations.size());
        for (Map.Entry<BlockPos, TunnelType> e : locations.entrySet()) {
            BlockPos p = e.getKey();
            next.add(new RenderEntry(p.getX(), p.getY(), p.getZ(), e.getValue()));
        }
        renderSnapshot = next; // volatile write — render thread picks it up next frame
    }

    // ------------------------------------------------------------------ //
    //  Pruning                                                             //
    // ------------------------------------------------------------------ //

    private void pruneOutOfRange() {
        if (mc.player == null) return;
        int centerCX = mc.player.getBlockPos().getX() >> 4;
        int centerCZ = mc.player.getBlockPos().getZ() >> 4;
        int r        = range.get();
        int rSq      = r * r;

        Iterator<ChunkPos> it = scannedChunks.iterator();
        while (it.hasNext()) {
            ChunkPos cp = it.next();
            int dx = cp.x - centerCX, dz = cp.z - centerCZ;
            if (dx * dx + dz * dz > rSq) {
                evictChunk(cp);
                it.remove();
                renderSnapshotDirty = true;
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Queue building (main thread, cheap)                                 //
    // ------------------------------------------------------------------ //

    private void enqueueNewChunks(int centerCX, int centerCZ) {
        int r     = range.get();
        int rSq   = r * r;
        int added = 0;
        long t0   = System.nanoTime();

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
    //  Snapshot + scan                                                     //
    // ------------------------------------------------------------------ //

    /**
     * FIX #3 — the snapshot (block iteration) and scan both happen on the
     * background thread. The main thread only hands over the live WorldChunk
     * reference. To guard against the chunk being modified mid-copy we do the
     * snapshot as early as possible and work only from the copied array.
     * The WorldChunk reference itself is not kept past the snapshot call.
     */
    private void drainSnapshotQueue() {
        for (int i = 0; i < DRAIN_PER_TICK; i++) {
            if (inFlight.size() >= MAX_IN_FLIGHT) break;
            if (snapshotQueue.isEmpty()) break;

            Iterator<ChunkPos> it = snapshotQueue.iterator();
            ChunkPos cp = it.next();
            it.remove();

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
                    // Snapshot and scan entirely off the main thread.
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
    //  Flush                                                               //
    // ------------------------------------------------------------------ //

    /**
     * FIX #2 — limits by number of batches consumed, not individual entries.
     * Each batch is one full chunk scan result. Capping at MAX_BATCHES_PER_FLUSH
     * gives a predictable, small upper bound on main-thread work per tick
     * regardless of how many BlockPos entries each batch contains.
     */
    private void flushPendingResults() {
        ScanResult batch;
        int batchesProcessed = 0;
        while (batchesProcessed < MAX_BATCHES_PER_FLUSH && (batch = pendingResults.poll()) != null) {
            scannedChunks.add(batch.chunkPos);
            Set<BlockPos> index = chunkIndex.computeIfAbsent(
                batch.chunkPos, k -> ConcurrentHashMap.newKeySet());
            for (Map.Entry<BlockPos, TunnelType> e : batch.results.entrySet()) {
                locations.put(e.getKey(), e.getValue());
                index.add(e.getKey());
            }
            batchesProcessed++;
            renderSnapshotDirty = true;
        }
    }

    // ------------------------------------------------------------------ //
    //  Snapshot (off-thread)                                               //
    // ------------------------------------------------------------------ //

    private BlockState[][] snapshotChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        BlockState[][] snapshot = new BlockState[sections.length][];
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) { snapshot[si] = null; continue; }
            BlockState[] data = new BlockState[16 * 16 * 16];
            for (int lx = 0; lx < 16; lx++)
                for (int ly = 0; ly < 16; ly++)
                    for (int lz = 0; lz < 16; lz++)
                        data[lx + lz * 16 + ly * 256] = sec.getBlockState(lx, ly, lz);
            snapshot[si] = data;
        }
        return snapshot;
    }

    // ------------------------------------------------------------------ //
    //  Off-thread scan                                                     //
    // ------------------------------------------------------------------ //

    private Map<BlockPos, TunnelType> scanSnapshot(
            ChunkPos cp,
            BlockState[][] snapshot,
            int bottomCoord, ScanConfig config
    ) {
        Map<BlockPos, TunnelType> results = new HashMap<>();
        int baseX = cp.x << 4;
        int baseZ = cp.z << 4;
        ScanContext ctx = new ScanContext(snapshot, bottomCoord, config.minY, config.maxY, baseX, baseZ);

        for (int si = 0; si < snapshot.length; si++) {
            if (snapshot[si] == null) continue;
            int sectionMinY = (bottomCoord + si) << 4;
            int sectionMaxY = sectionMinY + 16;
            if (sectionMaxY <= config.minY || sectionMinY >= config.maxY) continue;

            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    int wy = sectionMinY + ly;
                    if (wy < config.minY || wy >= config.maxY) continue;
                    for (int lz = 0; lz < 16; lz++) {
                        classifyBlock(baseX + lx, wy, baseZ + lz, ctx, config, results);
                    }
                }
            }
        }
        return results;
    }

    private void classifyBlock(
            int wx, int wy, int wz,
            ScanContext ctx,
            ScanConfig config,
            Map<BlockPos, TunnelType> results
    ) {
        if (config.doHoles && isHole(wx, wy, wz, ctx, config.holeDepth)) {
            for (int i = 0; i < config.holeDepth; i++)
                results.put(new BlockPos(wx, wy - i, wz), TunnelType.HOLE);
            return;
        }

        if (config.doLadder && isLadderShaft(wx, wy, wz, ctx, config.ladderMin)) {
            for (int i = 0; i < config.ladderMin; i++)
                results.put(new BlockPos(wx, wy + i, wz), TunnelType.LADDER_SHAFT);
        }

        if (config.do1x1 && is1x1Tunnel(wx, wy, wz, ctx)) {
            results.put(new BlockPos(wx, wy + 1, wz), TunnelType.TUNNEL_1x1);
        }

        if (config.do1x2 && is1x2Tunnel(wx, wy, wz, ctx)) {
            results.put(new BlockPos(wx, wy + 1, wz), TunnelType.TUNNEL_1x2);
            results.put(new BlockPos(wx, wy + 2, wz), TunnelType.TUNNEL_1x2);
        }

        if (config.doAbnormal) {
            int sz = getAbnormalTunnelSize(wx, wy, wz, ctx);
            if (sz > 0) {
                for (int dx = 0; dx < sz; dx++)
                    for (int dy = 1; dy <= sz; dy++)
                        for (int dz = 0; dz < sz; dz++)
                            results.put(new BlockPos(wx + dx, wy + dy, wz + dz),
                                TunnelType.ABNORMAL_TUNNEL);
            }
        }

        if (config.do2x2 && is2x2Tunnel(wx, wy, wz, ctx)) {
            for (int dx = 0; dx < 2; dx++)
                for (int dy = 1; dy <= 2; dy++)
                    for (int dz = 0; dz < 2; dz++)
                        results.put(new BlockPos(wx + dx, wy + dy, wz + dz),
                            TunnelType.TUNNEL_2x2);
        }
    }

    // ------------------------------------------------------------------ //
    //  Block tests                                                         //
    // ------------------------------------------------------------------ //

    private boolean isHole(int x, int y, int z, ScanContext ctx, int depth) {
        if (!ctx.isAir(x, y, z)) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0)
                    if (!ctx.isSolid(x + dx, y, z + dz)) return false;
        for (int i = 1; i < depth; i++) {
            int sy = y - i;
            if (!ctx.isAir(x, sy, z))       return false;
            if (!ctx.isSolid(x - 1, sy, z)) return false;
            if (!ctx.isSolid(x + 1, sy, z)) return false;
            if (!ctx.isSolid(x, sy, z - 1)) return false;
            if (!ctx.isSolid(x, sy, z + 1)) return false;
        }
        return true;
    }

    private boolean is1x1Tunnel(int x, int y, int z, ScanContext ctx) {
        if (!ctx.isSolid(x, y,     z)) return false;
        if (!ctx.isAir  (x, y + 1, z)) return false;
        if (!ctx.isSolid(x, y + 2, z)) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (!ctx.isSolid(x + dx, y + 1, z + dz)) return false;
            }
        return true;
    }

    private boolean is1x2Tunnel(int x, int y, int z, ScanContext ctx) {
        if (!ctx.isSolid(x, y,     z)) return false;
        if (!ctx.isAir  (x, y + 1, z)) return false;
        if (!ctx.isAir  (x, y + 2, z)) return false;
        if (!ctx.isSolid(x, y + 3, z)) return false;
        if (isMineshaftBlock(ctx.get(x, y, z)) || isMineshaftBlock(ctx.get(x, y + 3, z))) return false;
        boolean isZTunnel = true;
        for (int dy = 1; dy <= 2; dy++) {
            if (!ctx.isSolid(x - 1, y + dy, z) || !ctx.isSolid(x + 1, y + dy, z)) {
                isZTunnel = false;
                break;
            }
        }
        if (isZTunnel) return true;
        for (int dy = 1; dy <= 2; dy++) {
            if (!ctx.isSolid(x, y + dy, z - 1) || !ctx.isSolid(x, y + dy, z + 1)) return false;
        }
        return true;
    }

    private boolean is2x2Tunnel(int x, int y, int z, ScanContext ctx) {
        for (int fx = 0; fx < 2; fx++)
            for (int fz = 0; fz < 2; fz++)
                if (!ctx.isSolid(x + fx, y, z + fz)) return false;
        for (int fx = 0; fx < 2; fx++)
            for (int fy = 1; fy <= 2; fy++)
                for (int fz = 0; fz < 2; fz++)
                    if (!ctx.isAir(x + fx, y + fy, z + fz)) return false;
        for (int fx = 0; fx < 2; fx++)
            for (int fz = 0; fz < 2; fz++)
                if (!ctx.isSolid(x + fx, y + 3, z + fz)) return false;
        for (int fx = 0; fx < 2; fx++) for (int fy = 1; fy <= 2; fy++) {
            if (!ctx.isSolid(x + fx, y + fy, z - 1)) return false;
            if (!ctx.isSolid(x + fx, y + fy, z + 2)) return false;
        }
        for (int fz = 0; fz < 2; fz++) for (int fy = 1; fy <= 2; fy++) {
            if (!ctx.isSolid(x - 1, y + fy, z + fz)) return false;
            if (!ctx.isSolid(x + 2, y + fy, z + fz)) return false;
        }
        return true;
    }

    private int getAbnormalTunnelSize(int x, int y, int z, ScanContext ctx) {
        if (isTunnelOfSize(x, y, z, ctx, 5)) return 5;
        if (isTunnelOfSize(x, y, z, ctx, 4)) return 4;
        if (isTunnelOfSize(x, y, z, ctx, 3)) return 3;
        return 0;
    }

    private boolean isTunnelOfSize(int x, int y, int z, ScanContext ctx, int size) {
        for (int fx = 0; fx < size; fx++)
            for (int fz = 0; fz < size; fz++)
                if (!ctx.isSolid(x + fx, y, z + fz)) return false;
        for (int fx = 0; fx < size; fx++)
            for (int fy = 1; fy <= size; fy++)
                for (int fz = 0; fz < size; fz++)
                    if (!ctx.isAir(x + fx, y + fy, z + fz)) return false;
        for (int fx = 0; fx < size; fx++)
            for (int fz = 0; fz < size; fz++)
                if (!ctx.isSolid(x + fx, y + size + 1, z + fz)) return false;
        for (int fx = 0; fx < size; fx++) for (int fy = 1; fy <= size; fy++) {
            if (!ctx.isSolid(x + fx, y + fy, z - 1))    return false;
            if (!ctx.isSolid(x + fx, y + fy, z + size)) return false;
        }
        for (int fz = 0; fz < size; fz++) for (int fy = 1; fy <= size; fy++) {
            if (!ctx.isSolid(x - 1,    y + fy, z + fz)) return false;
            if (!ctx.isSolid(x + size, y + fy, z + fz)) return false;
        }
        return true;
    }

    private boolean isMineshaftBlock(BlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        return block == Blocks.OAK_PLANKS || block == Blocks.DARK_OAK_PLANKS;
    }

    private boolean isLadderShaft(int x, int y, int z, ScanContext ctx, int minHeight) {
        if (!ctx.isSolid(x, y - 1, z)) return false;
        for (int i = 0; i < minHeight; i++) {
            int cy = y + i;
            if (!ctx.isAir(x, cy, z)) return false;
            boolean hasLadder =
                ctx.isLadder(x - 1, cy, z) || ctx.isLadder(x + 1, cy, z) ||
                ctx.isLadder(x, cy, z - 1) || ctx.isLadder(x, cy, z + 1);
            if (!hasLadder) return false;
            int solidWalls = 0;
            if (ctx.isSolid(x - 1, cy, z)) solidWalls++;
            if (ctx.isSolid(x + 1, cy, z)) solidWalls++;
            if (ctx.isSolid(x, cy, z - 1)) solidWalls++;
            if (ctx.isSolid(x, cy, z + 1)) solidWalls++;
            if (solidWalls < 3) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ //
    //  ScanContext                                                         //
    // ------------------------------------------------------------------ //

    private static final class ScanContext {
        private final BlockState[][] snapshot;
        private final int bottomCoord, minY, maxY, baseX, baseZ;

        ScanContext(BlockState[][] snapshot, int bottomCoord,
                    int minY, int maxY, int baseX, int baseZ) {
            this.snapshot    = snapshot;
            this.bottomCoord = bottomCoord;
            this.minY        = minY;
            this.maxY        = maxY;
            this.baseX       = baseX;
            this.baseZ       = baseZ;
        }

        BlockState get(int x, int y, int z) {
            if (y < minY || y >= maxY) return null;
            int lx = x - baseX, lz = z - baseZ;
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return null;
            int si = (y >> 4) - bottomCoord;
            if (si < 0 || si >= snapshot.length) return null;
            BlockState[] sec = snapshot[si];
            if (sec == null) return null;
            return sec[lx + lz * 16 + (y & 15) * 256];
        }

        boolean isSolid(int x, int y, int z) {
            BlockState s = get(x, y, z);
            return s != null && s.isOpaque();
        }

        boolean isAir(int x, int y, int z) {
            BlockState s = get(x, y, z);
            return s == null || s.isAir();
        }

        boolean isLadder(int x, int y, int z) {
            BlockState s = get(x, y, z);
            return s != null && s.isOf(Blocks.LADDER);
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private void evictChunk(ChunkPos cp) {
        Set<BlockPos> indexed = chunkIndex.remove(cp);
        if (indexed != null) {
            for (BlockPos pos : indexed) locations.remove(pos);
        }
    }

    // ------------------------------------------------------------------ //
    //  Render                                                              //
    // ------------------------------------------------------------------ //

    /**
     * FIX #1 + FIX #4 — iterates the pre-built renderSnapshot (a plain ArrayList)
     * instead of the live ConcurrentHashMap, and uses squared-distance comparison
     * to avoid Math.sqrt per entry. No SettingColor is allocated per entry;
     * a single reusable SettingColor is mutated in-place for the faded case.
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        List<RenderEntry> snapshot = renderSnapshot; // single volatile read
        if (snapshot.isEmpty()) return;

        boolean doFade   = fadeWithDistance.get();
        // FIX #4 — compare squared distances; no sqrt needed.
        double maxDistSq = (double)(range.get() * 16) * (range.get() * 16);

        BlockPos playerPos = mc.player.getBlockPos();
        int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();

        // FIX #4 — one reusable color object; mutated per entry, never heap-allocated in the loop.
        SettingColor reusable = new SettingColor(0, 0, 0, 0);

        for (RenderEntry entry : snapshot) {
            SettingColor base = getColor(entry.type);
            if (base == null) continue;

            SettingColor c;
            if (doFade) {
                long dx = entry.x - px, dy = entry.y - py, dz = entry.z - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                float  frac   = (float) Math.max(0.0, 1.0 - distSq / maxDistSq);
                int fadedAlpha = Math.max(8, (int)(base.a * frac));
                // Mutate the reusable color rather than allocating a new one.
                reusable.r = base.r;
                reusable.g = base.g;
                reusable.b = base.b;
                reusable.a = fadedAlpha;
                c = reusable;
            } else {
                c = base;
            }

            event.renderer.box(entry.x, entry.y, entry.z,
                entry.x + 1, entry.y + 1, entry.z + 1,
                c, c, shapeMode.get(), 0);
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
        int holeDepth, int ladderMin,
        int minY, int maxY
    ) {}

    private static final class ScanResult {
        final ChunkPos chunkPos;
        final Map<BlockPos, TunnelType> results;
        ScanResult(ChunkPos chunkPos, Map<BlockPos, TunnelType> results) {
            this.chunkPos = chunkPos;
            this.results  = results;
        }
    }

    /**
     * FIX #1 — lightweight render entry that stores coordinates as primitive ints.
     * Avoids repeated BlockPos field unpacking in the hot render loop and keeps
     * the render list tightly packed for cache-friendly iteration.
     */
    private static final class RenderEntry {
        final int x, y, z;
        final TunnelType type;
        RenderEntry(int x, int y, int z, TunnelType type) {
            this.x = x; this.y = y; this.z = z; this.type = type;
        }
    }
}