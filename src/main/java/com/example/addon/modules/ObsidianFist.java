package com.example.addon.modules;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ObsidianFist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRepair  = settings.createGroup("Auto Repair");
    private final SettingGroup sgRender  = settings.createGroup("Render");

    // ── General ───────────────────────────────────────────────────────────────

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the block when mining/placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<BreakMode> breakMode = sgGeneral.add(new EnumSetting.Builder<BreakMode>()
        .name("break-mode")
        .description("Safe waits for server confirmation, Instant is fastest, Custom allows a specific delay.")
        .defaultValue(BreakMode.Instant)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swaps back to the previous slot after the burst finishes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> customBreakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("custom-break-delay")
        .description("Ticks to wait before sending the stop-break packet in Custom mode.")
        .defaultValue(1)
        .min(0)
        .sliderMax(20)
        .visible(() -> breakMode.get() == BreakMode.Custom)
        .build()
    );

    private final Setting<Integer> placeActionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-action-delay")
        .description("Ticks to wait after placing before mining again.")
        .defaultValue(1)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<Integer> burstCount = sgGeneral.add(new IntSetting.Builder()
        .name("burst-count")
        .description("Number of ender chest place-break cycles to perform per burst.")
        .defaultValue(8)
        .min(1)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between place-break cycles (0 = instant chain).")
        .defaultValue(0)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to mine/place.")
        .defaultValue(5.0)
        .min(0)
        .sliderMax(6)
        .build()
    );

    // ── Auto Repair ───────────────────────────────────────────────────────────

    private final Setting<Boolean> autoRepair = sgRepair.add(new BoolSetting.Builder()
        .name("auto-repair")
        .description("Automatically repairs your pickaxe with XP bottles when durability is low.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> repairThreshold = sgRepair.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Remaining durability below which to start repairing.")
        .defaultValue(50)
        .min(1)
        .sliderMax(500)
        .visible(autoRepair::get)
        .build()
    );

    private final Setting<Integer> repairPacketsPerBurst = sgRepair.add(new IntSetting.Builder()
        .name("packets-per-burst")
        .description("XP bottles to throw per repair burst.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .visible(autoRepair::get)
        .build()
    );

    private final Setting<Integer> repairBurstDelay = sgRepair.add(new IntSetting.Builder()
        .name("burst-delay")
        .description("Ticks to wait between repair bursts.")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .visible(autoRepair::get)
        .build()
    );

    // ── Render ────────────────────────────────────────────────────────────────

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render the target block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    // ── Enums ─────────────────────────────────────────────────────────────────

    private enum BreakMode { Safe, Instant, Custom }

    private enum State { Idle, Placing, PlacingConfirm, MiningStart, MiningHold, WaitingBreak, SpeedMining }

    // ── State ─────────────────────────────────────────────────────────────────

    private State     mode             = State.Idle;
    private BlockPos  currentPos;
    private Direction currentDir;
    private int       timer;
    private int       restorationCount;
    private BlockPos  placeTarget;
    private Direction placeSide;
    private int       prevSlot         = -1;
    private int       burstCyclesDone  = 0;

    /**
     * Set to true by onPacket() when a break is confirmed in Safe mode.
     * Read and cleared at the top of the next onTick() so the advance runs
     * inside the state machine loop rather than mid-packet-handler.
     * This is what allows zero-tick chaining from packet confirmation to the
     * next place within the same tick's loop iteration.
     */
    private volatile boolean pendingAdvance = false;

    // Auto-repair
    private boolean isRepairing = false;
    private int     repairTimer = 0;

    public ObsidianFist() {
        super(HuntingUtilities.CATEGORY, "obsidian-fist",
              "Place-break Ender Chests for XP. Fully self-contained.");
    }

    @Override public void onActivate()   { reset(); }
    @Override public void onDeactivate() { reset(); }

    // ── Reset ─────────────────────────────────────────────────────────────────

    private void reset() {
        if (swapBack.get() && prevSlot != -1) {
            InvUtils.swap(prevSlot, false);
        }
        mode             = State.Idle;
        currentPos       = null;
        currentDir       = null;
        timer            = 0;
        restorationCount = 0;
        placeTarget      = null;
        placeSide        = null;
        prevSlot         = -1;
        burstCyclesDone  = 0;
        isRepairing      = false;
        repairTimer      = 0;
        pendingAdvance   = false;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // ── Drain any packet-triggered advance first ──────────────────────────
        // onPacket() sets pendingAdvance instead of calling advanceBurst() directly.
        // By draining it here at the top of the tick, the resulting state change
        // (mode = Placing, timer = delay) feeds straight into the state machine
        // loop below — eliminating the mandatory 1-tick gap between break
        // confirmation and the next place.
        if (pendingAdvance) {
            pendingAdvance = false;
            advanceBurst();
            // If advanceBurst() reset the module (burst complete), stop here.
            if (mode == State.Idle) return;
        }

        // ── Auto-repair gate ──────────────────────────────────────────────────
        if (autoRepair.get()) {
            FindItemResult pickaxe = findPickaxe();
            boolean needsRepair = pickaxe.found()
                && mc.player.getInventory().getStack(pickaxe.slot()).isDamaged()
                && (mc.player.getInventory().getStack(pickaxe.slot()).getMaxDamage()
                    - mc.player.getInventory().getStack(pickaxe.slot()).getDamage()
                    <= repairThreshold.get());

            if (needsRepair && !isRepairing)       isRepairing = true;
            else if (!needsRepair && isRepairing) { isRepairing = false; info("Pickaxe repaired."); }

            if (isRepairing) {
                handleRepair(pickaxe);
                return;
            }
        }

        // ── Tick-down ─────────────────────────────────────────────────────────
        if (timer > 0) {
            timer--;
            if (timer > 0) return;

            // Timer expired — handle states that use it as a timeout
            if (mode == State.WaitingBreak) {
                handleRestoration();
                if (timer > 0) return;
            } else if (mode == State.PlacingConfirm) {
                // Place confirmation never arrived — server likely rejected the place.
                // Fall back to MiningStart; if the block isn't there the mine will fail
                // and handleRestoration will catch it.
                mode = State.MiningStart;
            }
        }

        // ── State-machine loop ────────────────────────────────────────────────
        // Chains states within a single tick when all delays are 0.
        for (int steps = 0; steps < 10; steps++) {
            if (timer > 0) break;
            State before = mode;
            runStateMachine();
            if (mode == before && mode != State.Idle) break;
            if (mode == State.Idle && before == State.Idle) break;
        }
    }

    private void runStateMachine() {
        switch (mode) {
            case Idle            -> handleIdle();
            case Placing         -> handlePlacing();
            case PlacingConfirm  -> { /* waiting for server's non-air packet at currentPos */ }
            case MiningStart     -> handleMiningStart();
            case MiningHold      -> { /* waiting for BlockUpdateS2CPacket */ }
            case WaitingBreak    -> { /* waiting for packet or tick timeout  */ }
            case SpeedMining     -> handleSpeedMining();
        }
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleIdle() {
        FindItemResult pickaxe = findPickaxe();
        if (!pickaxe.found()) return;

        if (!(mc.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (mc.player.squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return;

        if (prevSlot == -1) prevSlot = mc.player.getInventory().selectedSlot;

        // Case 1: existing ender chest – mine it directly
        if (mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
            currentPos       = pos;
            currentDir       = hit.getSide();
            placeTarget      = pos.offset(hit.getSide().getOpposite());
            placeSide        = hit.getSide();
            mode             = State.MiningStart;
            restorationCount = 0;
            burstCyclesDone  = 0;
            return;
        }

        // Case 2: place then mine
        FindItemResult echest = InvUtils.find(Items.ENDER_CHEST);
        if (!echest.found()) return;

        BlockPos placePos = pos.offset(hit.getSide());
        if (!canPlace(placePos)) return;

        currentPos       = placePos;
        currentDir       = getBreakDirection(placePos);
        mode             = State.Placing;
        restorationCount = 0;
        burstCyclesDone  = 0;
        placeTarget      = pos;
        placeSide        = hit.getSide();
    }

    private void handlePlacing() {
        FindItemResult echest = InvUtils.find(Items.ENDER_CHEST);
        if (!echest.found()) { reset(); return; }

        Runnable placeLogic = () -> {
            if (placeTarget == null || placeSide == null) return;

            InvUtils.swap(echest.slot(), false);

            boolean sneaking = !mc.player.isSneaking()
                && isClickable(mc.world.getBlockState(placeTarget).getBlock());
            if (sneaking) mc.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));

            BlockHitResult placeHit = new BlockHitResult(
                new Vec3d(
                    placeTarget.getX() + 0.5 + placeSide.getOffsetX() * 0.5,
                    placeTarget.getY() + 0.5 + placeSide.getOffsetY() * 0.5,
                    placeTarget.getZ() + 0.5 + placeSide.getOffsetZ() * 0.5),
                placeSide, placeTarget, false);

            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeHit);
            mc.player.swingHand(Hand.MAIN_HAND);

            if (sneaking) mc.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));

            // Wait for the server's non-air confirmation before arming mining.
            // This prevents the place-confirmation packet from being mistaken
            // for a restoration and triggering an instant break.
            // We use max(placeActionDelay, 1) so PlacingConfirm always has at
            // least one tick to receive the server packet before timing out.
            mode  = State.PlacingConfirm;
            timer = Math.max(placeActionDelay.get(), 1);
        };

        if (rotate.get()) Rotations.rotate(Rotations.getYaw(placeTarget), Rotations.getPitch(placeTarget), placeLogic);
        else              placeLogic.run();
    }

    private void handleMiningStart() {
        FindItemResult pickaxe = findPickaxe();
        if (!pickaxe.found()) { reset(); return; }

        // Swap to pickaxe before the rotation callback so the slot is correct
        // even if the rotation fires asynchronously.
        InvUtils.swap(pickaxe.slot(), false);

        Runnable mineLogic = () -> {
            if (currentPos == null || currentDir == null) return;

            if (mc.interactionManager.isBreakingBlock()) {
                mc.interactionManager.updateBlockBreakingProgress(currentPos, currentDir);
            } else {
                mc.interactionManager.attackBlock(currentPos, currentDir);
            }
            mc.player.swingHand(Hand.MAIN_HAND);

            switch (breakMode.get()) {
                case Instant -> {
                    mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentPos, currentDir));
                    // Use pendingAdvance so advanceBurst() runs at the top of the
                    // next tick inside the state-machine loop, not inside this callback.
                    // This keeps Instant mode consistent with Safe mode's packet path.
                    pendingAdvance = true;
                    mode = State.WaitingBreak;
                }
                case Custom -> {
                    mode  = State.SpeedMining;
                    timer = customBreakDelay.get();
                }
                case Safe -> {
                    mode  = State.MiningHold;
                    timer = 40; // Safety timeout; onPacket fires first on success
                }
            }
        };

        if (rotate.get()) Rotations.rotate(Rotations.getYaw(currentPos), Rotations.getPitch(currentPos), mineLogic);
        else              mineLogic.run();
    }

    private void handleSpeedMining() {
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentPos, currentDir));
        // Same pattern: queue the advance rather than calling directly
        pendingAdvance = true;
        mode = State.WaitingBreak;
    }

    // ── Core burst logic ──────────────────────────────────────────────────────

    /**
     * Called at the top of onTick() after draining pendingAdvance, OR directly
     * from handleRestoration(). Never called from inside a packet handler or
     * rotation callback — this keeps all state mutations on the tick thread.
     */
    private void advanceBurst() {
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.cancelBlockBreaking();

        burstCyclesDone++;

        if (burstCyclesDone >= burstCount.get()) {
            reset(); // swapBack happens here
        } else {
            restorationCount = 0;
            mode  = State.Placing;
            timer = delay.get();
        }
    }

    // ── Packet handler ────────────────────────────────────────────────────────

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (currentPos == null) return;

        if (event.packet instanceof BlockUpdateS2CPacket p) {
            if (p.getPos().equals(currentPos))
                handleBlockUpdate(p.getPos(), p.getState().isAir());

        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket p) {
            p.visitUpdates((pos, state) -> {
                if (pos.equals(currentPos))
                    handleBlockUpdate(pos, state.isAir());
            });
        }
    }

    /**
     * Called from the packet thread. Must NOT call advanceBurst() directly —
     * instead sets pendingAdvance so the tick thread picks it up immediately
     * at the start of the next onTick(), before the timer tick-down, feeding
     * it into the state machine loop with zero added latency.
     */
    private void handleBlockUpdate(BlockPos pos, boolean isAir) {
        if (currentPos == null || !pos.equals(currentPos)) return;

        if (isAir) {
            switch (mode) {
                case MiningStart, MiningHold, WaitingBreak -> {
                    // Break confirmed by server
                    pendingAdvance = true;
                    timer = 0;
                }
                default -> {}
            }
        } else {
            // Non-air packet: either a place confirmation or a restoration.
            switch (mode) {
                case PlacingConfirm -> {
                    // This is the expected server confirmation of our place.
                    // Now safe to arm mining — the block is definitely there.
                    mode  = State.MiningStart;
                    // timer was already set by handlePlacing; only override if
                    // the confirmation arrived before the timer expired.
                    if (timer == 0) timer = 0; // no-op, mining starts next loop
                }
                case WaitingBreak, MiningHold -> {
                    // Server pushed the block back — genuine restoration, retry.
                    handleRestoration();
                }
                case Placing -> {
                    // Stale packet from a previous cycle arriving late — ignore.
                    // (handlePlacing transitions to PlacingConfirm immediately,
                    //  so Placing + non-air means we haven't placed yet — safe to ignore.)
                }
                default -> {}
            }
        }
    }

    private void handleRestoration() {
        restorationCount++;
        if (restorationCount > 3) {
            error("Block restored too many times, aborting.");
            reset();
        } else {
            mode  = State.MiningStart;
            timer = delay.get();
        }
    }

    // ── Auto repair ───────────────────────────────────────────────────────────

    private void handleRepair(FindItemResult pickaxe) {
        if (repairTimer > 0) { repairTimer--; return; }

        if (!pickaxe.isMainHand()) InvUtils.swap(pickaxe.slot(), false);

        if (!mc.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            FindItemResult xp = InvUtils.find(Items.EXPERIENCE_BOTTLE);
            if (!xp.found()) {
                error("No XP bottles found. Disabling auto-repair.");
                autoRepair.set(false);
                isRepairing = false;
                return;
            }
            InvUtils.move().from(xp.slot()).toOffhand();
            repairTimer = 2;
            return;
        }

        Rotations.rotate(mc.player.getYaw(), 90, () -> {
            for (int i = 0; i < repairPacketsPerBurst.get(); i++) {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            }
        });
        repairTimer = repairBurstDelay.get();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FindItemResult findPickaxe() {
        FindItemResult r = InvUtils.find(Items.NETHERITE_PICKAXE);
        return r.found() ? r : InvUtils.find(Items.DIAMOND_PICKAXE);
    }

    private Direction getBreakDirection(BlockPos pos) {
        Vec3d diff = mc.player.getEyePos().subtract(pos.toCenterPos());
        return Direction.getFacing(diff.x, diff.y, diff.z);
    }

    private boolean canPlace(BlockPos pos) {
        if (!World.isValid(pos)) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!mc.world.getFluidState(pos).isEmpty()) return false;
        if (mc.world.isOutOfHeightLimit(pos)) return false;
        return mc.world.canPlace(Blocks.ENDER_CHEST.getDefaultState(), pos,
            net.minecraft.block.ShapeContext.absent());
    }

    private boolean isClickable(Block block) {
        return block instanceof net.minecraft.block.CraftingTableBlock
            || block instanceof net.minecraft.block.AnvilBlock
            || block instanceof net.minecraft.block.BlockWithEntity
            || block instanceof net.minecraft.block.BedBlock
            || block instanceof net.minecraft.block.FenceGateBlock
            || block instanceof net.minecraft.block.DoorBlock
            || block instanceof net.minecraft.block.TrapdoorBlock
            || block == Blocks.CHEST
            || block == Blocks.ENDER_CHEST;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || currentPos == null) return;
        event.renderer.box(currentPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }
}