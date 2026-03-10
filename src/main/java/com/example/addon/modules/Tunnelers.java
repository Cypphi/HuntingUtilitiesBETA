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
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
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
import meteordevelopment.meteorclient.settings.DoubleSetting;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
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
    private final SettingGroup sgGlow     = settings.createGroup("Glow");

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

    private final Setting<Integer> min1x1Length = sg1x1.add(new IntSetting.Builder()
        .name("min-length")
        .description("Minimum length of a 1x1 tunnel to be rendered.")
        .defaultValue(8).min(1).sliderMax(64)
        .visible(find1x1::get)
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

    private final Setting<Integer> min1x2Length = sg1x2.add(new IntSetting.Builder()
        .name("min-length")
        .description("Minimum length of a 1x2 tunnel to be rendered.")
        .defaultValue(4).min(1).sliderMax(64)
        .visible(find1x2::get)
        .build());

    // ------------------------------------------------------------------ //
    //  2x2 Tunnels                                                         //
    // ------------------------------------------------------------------ //

    private final Setting<Boolean> find2x2 = sg2x2.add(new BoolSetting.Builder()
        .name("find-2x2-tunnels")
        .defaultValue(true)
        .build());

    private final Setting<Integer> min2x2Length = sg2x2.add(new IntSetting.Builder()
        .name("min-length")
        .description("Minimum length of a 2x2 tunnel to be rendered.")
        .defaultValue(2).min(1).sliderMax(64)
        .visible(find2x2::get)
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
    //  Glow                                                                //
    // ------------------------------------------------------------------ //

    private final Setting<Boolean> glowEnabled = sgGlow.add(new BoolSetting.Builder()
        .name("glow")
        .description("Render a bloom glow around each highlighted box.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> glowLayers = sgGlow.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("Number of bloom layers rendered around each box.")
        .defaultValue(4).min(1).sliderMax(8)
        .visible(glowEnabled::get)
        .build());

    private final Setting<Double> glowSpread = sgGlow.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.05).min(0.01).sliderMax(0.2)
        .visible(glowEnabled::get)
        .build());

    private final Setting<Integer> glowBaseAlpha = sgGlow.add(new IntSetting.Builder()
        .name("glow-base-alpha")
        .description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60).min(4).sliderMax(150)
        .visible(glowEnabled::get)
        .build());

    // ------------------------------------------------------------------ //
    //  State                                                               //
    // ------------------------------------------------------------------ //

    private final ConcurrentHashMap<BlockPos, TunnelType>    locations      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Set<BlockPos>> chunkIndex     = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ScanResult>          pendingResults = new ConcurrentLinkedQueue<>();

    private volatile List<MergedBox> renderSnapshot = Collections.emptyList();
    private final AtomicBoolean mergeScheduled = new AtomicBoolean(false);
    private volatile int snapPX, snapPY, snapPZ;

    private final Set<ChunkPos>          scannedChunks = new HashSet<>();
    private final LinkedHashSet<ChunkPos> snapshotQueue = new LinkedHashSet<>();
    private final Set<ChunkPos>          inFlight       = ConcurrentHashMap.newKeySet();

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
            renderSnapshot = Collections.emptyList();
            mergeScheduled.set(false);
            return;
        }

        boolean newData = flushPendingResults();
        if (newData) scheduleMerge();

        if (++pruneTimer >= scanDelay.get()) {
            pruneTimer = 0;
            if (pruneOutOfRange()) scheduleMerge();
        }

        int playerCX = mc.player.getBlockPos().getX() >> 4;
        int playerCZ = mc.player.getBlockPos().getZ() >> 4;
        enqueueNewChunks(playerCX, playerCZ);
        drainSnapshotQueue();
    }

    // ------------------------------------------------------------------ //
    //  Merge scheduling                                                    //
    // ------------------------------------------------------------------ //

    private void scheduleMerge() {
        if (!mergeScheduled.compareAndSet(false, true)) return;

        snapPX = mc.player.getBlockPos().getX();
        snapPY = mc.player.getBlockPos().getY();
        snapPZ = mc.player.getBlockPos().getZ();

        final Map<BlockPos, TunnelType> locSnapshot = new HashMap<>(locations);
        final int px = snapPX, py = snapPY, pz = snapPZ;
        final double maxDistSq = (double)(range.get() * 16) * (range.get() * 16);
        final int minLength1x1 = min1x1Length.get();
        final int minLength1x2 = min1x2Length.get();
        final int minLength2x2 = min2x2Length.get();

        executor.submit(() -> {
            try {
                List<MergedBox> merged = buildMergedBoxes(locSnapshot, px, py, pz, maxDistSq, minLength1x1, minLength1x2, minLength2x2);
                renderSnapshot = merged;
            } finally {
                mergeScheduled.set(false);
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Greedy Mesh                                                         //
    // ------------------------------------------------------------------ //

    private static List<MergedBox> buildMergedBoxes(
            Map<BlockPos, TunnelType> locs,
            int px, int py, int pz,
            double maxDistSq, int minLength1x1,
            int minLength1x2, int minLength2x2
    ) {
        if (locs.isEmpty()) return Collections.emptyList();

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

        // Filter tunnels by length
        filterTunnelTypeByLength(TunnelType.TUNNEL_1x1, minLength1x1, coordsByType, remaining);
        filterTunnelTypeByLength(TunnelType.TUNNEL_1x2, minLength1x2, coordsByType, remaining);
        filterTunnelTypeByLength(TunnelType.TUNNEL_2x2, minLength2x2, coordsByType, remaining);

        List<MergedBox> boxes = new ArrayList<>();

        for (TunnelType type : TunnelType.values()) {
            Set<Long>   rem    = remaining.get(type);
            List<int[]> coords = coordsByType.get(type);
            if (rem.isEmpty()) continue;

            for (int[] origin : coords) {
                int ox = origin[0], oy = origin[1], oz = origin[2];
                if (!rem.contains(pack(ox, oy, oz))) continue;

                int x2 = ox;
                while (rem.contains(pack(x2 + 1, oy, oz))) x2++;

                int z2 = oz;
                while (canExtendZ(rem, ox, x2, oy, z2 + 1)) z2++;

                int y2 = oy;
                while (canExtendY(rem, ox, x2, y2 + 1, oz, z2)) y2++;

                for (int x = ox; x <= x2; x++)
                    for (int y = oy; y <= y2; y++)
                        for (int z = oz; z <= z2; z++)
                            rem.remove(pack(x, y, z));

                double cx = (ox + x2) * 0.5 + 0.5;
                double cy = (oy + y2) * 0.5 + 0.5;
                double cz = (oz + z2) * 0.5 + 0.5;
                double ddx = cx - px, ddy = cy - py, ddz = cz - pz;
                double distSq = Math.min(ddx * ddx + ddy * ddy + ddz * ddz, maxDistSq);

                boxes.add(new MergedBox(ox, oy, oz, x2 + 1, y2 + 1, z2 + 1, type, distSq));
            }
        }

        boxes.sort(Comparator.comparingDouble(b -> b.distSq));
        return boxes;
    }

    private static void filterTunnelTypeByLength(TunnelType type, int minLength,
            EnumMap<TunnelType, List<int[]>> coordsByType, EnumMap<TunnelType, Set<Long>> remaining) {
        if (minLength <= 1) return;

        List<int[]> coords = coordsByType.get(type);
        if (coords == null || coords.isEmpty()) return;

        Set<BlockPos> allBlocks = new HashSet<>(coords.size());
        for (int[] c : coords) allBlocks.add(new BlockPos(c[0], c[1], c[2]));

        Set<BlockPos> blocksToKeep = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();

        for (BlockPos startPos : allBlocks) {
            if (visited.contains(startPos)) continue;

            List<BlockPos> component = new ArrayList<>();
            Queue<BlockPos> queue = new LinkedList<>();

            queue.add(startPos);
            visited.add(startPos);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                component.add(current);

                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.offset(dir);
                    if (allBlocks.contains(neighbor) && visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            if (component.size() >= minLength) {
                blocksToKeep.addAll(component);
            }
        }

        coordsByType.get(type).removeIf(c -> !blocksToKeep.contains(new BlockPos(c[0], c[1], c[2])));
        Set<Long> newRemaining = new HashSet<>(blocksToKeep.size());
        for (BlockPos pos : blocksToKeep) newRemaining.add(pack(pos.getX(), pos.getY(), pos.getZ()));
        remaining.put(type, newRemaining);
    }

    private static boolean canExtendZ(Set<Long> rem, int ox, int x2, int y, int z) {
        for (int x = ox; x <= x2; x++)
            if (!rem.contains(pack(x, y, z))) return false;
        return true;
    }

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
        return ((long)(x + 33_554_432) << 38) | ((long)(y + 2_048) << 26) | (z + 33_554_432);
    }

    // ------------------------------------------------------------------ //
    //  Pruning                                                             //
    // ------------------------------------------------------------------ //

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
    //  Queue building                                                      //
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
    //  Flush                                                               //
    // ------------------------------------------------------------------ //

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
        // Check for solid floor, air tunnel space, and solid ceiling.
        if (!ctx.isSolid(x, y, z) || !ctx.isAir(x, y + 1, z) || !ctx.isSolid(x, y + 2, z)) {
            return false;
        }

        boolean northSolid = ctx.isSolid(x, y + 1, z - 1);
        boolean southSolid = ctx.isSolid(x, y + 1, z + 1);
        boolean eastSolid = ctx.isSolid(x + 1, y + 1, z);
        boolean westSolid = ctx.isSolid(x - 1, y + 1, z);

        int solidHorizontalSides = (northSolid ? 1 : 0) + (southSolid ? 1 : 0) + (eastSolid ? 1 : 0) + (westSolid ? 1 : 0);

        // A tunnel segment should be enclosed on 2 or 3 sides horizontally.
        // This was too permissive and highlighted corners in open caves.
        // The new logic only allows dead-ends (3 walls) or straight passages (2 opposing walls).
        if (solidHorizontalSides == 3) return true;
        if (solidHorizontalSides == 2) {
            return (northSolid && southSolid) || (eastSolid && westSolid);
        }
        return false;
    }

    private boolean is1x2Tunnel(int x, int y, int z, ScanContext ctx) {
        // Check for the vertical 1x2 air column with floor and ceiling
        if (!is1x2Slice(x, y, z, ctx)) return false;

        // Optional: ignore tunnels that look like mineshafts
        if (isMineshaftBlock(ctx.get(x,y,z)) || isMineshaftBlock(ctx.get(x,y+3,z))) return false;

        // Check horizontal walls for both layers of the tunnel
        boolean northSolid = ctx.isSolid(x, y + 1, z - 1) && ctx.isSolid(x, y + 2, z - 1);
        boolean southSolid = ctx.isSolid(x, y + 1, z + 1) && ctx.isSolid(x, y + 2, z + 1);
        boolean eastSolid  = ctx.isSolid(x + 1, y + 1, z) && ctx.isSolid(x + 1, y + 2, z);
        boolean westSolid  = ctx.isSolid(x - 1, y + 1, z) && ctx.isSolid(x - 1, y + 2, z);

        int solidWalls = (northSolid ? 1 : 0) + (southSolid ? 1 : 0) + (eastSolid ? 1 : 0) + (westSolid ? 1 : 0);

        // A dead-end has 3 solid walls.
        if (solidWalls == 3) return true;

        // A straight tunnel segment has 2 opposing solid walls.
        if (solidWalls == 2) {
            return (northSolid && southSolid) || (eastSolid && westSolid);
        }

        return false;
    }

    private boolean is1x2Slice(int x, int y, int z, ScanContext ctx) {
        return ctx.isSolid(x,y,z) && ctx.isAir(x,y+1,z) && ctx.isAir(x,y+2,z) && ctx.isSolid(x,y+3,z);
    }

    private boolean is2x2Tunnel(int x, int y, int z, ScanContext ctx) {
        // Check for 2x2 floor, 2x2x2 air space, and 2x2 ceiling
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                if (!ctx.isSolid(x + dx, y, z + dz) || !ctx.isSolid(x + dx, y + 3, z + dz)) return false;
                if (!ctx.isAir(x + dx, y + 1, z + dz) || !ctx.isAir(x + dx, y + 2, z + dz)) return false;
            }
        }

        // Check for walls. A wall is solid if all its blocks are solid.
        boolean northSolid = true;
        outer: for (int dx = 0; dx < 2; dx++) {
            for (int dy = 1; dy <= 2; dy++) {
                if (!ctx.isSolid(x + dx, y + dy, z - 1)) {
                    northSolid = false;
                    break outer;
                }
            }
        }

        boolean southSolid = true;
        outer: for (int dx = 0; dx < 2; dx++) {
            for (int dy = 1; dy <= 2; dy++) {
                if (!ctx.isSolid(x + dx, y + dy, z + 2)) {
                    southSolid = false;
                    break outer;
                }
            }
        }

        boolean eastSolid = true;
        outer: for (int dz = 0; dz < 2; dz++) {
            for (int dy = 1; dy <= 2; dy++) {
                if (!ctx.isSolid(x + 2, y + dy, z + dz)) {
                    eastSolid = false;
                    break outer;
                }
            }
        }

        boolean westSolid = true;
        outer: for (int dz = 0; dz < 2; dz++) {
            for (int dy = 1; dy <= 2; dy++) {
                if (!ctx.isSolid(x - 1, y + dy, z + dz)) {
                    westSolid = false;
                    break outer;
                }
            }
        }

        int solidWalls = (northSolid ? 1 : 0) + (southSolid ? 1 : 0) + (eastSolid ? 1 : 0) + (westSolid ? 1 : 0);

        // A dead-end has 3 solid walls.
        if (solidWalls == 3) return true;

        // A straight tunnel segment has 2 opposing solid walls.
        if (solidWalls == 2) {
            return (northSolid && southSolid) || (eastSolid && westSolid);
        }
        return false;
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
    //  Render                                                              //
    // ------------------------------------------------------------------ //

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        List<MergedBox> snapshot = renderSnapshot; // single volatile read
        if (snapshot.isEmpty()) return;

        boolean   doFade    = fadeWithDistance.get();
        boolean   doGlow    = glowEnabled.get();
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

            // Bloom glow — drawn before the solid box so layers sit behind it.
            // Uses the same per-type color as the fill, tinted down via quadratic
            // alpha falloff so the glow is brightest near the box surface and
            // fades quickly outward. Fade-with-distance is also applied so distant
            // boxes don't bloom brighter than their fill.
            if (doGlow) {
                int    layers    = glowLayers.get();
                double spread    = glowSpread.get();
                int    baseAlpha = glowBaseAlpha.get();

                for (int i = layers; i >= 1; i--) {
                    double expansion = spread * i;

                    // t=0 innermost (brightest), t=1 outermost (most transparent).
                    // Squaring keeps the core bright and drops sharply at the edges.
                    double t          = (double)(i - 1) / layers;
                    int    layerAlpha = Math.max(4, (int)(baseAlpha * (1.0 - t * t)));

                    // Apply distance fade to glow alpha as well so it scales with the fill.
                    if (doFade) {
                        float frac = (float) Math.max(0.0, 1.0 - box.distSq / maxDistSq);
                        layerAlpha = Math.max(4, (int)(layerAlpha * frac));
                    }

                    event.renderer.box(
                        box.x1 - expansion, box.y1 - expansion, box.z1 - expansion,
                        box.x2 + expansion, box.y2 + expansion, box.z2 + expansion,
                        withAlpha(c, layerAlpha),
                        withAlpha(c, 0),
                        ShapeMode.Sides, 0
                    );
                }
            }

            // Solid highlight box on top of glow layers.
            event.renderer.box(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2, c, c, sm, 0);
            drawn++;
        }
    }

    // ------------------------------------------------------------------ //
    //  Color helpers                                                       //
    // ------------------------------------------------------------------ //

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

    private static SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
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

    private static final class MergedBox {
        final int x1, y1, z1, x2, y2, z2;
        final TunnelType type;
        final double distSq;
        MergedBox(int x1, int y1, int z1, int x2, int y2, int z2, TunnelType t, double d) {
            this.x1=x1; this.y1=y1; this.z1=z1; this.x2=x2; this.y2=y2; this.z2=z2; type=t; distSq=d;
        }
    }
}