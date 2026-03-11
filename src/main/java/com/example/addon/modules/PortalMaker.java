package com.example.addon.modules;

import java.util.ArrayList;
import java.util.List;

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
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PortalMaker extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgGlow    = settings.createGroup("Glow");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks to wait between placement actions.")
        .defaultValue(2).min(1).sliderRange(1, 12)
        .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Show remaining portal frame positions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the preview boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(80, 160, 255, 35))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(100, 180, 255, 255))
        .build()
    );

    private final Setting<Boolean> autoEnter = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-enter")
        .description("Automatically enter the portal after it is created.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useEnderPearl = sgGeneral.add(new BoolSetting.Builder()
        .name("use-ender-pearl")
        .description("Throw an ender pearl into the portal instead of walking in.")
        .defaultValue(false)
        .visible(autoEnter::get)
        .build()
    );

    private final Setting<Integer> finishDelay = sgGeneral.add(new IntSetting.Builder()
        .name("finish-delay")
        .description("Ticks to wait after lighting the portal before turning off.")
        .defaultValue(20).min(0).sliderMax(200)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Glow
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Integer> glowLayers = sgGlow.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("Number of bloom layers rendered around each preview block.")
        .defaultValue(4).min(1).sliderMax(8)
        .build()
    );

    private final Setting<Double> glowSpread = sgGlow.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.05).min(0.01).sliderMax(0.2)
        .build()
    );

    private final Setting<Integer> glowBaseAlpha = sgGlow.add(new IntSetting.Builder()
        .name("glow-base-alpha")
        .description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60).min(4).sliderMax(150)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    public final List<BlockPos> portalFramePositions = new ArrayList<>();
    private int     placementIndex    = 0;
    private int     tickTimer         = 0;
    private int     finishTimer       = 0;
    private boolean pearlThrown       = false;

    /** How many ticks we've been stuck at roughly the same position while walking to portal. */
    private int     stuckTicks        = 0;
    private Vec3d   lastPos           = null;
    private int     scaffoldCooldown  = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public PortalMaker() {
        super(HuntingUtilities.CATEGORY, "portal-maker", "Builds and lights a minimal Nether portal (10 obsidian).");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        portalFramePositions.clear();
        placementIndex   = 0;
        tickTimer        = 0;
        finishTimer      = 0;
        pearlThrown      = false;
        stuckTicks       = 0;
        lastPos          = null;
        scaffoldCooldown = 0;

        if (mc.player == null || mc.world == null) { toggle(); return; }

        if (!hasItemInHotbar(Items.OBSIDIAN)) {
            int total = countItem(Items.OBSIDIAN);
            if (total > 0) warning("Obsidian is in inventory but not hotbar!");
        }
        int obsidianCount = countItem(Items.OBSIDIAN);
        if (obsidianCount < 10) {
            error("Need at least 10 obsidian (found " + obsidianCount + ")");
            toggle();
            return;
        }

        if (!hasItem(Items.FLINT_AND_STEEL)) warning("No flint & steel found — light manually.");

        Direction facing = mc.player.getHorizontalFacing();
        Direction right  = facing.rotateYClockwise();

        BlockPos feet     = mc.player.getBlockPos();
        boolean  adjusted = false;

        if (!mc.world.getBlockState(feet.down()).isFullCube(mc.world, feet.down())) {
            feet     = feet.up();
            adjusted = true;
        }

        BlockPos origin = feet.offset(facing, 2).offset(right, -1);

        portalFramePositions.add(origin.offset(right, 1));
        portalFramePositions.add(origin.offset(right, 2));
        portalFramePositions.add(origin.up(1));
        portalFramePositions.add(origin.up(2));
        portalFramePositions.add(origin.up(3));
        portalFramePositions.add(origin.offset(right, 3).up(1));
        portalFramePositions.add(origin.offset(right, 3).up(2));
        portalFramePositions.add(origin.offset(right, 3).up(3));
        portalFramePositions.add(origin.offset(right, 1).up(4));
        portalFramePositions.add(origin.offset(right, 2).up(4));

        if (adjusted) {
            BlockPos stepPos = feet.offset(facing, 1);
            if (mc.world.getBlockState(stepPos).isReplaceable()) portalFramePositions.add(stepPos);
        }

        boolean blocked = portalFramePositions.stream()
            .anyMatch(p -> !mc.world.getBlockState(p).isReplaceable());
        if (blocked) { error("Portal area is obstructed. Move slightly and try again."); toggle(); return; }

        long existing = portalFramePositions.stream()
            .filter(p -> mc.world.getBlockState(p).getBlock() == Blocks.OBSIDIAN)
            .count();
        if (existing >= 9) {
            info("Portal frame looks complete → attempting to light it.");
            placementIndex = portalFramePositions.size();
        }

        selectHotbarItem(Items.OBSIDIAN);
        info("Building minimal Nether portal...");
    }

    @Override
    public void onDeactivate() {
        portalFramePositions.clear();
        placementIndex   = 0;
        tickTimer        = 0;
        stuckTicks       = 0;
        lastPos          = null;
        stopMovement();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tick
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Successfully inside the portal — done.
        if (isPlayerInPortal()) {
            stopMovement();
            toggle();
            return;
        }

        // Recalculate which blocks still need placing.
        placementIndex = portalFramePositions.size();
        for (int i = 0; i < portalFramePositions.size(); i++) {
            if (mc.world.getBlockState(portalFramePositions.get(i)).getBlock() != Blocks.OBSIDIAN) {
                placementIndex = i;
                break;
            }
        }

        // ── Phase 1: place obsidian ────────────────────────────────────────────
        if (placementIndex < portalFramePositions.size()) {
            if (!mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
                FindItemResult obsidian = InvUtils.find(Items.OBSIDIAN);
                if (!obsidian.found()) { error("No obsidian found → disabled."); toggle(); return; }
                if (obsidian.isHotbar()) mc.player.getInventory().selectedSlot = obsidian.slot();
                else InvUtils.move().from(obsidian.slot()).toHotbar(mc.player.getInventory().selectedSlot);
            }

            tickTimer++;
            if (tickTimer < placeDelay.get()) return;
            tickTimer = 0;

            BlockPos target = portalFramePositions.get(placementIndex);
            if (mc.world.getBlockState(target).getBlock() == Blocks.OBSIDIAN) { placementIndex++; return; }

            if (!mc.world.getBlockState(target).isReplaceable()) {
                mc.interactionManager.attackBlock(target, mc.player.getHorizontalFacing().getOpposite());
                mc.player.swingHand(Hand.MAIN_HAND);
                return;
            }

            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), () -> {
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(target), Direction.UP, target, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            });
            placementIndex++;
            return;
        }

        // ── Phase 2: light / enter ─────────────────────────────────────────────
        if (isPortalLit()) {
            if (autoEnter.get()) {
                moveToPortal();
                // Only start the timeout once the player is close enough that
                // they should be able to step in within finishDelay ticks.
                // Counting from too far away meant 1-second default expired
                // before the player even reached the portal.
                Vec3d portalCenter = getPortalCenter();
                Vec3d playerPos    = mc.player.getPos();
                double dx = portalCenter.x - playerPos.x;
                double dz = portalCenter.z - playerPos.z;
                if (dx * dx + dz * dz < 4.0) { // within 2 blocks
                    if (finishTimer++ >= finishDelay.get()) {
                        error("Failed to enter portal.");
                        toggle();
                    }
                }
            } else {
                if (finishTimer++ >= finishDelay.get()) {
                    info("PortalMaker finished.");
                    toggle();
                }
            }
        } else {
            finishTimer = 0;
            if (tickTimer++ >= 10) { lightPortal(); tickTimer = 0; }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Portal Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void lightPortal() {
        if (portalFramePositions.isEmpty()) return;
        if (!selectHotbarItem(Items.FLINT_AND_STEEL)) { warning("Cannot find flint & steel in hotbar."); return; }

        BlockPos bottom1 = portalFramePositions.get(0);
        BlockPos bottom2 = portalFramePositions.get(1);

        for (BlockPos pos : new BlockPos[]{bottom1, bottom2}) {
            if (mc.world.getBlockState(pos.up()).isAir()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, 0.5, 0), Direction.UP, pos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                });
                break;
            }
        }
    }

    private boolean isPortalLit() {
        if (portalFramePositions.size() < 2) return false;
        BlockPos p1 = portalFramePositions.get(0).up();
        BlockPos p2 = portalFramePositions.get(1).up();
        return mc.world.getBlockState(p1).getBlock() == Blocks.NETHER_PORTAL ||
               mc.world.getBlockState(p2).getBlock() == Blocks.NETHER_PORTAL;
    }

    /** True when the player's feet OR head block is inside a nether portal. */
    private boolean isPlayerInPortal() {
        BlockPos feet = mc.player.getBlockPos();
        return mc.world.getBlockState(feet).isOf(Blocks.NETHER_PORTAL) ||
               mc.world.getBlockState(feet.up()).isOf(Blocks.NETHER_PORTAL);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Portal Entry Movement  (completely rewritten)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Moves the player into the portal using a straightforward state machine:
     *
     *  1. If ender-pearl mode is enabled, throw a pearl and return.
     *  2. Compute a horizontal approach vector toward the portal center.
     *  3. Face that direction via Rotations.rotate.
     *  4. Walk forward, sprint when far away, stop sprinting when close.
     *  5. Jump over any solid block immediately in front at foot level.
     *  6. Bridge a gap (scaffold) if we're about to step into air.
     *  7. Track "stuck" ticks; if stuck for too long, jump to break free.
     *  8. Abort if we fell far below the portal.
     */
    private void moveToPortal() {
        if (portalFramePositions.size() < 2 || mc.player == null || mc.world == null) return;

        // ── Ender pearl fast-path ──────────────────────────────────────────────
        if (useEnderPearl.get()) {
            if (!pearlThrown && selectHotbarItem(Items.ENDER_PEARL)) {
                Vec3d target = getPortalCenter().add(0, 1.5, 0);
                Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), () -> {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.swingHand(Hand.MAIN_HAND);
                });
                pearlThrown = true;
            }
            return;
        }

        Vec3d portalCenter = getPortalCenter();
        Vec3d playerPos    = mc.player.getPos();

        // ── Fell-off guard ─────────────────────────────────────────────────────
        if (playerPos.y < portalCenter.y - 4.0) {
            error("Fell too far below the portal — stopping.");
            stopMovement();
            toggle();
            return;
        }

        // ── Stuck detection ────────────────────────────────────────────────────
        if (lastPos != null && lastPos.squaredDistanceTo(playerPos) < 0.001) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPos = playerPos;

        // ── Horizontal distance & alignment ───────────────────────────────────
        double dx        = portalCenter.x - playerPos.x;
        double dz        = portalCenter.z - playerPos.z;
        double hDistSq   = dx * dx + dz * dz;
        double hDist     = Math.sqrt(hDistSq);

        // Target yaw pointing straight at the portal center (horizontal only).
        float targetYaw  = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawDiff    = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        boolean aligned  = Math.abs(yawDiff) < 30f;   // tighter than original — avoids diagonal drift

        // Always face the portal.
        Rotations.rotate(targetYaw, mc.player.getPitch());

        // ── Already inside portal blocks? ─────────────────────────────────────
        if (isPlayerInPortal()) {
            stopMovement();
            return;
        }

        // ── Close enough — just press forward gently ──────────────────────────
        if (hDist < 0.6) {
            mc.options.sprintKey.setPressed(false);
            mc.options.forwardKey.setPressed(true);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            return;
        }

        // ── Check what's directly ahead along the TARGET approach vector ────────
        // We must NOT use mc.player.getHorizontalFacing() here — that reflects
        // the body's current facing which lags behind the Rotations.rotate target
        // and can land on the obsidian frame column rather than the portal opening,
        // triggering the obstacle-jump loop every tick.
        // Instead, quantise the approach direction to the nearest cardinal so
        // footFront always looks into the portal opening, not the frame pillar.
        Direction approachDir = directionFromVector(dx, dz);
        BlockPos  footFront      = mc.player.getBlockPos().offset(approachDir);
        BlockPos  headFront      = footFront.up();
        BlockPos  footFrontBelow = footFront.down();

        // Never treat nether portal blocks as obstacles — walking into them IS the goal.
        boolean footBlocked = isHardObstacle(footFront);
        boolean headBlocked = isHardObstacle(headFront);
        boolean gapAhead    = !isHardObstacle(footFront) && !isHardObstacle(footFrontBelow)
                           && !mc.world.getBlockState(footFrontBelow).isOf(Blocks.NETHER_PORTAL);

        // ── Scaffold into the gap if needed ───────────────────────────────────
        if (scaffoldCooldown > 0) scaffoldCooldown--;

        if (aligned && gapAhead && mc.player.isOnGround() && scaffoldCooldown == 0) {
            // Sneak so we don't walk off, then place a bridge block.
            mc.options.sneakKey.setPressed(true);
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);

            if (tryScaffoldPlace(footFrontBelow)) {
                scaffoldCooldown = placeDelay.get() + 2;
            } else {
                // No blocks to bridge with — just walk straight through (gap might be 1 wide).
                mc.options.sneakKey.setPressed(false);
                mc.options.forwardKey.setPressed(true);
            }
            return;
        }
        mc.options.sneakKey.setPressed(false);

        // ── Jump over a single-block obstacle at foot level ───────────────────
        if (aligned && footBlocked && !headBlocked && mc.player.isOnGround()) {
            mc.options.sprintKey.setPressed(false);
            mc.options.forwardKey.setPressed(true);
            mc.player.jump();
            return;
        }

        // ── Stuck recovery — try jumping ──────────────────────────────────────
        if (stuckTicks > 8 && mc.player.isOnGround()) {
            mc.player.jump();
            stuckTicks = 0;
        }

        // ── Normal walk / sprint ──────────────────────────────────────────────
        boolean sprinting = hDist > 3.0 && aligned && !footBlocked;

        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.sprintKey.setPressed(sprinting);
        mc.options.forwardKey.setPressed(aligned);
    }

    /** Returns the cardinal Direction closest to the (dx, dz) approach vector. */
    private Direction directionFromVector(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    /**
     * Returns true only for blocks that are genuinely solid obstacles —
     * explicitly excludes nether portal blocks and any replaceable block.
     * Nether portal blocks must never be treated as obstacles because
     * walking into them is the goal of the entire enter-portal phase.
     */
    private boolean isHardObstacle(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        if (state.isAir() || state.isReplaceable()) return false;
        if (state.isOf(Blocks.NETHER_PORTAL))       return false;
        return true;
    }

    /** Returns the XZ centre of the two bottom portal interior blocks at their Y level. */
    private Vec3d getPortalCenter() {
        BlockPos p1 = portalFramePositions.get(0).up();
        BlockPos p2 = portalFramePositions.get(1).up();
        return new Vec3d(
            (p1.getX() + p2.getX()) / 2.0 + 0.5,
             p1.getY(),
            (p1.getZ() + p2.getZ()) / 2.0 + 0.5
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Block Placement Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean placeBlock(BlockPos pos) {
        BlockPos  neighbor  = null;
        Direction placeSide = null;
        for (Direction side : Direction.values()) {
            BlockPos check = pos.offset(side);
            if (!mc.world.getBlockState(check).isReplaceable()) { neighbor = check; placeSide = side.getOpposite(); break; }
        }
        if (neighbor == null) return false;

        final BlockPos  finalNeighbor  = neighbor;
        final Direction finalPlaceSide = placeSide;

        if (!mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
            FindItemResult obsidian = InvUtils.find(Items.OBSIDIAN);
            if (!obsidian.found()) return false;
            if (obsidian.isHotbar()) mc.player.getInventory().selectedSlot = obsidian.slot();
            else InvUtils.move().from(obsidian.slot()).toHotbar(mc.player.getInventory().selectedSlot);
        }

        Rotations.rotate(Rotations.getYaw(finalNeighbor), Rotations.getPitch(finalNeighbor), () -> {
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(finalNeighbor), finalPlaceSide, finalNeighbor, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
        });
        return true;
    }

    private void stopMovement() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    private boolean tryScaffoldPlace(BlockPos pos) {
        Direction[] order = {
            Direction.DOWN,
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
            Direction.UP
        };

        BlockPos  neighbor  = null;
        Direction placeSide = null;
        for (Direction side : order) {
            BlockPos check = pos.offset(side);
            if (!mc.world.getBlockState(check).isReplaceable()) { neighbor = check; placeSide = side.getOpposite(); break; }
        }
        if (neighbor == null) return false;

        if (!mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
            FindItemResult obsidian = InvUtils.find(Items.OBSIDIAN);
            if (!obsidian.found()) return false;
            if (obsidian.isHotbar()) mc.player.getInventory().selectedSlot = obsidian.slot();
            else InvUtils.move().from(obsidian.slot()).toHotbar(mc.player.getInventory().selectedSlot);
        }

        final BlockPos  finalNeighbor  = neighbor;
        final Direction finalPlaceSide = placeSide;

        Rotations.rotate(
            Rotations.getYaw(Vec3d.ofCenter(finalNeighbor)),
            Rotations.getPitch(Vec3d.ofCenter(finalNeighbor)),
            () -> {
                BlockHitResult hit = new BlockHitResult(
                    Vec3d.ofCenter(finalNeighbor).offset(finalPlaceSide, 0.5),
                    finalPlaceSide, finalNeighbor, false
                );
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        );
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || portalFramePositions.isEmpty()) return;

        for (int i = placementIndex; i < portalFramePositions.size(); i++) {
            BlockPos pos = portalFramePositions.get(i);
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;

            Box box = new Box(pos);
            renderGlowLayers(event, box, lineColor.get());
            event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bloom Rendering
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderGlowLayers(Render3DEvent event, Box box, SettingColor color) {
        int    layers    = glowLayers.get();
        double spread    = glowSpread.get();
        int    baseAlpha = glowBaseAlpha.get();

        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            int    layerAlpha = Math.max(4, (int) (baseAlpha * (1.0 - (double)(i - 1) / layers)));
            event.renderer.box(
                box.expand(expansion),
                withAlpha(color, layerAlpha),
                withAlpha(color, 0),
                ShapeMode.Sides, 0
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Color Helper
    // ═══════════════════════════════════════════════════════════════════════════

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inventory Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean selectHotbarItem(Item targetItem) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == targetItem) {
                mc.player.getInventory().selectedSlot = i;
                return true;
            }
        }
        return false;
    }

    private boolean hasItemInHotbar(Item targetItem) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == targetItem) return true;
        }
        return false;
    }

    private int countItem(Item targetItem) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == targetItem)
                count += mc.player.getInventory().getStack(i).getCount();
        }
        return count;
    }

    private boolean hasItem(Item targetItem) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == targetItem) return true;
        }
        return false;
    }
}