package leader.module.modules.movement;

import leader.Leader;
import leader.enums.BlinkModules;
import leader.enums.DelayModules;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.*;
import leader.mixin.IAccessorMinecraft;
import leader.module.Module;
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
    public final IntProperty stuckTicks = new IntProperty("Stuck Ticks", 10, 10, 20);
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int tick;
    private boolean using = false;

    private boolean knockbackRelease = false;
    private boolean internalToggle = false;
    private boolean releasing = false;

    public Stuck() {
        super("Stuck",false,false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled && this.knockbackRelease && !this.internalToggle) return;
        if (!enabled && !this.internalToggle) this.knockbackRelease = false;
        super.setEnabled(enabled);
    }

    public boolean isStuckActive() {
        return this.isEnabled() || this.using || this.knockbackRelease || this.releasing;
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
            if (event.getPacket() instanceof S12PacketEntityVelocity s12) {
                knockback = s12.getEntityID() == mc.thePlayer.getEntityId();
            } else if (event.getPacket() instanceof S27PacketExplosion s27) {
                knockback = s27.func_149149_c() != 0.0F || s27.func_149144_d() != 0.0F || s27.func_149147_e() != 0.0F;
            }
            if (knockback) {
                Leader.delayManager.setDelayState(true, DelayModules.VELOCITY);
                Leader.delayManager.delayedPacket.offer((Packet<INetHandlerPlayClient>) event.getPacket());
                event.setCancelled(true);
                this.knockbackRelease = true;
                tick = this.stuckTicks.getValue();
            }
        }
    }
    @EventTarget
    public void onTick(TickEvent event){
        if (using && event.getType() == EventType.PRE) {
            int releaseTick = this.stuckTicks.getValue();
            if (tick == releaseTick){
                this.setEnabledInternal(false);
                using = true;
            }
            if (tick == releaseTick + 1){
                this.knockbackRelease = false;
                this.setEnabledInternal(true);
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
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionZ = savedMotionZ;
            mc.thePlayer.motionY = savedMotionY;
            this.releasing = true;
            try {
                Leader.delayManager.setDelayState(false, DelayModules.VELOCITY);
                Leader.blinkManager.setBlinkState(false, BlinkModules.BLINK);
            } finally {
                this.releasing = false;
            }
            ((IAccessorMinecraft)mc).getTimer().timerSpeed = 1.0F;
        }
    }
}
