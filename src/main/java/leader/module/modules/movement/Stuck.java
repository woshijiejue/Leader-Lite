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
import net.minecraft.client.entity.EntityPlayerSP;
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
    private EntityPlayerSP motionOwner;
    private boolean internalReleasePending;

    public Stuck() {
        super("Stuck",false,false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled && this.knockbackRelease && !this.internalToggle) return;
        if (!enabled && !this.internalToggle) {
            boolean cleanup = this.using || this.knockbackRelease || this.motionOwner != null;
            this.knockbackRelease = false;
            // Internal release temporarily sets enabled=false while leaving using=true.
            // Module.setEnabled(false) is a no-op in that window, so explicitly stop its restart cycle.
            if (!this.isEnabled()) {
                if (cleanup) this.onDisabled();
                return;
            }
        }
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
            internalReleasePending = false;
            savedMotionX = mc.thePlayer.motionX;
            savedMotionY = mc.thePlayer.motionY;
            savedMotionZ = mc.thePlayer.motionZ;
            motionOwner = mc.thePlayer;
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
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer != motionOwner || mc.thePlayer.isDead) {
            this.setEnabled(false);
            return;
        }
        if (using) {
            int releaseTick = this.stuckTicks.getValue();
            if (internalReleasePending) {
                internalReleasePending = false;
                this.knockbackRelease = false;
                this.setEnabledInternal(true);
            } else if (tick >= releaseTick) {
                this.setEnabledInternal(false);
                using = true;
                internalReleasePending = true;
            }
            tick++;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && mc.thePlayer != null) {
            Leader.blinkManager.setBlinkState(true, BlinkModules.BLINK);
            KeyBinding.unPressAllKeys();
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && mc.thePlayer != null) {
            mc.thePlayer.movementInput.moveForward = 0.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && mc.thePlayer != null) {
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
        using = false;
        if (!internalToggle) { tick = 0; internalReleasePending = false; }
        if (!internalToggle) knockbackRelease = false;
        if (mc.thePlayer != null && mc.thePlayer == motionOwner) {
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionZ = savedMotionZ;
            mc.thePlayer.motionY = savedMotionY;
        }
        if (!internalToggle) motionOwner = null;
        this.releasing = true;
        try {
            if (Leader.delayManager != null && Leader.delayManager.getDelayModule() == DelayModules.VELOCITY) {
                if (mc.getNetHandler() == null) {
                    Leader.delayManager.delayedPacket.clear();
                    Leader.delayManager.delayModule = DelayModules.NONE;
                } else {
                    Leader.delayManager.setDelayState(false, DelayModules.VELOCITY);
                }
            }
            if (Leader.blinkManager != null && Leader.blinkManager.getBlinkingModule() == BlinkModules.BLINK) {
                if (mc.getNetHandler() == null) {
                    Leader.blinkManager.blinkedPackets.clear();
                    Leader.blinkManager.blinking = false;
                    Leader.blinkManager.blinkModule = BlinkModules.NONE;
                } else {
                    Leader.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                }
            }
        } finally {
            this.releasing = false;
        }
        ((IAccessorMinecraft)mc).getTimer().timerSpeed = 1.0F;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.setEnabled(false);
    }
}
