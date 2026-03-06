package com.example.addon.modules;

import com.example.addon.HuntingUtilities;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;

public class ThirdSight extends Module {

    public enum CameraMode { ThirdPerson, Overhead }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<CameraMode> cameraMode = sgGeneral.add(new EnumSetting.Builder<CameraMode>()
        .name("camera-mode")
        .description("ThirdPerson: standard over-the-shoulder. Overhead: top-down view looking straight down.")
        .defaultValue(CameraMode.ThirdPerson)
        .build()
    );

    public final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("Camera distance from the player. In Overhead mode this controls height.")
        .defaultValue(4.0)
        .min(1.0)
        .max(30.0)
        .sliderRange(1.0, 30.0)
        .build()
    );

    public final Setting<Boolean> freeLook = sgGeneral.add(new BoolSetting.Builder()
        .name("free-look")
        .description("Orbit the camera around the player without affecting movement direction. Disabled in Overhead mode.")
        .defaultValue(true)
        .visible(() -> cameraMode.get() == CameraMode.ThirdPerson)
        .build()
    );

    public final Setting<Double> sensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("sensitivity")
        .description("Free-look mouse sensitivity.")
        .defaultValue(1.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .visible(() -> cameraMode.get() == CameraMode.ThirdPerson && freeLook.get())
        .build()
    );

    // Independent camera yaw/pitch for free look.
    // Updated by ThirdSightMouseMixin via mouse delta.
    // In Overhead mode cameraYaw tracks player yaw and cameraPitch is locked to -90.
    public float cameraYaw   = 0f;
    public float cameraPitch = 0f;

    private Perspective previousPerspective = null;

    public ThirdSight() {
        super(HuntingUtilities.CATEGORY, "third-sight",
            "Third-person camera with configurable distance, no block clipping, free look, and overhead mode.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.options == null) return;
        cameraYaw   = mc.player.getYaw();
        cameraPitch = mc.player.getPitch();
        previousPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null) return;

        if (cameraMode.get() == CameraMode.Overhead) {
            // Lock pitch straight down, track yaw to player so forward stays consistent.
            cameraYaw   = mc.player.getYaw();
            cameraPitch = -90f;
        }

        // Re-enforce perspective in case setting changed mid-session or F5 was pressed.
        if (mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null && previousPerspective != null) {
            mc.options.setPerspective(previousPerspective);
        }
        previousPerspective = null;
    }

    /**
     * Called by ThirdSightMouseMixin — mouse delta should only apply in
     * ThirdPerson mode with free-look on. Overhead drives cameraYaw itself.
     */
    public boolean isFreeLookActive() {
        return isActive()
            && cameraMode.get() == CameraMode.ThirdPerson
            && freeLook.get();
    }
}