package com.example.addon.modules;

import java.util.HashSet;
import java.util.Set;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.util.math.Box;

public class Illushine extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════════════════

    private enum MobCategory { PASSIVE, NEUTRAL, HOSTILE }

    public enum CrosshairMode {
        None("None"),
        WhiteDot("White Dot"),
        Normal("Normal");

        private final String title;
        CrosshairMode(String title) { this.title = title; }

        @Override
        public String toString() { return title; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgCrosshair = settings.createGroup("Crosshair");
    private final SettingGroup sgPassive   = settings.createGroup("Passive");
    private final SettingGroup sgNeutral   = settings.createGroup("Neutral");
    private final SettingGroup sgHostile   = settings.createGroup("Hostile");
    private final SettingGroup sgGlow      = settings.createGroup("Glow");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range to highlight mobs.")
        .defaultValue(64)
        .min(1)
        .sliderMax(256)
        .build()
    );

    private final Setting<Set<EntityType<?>>> ignoredEntities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("ignored-entities")
        .description("Entities to ignore.")
        .defaultValue(new HashSet<>())
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Crosshair
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<CrosshairMode> crosshairMode = sgCrosshair.add(new EnumSetting.Builder<CrosshairMode>()
        .name("crosshair-mode")
        .description("The crosshair style to display while Illushine is active.")
        .defaultValue(CrosshairMode.Normal)
        .build()
    );

    private final Setting<SettingColor> crosshairColor = sgCrosshair.add(new ColorSetting.Builder()
        .name("crosshair-color")
        .description("Color of the Normal crosshair lines.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> crosshairMode.get() == CrosshairMode.Normal)
        .build()
    );

    private final Setting<Integer> crosshairSize = sgCrosshair.add(new IntSetting.Builder()
        .name("crosshair-size")
        .description("Half-length of each crosshair arm in pixels.")
        .defaultValue(5)
        .min(1)
        .sliderMax(20)
        .visible(() -> crosshairMode.get() == CrosshairMode.Normal)
        .build()
    );

    private final Setting<Integer> crosshairGap = sgCrosshair.add(new IntSetting.Builder()
        .name("crosshair-gap")
        .description("Gap (in pixels) between center and each arm.")
        .defaultValue(2)
        .min(0)
        .sliderMax(10)
        .visible(() -> crosshairMode.get() == CrosshairMode.Normal)
        .build()
    );

    private final Setting<Integer> crosshairThickness = sgCrosshair.add(new IntSetting.Builder()
        .name("crosshair-thickness")
        .description("Thickness of the crosshair lines in pixels.")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .visible(() -> crosshairMode.get() == CrosshairMode.Normal)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Passive
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> highlightPassive = sgPassive.add(new BoolSetting.Builder()
        .name("highlight-passive")
        .description("Highlight passive mobs (cows, pigs, sheep, etc.).")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> passiveColor = sgPassive.add(new ColorSetting.Builder()
        .name("passive-color")
        .description("Outline color for passive mobs.")
        .defaultValue(new SettingColor(0, 255, 100, 255))
        .visible(highlightPassive::get)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Neutral
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> highlightNeutral = sgNeutral.add(new BoolSetting.Builder()
        .name("highlight-neutral")
        .description("Highlight neutral mobs (wolves, bees, endermen, etc.).")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> neutralColor = sgNeutral.add(new ColorSetting.Builder()
        .name("neutral-color")
        .description("Outline color for neutral mobs.")
        .defaultValue(new SettingColor(255, 200, 0, 255))
        .visible(highlightNeutral::get)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Hostile
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> highlightHostile = sgHostile.add(new BoolSetting.Builder()
        .name("highlight-hostile")
        .description("Highlight hostile mobs (zombies, skeletons, creepers, etc.).")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> hostileColor = sgHostile.add(new ColorSetting.Builder()
        .name("hostile-color")
        .description("Outline color for hostile mobs.")
        .defaultValue(new SettingColor(255, 50, 50, 255))
        .visible(highlightHostile::get)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Glow
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> glowEnabled = sgGlow.add(new BoolSetting.Builder()
        .name("glow")
        .description("Render a bloom halo around each mob in addition to the outline.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> glowLayers = sgGlow.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("Number of bloom layers rendered around each mob.")
        .defaultValue(4)
        .min(1)
        .sliderMax(8)
        .visible(glowEnabled::get)
        .build()
    );

    private final Setting<Double> glowSpread = sgGlow.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.05)
        .min(0.01)
        .sliderMax(0.2)
        .visible(glowEnabled::get)
        .build()
    );

    private final Setting<Integer> glowBaseAlpha = sgGlow.add(new IntSetting.Builder()
        .name("glow-base-alpha")
        .description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60)
        .min(10)
        .sliderMax(150)
        .visible(glowEnabled::get)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    // Entities whose team color we have overridden this tick.
    // Cleared and rebuilt every tick so we never hold stale references.
    private final Set<Integer> activelyOutlined = new HashSet<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public Illushine() {
        super(HuntingUtilities.CATEGORY, "illushine", "Highlights mobs with a glow outline by hostility type.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public Getters
    // ═══════════════════════════════════════════════════════════════════════════

    public CrosshairMode getCrosshairMode() {
        return crosshairMode.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        activelyOutlined.clear();
    }

    @Override
    public void onDeactivate() {
        // Clear the glowing flag from every entity we were highlighting so
        // they don't stay lit up after the module is toggled off.
        if (mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (activelyOutlined.contains(entity.getId())) {
                    entity.setGlowing(false);
                }
            }
        }
        activelyOutlined.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tick — apply / remove glowing flag and team color each tick
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        Set<Integer> newlyActive = new HashSet<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof MobEntity mob)) continue;
            if (ignoredEntities.get().contains(mob.getType())) continue;
            if (mc.player.distanceTo(mob) > range.get()) continue;

            MobCategory category = categorise(mob);
            boolean shouldHighlight = switch (category) {
                case PASSIVE -> highlightPassive.get();
                case NEUTRAL -> highlightNeutral.get();
                case HOSTILE -> highlightHostile.get();
            };
            if (!shouldHighlight) continue;

            SettingColor color = switch (category) {
                case PASSIVE -> passiveColor.get();
                case NEUTRAL -> neutralColor.get();
                case HOSTILE -> hostileColor.get();
            };

            // Enable vanilla glowing effect on the entity.
            // Meteor's OutlineEntityFeatureRenderer reads entity.isGlowing() and
            // entity.getTeamColorValue() to decide the outline color, so we set
            // both here on the client side — no packet is sent to the server.
            mob.setGlowing(true);

            // Override the outline color by temporarily assigning the entity to a
            // synthetic Meteor-managed color team. Meteor's built-in ESP modules
            // (e.g. Entities, Players) use this same path via
            // RenderUtils.getOutlineColor / team color injection.
            // The color is re-applied every tick so it stays in sync with setting changes.
            setOutlineColor(mob, color);

            newlyActive.add(mob.getId());
        }

        // Remove glowing from entities that left range or were toggled off.
        for (int id : activelyOutlined) {
            if (!newlyActive.contains(id)) {
                Entity e = mc.world.getEntityById(id);
                if (e != null) e.setGlowing(false);
            }
        }

        activelyOutlined.clear();
        activelyOutlined.addAll(newlyActive);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Render — optional bloom halo behind the silhouette
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!glowEnabled.get() || mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!activelyOutlined.contains(entity.getId())) continue;
            if (!(entity instanceof MobEntity mob)) continue;

            MobCategory category = categorise(mob);
            SettingColor color = switch (category) {
                case PASSIVE -> passiveColor.get();
                case NEUTRAL -> neutralColor.get();
                case HOSTILE -> hostileColor.get();
            };

            renderGlowLayers(event, mob.getBoundingBox(), color);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Outline color injection
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Meteor's outline shader resolves the outline color by calling
     * {@code entity.getTeamColorValue()}, which returns the integer RGB of the
     * entity's scoreboard team color, or white (0xFFFFFF) if the entity has no
     * team. We can't reassign scoreboard teams at runtime without sending server
     * packets, so instead we hook into Meteor's own color override system:
     *
     * {@code meteordevelopment.meteorclient.utils.render.RenderUtils} keeps a
     * static {@code Map<Integer, Color> entityOutlineColors} that the
     * OutlineEntityFeatureRenderer checks BEFORE falling back to the team color.
     * Writing into that map here gives us full per-entity color control with
     * no side effects and no server interaction.
     *
     * If the map doesn't exist in your version of Meteor (it was added in a
     * mid-2024 build), fall back to the setGlowing-only path — the outline will
     * appear in the vanilla team color (white by default), which is still
     * functionally correct.
     */
    private void setOutlineColor(Entity entity, SettingColor color) {
        try {
            var field = meteordevelopment.meteorclient.utils.render.RenderUtils.class
                .getDeclaredField("entityOutlineColors");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Integer, Color> map =
                (java.util.Map<Integer, Color>) field.get(null);
            if (map != null) {
                map.put(entity.getId(), new Color(color.r, color.g, color.b, color.a));
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Field not present in this Meteor build — glowing outline will use
            // the default team/white color instead. No action needed.
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bloom Rendering (box-space halo behind the silhouette outline)
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderGlowLayers(Render3DEvent event, Box box, SettingColor color) {
        int    layers    = glowLayers.get();
        double spread    = glowSpread.get();
        int    baseAlpha = glowBaseAlpha.get();

        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            // Quadratic falloff: bright at the mob surface, drops off sharply outward.
            double t          = (double)(i - 1) / layers;
            int    layerAlpha = Math.max(4, (int)(baseAlpha * (1.0 - t * t)));

            event.renderer.box(
                box.expand(expansion),
                withAlpha(color, layerAlpha),
                withAlpha(color, 0),
                ShapeMode.Sides, 0
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Crosshair Drawing (called from mixin with the real DrawContext)
    // ═══════════════════════════════════════════════════════════════════════════

    public void drawCrosshair(DrawContext context) {
        if (!mc.options.getPerspective().isFirstPerson()) return;
        if (mc.currentScreen != null) return;

        int cx = mc.getWindow().getScaledWidth()  / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;

        switch (crosshairMode.get()) {
            case WhiteDot -> context.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
            case Normal   -> drawNormalCrosshair(context, cx, cy);
            default       -> {}
        }
    }

    private void drawNormalCrosshair(DrawContext context, int cx, int cy) {
        int arm  = crosshairSize.get();
        int gap  = crosshairGap.get();
        int th   = crosshairThickness.get();
        int half = th / 2;
        int col  = toARGB(crosshairColor.get());

        // Horizontal left arm
        context.fill(cx - arm - gap, cy - half,      cx - gap,       cy - half + th, col);
        // Horizontal right arm
        context.fill(cx + gap,       cy - half,      cx + arm + gap, cy - half + th, col);
        // Vertical top arm
        context.fill(cx - half,      cy - arm - gap, cx - half + th, cy - gap,       col);
        // Vertical bottom arm
        context.fill(cx - half,      cy + gap,       cx - half + th, cy + arm + gap, col);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Categorisation
    // ═══════════════════════════════════════════════════════════════════════════

    private MobCategory categorise(MobEntity mob) {
        if (mob instanceof Angerable)     return MobCategory.NEUTRAL;
        if (mob instanceof HostileEntity) return MobCategory.HOSTILE;
        if (mob instanceof PassiveEntity) return MobCategory.PASSIVE;
        return MobCategory.HOSTILE;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Color Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    private static int toARGB(SettingColor c) {
        return (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
    }
}