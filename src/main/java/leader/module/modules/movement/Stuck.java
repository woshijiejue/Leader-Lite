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
import leader.property.properties.ModeProperty;
import leader.util.KeyBindUtil;
import leader.util.WorldTickUtil;
import leader.util.ISkipTickWorld;
import leader.util.SkipTickPacketPolicy;
import leader.util.TimerSpeedLease;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode= new ModeProperty("Mode",0,new String[]{"Normal","SkipTick"});
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
    private volatile boolean externalSkipFreeze;
    private volatile boolean externalAutoRelease;
    private boolean externalDelayOwned;
    private boolean externalDelayKnockback;
    private volatile boolean externalReleasingDelay;
    private WorldClient externalWorld;
    private volatile int requestedSkipTicks;
    private boolean requestedDelayKnockback;
    private EntityPlayerSP requestedPlayer;
    private WorldClient requestedWorld;
    private long freezeRequestSequence;
    private long startedFreezeRequest;

    public Stuck() {
        super("Stuck",false,false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && requestedSkipTicks > 0 && !internalToggle) abortExternalSkipFreeze();
        if (enabled && requestedSkipTicks > 0 && !internalToggle) return;
        if (!enabled && this.externalSkipFreeze && !this.internalToggle) {
            endExternalSkipFreeze();
            return;
        }
        if (enabled && this.externalSkipFreeze && !this.internalToggle) return;
        if (enabled && this.knockbackRelease && !this.internalToggle) return;
        if (!enabled && !this.internalToggle) {
            boolean cleanup = this.using || this.knockbackRelease || this.motionOwner != null;
            this.knockbackRelease = false;

            if (!this.isEnabled()) {
                if (cleanup) this.onDisabled();
                return;
            }
        }
        super.setEnabled(enabled);
    }

    public boolean isStuckActive() {
        return this.isEnabled() || this.using || this.knockbackRelease || this.releasing
                || this.externalSkipFreeze || this.externalReleasingDelay || requestedSkipTicks > 0;
    }

    public boolean beginExternalSkipFreeze(int ticks) {
        return beginExternalSkipFreeze(ticks, false, false);
    }

    public boolean freezeForTicks(int ticks) {
        return freezeForTicks(ticks, true);
    }

    public static boolean freezePlayerForTicks(int ticks) {
        if (Leader.moduleManager == null) return false;
        Stuck stuck = (Stuck) Leader.moduleManager.getModule(Stuck.class);
        return stuck != null && stuck.freezeForTicks(ticks);
    }

    public synchronized boolean freezeForTicks(int ticks, boolean delayKnockback) {
        if (!mc.isCallingFromMinecraftThread() || ticks <= 0 || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || mc.currentScreen != null || !mc.inGameHasFocus
                || isStuckActive() || WorldTickUtil.getSkipTicks() > 0) return false;
        if (Leader.blinkManager == null || Leader.blinkManager.getBlinkingModule() != BlinkModules.NONE) return false;
        if (Leader.delayManager == null || Leader.delayManager.getDelayModule() != DelayModules.NONE
                || !Leader.delayManager.delayedPacket.isEmpty()) return false;
        requestedDelayKnockback = delayKnockback;
        requestedPlayer = mc.thePlayer;
        requestedWorld = mc.theWorld;
        freezeRequestSequence++;
        requestedSkipTicks = ticks;
        return true;
    }

    private synchronized boolean beginExternalSkipFreeze(int ticks, boolean autoRelease, boolean delayKnockback) {
        if (!mc.isCallingFromMinecraftThread() || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.isDead || mc.currentScreen != null || !mc.inGameHasFocus || ticks <= 0) return false;
        if (isStuckActive() || externalReleasingDelay) return false;
        if (WorldTickUtil.getSkipTicks() > 0) return false;
        if (Leader.blinkManager == null || Leader.blinkManager.getBlinkingModule() != BlinkModules.NONE) return false;
        if (delayKnockback && (Leader.delayManager == null
                || Leader.delayManager.getDelayModule() != DelayModules.NONE
                || !Leader.delayManager.delayedPacket.isEmpty())) return false;
        externalAutoRelease = autoRelease;
        externalDelayOwned = false;
        externalDelayKnockback = delayKnockback;
        externalWorld = mc.theWorld;
        TimerSpeedLease.cancelActive();
        motionOwner = mc.thePlayer;
        savedMotionX = mc.thePlayer.motionX;
        savedMotionY = mc.thePlayer.motionY;
        savedMotionZ = mc.thePlayer.motionZ;
        KeyBinding.unPressAllKeys();
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0.0;
        mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = mc.thePlayer.movementInput.sneak = false;
        WorldTickUtil.setSkipTicks(ticks);
        externalSkipFreeze = true;
        return true;
    }

    public boolean isExternalSkipFreezeActive() { return externalSkipFreeze; }
    public boolean isReleasingExternalDelay() { return externalReleasingDelay; }
    public boolean isTimedSkipFreezeActive() { return externalSkipFreeze && externalAutoRelease; }
    public boolean hasPendingSkipFreeze() { return requestedSkipTicks > 0; }
    public long getFreezeRequestSequence() { return freezeRequestSequence; }
    public boolean hasStartedFreezeRequest(long sequence) { return sequence > 0 && startedFreezeRequest == sequence; }
    public int getFreezeTicksRemaining() {
        if (requestedSkipTicks > 0) return requestedSkipTicks;
        return externalSkipFreeze && externalWorld instanceof ISkipTickWorld
                ? ((ISkipTickWorld) externalWorld).leader$getSkipTicks() : 0;
    }

    public void abortExternalSkipFreeze() {
        requestedSkipTicks = 0;
        requestedPlayer = null;
        requestedWorld = null;
        endExternalSkipFreeze(false);
    }

    public void endExternalSkipFreeze() {
        endExternalSkipFreeze(true);
    }

    private synchronized void endExternalSkipFreeze(boolean releaseKnockback) {
        if (!externalSkipFreeze) return;
        externalReleasingDelay = true;
        externalSkipFreeze = false;
        boolean releaseDelayed = externalDelayOwned;
        externalAutoRelease = false;
        externalDelayOwned = false;
        externalDelayKnockback = false;
        if (externalWorld instanceof ISkipTickWorld) ((ISkipTickWorld) externalWorld).leader$setSkipTicks(0);
        boolean samePlayer = mc.thePlayer != null && mc.thePlayer == motionOwner && mc.theWorld == externalWorld;
        if (samePlayer) {
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionY = savedMotionY;
            mc.thePlayer.motionZ = savedMotionZ;
        }
        if (samePlayer && mc.currentScreen == null && mc.inGameHasFocus) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindForward.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindBack.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindLeft.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindRight.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindJump.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        motionOwner = null;
        try {
            if (releaseDelayed && Leader.delayManager != null) {
                if (releaseKnockback && samePlayer && !mc.thePlayer.isDead) Leader.delayManager.releaseDelay(DelayModules.STUCK);
                else Leader.delayManager.discardDelay(DelayModules.STUCK);
            }
        } finally {
            externalWorld = null;
            externalReleasingDelay = false;
        }
    }

    public static boolean shouldSuppressExternalSkipPacket(Packet<?> packet) {
        return isExternalPacketFreezeActive() && SkipTickPacketPolicy.isGameplayPacket(packet);
    }

    public static boolean isExternalPacketFreezeActive() {
        if (Leader.moduleManager == null) return false;
        Stuck stuck = (Stuck) Leader.moduleManager.getModule(Stuck.class);
        return stuck != null && (stuck.externalSkipFreeze || stuck.externalReleasingDelay);
    }

    public static boolean hasRequestedSkipFreeze() {
        if (Leader.moduleManager == null) return false;
        Stuck stuck = (Stuck) Leader.moduleManager.getModule(Stuck.class);
        return stuck != null && stuck.hasPendingSkipFreeze();
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
        EntityPlayerSP owner = motionOwner;
        if (externalSkipFreeze && externalAutoRelease && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            boolean knockback = externalDelayKnockback && owner != null && event.getPacket() instanceof S12PacketEntityVelocity
                    && ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == owner.getEntityId();
            if (event.getPacket() instanceof S27PacketExplosion) {
                S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
                knockback = externalDelayKnockback && (explosion.func_149149_c() != 0
                        || explosion.func_149144_d() != 0 || explosion.func_149147_e() != 0);
            }
            if (knockback) {
                synchronized (this) {
                    if (externalSkipFreeze && externalAutoRelease && Leader.delayManager != null
                            && Leader.delayManager.beginDelay(DelayModules.STUCK, (Packet<INetHandlerPlayClient>) event.getPacket())) {
                        externalDelayOwned = true;
                        event.setCancelled(true);
                    }
                }
            }
            return;
        }
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
        if (event.getType() == EventType.PRE && requestedSkipTicks > 0) {
            int ticks = requestedSkipTicks;
            boolean valid = mc.thePlayer == requestedPlayer && mc.theWorld == requestedWorld;
            requestedSkipTicks = 0;
            requestedPlayer = null;
            requestedWorld = null;
            if (valid && beginExternalSkipFreeze(ticks, true, requestedDelayKnockback)) {
                startedFreezeRequest = freezeRequestSequence;
            }
        }
        if (externalSkipFreeze) {
            if (mc.thePlayer == null || mc.theWorld != externalWorld || mc.thePlayer != motionOwner || mc.thePlayer.isDead
                    || mc.currentScreen != null || !mc.inGameHasFocus) {
                endExternalSkipFreeze();
                return;
            }
            if (externalAutoRelease && event.getType() == EventType.POST) {
                if (getFreezeTicksRemaining() == 0) endExternalSkipFreeze();
            }
            return;
        }
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer != motionOwner || mc.thePlayer.isDead) {
            this.setEnabled(false);
            return;
        }

        if (this.isEnabled() && this.using && mode.getValue() == 1) {
            if (WorldTickUtil.getSkipTicks() == 0) {
                if (Leader.blinkManager.getBlinkingModule() == BlinkModules.NONE) {
                    Leader.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                }
                WorldTickUtil.setSkipTicks(stuckTicks.getValue());
            }
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
        if ((this.isEnabled() || externalSkipFreeze) && mc.thePlayer != null) {
            if (this.isEnabled() && !externalSkipFreeze && mode.getValue() == 0) {
                Leader.blinkManager.setBlinkState(true, BlinkModules.BLINK);
            }
            KeyBinding.unPressAllKeys();
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if ((this.isEnabled() || externalSkipFreeze) && mc.thePlayer != null) {
            mc.thePlayer.movementInput.moveForward = 0.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if ((this.isEnabled() || externalSkipFreeze) && mc.thePlayer != null) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() || externalSkipFreeze) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
        }
    }

    @Override
    public void onDisabled() {
        endExternalSkipFreeze();
        WorldTickUtil.clearSkipTicks();
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
        if (requestedSkipTicks > 0) abortExternalSkipFreeze();
        if (externalSkipFreeze) { abortExternalSkipFreeze(); return; }
        this.setEnabled(false);
    }
}
