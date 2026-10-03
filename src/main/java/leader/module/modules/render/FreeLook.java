package leader.module.modules.render;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.LoadWorldEvent;
import leader.events.TickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.lwjgl.opengl.Display;

public class FreeLook extends Module {
    public static FreeLook INSTANCE;
    public final BooleanProperty hold = new BooleanProperty("Hold", true);
    public static boolean using = false;
    public static boolean perspectiveToggled = false;
    public static float cameraYaw = 0.0F;
    public static float cameraPitch = 0.0F;
    public static float prevCameraYaw = 0.0F;
    public static float prevCameraPitch = 0.0F;
    private int previousPerspective;
    private EntityPlayerSP cameraPlayer;
    private final FreeLookActivation activation = new FreeLookActivation();

    public FreeLook() {
        super("FreeLook", false, true);
        INSTANCE = this;
    }

    private void stop() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean wasActive = perspectiveToggled;
        using = false;
        perspectiveToggled = false;
        cameraPlayer = null;
        if (wasActive && mc.gameSettings != null) {
            mc.gameSettings.thirdPersonView = previousPerspective;
        }
    }

    @Override
    public void onDisabled() {
        this.stop();
    }

    /** Called instead of ModuleManager's generic toggle; repeated key events are ignored. */
    public boolean onBindPressed() {
        Minecraft mc = Minecraft.getMinecraft();
        if (getKey() == KeyBindUtil.NONE || !available(mc) || !activation.press()) return false;
        if (hold.getValue()) {
            setEnabled(true);
            updateActivation();
            return false;
        }
        boolean notify = toggle();
        updateActivation();
        return notify;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE || event.getType() == EventType.POST) updateActivation();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        stop();
        activation.reset(getKey() != KeyBindUtil.NONE && KeyBindUtil.isKeyDown(getKey()));
    }

    @Override
    public void setKey(int key) {
        if (key != getKey()) {
            stop();
            activation.reset(key != KeyBindUtil.NONE && KeyBindUtil.isKeyDown(key));
        }
        super.setKey(key);
    }

    @Override
    public void verifyValue(String name) {
        if ("Hold".equalsIgnoreCase(name)) {
            stop();
            activation.reset(getKey() != KeyBindUtil.NONE && KeyBindUtil.isKeyDown(getKey()));
        }
    }

    private boolean available(Minecraft mc) {
        return mc.thePlayer != null && mc.theWorld != null && mc.gameSettings != null
                && mc.currentScreen == null && mc.inGameHasFocus && Display.isActive() && !mc.thePlayer.isPlayerSleeping()
                && mc.getRenderViewEntity() == mc.thePlayer;
    }

    private void updateActivation() {
        Minecraft mc = Minecraft.getMinecraft();
        int key = this.getKey();
        boolean down = key != KeyBindUtil.NONE && KeyBindUtil.isKeyDown(key);
        boolean want = activation.update(isEnabled(), hold.getValue(), down, available(mc));
        if (cameraPlayer != null && cameraPlayer != mc.thePlayer) stop();
        if (want) {
            if (!perspectiveToggled) {
                perspectiveToggled = true;
                previousPerspective = mc.gameSettings.thirdPersonView;
                cameraPlayer = mc.thePlayer;
                cameraYaw = mc.thePlayer.rotationYaw;
                cameraPitch = mc.thePlayer.rotationPitch;
                prevCameraYaw = cameraYaw;
                prevCameraPitch = cameraPitch;
            }
            // F5 cannot take FreeLook back to first-person while it owns the camera.
            mc.gameSettings.thirdPersonView = 1;
            using = true;
        } else if (perspectiveToggled || using) {
            this.stop();
        }
    }

    public static void updateCamera() {
        if (INSTANCE != null) INSTANCE.updateActivation();
    }

    public static boolean turnCamera(float yawDelta, float pitchDelta) {
        updateCamera();
        if (!using) return false;
        cameraYaw += yawDelta * 0.15F;
        cameraPitch = Math.max(-90, Math.min(90, cameraPitch - pitchDelta * 0.15F));
        // Mouse deltas are already frame/sensitivity adjusted by vanilla.
        prevCameraYaw = cameraYaw;
        prevCameraPitch = cameraPitch;
        return true;
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
