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
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Box;

public class Mobanom extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgColors     = settings.createGroup("Colors");
    private final SettingGroup sgItemAnomaly = settings.createGroup("Item Anomaly");
    private final SettingGroup sgGlow       = settings.createGroup("Glow");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> chatNotification = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notification")
        .description("Notify in chat when an anomaly is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> ignoredEntities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("ignored-entities")
        .description("Entities to ignore.")
        .defaultValue(new HashSet<>())
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("The range to detect anomalies.")
        .defaultValue(128)
        .min(1)
        .sliderMax(256)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Colors
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<SettingColor> overworldLineColor = sgColors.add(new ColorSetting.Builder()
        .name("overworld-line-color")
        .description("Glow color for Overworld mobs in other dimensions.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> netherLineColor = sgColors.add(new ColorSetting.Builder()
        .name("nether-line-color")
        .description("Glow color for Nether mobs in other dimensions.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> endLineColor = sgColors.add(new ColorSetting.Builder()
        .name("end-line-color")
        .description("Glow color for End mobs in other dimensions.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Item Anomaly
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> detectUnnaturalItems = sgItemAnomaly.add(new BoolSetting.Builder()
        .name("detect-unnatural-items")
        .description("Detects mobs holding or wearing player-like items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> itemAnomalyLineColor = sgItemAnomaly.add(new ColorSetting.Builder()
        .name("item-anomaly-line-color")
        .description("The glow color for mobs with unnatural items.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .visible(detectUnnaturalItems::get)
        .build()
    );

    private final Setting<Boolean> detectPumpkins = sgItemAnomaly.add(new BoolSetting.Builder()
        .name("detect-pumpkins")
        .description("Detects mobs wearing carved pumpkins or jack-o'-lanterns.")
        .defaultValue(true)
        .visible(detectUnnaturalItems::get)
        .build()
    );

    private final Setting<Boolean> detectChestedAnimals = sgItemAnomaly.add(new BoolSetting.Builder()
        .name("detect-chested-animals")
        .description("Detects animals (donkeys, llamas, etc.) carrying a chest.")
        .defaultValue(true)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Glow
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Integer> glowLayers = sgGlow.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("Number of bloom layers rendered around each mob.")
        .defaultValue(4)
        .min(1)
        .sliderMax(8)
        .build()
    );

    private final Setting<Double> glowSpread = sgGlow.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.05)
        .min(0.01)
        .sliderMax(0.2)
        .build()
    );

    private final Setting<Integer> glowBaseAlpha = sgGlow.add(new IntSetting.Builder()
        .name("glow-base-alpha")
        .description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60)
        .min(10)
        .sliderMax(150)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Dimension Sets
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Set<EntityType<?>> NETHER_NATIVES = Set.of(
        EntityType.GHAST, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
        EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.MAGMA_CUBE, EntityType.BLAZE,
        EntityType.WITHER_SKELETON, EntityType.SKELETON, EntityType.ENDERMAN, EntityType.STRIDER
    );

    private static final Set<EntityType<?>> END_NATIVES = Set.of(
        EntityType.ENDERMAN, EntityType.SHULKER, EntityType.ENDER_DRAGON
    );

    private static final Set<EntityType<?>> OVERWORLD_ANOMALIES = Set.of(
        EntityType.GHAST, EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.MAGMA_CUBE,
        EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN,
        EntityType.STRIDER, EntityType.SHULKER, EntityType.ENDER_DRAGON,
        EntityType.ZOMBIFIED_PIGLIN, EntityType.WITHER
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final Set<Integer> notifiedEntities   = new HashSet<>();
    private final Set<Integer> highlightedEntities = new HashSet<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public Mobanom() {
        super(HuntingUtilities.CATEGORY, "mobanom", "Detects and highlights mobs in the wrong dimension or with unnatural items.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        notifiedEntities.clear();
        highlightedEntities.clear();
    }

    @Override
    public void onDeactivate() {
        notifiedEntities.clear();
        highlightedEntities.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        highlightedEntities.clear();
        notifiedEntities.removeIf(id -> mc.world.getEntityById(id) == null);

        String dim = mc.world.getRegistryKey().getValue().toString();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof MobEntity mob)) continue;
            if (ignoredEntities.get().contains(mob.getType())) continue;
            if (mc.player.distanceTo(mob) > range.get()) continue;

            boolean isDimensionAnomaly = isAnomaly(mob.getType(), dim);
            boolean isItemAnomaly      = detectUnnaturalItems.get() && hasUnnaturalItems(mob);
            boolean isChestedAnomaly   = detectChestedAnimals.get() && hasChestAttachment(mob);

            if (isDimensionAnomaly || isItemAnomaly || isChestedAnomaly) {
                highlightedEntities.add(mob.getId());

                if (chatNotification.get() && notifiedEntities.add(mob.getId())) {
                    if (isChestedAnomaly)      info("Chested animal detected: " + mob.getType().getName().getString());
                    else if (isItemAnomaly)    info("Item anomaly detected: "   + mob.getType().getName().getString());
                    else                       info("Dimension anomaly detected: " + mob.getType().getName().getString());
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || highlightedEntities.isEmpty()) return;

        for (int entityId : highlightedEntities) {
            Entity entity = mc.world.getEntityById(entityId);
            if (entity == null || !(entity instanceof MobEntity mob)) continue;

            boolean isItemAnomaly      = detectUnnaturalItems.get() && hasUnnaturalItems(mob);
            boolean isChestedAnomaly   = detectChestedAnimals.get() && hasChestAttachment(mob);

            SettingColor color = getColorForAnomaly(mob, isItemAnomaly || isChestedAnomaly);

            renderGlowLayers(event, mob.getBoundingBox(), color);
            event.renderer.box(mob.getBoundingBox(), withAlpha(color, 0), color, ShapeMode.Lines, 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bloom Rendering
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Renders layered expanding filled boxes around the given bounding box to
     * simulate a soft bloom/glow halo. Outermost layers are most transparent,
     * innermost are most opaque.
     */
    private void renderGlowLayers(Render3DEvent event, Box box, SettingColor color) {
        int    layers    = glowLayers.get();
        double spread    = glowSpread.get();
        int    baseAlpha = glowBaseAlpha.get();

        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            int layerAlpha   = Math.max(4, (int) (baseAlpha * (1.0 - (double)(i - 1) / layers)));
            Box expanded     = box.expand(expansion);
            event.renderer.box(expanded, withAlpha(color, layerAlpha), withAlpha(color, 0), ShapeMode.Sides, 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Detection Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isAnomaly(EntityType<?> type, String dimension) {
        return switch (dimension) {
            case "minecraft:the_nether" -> !NETHER_NATIVES.contains(type);
            case "minecraft:the_end"    -> !END_NATIVES.contains(type);
            case "minecraft:overworld"  -> OVERWORLD_ANOMALIES.contains(type);
            default                     -> false;
        };
    }

    private boolean hasUnnaturalItems(MobEntity mob) {
        for (ItemStack stack : mob.getArmorItems()) {
            if (isUnnatural(stack)) return true;
        }
        if (isUnnatural(mob.getMainHandStack())) return true;
        if (isUnnatural(mob.getOffHandStack()))  return true;
        return false;
    }

    private boolean isUnnatural(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();

        if (item == Items.ELYTRA) return true;
        if (item instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) return true;
        if (detectPumpkins.get() && (item == Items.CARVED_PUMPKIN || item == Items.JACK_O_LANTERN)) return true;

        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) return false;

        if (item.toString().startsWith("netherite_")) return true;

        for (RegistryEntry<Enchantment> enchantmentEntry : enchants.getEnchantments()) {
            Enchantment enchantment = enchantmentEntry.value();
            if (enchantment == null) continue;
            int level = enchants.getLevel(enchantmentEntry);
            if (enchantmentEntry.matchesKey(Enchantments.MENDING)) return true;
            if (level > enchantment.getMaxLevel()) return true;
        }

        return false;
    }

    private boolean hasChestAttachment(MobEntity mob) {
        if (mob instanceof AbstractDonkeyEntity donkey) return donkey.hasChest();
        if (mob instanceof LlamaEntity llama)           return llama.hasChest();
        return false;
    }

    private SettingColor getColorForAnomaly(MobEntity mob, boolean isItemAnomaly) {
        if (isItemAnomaly)                          return itemAnomalyLineColor.get();
        if (END_NATIVES.contains(mob.getType()))    return endLineColor.get();
        if (NETHER_NATIVES.contains(mob.getType())) return netherLineColor.get();
        return overworldLineColor.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Color Helper
    // ═══════════════════════════════════════════════════════════════════════════

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }
}