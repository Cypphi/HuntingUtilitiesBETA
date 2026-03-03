package com.example.addon.modules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnchantmentListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;

public class ServerHealthcareSystem extends Module {

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgAutoArmor  = settings.createGroup("Auto Armor");
    private final SettingGroup sgAutoEat    = settings.createGroup("Auto Eat");
    private final SettingGroup sgSafety     = settings.createGroup("Safety");
    private final SettingGroup sgAutoIgnore = settings.createGroup("Auto Ignore");
    private final SettingGroup sgTracking   = settings.createGroup("Player Tracking");
    private final SettingGroup sgFriends    = settings.createGroup("Friends & Enemies");
    private final SettingGroup sgTabList    = settings.createGroup("Tab List Monitoring");

    // ── General ──────────────────────────────────────────────────────────────

    private final Setting<Boolean> autoRespawn = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-respawn")
        .description("Automatically respawns after death.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-totem")
        .description("Automatically equips a totem of undying in your offhand.")
        .defaultValue(true)
        .build()
    );

    // ── Auto Armor ────────────────────────────────────────────────────────────

    private final Setting<Boolean> autoArmor = sgAutoArmor.add(new BoolSetting.Builder()
        .name("auto-armor")
        .description("Automatically equips the best armor in your inventory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ChestplatePreference> chestplatePreference = sgAutoArmor.add(new EnumSetting.Builder<ChestplatePreference>()
        .name("chestplate-preference")
        .description("Which item to prefer for the chest slot.")
        .defaultValue(ChestplatePreference.Chestplate)
        .visible(autoArmor::get)
        .build()
    );

    private final Setting<Boolean> chestplateOnGround = sgAutoArmor.add(new BoolSetting.Builder()
        .name("chestplate-on-ground")
        .description("Wears a chestplate while on the ground and an elytra while in the air.")
        .defaultValue(false)
        .visible(autoArmor::get)
        .build()
    );

    private final Setting<Integer> swapDelay = sgAutoArmor.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks to wait after performing a swap.")
        .defaultValue(10)
        .min(0)
        .visible(() -> autoArmor.get() && chestplateOnGround.get())
        .build()
    );

    private final Setting<Set<RegistryKey<Enchantment>>> ignoredEnchantments = sgAutoArmor.add(new EnchantmentListSetting.Builder()
        .name("ignored-enchantments")
        .description("Armor with these enchantments will be ignored by Auto Armor.")
        .defaultValue(Enchantments.BINDING_CURSE)
        .visible(autoArmor::get)
        .build()
    );

    // ── Auto Eat ──────────────────────────────────────────────────────────────

    private final Setting<Boolean> autoEat = sgAutoEat.add(new BoolSetting.Builder()
        .name("auto-eat")
        .description("Automatically eats Golden Apples when low on health.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> healthThreshold = sgAutoEat.add(new IntSetting.Builder()
        .name("health-threshold")
        .description("Health threshold to trigger auto-eat (in health points, 20 = full).")
        .defaultValue(10)
        .min(1)
        .max(19)
        .sliderRange(1, 19)
        .visible(autoEat::get)
        .build()
    );

    // ── Safety ────────────────────────────────────────────────────────────────

    private final Setting<Boolean> disconnectOnPlayer = sgSafety.add(new BoolSetting.Builder()
        .name("disconnect-on-player")
        .description("Disconnects if another player is detected nearby.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> playerDetectionRange = sgSafety.add(new IntSetting.Builder()
        .name("player-detection-range")
        .description("Distance to detect other players.")
        .defaultValue(32)
        .min(1)
        .sliderMax(128)
        .visible(disconnectOnPlayer::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriendsOnDisconnect = sgSafety.add(new BoolSetting.Builder()
        .name("ignore-friends-on-disconnect")
        .description("Prevents disconnecting if the nearby player is a friend.")
        .defaultValue(true)
        .visible(disconnectOnPlayer::get)
        .build()
    );

    private final Setting<Boolean> disconnectOnTotemPop = sgSafety.add(new BoolSetting.Builder()
        .name("disconnect-on-totem-pop")
        .description("Disconnects if a totem of undying is used.")
        .defaultValue(false)
        .build()
    );

    // ── Auto Ignore ───────────────────────────────────────────────────────────

    private final Setting<Boolean> autoIgnore = sgAutoIgnore.add(new BoolSetting.Builder()
        .name("auto-ignore")
        .description("Silently runs /ignorehard on players who say certain keywords in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> ignoreKeywords = sgAutoIgnore.add(new StringListSetting.Builder()
        .name("keywords")
        .description("If any of these words appear in a player's message body, they will be /ignorehard'd.")
        .defaultValue(List.of())
        .visible(autoIgnore::get)
        .build()
    );

    private final Setting<Boolean> ignoreCaseSensitive = sgAutoIgnore.add(new BoolSetting.Builder()
        .name("case-sensitive")
        .description("Match keywords with case sensitivity.")
        .defaultValue(false)
        .visible(autoIgnore::get)
        .build()
    );

    private final Setting<Boolean> ignoreNotify = sgAutoIgnore.add(new BoolSetting.Builder()
        .name("notify")
        .description("Print a local message when a player is auto-ignored.")
        .defaultValue(true)
        .visible(autoIgnore::get)
        .build()
    );

    // ── Player Tracking ───────────────────────────────────────────────────────

    private final Setting<Boolean> trackPlayers = sgTracking.add(new BoolSetting.Builder()
        .name("track-players")
        .description("Tracks players entering visual range.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> trackRange = sgTracking.add(new IntSetting.Builder()
        .name("track-range")
        .description("Distance to track players.")
        .defaultValue(128)
        .min(1)
        .sliderMax(256)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Boolean> trackFriends = sgTracking.add(new BoolSetting.Builder()
        .name("track-friends")
        .description("Highlight friends.")
        .defaultValue(true)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Boolean> trackEnemies = sgTracking.add(new BoolSetting.Builder()
        .name("track-enemies")
        .description("Highlight enemies.")
        .defaultValue(true)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Boolean> trackOthers = sgTracking.add(new BoolSetting.Builder()
        .name("track-others")
        .description("Highlight other players (not friends or enemies).")
        .defaultValue(true)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<ShapeMode> trackingShapeMode = sgTracking.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Lines)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<SettingColor> otherColor = sgTracking.add(new ColorSetting.Builder()
        .name("other-color")
        .description("Color for other players (not friends or enemies).")
        .defaultValue(new SettingColor(139, 0, 0, 255))
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Boolean> notifyChat = sgTracking.add(new BoolSetting.Builder()
        .name("notify-chat")
        .description("Send a chat message when a player is detected.")
        .defaultValue(true)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Boolean> playSound = sgTracking.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play a sound when a player is detected.")
        .defaultValue(false)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<String> customMessage = sgTracking.add(new StringSetting.Builder()
        .name("custom-message")
        .description("Message to send. Use {player} for name and {status} for relation.")
        .defaultValue("Warning: {status} {player} is in visual range!")
        .visible(() -> trackPlayers.get() && notifyChat.get())
        .build()
    );

    // ── Friends & Enemies ─────────────────────────────────────────────────────

    private final Setting<List<String>> friends = sgFriends.add(new StringListSetting.Builder()
        .name("friends")
        .description("Players to be treated as friends. Case-insensitive.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<List<String>> enemies = sgFriends.add(new StringListSetting.Builder()
        .name("enemies")
        .description("Players to be treated as enemies. Case-insensitive.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<SettingColor> friendColor = sgFriends.add(new ColorSetting.Builder()
        .name("friend-color")
        .description("Color for friends in Player Tracking.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<SettingColor> enemyColor = sgFriends.add(new ColorSetting.Builder()
        .name("enemy-color")
        .description("Color for enemies in Player Tracking.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Boolean> colorFriendInTab = sgFriends.add(new BoolSetting.Builder()
        .name("color-friend-in-tab")
        .description("Highlights friends in the tab list with a specific color.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> friendTabColor = sgFriends.add(new ColorSetting.Builder()
        .name("friend-tab-color")
        .description("The color to use for friends in the tab list.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .visible(colorFriendInTab::get)
        .build()
    );

    private final Setting<Boolean> colorEnemyInTab = sgFriends.add(new BoolSetting.Builder()
        .name("color-enemy-in-tab")
        .description("Highlights enemies in the tab list with a specific color.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> enemyTabColor = sgFriends.add(new ColorSetting.Builder()
        .name("enemy-tab-color")
        .description("The color to use for enemies in the tab list.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(colorEnemyInTab::get)
        .build()
    );

    // ── Tab List Monitoring ───────────────────────────────────────────────────

    private final Setting<Boolean> monitorTabList = sgTabList.add(new BoolSetting.Builder()
        .name("monitor-tab-list")
        .description("Tracks player joins/leaves from the tab list (beyond visual range).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifyOnJoin = sgTabList.add(new BoolSetting.Builder()
        .name("notify-on-join")
        .description("Notifies when a player joins the server.")
        .defaultValue(true)
        .visible(monitorTabList::get)
        .build()
    );

    private final Setting<Boolean> notifyOnLeave = sgTabList.add(new BoolSetting.Builder()
        .name("notify-on-leave")
        .description("Notifies when a player leaves the server.")
        .defaultValue(true)
        .visible(monitorTabList::get)
        .build()
    );

    private final Setting<Boolean> tabNotifyFriends = sgTabList.add(new BoolSetting.Builder()
        .name("notify-friends")
        .description("Notify when friends join/leave.")
        .defaultValue(true)
        .visible(monitorTabList::get)
        .build()
    );

    private final Setting<Boolean> tabNotifyEnemies = sgTabList.add(new BoolSetting.Builder()
        .name("notify-enemies")
        .description("Notify when enemies join/leave.")
        .defaultValue(true)
        .visible(monitorTabList::get)
        .build()
    );

    private final Setting<Boolean> tabNotifyOthers = sgTabList.add(new BoolSetting.Builder()
        .name("notify-others")
        .description("Notify when other players join/leave.")
        .defaultValue(false)
        .visible(monitorTabList::get)
        .build()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    /** Players ignored this session. Cleared on toggle or server join. */
    private final Set<String> ignoredThisSession = new HashSet<>();
    private final Set<Integer> notifiedPlayers = new HashSet<>();
    private final Set<String> playersInTab = new HashSet<>();

    private int totemPops = 0;
    private boolean isEating = false;
    private boolean ateForFire = false;
    /**
     * The hotbar slot (0-8) we committed to eating from.
     * Always a valid hotbar index while isEating == true. -1 otherwise.
     */
    private int eatHotbarSlot = -1;
    private int swapTimer = 0;

    private float lastHealth = -1;
    private boolean tookDamageWhileOnFire = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ServerHealthcareSystem() {
        super(HuntingUtilities.CATEGORY, "server-healthcare-system",
            "SHS - Manages health and safety features like auto-totem and auto-eat.");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onActivate() {
        if (mc.player != null) {
            totemPops = mc.player.getStatHandler().getStat(Stats.USED, Items.TOTEM_OF_UNDYING);
            lastHealth = mc.player.getHealth();
        }
        isEating = false;
        ateForFire = false;
        tookDamageWhileOnFire = false;
        eatHotbarSlot = -1;
        swapTimer = 0;
        ignoredThisSession.clear();
        notifiedPlayers.clear();
        playersInTab.clear();
    }

    @Override
    public void onDeactivate() {
        if (isEating) {
            mc.options.useKey.setPressed(false);
            isEating = false;
            eatHotbarSlot = -1;
        }
        ateForFire = false;
        tookDamageWhileOnFire = false;
        lastHealth = -1;
        ignoredThisSession.clear();
        notifiedPlayers.clear();
        playersInTab.clear();
    }

    /** Clear the ignored-set when joining a new server so old names don't carry over. */
    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        ignoredThisSession.clear();
        notifiedPlayers.clear();
        playersInTab.clear();
        if (mc.player != null) {
            totemPops = mc.player.getStatHandler().getStat(Stats.USED, Items.TOTEM_OF_UNDYING);
            lastHealth = mc.player.getHealth();
        }
        tookDamageWhileOnFire = false;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (swapTimer > 0) swapTimer--;

        // Damage detection for Auto Eat
        if (lastHealth == -1) lastHealth = mc.player.getHealth();
        float health = mc.player.getHealth();

        if (mc.player.isOnFire()) {
            if (health < lastHealth) {
                tookDamageWhileOnFire = true;
            }
        } else {
            ateForFire = false;
            tookDamageWhileOnFire = false;
        }
        lastHealth = health;

        int currentPops = mc.player.getStatHandler().getStat(Stats.USED, Items.TOTEM_OF_UNDYING);

        // Disconnect on Totem Pop
        if (disconnectOnTotemPop.get()) {
            if (currentPops > totemPops) {
                int remainingTotems = countTotems();
                if (mc.player.networkHandler != null) {
                    mc.player.networkHandler.getConnection().disconnect(
                        Text.literal("[SHS] Disconnected on totem pop. " + remainingTotems + " totems remaining.")
                    );
                }
                this.toggle();
                return;
            }
        }
        totemPops = currentPops;

        // Disconnect on Player
        if (disconnectOnPlayer.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || player.isCreative() || player.isSpectator()) continue;
                if (ignoreFriendsOnDisconnect.get() && isFriend(player.getName().getString())) continue;
                if (mc.player.distanceTo(player) <= playerDetectionRange.get()) {
                    if (mc.player.networkHandler != null) {
                        mc.player.networkHandler.getConnection().disconnect(
                            Text.literal("[SHS] Player detected: " + player.getName().getString())
                        );
                    }
                    this.toggle();
                    return;
                }
            }
        }

        // Auto Respawn
        if (autoRespawn.get() && mc.currentScreen instanceof DeathScreen) {
            mc.player.requestRespawn();
            mc.setScreen(null);
        }

        // Auto Totem
        if (autoTotem.get()) {
            if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
                if (totem.found()) InvUtils.move().from(totem.slot()).toOffhand();
            }
        }

        // Auto Armor
        if (autoArmor.get() && swapTimer == 0) {
            if (chestplateOnGround.get()) handleChestplateElytraSwitch();

            EquipmentSlot[] armorSlots = { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
            for (int i = 0; i < 4; i++) {
                EquipmentSlot slot = armorSlots[i];
                if (slot == EquipmentSlot.CHEST && chestplateOnGround.get()) continue;

                ItemStack current   = mc.player.getEquippedStack(slot);
                int       bestValue = getArmorValue(current);

                if (slot == EquipmentSlot.CHEST
                        && chestplatePreference.get() == ChestplatePreference.Elytra
                        && current.isOf(Items.ELYTRA)) {
                    bestValue = 1_000_000;
                }

                int bestSlot = -1;
                for (int j = 0; j < 36; j++) {
                    ItemStack stack = mc.player.getInventory().getStack(j);
                    if (stack.isEmpty()) continue;
                    if (hasIgnoredEnchantment(stack)) continue;
                    var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
                    if (equippable == null || equippable.slot() != slot) continue;

                    int value = getArmorValue(stack);
                    if (slot == EquipmentSlot.CHEST
                            && chestplatePreference.get() == ChestplatePreference.Elytra
                            && stack.isOf(Items.ELYTRA)) {
                        value = 1_000_000;
                    }

                    if (value > bestValue) {
                        bestValue = value;
                        bestSlot  = j;
                    }
                }

                if (bestSlot != -1) InvUtils.move().from(bestSlot).toArmor(i);
            }
        }

        // Auto Eat
        if (autoEat.get()) {
            boolean shouldEatForHealth = health <= healthThreshold.get();
            boolean shouldEatForFire = mc.player.isOnFire() && tookDamageWhileOnFire && !ateForFire;

            // Start eating if conditions are met and we aren't already eating/using an item
            if ((shouldEatForHealth || shouldEatForFire) && !isEating && !mc.player.isUsingItem()) {
                    int found = -1;
                    boolean isForFire = false;

                    if (shouldEatForFire) {
                        found = findEnchantedGapple();
                        if (found != -1) isForFire = true;
                    }

                    if (found == -1 && shouldEatForHealth) {
                        found = findGapple();
                        isForFire = false;
                    }

                    if (found != -1) {
                        if (found < 9) {
                            eatHotbarSlot = found;
                        } else {
                            eatHotbarSlot = mc.player.getInventory().selectedSlot;
                            InvUtils.move().from(found).toHotbar(eatHotbarSlot);
                        }
                        mc.player.getInventory().selectedSlot = eatHotbarSlot;
                        mc.options.useKey.setPressed(true);
                        isEating = true;
                        if (isForFire) {
                            ateForFire = true;
                            tookDamageWhileOnFire = false;
                        }
                    }
            }

            // Manage eating state (continue or stop)
            if (isEating) {
                ItemStack activeItem = mc.player.getActiveItem();
                boolean isHoldingGapple = activeItem.isOf(Items.GOLDEN_APPLE) || activeItem.isOf(Items.ENCHANTED_GOLDEN_APPLE);

                // Stop eating if we are no longer using an item, or if the item is not a gapple
                if (!mc.player.isUsingItem() || !isHoldingGapple) {
                    mc.options.useKey.setPressed(false);
                    isEating = false;
                    eatHotbarSlot = -1;
                } else {
                    // Continue eating: ensure correct slot is selected
                    if (mc.player.getInventory().selectedSlot != eatHotbarSlot) {
                        mc.player.getInventory().selectedSlot = eatHotbarSlot;
                    }
                }
            }
        }

        // Player Tracking
        if (trackPlayers.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || player.isSpectator()) continue;
                
                if (mc.player.distanceTo(player) <= trackRange.get()) {
                    if (notifiedPlayers.add(player.getId())) {
                        if (notifyChat.get()) {
                            String msg = customMessage.get().replace("{player}", player.getName().getString());
                            info(msg);
                        }
                        if (playSound.get()) {
                            mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
                        }
                    }
                }
            }
            notifiedPlayers.removeIf(id -> mc.world.getEntityById(id) == null);
        }
    }

    // ── Auto Ignore ───────────────────────────────────────────────────────────

    /**
     * Listens to every incoming chat message. If auto-ignore is enabled and the
     * message body contains a flagged keyword, silently runs /ignorehard on the sender.
     *
     * Supported chat formats:
     *   <PlayerName> message text     (standard vanilla)
     *   PlayerName: message text      (some plugin formats)
     *
     * Sender and body are parsed separately so keywords can never accidentally
     * match the sender's name and trigger a false positive.
     */
    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!autoIgnore.get()) return;
        if (mc.player == null || mc.player.networkHandler == null) return;

        List<String> keywords = ignoreKeywords.get();
        if (keywords.isEmpty()) return;

        String raw = event.getMessage().getString();

        String sender;
        String messageBody;

        if (raw.startsWith("<")) {
            // Format: <PlayerName> message
            int close = raw.indexOf('>');
            if (close < 1) return;
            sender      = raw.substring(1, close).trim();
            messageBody = raw.substring(close + 1).trim();
        } else {
            // Format: PlayerName: message
            int colon = raw.indexOf(':');
            if (colon < 1 || colon >= 20) return;
            String possibleName = raw.substring(0, colon);
            if (possibleName.contains(" ")) return; // reject prefixed/decorated names
            sender      = possibleName.trim();
            messageBody = raw.substring(colon + 1).trim();
        }

        if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;
        if (isFriend(sender)) return;
        if (ignoredThisSession.contains(sender.toLowerCase())) return;

        boolean matched = false;
        for (String keyword : keywords) {
            if (keyword.isBlank()) continue;
            if (ignoreCaseSensitive.get()) {
                if (messageBody.contains(keyword)) { matched = true; break; }
            } else {
                if (messageBody.toLowerCase().contains(keyword.toLowerCase())) { matched = true; break; }
            }
        }

        if (!matched) return;

        mc.player.networkHandler.sendChatCommand("ignorehard " + sender);
        ignoredThisSession.add(sender.toLowerCase());

        if (ignoreNotify.get()) {
            mc.player.sendMessage(
                Text.literal("[SHS] Auto-ignored " + sender + " (keyword match)."), false
            );
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!trackPlayers.get() || mc.world == null || mc.player == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isSpectator()) continue;
            if (mc.player.distanceTo(player) > trackRange.get()) continue;

            PlayerStatus status = getPlayerStatus(player.getName().getString());
            SettingColor color = null;

            switch (status) {
                case Friend:
                    if (trackFriends.get()) color = friendColor.get();
                    break;
                case Enemy:
                    if (trackEnemies.get()) color = enemyColor.get();
                    break;
                case Other:
                    if (trackOthers.get()) color = otherColor.get();
                    break;
            }

            if (color != null) {
                event.renderer.box(player.getBoundingBox(), color, color, trackingShapeMode.get(), 0);
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!monitorTabList.get() || !(event.packet instanceof PlayerListS2CPacket packet)) return;

        for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
            if (entry.profile() == null || entry.profile().getName() == null || entry.profile().getName().isEmpty()) continue;

            String name = entry.profile().getName();

            if (packet.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
                if (playersInTab.add(name)) {
                    handleTabListChange(name, "joined");
                }
            } else if (packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LISTED) && !entry.listed()) {
                if (playersInTab.remove(name)) {
                    handleTabListChange(name, "left");
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void handleTabListChange(String playerName, String action) {
        PlayerStatus status = getPlayerStatus(playerName);

        boolean shouldNotify = switch (status) {
            case Friend -> tabNotifyFriends.get();
            case Enemy -> tabNotifyEnemies.get();
            case Other -> tabNotifyOthers.get();
        };

        if (!shouldNotify) return;
        if (action.equals("joined") && !notifyOnJoin.get()) return;
        if (action.equals("left") && !notifyOnLeave.get()) return;

        String statusText = switch (status) {
            case Friend -> "§aFriend";
            case Enemy -> "§cEnemy";
            case Other -> "Player";
        };

        info("%s %s has %s the server.", statusText, playerName, action);
    }

    private boolean hasIgnoredEnchantment(ItemStack stack) {
        if (ignoredEnchantments.get().isEmpty()) return false;

        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants == null) return false;

        for (RegistryEntry<Enchantment> enchantmentEntry : enchants.getEnchantments()) {
            if (enchantmentEntry.getKey().isPresent() && ignoredEnchantments.get().contains(enchantmentEntry.getKey().get())) {
                return true;
            }
        }

        return false;
    }

    public boolean isFriend(String name) {
        return friends.get().stream().anyMatch(friend -> friend.equalsIgnoreCase(name));
    }

    public boolean isEnemy(String name) {
        return enemies.get().stream().anyMatch(enemy -> enemy.equalsIgnoreCase(name));
    }

    private PlayerStatus getPlayerStatus(String name) {
        if (isFriend(name)) return PlayerStatus.Friend;
        if (isEnemy(name)) return PlayerStatus.Enemy;
        return PlayerStatus.Other;
    }

    public boolean shouldColorFriendInTab() {
        return colorFriendInTab.get();
    }

    public SettingColor getFriendTabColor() {
        return friendTabColor.get();
    }

    public boolean shouldColorEnemyInTab() {
        return colorEnemyInTab.get();
    }

    public SettingColor getEnemyTabColor() {
        return enemyTabColor.get();
    }

    /**
     * Searches for an enchanted golden apple in the inventory.
     */
    private int findEnchantedGapple() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENCHANTED_GOLDEN_APPLE)) return i;
        }
        return -1;
    }

    /**
     * Searches hotbar first (no item move needed), then main inventory.
     * Enchanted golden apples are preferred over regular ones in both zones.
     */
    private int findGapple() {
        int regularFallback = -1;
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.ENCHANTED_GOLDEN_APPLE) return i;
            if (item == Items.GOLDEN_APPLE && regularFallback == -1) regularFallback = i;
        }
        if (regularFallback != -1) return regularFallback;
        for (int i = 9; i < 36; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.ENCHANTED_GOLDEN_APPLE) return i;
            if (item == Items.GOLDEN_APPLE && regularFallback == -1) regularFallback = i;
        }
        return regularFallback;
    }

    private int countTotems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (ItemStack stack : mc.player.getInventory().main) {
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            count += mc.player.getOffHandStack().getCount();
        }
        return count;
    }

    /**
     * Scores an armor piece by armor, toughness, and protection enchantments.
     * Uses double precision to avoid truncation of fractional attribute values.
     */
    private int getArmorValue(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        var equippable = stack.getOrDefault(DataComponentTypes.EQUIPPABLE, null);
        if (equippable == null) return -1;

        AttributeModifiersComponent attrs = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, null);
        double armor = 0, toughness = 0;

        if (attrs != null) {
            for (var entry : attrs.modifiers()) {
                if (entry == null || entry.attribute() == null || entry.modifier() == null) continue;
                var keyOpt = entry.attribute().getKey();
                if (keyOpt == null || keyOpt.isEmpty()) continue;
                String id  = keyOpt.get().getValue().toString();
                double val = entry.modifier().value();
                if (id.equals("minecraft:generic.armor"))                armor    += val;
                else if (id.equals("minecraft:generic.armor_toughness")) toughness += val;
            }
        }

        double enchantBonus =
              getEnchantmentLevel(stack, "minecraft:protection")            * 3.0
            + getEnchantmentLevel(stack, "minecraft:fire_protection")       * 1.0
            + getEnchantmentLevel(stack, "minecraft:projectile_protection") * 1.0;

        return (int) (armor * 100 + toughness * 10 + enchantBonus);
    }

    /** Returns the level of a named enchantment on a stack, or 0 if absent. */
    private int getEnchantmentLevel(ItemStack stack, String enchantmentId) {
        var enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants == null) return 0;
        for (var entry : enchants.getEnchantments()) {
            var key = entry.getKey();
            if (key.isPresent() && key.get().getValue().toString().equals(enchantmentId)) {
                return enchants.getLevel(entry);
            }
        }
        return 0;
    }

    private void handleChestplateElytraSwitch() {
        if (Modules.get().get(RocketPilot.class).isActive()) return;

        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);

        if (mc.player.isOnGround()) {
            if (chestplatePreference.get() != ChestplatePreference.Elytra && chest.isOf(Items.ELYTRA)) {
                FindItemResult cp = findBestChestplate();
                if (cp.found()) {
                    InvUtils.move().from(cp.slot()).toArmor(2);
                    swapTimer = swapDelay.get();
                }
            }
        } else {
            if (!chest.isOf(Items.ELYTRA)) {
                FindItemResult elytra = InvUtils.find(Items.ELYTRA);
                if (elytra.found()) {
                    InvUtils.move().from(elytra.slot()).toArmor(2);
                    swapTimer = swapDelay.get();
                }
            }
        }
    }

    private FindItemResult findBestChestplate() {
        int bestValue = -1;
        int bestSlot  = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null || equippable.slot() != EquipmentSlot.CHEST) continue;
            if (stack.isOf(Items.ELYTRA)) continue;

            int value = getArmorValue(stack);
            if (value > bestValue) {
                bestValue = value;
                bestSlot  = i;
            }
        }

        if (bestSlot != -1) {
            return new FindItemResult(bestSlot, mc.player.getInventory().getStack(bestSlot).getCount());
        }
        return new FindItemResult(-1, 0);
    }

    // ── Enum ──────────────────────────────────────────────────────────────────

    public enum ChestplatePreference {
        Chestplate,
        Elytra
    }

    public enum PlayerStatus {
        Friend,
        Enemy,
        Other
    }
}