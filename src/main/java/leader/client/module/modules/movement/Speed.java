package leader.client.module.modules.movement;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.event.types.Priority;
import leader.client.events.LivingUpdateEvent;
import leader.client.events.StrafeEvent;
import leader.mixin.accessor.IAccessorEntity;
import leader.client.module.Module;
import leader.client.module.modules.player.Scaffold;
import leader.client.util.player.MoveUtil;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.SliderValue;

public class Speed extends Module {
    
    public final SliderValue multiplier = new SliderValue("multiplier", 1.0, 0.0, 10.0, Representation.FLOAT, this);
    public final SliderValue friction = new SliderValue("friction", 1.0, 0.0, 10.0, Representation.FLOAT, this);
    public final SliderValue strafe = new SliderValue("strafe", 0, 0, 100, Representation.INT, this);

    private boolean canBoost() {
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        return !scaffold.isEnabled() && MoveUtil.isForwardPressed()
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isInLava()
                && !((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    public Speed() {
        super("Speed", false);
    }

    @EventTarget(Priority.LOW)
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && this.canBoost()) {
            if (mc.thePlayer.onGround) {
                mc.thePlayer.motionY = 0.42F;
                MoveUtil.setSpeed(
                        MoveUtil.getJumpMotion() * (double) this.multiplier.getValue().floatValue(),
                        MoveUtil.getMoveYaw()
                );
            } else {
                if (this.friction.getValue() != 1.0F) {
                    event.setFriction(event.getFriction() * this.friction.getValue());
                }
                if (this.strafe.getValue() > 0) {
                    double speed = MoveUtil.getSpeed();
                    MoveUtil.setSpeed(speed * (double) ((float) (100 - this.strafe.getValue()) / 100.0F), MoveUtil.getDirectionYaw());
                    MoveUtil.addSpeed(
                            speed * (double) ((float) this.strafe.getValue().intValue() / 100.0F), MoveUtil.getMoveYaw()
                    );
                    MoveUtil.setSpeed(speed);
                }
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.canBoost()) {
            mc.thePlayer.movementInput.jump = false;
        }
    }
}
