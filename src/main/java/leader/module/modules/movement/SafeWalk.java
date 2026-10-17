package leader.module.modules.movement;

import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.SafeWalkEvent;
import leader.events.UpdateEvent;
import leader.module.Module;
import leader.module.modules.player.Scaffold;
import leader.util.ItemUtil;
import leader.util.MoveUtil;
import leader.util.PlayerUtil;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.ModeProperty;
import leader.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;

public class SafeWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean forcedSneak;
    public final FloatProperty motion = new FloatProperty("motion", 1.0F, 0.5F, 1.0F);
    public final FloatProperty speedMotion = new FloatProperty("speed-motion", 1.0F, 0.5F, 1.5F);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SafeWalk", "Sneak"});
    public final BooleanProperty air = new BooleanProperty("air", false);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty pitCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty requirePress = new BooleanProperty("require-press", false);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);

    private boolean canSafeWalk() {
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            return false;
        } else if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.pitCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else if (this.blocksOnly.getValue() && !ItemUtil.isHoldingBlock()) {
            return false;
        } else {
            return (!this.requirePress.getValue() || mc.gameSettings.keyBindUseItem.isKeyDown()) && (mc.thePlayer.onGround && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)
                    || this.air.getValue() && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -2.0));
        }
    }

    public SafeWalk() {
        super("SafeWalk", false);
    }

    @EventTarget
    public void onMove(SafeWalkEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 0 && this.canSafeWalk()) {
            event.setSafeWalk(true);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.mode.getValue() == 1) {
            this.updateSneakState();
            return;
        }
        if (this.mode.getValue() == 0) {
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && this.canSafeWalk()) {
                if (MoveUtil.getSpeedLevel() <= 0) {
                    if (this.motion.getValue() != 1.0F) {
                        MoveUtil.setSpeed(MoveUtil.getSpeed() * (double) this.motion.getValue());
                    }
                } else if (this.speedMotion.getValue() != 1.0F) {
                    MoveUtil.setSpeed(MoveUtil.getSpeed() * (double) this.speedMotion.getValue());
                }
            }
        }
    }

    private void updateSneakState() {
        boolean shouldSneak = canSneakAtEdge();
        if (shouldSneak) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        } else if (this.forcedSneak) {

            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        }
        this.forcedSneak = shouldSneak;
    }

    private boolean canSneakAtEdge() {
        if (mc.currentScreen != null || !mc.thePlayer.onGround || !MoveUtil.isForwardPressed()) return false;
        if (this.directionCheck.getValue() && !mc.gameSettings.keyBindForward.isKeyDown()) return false;
        if (this.pitCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) return false;
        if (this.blocksOnly.getValue() && !ItemUtil.isHoldingBlock()) return false;
        if (this.requirePress.getValue() && !mc.gameSettings.keyBindUseItem.isKeyDown()) return false;
        if (mc.gameSettings.keyBindJump.isKeyDown()) return false;
        double[] predicted = MoveUtil.predictMovement();
        double distance = Math.max(0.08D, Math.min(0.30D, Math.hypot(predicted[0], predicted[1]) * 0.75D));
        AxisAlignedBB current = mc.thePlayer.getEntityBoundingBox();
        AxisAlignedBB next = current.offset(predicted[0], -1.0D, predicted[1]);
        AxisAlignedBB edge = next.offset(predicted[0] == 0D ? 0D : Math.signum(predicted[0]) * distance,
                0D, predicted[1] == 0D ? 0D : Math.signum(predicted[1]) * distance);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, next).isEmpty()
                || mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, edge).isEmpty();
    }

    @Override
    public void onDisabled() {
        if (this.forcedSneak && mc.gameSettings != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        }
        this.forcedSneak = false;
    }
}
