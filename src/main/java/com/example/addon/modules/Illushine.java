package com.example.addon.modules;

import java.util.HashSet;
import java.util.Set;

import com.example.addon.HuntingUtilities;
import com.example.addon.utils.GlowingRegistry;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;

public class Illushine extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════════════════

    private enum MobCategory { PASSIVE, NEUTRAL, HOSTILE }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPassive = settings.createGroup("Passive");
    private final SettingGroup sgNeutral = settings.createGroup("Neutral");
    private final SettingGroup sgHostile = settings.createGroup("Hostile");

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
        .description("Glow color for passive mobs.")
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
        .description("Glow color for neutral mobs.")
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
        .description("Glow color for hostile mobs.")
        .defaultValue(new SettingColor(255, 50, 50, 255))
        .visible(highlightHostile::get)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final Set<Integer> highlightedEntities = new HashSet<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public Illushine() {
        super(HuntingUtilities.CATEGORY, "illushine", "Highlights mobs with a glow outline by hostility type.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        highlightedEntities.clear();
    }

    @Override
    public void onDeactivate() {
        if (mc.world != null) {
            for (int id : highlightedEntities) {
                GlowingRegistry.remove(id);
                Entity entity = mc.world.getEntityById(id);
                if (entity != null) clearEntityTeam(entity);
            }
        }
        highlightedEntities.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event Handler
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        Set<Integer> currentlyVisible = new HashSet<>();

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

            currentlyVisible.add(mob.getId());
            highlightedEntities.add(mob.getId());
            GlowingRegistry.add(mob.getId());
            setEntityTeam(mob, getNearestColor(color));
        }

        highlightedEntities.removeIf(id -> {
            if (!currentlyVisible.contains(id)) {
                GlowingRegistry.remove(id);
                Entity entity = mc.world.getEntityById(id);
                if (entity != null) clearEntityTeam(entity);
                return true;
            }
            return false;
        });
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
    // Team / Colour Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Formatting getNearestColor(SettingColor color) {
        Formatting best   = Formatting.WHITE;
        double    minDist = Double.MAX_VALUE;
        for (Formatting f : Formatting.values()) {
            if (!f.isColor()) continue;
            Integer rgb = f.getColorValue();
            if (rgb == null) continue;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >>  8) & 0xFF;
            int b =  rgb        & 0xFF;
            double dist = Math.pow(r - color.r, 2)
                        + Math.pow(g - color.g, 2)
                        + Math.pow(b - color.b, 2);
            if (dist < minDist) { minDist = dist; best = f; }
        }
        return best;
    }

    private void setEntityTeam(Entity entity, Formatting color) {
        Scoreboard scoreboard = mc.world.getScoreboard();
        String     teamName   = "illushine_" + color.getName();
        Team       team       = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
            team.setColor(color);
        }
        if (entity.getScoreboardTeam() == null
                || !entity.getScoreboardTeam().getName().equals(teamName)) {
            scoreboard.addScoreHolderToTeam(entity.getNameForScoreboard(), team);
        }
    }

    private void clearEntityTeam(Entity entity) {
        Scoreboard scoreboard   = mc.world.getScoreboard();
        Team       currentTeam  = entity.getScoreboardTeam();
        if (currentTeam != null && currentTeam.getName().startsWith("illushine_")) {
            scoreboard.removeScoreHolderFromTeam(entity.getNameForScoreboard(), currentTeam);
        }
    }
}