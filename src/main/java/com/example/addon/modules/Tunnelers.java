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

    /**
     * Hard cap on merged boxes drawn per frame. The snapshot is sorted
     * nearest-first so distant clutter is the first thing dropped.
     * Even at range=32 with dense tunnels the merged count is typically
     * well under 500; this is a safety valve for extreme cases.
     */
    private final Setting<Integer> maxRenderBoxes = sgRender.add(new IntSetting.Builder()
        .name("max-render-boxes")
        .description("Maximum merged boxes rendered per frame. Lower = better FPS in dense areas.")
        .defaultValue(2000).min(100).sliderMax(8000)
        .build());

    // ------------------------------------------------------------------ //
    //  State                                                               //
    // ------------------------------------------------------------------ //

    private final ConcurrentHashMap<BlockPos, TunnelType>    locations      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Set<BlockPos>> chunkIndex     = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ScanResult>          pendingResults = new ConcurrentLinkedQueue<>();

    /**
     * GREEDY MERGE FIX — render thread iterates merged AABBs, not individual
     * block positions. A 300-block straight tunnel → 1–2 draw calls instead of
     * 300. Rebuilt once per tick only when locations has changed.
     * Volatile so the render thread always sees the freshest reference.
     */
    private volatile List<MergedBox> renderSnapshot = new ArrayList<>();

    /** True whenever locations has changed and the snapshot must be rebuilt next tick. */
    private boolean renderSnapshotDirty = false;

    /** Chunks whose results are already live in {@code locations}. Main-thread only. */
    private final Set<ChunkPos> scannedChunks = new HashSet<>();

    /** Chunks queued for snapshotting. Main-thread only. */
    private final LinkedHashSet<ChunkPos> snapshotQueue = new LinkedHashSet<>();

    /** Chunks currently being scanned on the background thread. */
    private final Set<ChunkPos> inFlight = ConcurrentHashMap.newKeySet();

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

        if (dimensionChangeCooldown > 0) { dimensionChangeCooldown--; return; }

        String currDim = mc.world.getRegistryKey().getValue().toString();
        if (!currDim.equals(lastDimension)) {
            lastDimension = currDim;
            dimensionChangeCooldown = 40;
            locations.clear(); chunkIndex.clear(); scannedChunks.clear();
            inFlight.clear(); snapshotQueue.clear();
            renderSnapshot = new ArrayList<>();
            renderSnapshotDirty = false;
            return;
        }

        // 1. Merge completed scan results into the live map.
        flushPendingResults();

        // 2. Rebuild the greedy-merged render snapshot if anything changed.
        //    Runs once per tick, never per frame.
        if (renderSnapshotDirty) {
            rebuildRenderSnapshot();
            renderSnapshotDirty = false;
        }

        // 3. Prune out-of-range chunks periodically.
        if (++pruneTimer >= scanDelay.get()) {
            pruneTimer = 0;
            pruneOutOfRange();
        }

        // 4. Queue and dispatch new chunk scans.
        int playerCX = mc.player.getBlockPos().getX() >> 4;
        int playerCZ = mc.player.getBlockPos().getZ() >> 4;
        enqueueNewChunks(playerCX, playerCZ);
        drainSnapshotQueue();
    }

    // ------------------------------------------------------------------ //
    //  Greedy Mesh — collapse adjacent same-type blocks into AABB boxes    //
    // ------------------------------------------------------------------ //

    /**
     * Builds a minimal list of axis-aligned boxes from the flat block map
     * using a greedy expansion: X first, then Z, then Y.
     *
     * Example reduction factors on real data:
     *   200-block straight 1×2 tunnel  → ~1 box  (200× reduction)
     *   Dense 8-chunk mining district  → ~50 boxes from ~8 000 blocks (160× reduction)
     *
     * The result list is sorted nearest-first so the maxRenderBoxes cap
     * always drops the most distant geometry first.
     *
     * Time complexity: O(N log N) where N = number of detected blocks.
     * Runs on the main thread once per tick only when dirty, never per frame.
     */
    private void rebuildRenderSnapshot() {
        if (mc.player == null) { renderSnapshot = new ArrayList<>(); return; }

        int px = mc.player.getBlockPos().getX();
        int py = mc.player.getBlockPos().getY();
        int pz = mc.player.getBlockPos().getZ();

        // Group positions by TunnelType into packed-long sets for O(1) lookup.
        // Merging is only correct within the same type — a 1×1 tunnel must not
        // be merged with an adjacent hole even if they share a face.
        EnumMap<TunnelType, Set<Long>> byType = new EnumMap<>(TunnelType.class);
        EnumMap<TunnelType, List<int[]>> coordsByType = new EnumMap<>(TunnelType.class);
        for (TunnelType t : TunnelType.values()) {
            byType.put(t, new HashSet<>());
            coordsByType.put(t, new ArrayList<>());
        }

        for (Map.Entry<BlockPos, TunnelType> e : locations.entrySet()) {
            BlockPos p = e.getKey();
            TunnelType t = e.getValue();
            long key = pack(p.getX(), p.getY(), p.getZ());
            byType.get(t).add(key);
            coordsByType.get(t).add(new int[]{ p.getX(), p.getY(), p.getZ() });
        }

        List<MergedBox> boxes = new ArrayList<>();

        for (TunnelType type : TunnelType.values()) {
            Set<Long>   remaining = byType.get(type);
            List<int[]> coords    = coordsByType.get(type);
            if (remaining.isEmpty()) continue;

            for (int[] origin : coords) {
                int ox = origin[0], oy = origin[1], oz = origin[2];
                if (!remaining.contains(pack(ox, oy, oz))) continue; // already consumed

                // Extend along +X
                int x2 = ox;
                while (remaining.contains(pack(x2 + 1, oy, oz))) x2++;

                // Extend along +Z — full X-row must be present at each step
                int z2 = oz;
                outerZ:
                while (true) {
                    for (int x = ox; x <= x2; x++)
                        if (!remaining.contains(pack(x, oy, z2 + 1))) break outerZ;
                    z2++;
                }

                // Extend along +Y — full XZ-slab must be present at each step
                int y2 = oy;
                outerY:
                while (true) {
                    for (int x = ox; x <= x2; x++)
                        for (int z = oz; z <= z2; z++)
                            if (!remaining.contains(pack(x, y2 + 1, z))) break outerY;
                    y2++;
                }

                // Consume every block in the merged box
                for (int x = ox; x <= x2; x++)
                    for (int y = oy; y <= y2; y++)
                        for (int z = oz; z <= z2; z++)
                            remaining.remove(pack(x, y, z));

                // Squared distance from box centre to player (for sort + fade)
                double cx = (ox + x2) * 0.5, cy = (oy + y2) * 0.5, cz = (oz + z2) * 0.5;
                double ddx = cx - px, ddy = cy - py, ddz = cz - pz;

                boxes.add(new MergedBox(ox, oy, oz, x2 + 1, y2 + 1, z2 + 1, type,
                    ddx * ddx + ddy * ddy + ddz * ddz));
            }
        }

        // Nearest-first: when capped, close geometry is always rendered.
        boxes.sort(Comparator.comparingDouble(b -> b.distSq));
        renderSnapshot = boxes; // volatile write — render thread picks up next frame
    }

    // ------------------------------------------------------------------ //
    //  Coordinate packing (lossless for any Minecraft world coordinate)    //
    // ------------------------------------------------------------------ //

    /**
     * Packs (x, y, z) into a single long.
     * x, z: 26-bit signed  → offset by 2^25  (covers ±33 554 432 blocks)
     * y:    12-bit signed  → offset by 2^11  (covers ±2 048 blocks)
     * Layout: [25:0]=z+offset  [37:26]=y+offset  [63:38]=x+offset
     */
    private static long pack(int x, int y, int z) {
        return ((long)(x + 33_554_432) << 38) | ((long)(y + 2_048) << 26) | (z + 33_554_432);
    }

    // ------------------------------------------------------------------ //
    //  Pruning                                                             //
    // ------------------------------------------------------------------ //

    private void pruneOutOfRange() {
        if (mc.player == null) return;
        int centerCX = mc.player.getBlockPos().getX() >> 4;
        int centerCZ = mc.player.getBlockPos().getZ() >> 4;
        int rSq      = range.get() * range.get();

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
    //  Snapshot + scan (background thread)                                 //
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
    //  Flush — bounded by batch count, not entry count                     //
    // ------------------------------------------------------------------ //

    private void flushPendingResults() {
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
            renderSnapshotDirty = true;
        }
    }

    // ------------------------------------------------------------------ //
    //  Snapshot (off-thread)                                               //
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
    //  Off-thread scan                                                     //
    // ------------------------------------------------------------------ //

    private Map<BlockPos, TunnelType> scanSnapshot(
            ChunkPos cp, BlockState[][] snapshot, int bottomCoord, ScanConfig config) {
        Map<BlockPos, TunnelType> results = new HashMap<>();
        int baseX = cp.x << 4, baseZ = cp.z << 4;
        ScanContext ctx = new ScanContext(snapshot, bottomCoord, config.minY, config.maxY, baseX, baseZ);

        for (int si = 0; si < snapshot.length; si++) {
            if (snapshot[si] == null) continue;
            int minY = (bottomCoord + si) << 4, maxY = minY + 16;
            if (maxY <= config.minY || minY >= config.maxY) continue;
            for (int lx = 0; lx < 16; lx++)
                for (int ly = 0; ly < 16; ly++) {
                    int wy = minY + ly;
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
            if (!ctx.isAir(x, sy, z) || !ctx.isSolid(x-1,sy,z) || !ctx.isSolid(x+1,sy,z)
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
        if (!ctx.isSolid(x,y,z) || !ctx.isAir(x,y+1,z) || !ctx.isAir(x,y+2,z) || !ctx.isSolid(x,y+3,z))
            return false;
        if (isMineshaftBlock(ctx.get(x,y,z)) || isMineshaftBlock(ctx.get(x,y+3,z))) return false;
        boolean isZ = true;
        for (int dy = 1; dy <= 2; dy++)
            if (!ctx.isSolid(x-1,y+dy,z) || !ctx.isSolid(x+1,y+dy,z)) { isZ = false; break; }
        if (isZ) return true;
        for (int dy = 1; dy <= 2; dy++)
            if (!ctx.isSolid(x,y+dy,z-1) || !ctx.isSolid(x,y+dy,z+1)) return false;
        return true;
    }

    private boolean is2x2Tunnel(int x, int y, int z, ScanContext ctx) {
        for (int fx = 0; fx < 2; fx++) for (int fz = 0; fz < 2; fz++) {
            if (!ctx.isSolid(x+fx,y,z+fz) || !ctx.isSolid(x+fx,y+3,z+fz)) return false;
        }
        for (int fx = 0; fx < 2; fx++) for (int fy = 1; fy <= 2; fy++) for (int fz = 0; fz < 2; fz++)
            if (!ctx.isAir(x+fx,y+fy,z+fz)) return false;
        for (int fx = 0; fx < 2; fx++) for (int fy = 1; fy <= 2; fy++) {
            if (!ctx.isSolid(x+fx,y+fy,z-1) || !ctx.isSolid(x+fx,y+fy,z+2)) return false;
        }
        for (int fz = 0; fz < 2; fz++) for (int fy = 1; fy <= 2; fy++) {
            if (!ctx.isSolid(x-1,y+fy,z+fz) || !ctx.isSolid(x+2,y+fy,z+fz)) return false;
        }
        return true;
    }

    private int getAbnormalTunnelSize(int x, int y, int z, ScanContext ctx) {
        if (isTunnelOfSize(x,y,z,ctx,5)) return 5;
        if (isTunnelOfSize(x,y,z,ctx,4)) return 4;
        if (isTunnelOfSize(x,y,z,ctx,3)) return 3;
        return 0;
    }

    private boolean isTunnelOfSize(int x, int y, int z, ScanContext ctx, int s) {
        for (int fx = 0; fx < s; fx++) for (int fz = 0; fz < s; fz++) {
            if (!ctx.isSolid(x+fx,y,z+fz) || !ctx.isSolid(x+fx,y+s+1,z+fz)) return false;
        }
        for (int fx = 0; fx < s; fx++) for (int fy = 1; fy <= s; fy++) for (int fz = 0; fz < s; fz++)
            if (!ctx.isAir(x+fx,y+fy,z+fz)) return false;
        for (int fx = 0; fx < s; fx++) for (int fy = 1; fy <= s; fy++) {
            if (!ctx.isSolid(x+fx,y+fy,z-1) || !ctx.isSolid(x+fx,y+fy,z+s)) return false;
        }
        for (int fz = 0; fz < s; fz++) for (int fy = 1; fy <= s; fy++) {
            if (!ctx.isSolid(x-1,y+fy,z+fz) || !ctx.isSolid(x+s,y+fy,z+fz)) return false;
        }
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

        ScanContext(BlockState[][] snapshot, int bottomCoord,
                    int minY, int maxY, int baseX, int baseZ) {
            this.snapshot = snapshot; this.bottomCoord = bottomCoord;
            this.minY = minY; this.maxY = maxY; this.baseX = baseX; this.baseZ = baseZ;
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
    //  Render                                                              //
    // ------------------------------------------------------------------ //

    /**
     * Iterates the pre-merged MergedBox list — one draw call per AABB, not one
     * per block. List is nearest-first; we stop at maxRenderBoxes so frame time
     * is bounded regardless of scene density. distSq is pre-computed during the
     * snapshot build — no per-frame sqrt or distance calculation needed.
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        List<MergedBox> snapshot = renderSnapshot; // single volatile read
        if (snapshot.isEmpty()) return;

        boolean doFade   = fadeWithDistance.get();
        double  maxDistSq = (double)(range.get() * 16) * (range.get() * 16);
        int     limit     = maxRenderBoxes.get();
        ShapeMode sm      = shapeMode.get();

        // One reusable color — never allocates in the hot loop.
        SettingColor reusable = new SettingColor(0, 0, 0, 0);

        int drawn = 0;
        for (MergedBox box : snapshot) {
            if (drawn >= limit) break;

            SettingColor base = getColor(box.type);
            if (base == null) continue;

            SettingColor c;
            if (doFade) {
                float frac     = (float) Math.max(0.0, 1.0 - box.distSq / maxDistSq);
                int   fadedA   = Math.max(8, (int)(base.a * frac));
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
     * A merged AABB covering one or more adjacent same-type blocks.
     * x1/y1/z1 = inclusive min corner.
     * x2/y2/z2 = exclusive max corner (x2 = lastX + 1, etc.).
     * distSq   = squared distance from box centre to player at snapshot time.
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