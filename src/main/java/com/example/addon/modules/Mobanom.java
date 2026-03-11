package com.example.addon.modules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.addon.HuntingUtilities;
import com.example.addon.utils.GlowingRegistry;

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
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

public class Mobanom extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════════════════

    private enum AnomalyType {
        DIMENSION_NETHER,
        DIMENSION_END,
        DIMENSION_OVERWORLD,
        ITEM,
        CHESTED
    }

    public enum HighlightMode {
        Wireframe("Wireframe"),
        Spectral("Spectral");

        private final String title;
        HighlightMode(String title) { this.title = title; }

        @Override public String toString() { return title; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral     = settings.getDefaultGroup();
    private final SettingGroup sgColors      = settings.createGroup("Colors");
    private final SettingGroup sgItemAnomaly = settings.createGroup("Item Anomaly");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<HighlightMode> highlightMode = sgGeneral.add(new EnumSetting.Builder<HighlightMode>()
        .name("highlight-mode")
        .description("How anomalous mobs are outlined. Wireframe draws a box outline; Spectral uses the vanilla glow pipeline.")
        .defaultValue(HighlightMode.Wireframe)
        .build()
    );

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
        .defaultValue(128).min(1).sliderMax(256)
        .build()
    );

    // ── Highlight rendering ───────────────────────────────────────────────────

    private final Setting<Integer> glowLayers = sgGeneral.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("Number of bloom layers rendered around each mob.")
        .defaultValue(4).min(1).sliderMax(8)
        .visible(() -> highlightMode.get() == HighlightMode.Wireframe)
        .build()
    );

    private final Setting<Double> glowSpread = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.05).min(0.01).sliderMax(0.2)
        .visible(() -> highlightMode.get() == HighlightMode.Wireframe)
        .build()
    );

    private final Setting<Integer> glowBaseAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("glow-base-alpha")
        .description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60).min(10).sliderMax(150)
        .visible(() -> highlightMode.get() == HighlightMode.Wireframe)
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
    // Dimension anomaly sets
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Set<EntityType<?>> OVERWORLD_ANOMALIES = Set.of(
        EntityType.GHAST, EntityType.BLAZE, EntityType.WITHER_SKELETON,
        EntityType.MAGMA_CUBE, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
        EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.STRIDER,
        EntityType.SHULKER, EntityType.ENDER_DRAGON, EntityType.ZOMBIFIED_PIGLIN,
        EntityType.WITHER
    );

    private static final Set<EntityType<?>> NETHER_ANOMALIES = Set.of(
        EntityType.ENDER_DRAGON, EntityType.SHULKER, EntityType.COW, EntityType.PIG,
        EntityType.SHEEP, EntityType.CHICKEN, EntityType.HORSE, EntityType.DONKEY,
        EntityType.MULE, EntityType.RABBIT, EntityType.FOX, EntityType.WOLF,
        EntityType.OCELOT, EntityType.CAT, EntityType.POLAR_BEAR, EntityType.MOOSHROOM,
        EntityType.VILLAGER, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.BEE,
        EntityType.PANDA, EntityType.TURTLE, EntityType.DOLPHIN, EntityType.SQUID,
        EntityType.GLOW_SQUID, EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH,
        EntityType.PUFFERFISH, EntityType.LLAMA, EntityType.TRADER_LLAMA,
        EntityType.WANDERING_TRADER, EntityType.PILLAGER, EntityType.VINDICATOR,
        EntityType.EVOKER, EntityType.RAVAGER, EntityType.VEX, EntityType.WITCH,
        EntityType.ILLUSIONER
    );

    private static final Set<EntityType<?>> END_ANOMALIES = Set.of(
        EntityType.GHAST, EntityType.BLAZE, EntityType.WITHER_SKELETON,
        EntityType.MAGMA_CUBE, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
        EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.STRIDER,
        EntityType.ZOMBIFIED_PIGLIN, EntityType.WITHER, EntityType.COW,
        EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.HORSE,
        EntityType.DONKEY, EntityType.VILLAGER, EntityType.IRON_GOLEM,
        EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER,
        EntityType.RAVAGER, EntityType.WITCH
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final Map<Integer, AnomalyType> highlightedEntities = new HashMap<>();
    private final Set<Integer>              notifiedEntities    = new HashSet<>();

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
        highlightedEntities.clear();
        notifiedEntities.clear();
        GlowingRegistry.clear();
    }

    @Override
    public void onDeactivate() {
        highlightedEntities.clear();
        notifiedEntities.clear();
        GlowingRegistry.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tick
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        highlightedEntities.clear();
        GlowingRegistry.clear();
        notifiedEntities.removeIf(id -> mc.world.getEntityById(id) == null);

        String dim = mc.world.getRegistryKey().getValue().toString();
        boolean spectral = highlightMode.get() == HighlightMode.Spectral;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof MobEntity mob)) continue;
            if (ignoredEntities.get().contains(mob.getType())) continue;
            if (mc.player.distanceTo(mob) > range.get()) continue;

            AnomalyType type = resolveAnomalyType(mob, dim);
            if (type == null) continue;

            highlightedEntities.put(mob.getId(), type);

            if (spectral) {
                SettingColor c = getColorForType(type);
                GlowingRegistry.add(mob.getId(), (255 << 24) | (c.r << 16) | (c.g << 8) | c.b);
            }

            if (chatNotification.get() && notifiedEntities.add(mob.getId())) {
                String mobName = mob.getType().getName().getString();
                switch (type) {
                    case CHESTED -> info("Chested animal detected: "    + mobName);
                    case ITEM    -> info("Item anomaly detected: "      + mobName);
                    default      -> info("Dimension anomaly detected: " + mobName);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Render
    // Wireframe mode — box outline + bloom drawn here.
    // Spectral mode  — outline drawn by vanilla glow pipeline via GlowingRegistry;
    //                  no bloom (bloom settings are hidden in Spectral mode).
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null || highlightedEntities.isEmpty()) return;

        boolean wireframe = highlightMode.get() == HighlightMode.Wireframe;

        for (Map.Entry<Integer, AnomalyType> entry : highlightedEntities.entrySet()) {
            Entity entity = mc.world.getEntityById(entry.getKey());
            if (!(entity instanceof MobEntity mob)) continue;

            SettingColor color = getColorForType(entry.getValue());

            if (wireframe) {
                renderGlowLayers(event, mob.getBoundingBox(), color);
                event.renderer.box(mob.getBoundingBox(), withAlpha(color, 0), color, ShapeMode.Lines, 0);
            }
            // Spectral: outline handled entirely by GlowingRegistry — nothing to draw here.
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bloom
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderGlowLayers(Render3DEvent event, Box box, SettingColor color) {
        int    layers    = glowLayers.get();
        double spread    = glowSpread.get();
        int    baseAlpha = glowBaseAlpha.get();

        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            int layerAlpha   = Math.max(4, (int)(baseAlpha * (1.0 - (double)(i - 1) / layers)));
            event.renderer.box(box.expand(expansion), withAlpha(color, layerAlpha), withAlpha(color, 0), ShapeMode.Sides, 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Detection Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private AnomalyType resolveAnomalyType(MobEntity mob, String dimension) {
        if (detectChestedAnimals.get() && hasChestAttachment(mob)) return AnomalyType.CHESTED;
        if (detectUnnaturalItems.get() && hasUnnaturalItems(mob))  return AnomalyType.ITEM;

        if (!isDimensionAnomaly(mob.getType(), dimension)) return null;

        if (END_ANOMALIES.contains(mob.getType()) && !dimension.equals("minecraft:the_end"))
            return AnomalyType.DIMENSION_END;
        if (NETHER_ANOMALIES.contains(mob.getType()) && !dimension.equals("minecraft:the_nether"))
            return AnomalyType.DIMENSION_NETHER;

        return AnomalyType.DIMENSION_OVERWORLD;
    }

    private boolean isDimensionAnomaly(EntityType<?> type, String dimension) {
        return switch (dimension) {
            case "minecraft:overworld"  -> OVERWORLD_ANOMALIES.contains(type);
            case "minecraft:the_nether" -> NETHER_ANOMALIES.contains(type);
            case "minecraft:the_end"    -> END_ANOMALIES.contains(type);
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

        Identifier itemId = Registries.ITEM.getId(item);
        if (itemId.getNamespace().equals("minecraft") && itemId.getPath().startsWith("netherite_")) return true;

        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) return false;

        for (RegistryEntry<Enchantment> enchantmentEntry : enchants.getEnchantments()) {
            Enchantment enchantment = enchantmentEntry.value();
            if (enchantment == null) continue;
            if (enchantmentEntry.matchesKey(Enchantments.MENDING)) return true;
            if (enchants.getLevel(enchantmentEntry) > enchantment.getMaxLevel()) return true;
        }

        return false;
    }

    private boolean hasChestAttachment(MobEntity mob) {
        if (mob instanceof AbstractDonkeyEntity donkey) return donkey.hasChest();
        if (mob instanceof LlamaEntity llama)           return llama.hasChest();
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Color Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private SettingColor getColorForType(AnomalyType type) {
        return switch (type) {
            case ITEM, CHESTED       -> itemAnomalyLineColor.get();
            case DIMENSION_END       -> endLineColor.get();
            case DIMENSION_NETHER    -> netherLineColor.get();
            case DIMENSION_OVERWORLD -> overworldLineColor.get();
        };
    }

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }
}