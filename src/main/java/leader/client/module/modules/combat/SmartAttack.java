package leader.client.module.modules.combat;

import net.minecraft.entity.EntityLivingBase;
import leader.client.event.EventTarget;
import leader.client.events.AttackEvent;
import leader.client.events.LeftClickMouseEvent;
import leader.client.events.UpdateEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;

public class SmartAttack extends Module {
    public SmartAttack() {
        super("SmartAttack", false, false);
        onKillAura = new BoolValue("OnKillAura", true, this);
        cancelAuraBlocking = new BoolValue("CancelAuraBlocking", true, onKillAura::getValue, this);
    }

    private final BoolValue onGround = new BoolValue("CancelGroundAttack", true, this);
    private final BoolValue onRising = new BoolValue("CancelRisingAttack", true, this);
    private final SliderValue stopHurtTime = new SliderValue("StopHurtTime", 7, 0, 9, Representation.INT, this);
    public static BoolValue onKillAura;
    public static BoolValue cancelAuraBlocking;
    public static boolean shouldCancel;
    private EntityLivingBase target;

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (isEnabled()) {
            target = (EntityLivingBase) event.getTarget();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (isEnabled()) {
            if (mc.thePlayer.getDistanceToEntity(target) > 6) target = null;
            if (target == null) {
                shouldCancel = false;
                return;
            }
            if (mc.thePlayer.onGround && onGround.getValue()) shouldCancel = true;
            if (mc.thePlayer.motionY >= 0 && onRising.getValue()) shouldCancel = true;
            if (target.hurtTime <= 2) shouldCancel = false;
            if (target.isBurning()) shouldCancel = false;
            if (mc.thePlayer.hurtTime > stopHurtTime.getValue().intValue()) shouldCancel = false;
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (shouldCancel) {
            event.setCancelled(true);
        }
    }
}
