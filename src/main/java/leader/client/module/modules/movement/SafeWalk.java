package leader.client.module.modules.movement;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.SafeWalkEvent;
import leader.client.events.UpdateEvent;
import leader.client.module.Module;
import leader.client.module.modules.player.Scaffold;
import leader.client.util.player.ItemUtil;
import leader.client.util.player.MoveUtil;
import leader.client.util.player.PlayerUtil;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;

public class SafeWalk extends Module {
    
    public final SliderValue motion = new SliderValue("motion", 1.0, 0.5, 1.0, Representation.FLOAT, this);
    public final SliderValue speedMotion = new SliderValue("speed-motion", 1.0, 0.5, 1.5, Representation.FLOAT, this);
    public final BoolValue air = new BoolValue("air", false, this);
    public final BoolValue directionCheck = new BoolValue("direction-check", true, this);
    public final BoolValue pitCheck = new BoolValue("pitch-check", true, this);
    public final BoolValue requirePress = new BoolValue("require-press", false, this);
    public final BoolValue blocksOnly = new BoolValue("blocks-only", true, this);

    private boolean canSafeWalk() {
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
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
        if (this.isEnabled()) {
            if (this.canSafeWalk()) {
                event.setSafeWalk(true);
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
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
}
