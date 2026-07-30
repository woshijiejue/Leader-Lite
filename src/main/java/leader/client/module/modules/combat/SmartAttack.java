package leader.client.module.modules.combat;

import net.minecraft.entity.EntityLivingBase;
import leader.client.event.EventTarget;
import leader.client.events.AttackEvent;
import leader.client.events.LeftClickMouseEvent;
import leader.client.events.UpdateEvent;
import leader.client.module.Module;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.IntProperty;

import static leader.client.config.Config.mc;

public class SmartAttack extends Module {
    public SmartAttack(){super("SmartAttack",false,false);}
    private final BooleanProperty onGround = new BooleanProperty("CancelGroundAttack",true);
    private final BooleanProperty onRising = new BooleanProperty("CancelRisingAttack",true);
    private final IntProperty stopHurtTime = new IntProperty("StopHurtTime",7,0,9);
    public static final BooleanProperty onKillAura = new BooleanProperty("OnKillAura",true);
    public static final BooleanProperty cancelAuraBlocking = new BooleanProperty("CancelAuraBlocking",true,onKillAura::getValue);
    public static boolean shouldCancel;
    private EntityLivingBase target;
    @EventTarget
    public void onAttack(AttackEvent event){
        if (isEnabled()){
            target = (EntityLivingBase) event.getTarget();
        }
    }
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (isEnabled()){
            if (mc.thePlayer.getDistanceToEntity(target) > 6)target = null;
            if (target == null)
            {
                shouldCancel = false;
                return;
            }
            if (mc.thePlayer.onGround && onGround.getValue())shouldCancel = true;
            if (mc.thePlayer.motionY >= 0 && onRising.getValue())shouldCancel = true;
            if (target.hurtTime <= 2)shouldCancel = false;
            if (target.isBurning())shouldCancel = false;
            if (mc.thePlayer.hurtTime > stopHurtTime.getValue())shouldCancel = false;
        }
    }
    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (shouldCancel) {
            event.setCancelled(true);
        }
    }
}
