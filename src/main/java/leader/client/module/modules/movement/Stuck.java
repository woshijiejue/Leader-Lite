package leader.client.module.modules.movement;

import leader.client.Leader;
import leader.client.enums.BlinkModules;
import leader.client.enums.DelayModules;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.*;
import leader.mixin.IAccessorMinecraft;
import leader.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int tick;
    private boolean using = false;

    public Stuck() {
        super("Stuck",false,false);
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            tick = 0;
            using = true;
            savedMotionX = mc.thePlayer.motionX;
            savedMotionY = mc.thePlayer.motionY;
            savedMotionZ = mc.thePlayer.motionZ;
        }
    }
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S12PacketEntityVelocity){
                S12PacketEntityVelocity s12PacketEntityVelocity = (S12PacketEntityVelocity) event.getPacket();
                if (s12PacketEntityVelocity.getEntityID() == mc.thePlayer.getEntityId()){
                    Leader.delayManager.setDelayState(true, DelayModules.VELOCITY);
                    tick = 10;
                    Leader.delayManager.delayedPacket.offer(s12PacketEntityVelocity);
                    event.setCancelled(true);
                }
            }
        }
    }
    @EventTarget
    public void onTick(TickEvent event){
        if (using && event.getType() == EventType.PRE) {
            if (tick == 10){
                this.setEnabled(false);
                using = true;
            }
            if (tick == 11){
                this.setEnabled(true);
                tick = 0;
            }
            tick++;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled()) {
            Leader.blinkManager.setBlinkState(true, BlinkModules.BLINK);
            KeyBinding.unPressAllKeys();
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            mc.thePlayer.movementInput.moveForward = 0.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
        }
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) {
            using = false;
            Leader.blinkManager.setBlinkState(false, BlinkModules.BLINK);
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionZ = savedMotionZ;
            mc.thePlayer.motionY = savedMotionY;
            Leader.delayManager.setDelayState(false, DelayModules.VELOCITY);
            ((IAccessorMinecraft)mc).getTimer().timerSpeed = 1.0F;
        }
    }
}
