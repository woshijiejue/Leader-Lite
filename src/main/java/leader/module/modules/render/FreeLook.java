package leader.module.modules.render;

import net.minecraft.client.Minecraft;
import leader.event.EventTarget;
import leader.events.TickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.util.KeyBindUtil;

public class FreeLook extends Module {
    public static FreeLook INSTANCE;
    public final BooleanProperty autoF5 = new BooleanProperty("AutoF5", true);
    public final BooleanProperty hold = new BooleanProperty("Hold", false);
    public boolean active = false;
    public float cameraYaw;
    public float cameraPitch;
    public float prevCameraYaw;
    public float prevCameraPitch;
    private int prevPerspective = 0;

    public FreeLook() {
        super("FreeLook", false, true);
        INSTANCE = this;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        if (hold.getValue()) {
            boolean keyDown = KeyBindUtil.isKeyDown(this.getKey());
            if (keyDown && !this.isEnabled()) {
                setEnabled(true);
            } else if (!keyDown && this.isEnabled()) {
                setEnabled(false);
            }
            if (this.isEnabled() && !this.active) {
                this.active = true;
                this.prevPerspective = mc.gameSettings.thirdPersonView;
                if (this.autoF5.getValue()) {
                    mc.gameSettings.thirdPersonView = 1;
                }
                this.cameraYaw = mc.thePlayer.rotationYaw;
                this.cameraPitch = mc.thePlayer.rotationPitch;
                this.prevCameraYaw = this.cameraYaw;
                this.prevCameraPitch = this.cameraPitch;
            }
            if (this.isEnabled() && this.active) {
                this.prevCameraYaw = this.cameraYaw;
                this.prevCameraPitch = this.cameraPitch;
            }
        } else {
            if (!this.isEnabled()) {
                if (this.active) {
                    this.active = false;
                    mc.gameSettings.thirdPersonView = this.prevPerspective;
                }
                return;
            }

            boolean isKeyDown = mc.currentScreen == null && KeyBindUtil.isKeyDown(this.getKey());
            if (isKeyDown) {
                if (!this.active) {
                    this.active = true;
                    this.prevPerspective = mc.gameSettings.thirdPersonView;
                    if (this.autoF5.getValue()) {
                        mc.gameSettings.thirdPersonView = 1;
                    }
                    this.cameraYaw = mc.thePlayer.rotationYaw;
                    this.cameraPitch = mc.thePlayer.rotationPitch;
                    this.prevCameraYaw = this.cameraYaw;
                    this.prevCameraPitch = this.cameraPitch;
                }
            } else if (this.active) {
                this.active = false;
                mc.gameSettings.thirdPersonView = this.prevPerspective;
            }

            if (this.active) {
                this.prevCameraYaw = this.cameraYaw;
                this.prevCameraPitch = this.cameraPitch;
            }
        }
    }

    @Override
    public void onDisabled() {
        if (this.active) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.gameSettings.thirdPersonView = this.prevPerspective;
            this.active = false;
        }
    }

    public boolean isActive() {
        return this.isEnabled() && this.active;
    }
}