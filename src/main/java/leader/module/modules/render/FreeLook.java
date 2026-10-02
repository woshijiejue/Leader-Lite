package leader.module.modules.render;

import leader.event.EventTarget;
import leader.events.TickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class FreeLook extends Module {
    public static FreeLook INSTANCE;
    public final BooleanProperty hold = new BooleanProperty("Hold", true);
    public final BooleanProperty autoF5 = new BooleanProperty("AutoF5", true);
    public static boolean using = false;
    public static boolean perspectiveToggled = false;
    public static float cameraYaw = 0.0F;
    public static float cameraPitch = 0.0F;
    public static float prevCameraYaw = 0.0F;
    public static float prevCameraPitch = 0.0F;
    private static int previousPerspective = 0;
    private static boolean stashed = false;
    private static float stashedYaw;
    private static float stashedPitch;
    private static float stashedPrevYaw;
    private static float stashedPrevPitch;
    private boolean latch = false;
    private boolean toggled = false;

    public FreeLook() {
        super("FreeLook", false, true);
        INSTANCE = this;
    }

    private void stop() {
        Minecraft mc = Minecraft.getMinecraft();
        using = false;
        perspectiveToggled = false;
        this.toggled = false;
        this.latch = false;
        if (mc.gameSettings != null) {
            mc.gameSettings.thirdPersonView = previousPerspective;
        }
    }

    @Override
    public void onDisabled() {
        this.stop();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            if (using || perspectiveToggled) this.stop();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings == null || mc.currentScreen != null) {
            if (perspectiveToggled || using) this.stop();
            return;
        }
        int key = this.getKey();
        boolean want;
        if (key == 0) {
            want = false;
        } else if (this.hold.getValue()) {
            want = KeyBindUtil.isKeyDown(key);
        } else {
            boolean down = KeyBindUtil.isKeyDown(key);
            if (down && !this.latch) this.toggled = !this.toggled;
            this.latch = down;
            want = this.toggled;
        }
        if (want) {
            if (!perspectiveToggled) {
                perspectiveToggled = true;
                previousPerspective = mc.gameSettings.thirdPersonView;
                cameraYaw = mc.thePlayer.rotationYaw;
                cameraPitch = mc.thePlayer.rotationPitch;
                prevCameraYaw = cameraYaw;
                prevCameraPitch = cameraPitch;
                if (this.autoF5.getValue()) {
                    mc.gameSettings.thirdPersonView = 1;
                }
            }
            using = true;
        } else if (perspectiveToggled || using) {
            this.stop();
        }
    }

    public static void applyMouse() {
        if (!perspectiveToggled || stashed) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        stashedYaw = mc.thePlayer.rotationYaw;
        stashedPitch = mc.thePlayer.rotationPitch;
        stashedPrevYaw = mc.thePlayer.prevRotationYaw;
        stashedPrevPitch = mc.thePlayer.prevRotationPitch;
        stashed = true;
        mc.thePlayer.rotationYaw = cameraYaw;
        mc.thePlayer.rotationPitch = cameraPitch;
        mc.thePlayer.prevRotationYaw = prevCameraYaw;
        mc.thePlayer.prevRotationPitch = prevCameraPitch;
    }

    public static void captureMouse() {
        if (!stashed) return;
        stashed = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        prevCameraYaw = cameraYaw;
        prevCameraPitch = cameraPitch;
        cameraYaw = mc.thePlayer.rotationYaw;
        cameraPitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationYaw = stashedYaw;
        mc.thePlayer.rotationPitch = stashedPitch;
        mc.thePlayer.prevRotationYaw = stashedPrevYaw;
        mc.thePlayer.prevRotationPitch = stashedPrevPitch;
    }

    public static float interpolatedYaw(float partialTicks) {
        return prevCameraYaw + (cameraYaw - prevCameraYaw) * partialTicks;
    }

    public static float interpolatedPitch(float partialTicks) {
        return prevCameraPitch + (cameraPitch - prevCameraPitch) * partialTicks;
    }

    public static boolean isUsing() {
        return using;
    }

    public boolean isActive() {
        return this.isEnabled() && perspectiveToggled;
    }
}