package com.example.addon.modules;

import com.example.addon.HuntingUtilities;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Handmold extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgMainHand = settings.createGroup("Main Hand");
    private final SettingGroup sgOffHand  = settings.createGroup("Off Hand");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> noHandBob = sgGeneral.add(new BoolSetting.Builder()
        .name("no-hand-bob")
        .description("Disables the hand bobbing movement while walking or running.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideEmptyMainhand = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-empty-mainhand")
        .description("Hides the main hand when it is not holding any item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideOffhandCompletely = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-offhand-completely")
        .description("Hides the offhand completely, regardless of what it is holding.")
        .defaultValue(false)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Main Hand
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Double> mainX = sgMainHand.add(new DoubleSetting.Builder()
        .name("x").description("Main hand horizontal offset.")
        .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
        .build()
    );

    private final Setting<Double> mainY = sgMainHand.add(new DoubleSetting.Builder()
        .name("y").description("Main hand vertical offset.")
        .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
        .build()
    );

    private final Setting<Double> mainZ = sgMainHand.add(new DoubleSetting.Builder()
        .name("z").description("Main hand depth offset.")
        .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
        .build()
    );

    private final Setting<Double> mainScale = sgMainHand.add(new DoubleSetting.Builder()
        .name("scale").description("Main hand scale multiplier.")
        .defaultValue(1.0).min(0.1).max(3.0).sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Double> mainRotX = sgMainHand.add(new DoubleSetting.Builder()
        .name("rot-x").description("Main hand rotation around the X axis (degrees).")
        .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
        .build()
    );

    private final Setting<Double> mainRotY = sgMainHand.add(new DoubleSetting.Builder()
        .name("rot-y").description("Main hand rotation around the Y axis (degrees).")
        .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
        .build()
    );

    private final Setting<Double> mainRotZ = sgMainHand.add(new DoubleSetting.Builder()
        .name("rot-z").description("Main hand rotation around the Z axis (degrees).")
        .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Off Hand
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Double> offX = sgOffHand.add(new DoubleSetting.Builder()
        .name("x").description("Off hand horizontal offset.")
        .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
        .build()
    );

    private final Setting<Double> offY = sgOffHand.add(new DoubleSetting.Builder()
        .name("y").description("Off hand vertical offset.")
        .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
        .build()
    );

    private final Setting<Double> offZ = sgOffHand.add(new DoubleSetting.Builder()
        .name("z").description("Off hand depth offset.")
        .defaultValue(0.0).min(-2.0).max(2.0).sliderRange(-1.0, 1.0)
        .build()
    );

    private final Setting<Double> offScale = sgOffHand.add(new DoubleSetting.Builder()
        .name("scale").description("Off hand scale multiplier.")
        .defaultValue(1.0).min(0.1).max(3.0).sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Double> offRotX = sgOffHand.add(new DoubleSetting.Builder()
        .name("rot-x").description("Off hand rotation around the X axis (degrees).")
        .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
        .build()
    );

    private final Setting<Double> offRotY = sgOffHand.add(new DoubleSetting.Builder()
        .name("rot-y").description("Off hand rotation around the Y axis (degrees).")
        .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
        .build()
    );

    private final Setting<Double> offRotZ = sgOffHand.add(new DoubleSetting.Builder()
        .name("rot-z").description("Off hand rotation around the Z axis (degrees).")
        .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public Handmold() {
        super(HuntingUtilities.CATEGORY, "handmold",
            "Adjusts the position, scale, and rotation of each hand independently.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API — read by mixins
    // ═══════════════════════════════════════════════════════════════════════════

    public double getMainX()     { return mainX.get(); }
    public double getMainY()     { return mainY.get(); }
    public double getMainZ()     { return mainZ.get(); }
    public double getMainScale() { return mainScale.get(); }
    public double getMainRotX()  { return mainRotX.get(); }
    public double getMainRotY()  { return mainRotY.get(); }
    public double getMainRotZ()  { return mainRotZ.get(); }

    public double getOffX()      { return offX.get(); }
    public double getOffY()      { return offY.get(); }
    public double getOffZ()      { return offZ.get(); }
    public double getOffScale()  { return offScale.get(); }
    public double getOffRotX()   { return offRotX.get(); }
    public double getOffRotY()   { return offRotY.get(); }
    public double getOffRotZ()   { return offRotZ.get(); }

    public boolean shouldDisableHandBob()        { return isActive() && noHandBob.get(); }
    public boolean shouldHideEmptyMainhand()     { return isActive() && hideEmptyMainhand.get(); }
    public boolean shouldHideOffhandCompletely() { return isActive() && hideOffhandCompletely.get(); }
}