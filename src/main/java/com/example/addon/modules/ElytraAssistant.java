package com.example.addon.modules;

import org.lwjgl.glfw.GLFW;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;

public class ElytraAssistant extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════════════════

    public enum MiddleClickAction { None, Rocket, Pearl }
    public enum DurabilityMode { None, AutoSwap, AutoMend }
    public enum MendTarget { Elytra, Tools, Armour, All }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgDurability = settings.createGroup("Durability");
    private final SettingGroup sgUtilities  = settings.createGroup("Utilities");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Durability
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<DurabilityMode> durabilityMode = sgDurability.add(new EnumSetting.Builder<DurabilityMode>()
        .name("durability-mode")
        .description("How to manage elytra durability.")
        .defaultValue(DurabilityMode.AutoSwap)
        .build()
    );

    private final Setting<Integer> durabilityThreshold = sgDurability.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Remaining durability below which swap occurs.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
        .visible(() -> durabilityMode.get() == DurabilityMode.AutoSwap)
        .build()
    );

    private final Setting<Keybind> autoSwapKey = sgDurability.add(new KeybindSetting.Builder()
        .name("auto-swap-key")
        .description("Key to toggle auto swap.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.currentScreen != null) return;
            if (durabilityMode.get() == DurabilityMode.AutoSwap) {
                durabilityMode.set(DurabilityMode.None);
            } else {
                durabilityMode.set(DurabilityMode.AutoSwap);
            }
            info("Auto Swap " + (durabilityMode.get() == DurabilityMode.AutoSwap ? "enabled" : "disabled") + ".");
        })
        .build()
    );

    private final Setting<Keybind> autoMendToggleKey = sgDurability.add(new KeybindSetting.Builder()
        .name("auto-mend-key")
        .description("Key to toggle auto mending.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.currentScreen != null) return;
            if (durabilityMode.get() == DurabilityMode.AutoMend) {
                durabilityMode.set(DurabilityMode.None);
            } else {
                durabilityMode.set(DurabilityMode.AutoMend);
            }
            info("Auto Mend " + (durabilityMode.get() == DurabilityMode.AutoMend ? "enabled" : "disabled") + ".");
        })
        .build()
    );

    private final Setting<MendTarget> mendTarget = sgDurability.add(new EnumSetting.Builder<MendTarget>()
        .name("mend-target")
        .description("What to repair with Auto Mend.")
        .defaultValue(MendTarget.Elytra)
        .visible(() -> durabilityMode.get() == DurabilityMode.AutoMend)
        .build()
    );

    private final Setting<Integer> packetsPerBurst = sgDurability.add(new IntSetting.Builder()
        .name("packets-per-burst")
        .description("How many XP bottles to throw per burst.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .visible(() -> durabilityMode.get() == DurabilityMode.AutoMend)
        .build()
    );

    private final Setting<Integer> burstDelay = sgDurability.add(new IntSetting.Builder()
        .name("burst-delay")
        .description("Ticks to wait between bursts.")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .visible(() -> durabilityMode.get() == DurabilityMode.AutoMend)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Utilities
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<MiddleClickAction> middleClickAction = sgUtilities.add(new EnumSetting.Builder<MiddleClickAction>()
        .name("middle-click-action")
        .description("Item to use when middle clicking.")
        .defaultValue(MiddleClickAction.None)
        .build()
    );

    public final Setting<Boolean> silentRocket = sgUtilities.add(new BoolSetting.Builder()
        .name("silent-rocket")
        .description("Prevents hand swing animation when using rockets.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> preventGroundUsage = sgUtilities.add(new BoolSetting.Builder()
        .name("prevent-ground-usage")
        .description("Blocks rocket usage while standing on ground.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antiAfk = sgUtilities.add(new BoolSetting.Builder()
        .name("anti-afk")
        .description("Prevents being kicked for AFK by swinging your hand periodically.")
        .defaultValue(false)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private static final double AFK_INTERVAL_SECONDS = 15.0;

    private boolean noReplacementWarned   = false;
    private boolean noUsableElytraWarned  = false;
    private boolean wasMiddlePressed      = false;
    private int     mendTimer             = 0;
    private int     middleClickTimer      = 0;
    private int     swingTimer            = 0;
    private int     autoSwapReenableTimer = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public ElytraAssistant() {
        super(HuntingUtilities.CATEGORY, "elytra-assistant", "Smart elytra & rocket management.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        noReplacementWarned   = false;
        noUsableElytraWarned  = false;
        wasMiddlePressed      = false;
        mendTimer             = 0;
        middleClickTimer      = 0;
        swingTimer            = 0;
        autoSwapReenableTimer = 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event Handler
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (autoSwapReenableTimer > 0) {
            autoSwapReenableTimer--;
            if (autoSwapReenableTimer == 0 && durabilityMode.get() == DurabilityMode.None) {
                durabilityMode.set(DurabilityMode.AutoSwap);
                info("Auto-mend finished. Re-enabling auto-swap.");
            }
        }

        if (middleClickTimer > 0) middleClickTimer--;

        if (middleClickAction.get() != MiddleClickAction.None) {
            if (Input.isButtonPressed(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
                if (!wasMiddlePressed && middleClickTimer == 0) {
                    runMiddleClickAction();
                    wasMiddlePressed = true;
                    middleClickTimer = 5;
                }
            } else {
                wasMiddlePressed = false;
            }
        }

        if (antiAfk.get()) {
            if (swingTimer <= 0) {
                mc.player.swingHand(Hand.MAIN_HAND);
                int base = (int) (AFK_INTERVAL_SECONDS * 20);
                base += (int) ((Math.random() - 0.5) * (base * 0.4));
                swingTimer = Math.max(1, base);
            } else {
                swingTimer--;
            }
        }

        if (durabilityMode.get() == DurabilityMode.AutoMend) {
            handleAutoMend();
            return;
        }

        if (durabilityMode.get() == DurabilityMode.AutoSwap) {
            handleChestplateElytraSwitch();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Durability Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleChestplateElytraSwitch() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA)) return;

        int remaining = chest.getMaxDamage() - chest.getDamage();
        if (remaining > durabilityThreshold.get()) {
            noReplacementWarned = false;
            return;
        }

        FindItemResult replacement = findUsableElytra();
        if (replacement.found()) {
            silentEquip(replacement.slot());
            warning("Elytra durability low! Swapping to fresh elytra.");
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            noReplacementWarned = false;
        } else if (!noReplacementWarned) {
            warning("No replacement elytra available!");
            mc.player.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
            noReplacementWarned = true;
        }
    }

    private FindItemResult findUsableElytra() {
        int bestSlot = -1;
        int bestDurability = -1;

        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isOf(Items.ELYTRA)) continue;

            int durability = stack.getMaxDamage() - stack.getDamage();
            if (durability > durabilityThreshold.get() && durability > bestDurability) {
                bestSlot       = i;
                bestDurability = durability;
            }
        }

        if (bestSlot != -1) return new FindItemResult(bestSlot, mc.player.getInventory().getStack(bestSlot).getCount());
        return new FindItemResult(-1, 0);
    }

    private void silentEquip(int slot) {
        InvUtils.move().from(getSlotId(slot)).toArmor(2);
    }

    private int getSlotId(int slot) {
        return (slot >= 0 && slot < 9) ? 36 + slot : slot;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Auto Mend Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleAutoMend() {
        if (mendTimer > 0) {
            mendTimer--;
            return;
        }

        if (!InvUtils.find(Items.EXPERIENCE_BOTTLE).found()) {
            info("No more XP bottles — disabling auto-mend.");
            durabilityMode.set(DurabilityMode.None);
            autoSwapReenableTimer = 30;
            return;
        }

        MendTarget currentTarget = mendTarget.get();

        switch (currentTarget) {
            case Elytra:
                if (!handleElytraMending()) {
                    info("All Elytras mended!");
                    durabilityMode.set(DurabilityMode.None);
                    autoSwapReenableTimer = 30;
                }
                break;
            case Tools:
                if (!handleToolMending()) {
                    info("All tools mended!");
                    durabilityMode.set(DurabilityMode.None);
                    autoSwapReenableTimer = 30;
                }
                break;
            case Armour:
                if (!handleArmourMending()) {
                    info("All armour mended!");
                    durabilityMode.set(DurabilityMode.None);
                    autoSwapReenableTimer = 30;
                }
                break;
            case All:
                if (!handleElytraMending() && !handleToolMending() && !handleArmourMending()) {
                    info("All items mended!");
                    durabilityMode.set(DurabilityMode.None);
                    autoSwapReenableTimer = 30;
                }
                break;
        }
    }

    private boolean handleElytraMending() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA) || !chest.isDamaged()) {
            FindItemResult damaged = InvUtils.find(stack -> stack.isOf(Items.ELYTRA) && stack.isDamaged());
            if (damaged.found()) {
                InvUtils.move().from(damaged.slot()).toArmor(2);
                return true;
            } else {
                return false;
            }
        }

        throwXpBottles();
        return true;
    }

    private boolean handleToolMending() {
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        boolean mainHandNeedsMend = isMendableTool(mainHand);
        boolean offHandNeedsMend = isMendableTool(offHand);

        if (!mainHandNeedsMend && !offHandNeedsMend) {
            FindItemResult damagedTool = InvUtils.find(this::isMendableTool);
            if (damagedTool.found()) {
                if (offHand.isEmpty()) {
                    InvUtils.move().from(damagedTool.slot()).toOffhand();
                } else {
                    InvUtils.swap(damagedTool.slot(), true);
                }
                return true;
            } else {
                return false;
            }
        }

        throwXpBottles();
        return true;
    }

    private boolean handleArmourMending() {
        // Check equipped armor first.
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            // Don't try to mend elytra here, handleElytraMending does that.
            if (isMendableArmour(stack) && !stack.isOf(Items.ELYTRA)) {
                throwXpBottles();
                return true;
            }
        }

        // No damaged armor equipped, find one in inventory.
        FindItemResult damagedArmour = InvUtils.find(s -> isMendableArmour(s) && !s.isOf(Items.ELYTRA));
        if (damagedArmour.found()) {
            ItemStack stack = mc.player.getInventory().getStack(damagedArmour.slot());
            var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable != null) {
                EquipmentSlot slot = equippable.slot();
                // Check if the target slot is empty or has a fully repaired item we can swap out.
                ItemStack equipped = mc.player.getEquippedStack(slot);
                if (equipped.isEmpty() || !equipped.isDamaged()) {
                     InvUtils.move().from(damagedArmour.slot()).toArmor(slot.getEntitySlotId());
                     return true;
                }
            }
        }

        // No damaged armor found anywhere.
        return false;
    }

    private boolean isMendableArmour(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamaged()) return false;
        return stack.getItem() instanceof ArmorItem;
    }

    private boolean isMendableTool(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamaged()) return false;
        Item item = stack.getItem();
        return item instanceof net.minecraft.item.PickaxeItem ||
               item instanceof net.minecraft.item.SwordItem ||
               item instanceof net.minecraft.item.AxeItem ||
               item instanceof net.minecraft.item.ShovelItem ||
               item == Items.BOW ||
               item == Items.FLINT_AND_STEEL ||
               item == Items.SHIELD ||
               item == Items.TRIDENT ||
               item == Items.FISHING_ROD;
    }

    private void throwXpBottles() {
        // Add small random rotation to prevent AFK kick
        float yaw = mc.player.getYaw() + (float) (Math.random() * 0.2 - 0.1);
        float pitch = 90 + (float) (Math.random() * 0.2 - 0.1);

        Rotations.rotate(yaw, pitch, () -> {
            FindItemResult xp = InvUtils.find(Items.EXPERIENCE_BOTTLE);
            if (!xp.found()) return;

            if (xp.isHotbar()) {
                InvUtils.swap(xp.slot(), true);
                for (int i = 0; i < packetsPerBurst.get(); i++) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }
                InvUtils.swapBack();
            } else {
                int emptySlot = InvUtils.findEmpty().slot();

                if (emptySlot != -1) {
                    // Move to empty slot, use, and move back to keep hotbar clean.
                    InvUtils.move().from(xp.slot()).toHotbar(emptySlot);
                    InvUtils.swap(emptySlot, true);
                    for (int i = 0; i < packetsPerBurst.get(); i++) mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    InvUtils.swapBack();
                    InvUtils.move().from(emptySlot).to(xp.slot());
                } else {
                    // No empty slot, use original logic (swap with current slot)
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    InvUtils.move().from(xp.slot()).toHotbar(prevSlot);
                    for (int i = 0; i < packetsPerBurst.get(); i++) mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    InvUtils.move().from(prevSlot).to(xp.slot());
                }
            }
        });
        mendTimer = burstDelay.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rocket / Middle Click Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void fireRocket() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (preventGroundUsage.get() && mc.player.isOnGround()) return;

        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        FindItemResult rocketResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (rocketResult.found()) {
            int prevSlot = mc.player.getInventory().selectedSlot;
            InvUtils.swap(rocketResult.slot(), false);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swap(prevSlot, false);
        }
    }

    private void runMiddleClickAction() {
        if (mc.currentScreen != null) return;

        MiddleClickAction action = middleClickAction.get();
        FindItemResult itemResult = null;

        if (action == MiddleClickAction.Rocket) {
            if (preventGroundUsage.get() && mc.player.isOnGround()) return;
            itemResult = InvUtils.find(Items.FIREWORK_ROCKET);
        } else if (action == MiddleClickAction.Pearl) {
            itemResult = InvUtils.find(Items.ENDER_PEARL);
        }

        if (itemResult == null || !itemResult.found()) return;

        int slot     = itemResult.slot();
        int prevSlot = mc.player.getInventory().selectedSlot;

        if (slot < 9) {
            InvUtils.swap(slot, true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
        } else {
            InvUtils.move().from(slot).toHotbar(prevSlot);
            InvUtils.swap(prevSlot, true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
            InvUtils.move().from(prevSlot).to(slot);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean shouldPreventRocketUse() {
        return isActive() && preventGroundUsage.get() && mc.player.isOnGround();
    }

    public boolean shouldSilentRocket() {
        return isActive() && silentRocket.get();
    }

    public boolean isAutoSwapEnabled() {
        return isActive() && durabilityMode.get() == DurabilityMode.AutoSwap;
    }
}