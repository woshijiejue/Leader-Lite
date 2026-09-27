package leader.module.modules.movement;

import leader.Leader;
import leader.enums.BlinkModules;
import leader.enums.DelayModules;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.*;
import leader.mixin.IAccessorMinecraft;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int tick;
    private boolean using = false;
    private volatile boolean velocityPending = false;
    private int releaseWait = -1;
    private int releaseTicksLeft = 0;
    private boolean internalToggle = false;

    public final BooleanProperty velocityRelease = new BooleanProperty("Velocity Release", true);
    public final IntProperty releaseDelay = new IntProperty("Release Delay", 0, 0, 5, this.velocityRelease::getValue);
    public final IntProperty releaseTicks = new IntProperty("Release Ticks", 1, 1, 5, this.velocityRelease::getValue);

    public Stuck() {
        super("Stuck",false,false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.releaseTicksLeft > 0 && !this.internalToggle) {
            if (!enabled) this.releaseTicksLeft = 0;
            return;
        }
        super.setEnabled(enabled);
    }

    private void setEnabledInternal(boolean enabled) {
        this.internalToggle = true;
        try {
            super.setEnabled(enabled);
        } finally {
            this.internalToggle = false;
        }
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
        if (this.isEnabled() && event.getType() == EventType.RECEIVE && mc.thePlayer != null) {
            boolean knockback = false;
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                knockback = ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId();
            } else if (event.getPacket() instanceof S27PacketExplosion) {
                S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
                knockback = explosion.func_149149_c() != 0.0F || explosion.func_149144_d() != 0.0F || explosion.func_149147_e() != 0.0F;
            }
            if (knockback) {
                Leader.delayManager.setDelayState(true, DelayModules.VELOCITY);
                Leader.delayManager.delayedPacket.offer((Packet<INetHandlerPlayClient>) event.getPacket());
                event.setCancelled(true);
                if (this.velocityRelease.getValue()) {
                    this.velocityPending = true;
                } else {
                    tick = 11;
                }
            }
        }
    }
    @EventTarget
    public void onTick(TickEvent event){
        if (event.getType() != EventType.PRE) return;
        if (this.velocityPending) {
            this.velocityPending = false;
            if (this.isEnabled() && this.releaseWait < 0) this.releaseWait = this.releaseDelay.getValue();
        }
        if (this.releaseWait >= 0) {
            if (!this.isEnabled()) {
                this.releaseWait = -1;
            } else if (this.releaseWait == 0) {
                this.releaseWait = -1;
                this.setEnabledInternal(false);
                this.releaseTicksLeft = this.releaseTicks.getValue();
                return;
            } else {
                this.releaseWait--;
            }
        }
        if (this.releaseTicksLeft > 0) {
            this.releaseTicksLeft--;
            if (this.releaseTicksLeft == 0) this.setEnabledInternal(true);
            return;
        }
        if (using) {
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
            Leader.delayManager.setDelayState(false, DelayModules.VELOCITY);
            Leader.blinkManager.setBlinkState(false, BlinkModules.BLINK);
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionZ = savedMotionZ;
            mc.thePlayer.motionY = savedMotionY;
            ((IAccessorMinecraft)mc).getTimer().timerSpeed = 1.0F;
        }
    }
}
