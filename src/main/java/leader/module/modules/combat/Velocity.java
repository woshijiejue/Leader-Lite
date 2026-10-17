package leader.module.modules.combat;

import com.google.common.base.CaseFormat;
import leader.event.EventManager;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.*;
import leader.mixin.IAccessorEntity;
import leader.mixin.IAccessorMinecraft;
import leader.module.modules.combat.KillAura;
import leader.module.Module;
import leader.module.modules.movement.LongJump;
import leader.module.modules.movement.Stuck;
import leader.module.modules.player.Scaffold;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.ModeProperty;
import leader.property.properties.PercentProperty;
import leader.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.potion.Potion;
import leader.Leader;
import leader.enums.DelayModules;
import leader.module.modules.player.KeepSprint;

import java.util.Objects;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long SKIP_HURT_CONFIRMATION_NANOS = 250_000_000L;
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla","Prediction","GrimReduce","SkipTick"});
    public final IntProperty skipTick = new IntProperty("SkipTick",3,1,5,() -> mode.getValue() == 3);
    public final BooleanProperty skipRangeCheck = new BooleanProperty("Range Check", true, () -> mode.getValue() == 3);
    public final FloatProperty skipRange = new FloatProperty("Range", 3.0F, 0.0F, 6.0F,
            () -> mode.getValue() == 3 && skipRangeCheck.getValue());
    public final BooleanProperty reduce = new BooleanProperty("Reduce", true, () -> mode.getValue() == 1);
    public final ModeProperty reduceMode = new ModeProperty("ReduceMode", 0, new String[]{"Attack", "ReleaseWhenCanAttack", "ReleaseBeforeCanAttack", "ReleaseWhenSprinting"}, () -> mode.getValue() == 1 && reduce.getValue());
    public final IntProperty grimReduceTicks = new IntProperty("Grim Reduce Ticks", 4, 1, 10, () -> mode.getValue() == 2);
    private final BooleanProperty extraAttack = new BooleanProperty("ExtraAttack", false, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() != 0);
    private final BooleanProperty reduceWhenCanAttack = new BooleanProperty("Reduce When Can Attack", true, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() == 0);
    public final BooleanProperty cancelKillAuraAttack = new BooleanProperty("CancelKillAuraAttack", false, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() == 0);
    private final BooleanProperty onlySprinting = new BooleanProperty("Only Sprinting", true, () -> mode.getValue() == 1 && reduceMode.getValue() == 0 && reduce.getValue());
    public final BooleanProperty smartTimes = new BooleanProperty("SmartTimes",true, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0);
    public final IntProperty attackTimes = new IntProperty("Attack Times", 1, 1, 5, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0 && !smartTimes.getValue());
    public final BooleanProperty keepSprint = new BooleanProperty("KeepSprint",false, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0);
    public final BooleanProperty testMode = new BooleanProperty("TestMode",false, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0);
    private final IntProperty stopBlockHurtTime = new IntProperty("StopBlockHurtTime",2,0,10, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0 && testMode.getValue());
    public final BooleanProperty airPush = new BooleanProperty("AirPush", false, () -> this.mode.getValue() == 1 && this.reduce.getValue());

    public final BooleanProperty jump = new BooleanProperty("Jump", true, () -> mode.getValue() >= 1);
    public final BooleanProperty delay = new BooleanProperty("Delay", false, () -> mode.getValue() == 1 || mode.getValue() == 2);
    public final IntProperty delayTicks = new IntProperty("Delay Ticks", 1, 1, 10, () -> (mode.getValue() == 1 || mode.getValue() == 2) && delay.getValue() && !this.airBuffer.getValue());
    public final BooleanProperty forceDelayRisingToFalling = new BooleanProperty("Force Delay Rising To Falling",false,() -> (mode.getValue() == 1 || mode.getValue() == 2) && delay.getValue() && !this.airBuffer.getValue());
    public final BooleanProperty airBuffer = new BooleanProperty("Delay Till On Ground", true, () -> (mode.getValue() == 1 || mode.getValue() == 2) && delay.getValue());
    public final BooleanProperty groundDelay = new BooleanProperty("Ground Delay", false, () -> (mode.getValue() == 1 || mode.getValue() == 2) && delay.getValue() && !airBuffer.getValue());
    public final BooleanProperty rotate = new BooleanProperty("Rotate", false, () -> this.mode.getValue() == 1);
    public final IntProperty rotateTick = new IntProperty("Rotate Ticks", 3, 1, 12, () -> this.mode.getValue() == 1 && this.rotate.getValue());
    public final BooleanProperty autoMove = new BooleanProperty("Auto Move", false, () -> this.mode.getValue() == 1 && this.rotate.getValue());
    public final PercentProperty chance = new PercentProperty("Chance", 100, () -> mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("Horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("Vertical", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("Explosions Horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("Explosions Vertical", 100, () -> mode.getValue() == 0);
    public final BooleanProperty fakeCheck = new BooleanProperty("Fake Check", true);
    public final BooleanProperty debug = new BooleanProperty("Debug", false);
    private int chanceCounter = 0;
    private int rotateTickCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean delayFlag = false;
    private boolean jumpFlag = false;
    private boolean grimActive = false;
    private int grimTick = 0;
    public static boolean hasReceivedVelocity;
    private int ticksSinceVelocity = -1;
    private int airPushLeft = 0;
    private volatile boolean skipSequence;
    private volatile boolean skipPendingFreeze;
    private volatile boolean skipFreezeActive;
    private volatile boolean skipReleasingDelay;
    private volatile boolean skipAttackWindow;
    private volatile boolean skipWaitingForMovement;
    private volatile boolean skipDelayOnly;
    private long skipDelayDeadlineTick;
    private long skipReleaseMovementSequence;
    private volatile int skipBoostTicks;
    private int skipBoostTotal;
    private int skipFreezeTicksRemaining;
    private EntityPlayerSP skipPlayer;
    private WorldClient skipWorld;
    private long skipFreezeStarted;
    private boolean skipOwnsDelay;
    private volatile boolean skipSuppressPackets;
    private final TimerSpeedLease skipTimer = new TimerSpeedLease();
    private volatile long combatTick;
    private long killAuraSuppressedTick = Long.MIN_VALUE;
    private long skipAttackClaimTick = Long.MIN_VALUE;
    private boolean skipHurtConfirmed;
    private long skipHurtStatusNanos;
    private EntityPlayerSP skipHurtPlayer;
    private WorldClient skipHurtWorld;

    private double knockbackX = 0;
    private float[] targetRotation = null;
    private double knockbackZ = 0;
    public int reduceTick = -1;
    public int hitCount;
    public static boolean extraAttacked,velocityAttacked = false;
    public static boolean stoppedBlock = false;
    public static boolean cancellingKillAuraAttack = false;
    public Velocity() {
        super("Velocity", false, false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private int computeReduceTicks(int motionX, int motionZ) {
        double kb = Math.hypot(motionX, motionZ);
        double ticksExact = 0.000643153527 * kb + 2.9419087136;
        int ticks = (int) Math.round(ticksExact);

        if (ticks < 1) ticks = 1;
        if (ticks > 10) ticks = 10;

        return ticks;
    }
    private boolean isStuckActive() {
        Stuck stuck = (Stuck) Leader.moduleManager.getModule(Stuck.class);
        return stuck != null && stuck.isStuckActive();
    }

    private void resetForStuck() {
        pendingExplosion = false;
        allowNext = true;
        hasReceivedVelocity = false;
        airPushLeft = 0;
        rotateTickCounter = 0;
        targetRotation = null;
        jumpFlag = false;
        velocityAttacked = false;
        grimActive = false;
        cancellingKillAuraAttack = false;
        delayFlag = false;
    }

    private Stuck stuckModule() { return (Stuck) Leader.moduleManager.getModule(Stuck.class); }

    private synchronized void clearSkipHurtConfirmation() {
        skipHurtConfirmed = false;
        skipHurtStatusNanos = 0;
        skipHurtPlayer = null;
        skipHurtWorld = null;
    }

    private synchronized void recordSkipHurtConfirmation() {
        clearSkipHurtConfirmation();
        if (mc.thePlayer == null || mc.theWorld == null || isStuckActive()
                || skipSequence || skipPendingFreeze || skipFreezeActive || skipReleasingDelay
                || skipWaitingForMovement || skipAttackWindow || skipDelayOnly) return;
        skipHurtPlayer = mc.thePlayer;
        skipHurtWorld = mc.theWorld;
        skipHurtStatusNanos = System.nanoTime();
        skipHurtConfirmed = true;
    }

    private synchronized boolean consumeSkipHurtConfirmation() {
        long age = System.nanoTime() - skipHurtStatusNanos;
        boolean confirmed = skipHurtConfirmed && skipHurtPlayer == mc.thePlayer && skipHurtWorld == mc.theWorld
                && age >= 0 && age <= SKIP_HURT_CONFIRMATION_NANOS;
        clearSkipHurtConfirmation();
        return confirmed;
    }

    private synchronized void resetSkipState(boolean releaseDelay) {
        clearSkipHurtConfirmation();
        Stuck stuck = Leader.moduleManager == null ? null : stuckModule();

        if (skipFreezeActive && stuck != null && stuck.isExternalSkipFreezeActive()
                && !stuck.isTimedSkipFreezeActive()) stuck.endExternalSkipFreeze();
        skipFreezeActive = false;
        DelayModules delayOwner = skipDelayOnly ? DelayModules.VELOCITY : DelayModules.VELOCITY_SKIP_TICK;
        if (skipOwnsDelay && Leader.delayManager != null && Leader.delayManager.getDelayModule() == delayOwner) {
            skipReleasingDelay = true;
            try {
                if (releaseDelay && mc.thePlayer == skipPlayer && mc.theWorld == skipWorld
                        && mc.getNetHandler() != null && !mc.thePlayer.isDead) {
                    Leader.delayManager.releaseDelay(delayOwner);
                } else {
                    Leader.delayManager.discardDelay(delayOwner);
                }
            } finally { skipReleasingDelay = false; }
        }
        skipTimer.release();
        skipSequence = skipPendingFreeze = skipFreezeActive = skipReleasingDelay = skipAttackWindow = false;
        skipWaitingForMovement = false;
        skipDelayOnly = false;
        skipDelayDeadlineTick = 0;
        skipBoostTicks = 0;
        skipBoostTotal = 0;
        skipFreezeTicksRemaining = 0;
        skipOwnsDelay = false;
        skipSuppressPackets = false;
        skipPlayer = null; skipWorld = null;
        skipFreezeStarted = 0;
        jumpFlag = false;
        cancellingKillAuraAttack = false;
    }

    private synchronized boolean beginSkipSequence(Packet<?> packet, boolean hurtConfirmed) {
        if (skipSequence || skipAttackWindow || skipWaitingForMovement || skipDelayOnly
                || mc.thePlayer == null || mc.theWorld == null || isStuckActive()
                || Leader.delayManager == null || Leader.delayManager.getDelayModule() != DelayModules.NONE
                || !Leader.delayManager.delayedPacket.isEmpty() || WorldTickUtil.getSkipTicks() > 0
                || mc.thePlayer.isDead || mc.thePlayer.hurtTime <= 0 && !hurtConfirmed || mc.thePlayer.isRiding()
                || mc.currentScreen != null || !mc.inGameHasFocus) return false;

        boolean delayOnly = !isSkipTargetInRange();
        if (delayOnly) queuePredictionDelay((Packet<INetHandlerPlayClient>) packet);
        else if (!Leader.delayManager.beginDelay(DelayModules.VELOCITY_SKIP_TICK, (Packet<INetHandlerPlayClient>) packet)) return false;
        skipOwnsDelay = true;
        skipSuppressPackets = false;
        skipPlayer = mc.thePlayer; skipWorld = mc.theWorld;
        skipBoostTotal = skipTick.getValue();
        skipDelayDeadlineTick = combatTick + skipBoostTotal;
        skipFreezeStarted = System.nanoTime();
        skipDelayOnly = delayOnly;
        skipSequence = !delayOnly;
        skipFreezeActive = false;
        skipReleasingDelay = false;
        skipAttackWindow = false;
        skipPendingFreeze = !delayOnly;
        return true;
    }

    private boolean isSkipTargetInRange() {
        if (!skipRangeCheck.getValue()) return true;
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return false;
        float range = skipRange.getValue();
        if (!Float.isFinite(range) || range < 0) return false;
        KillAura aura = Leader.moduleManager == null ? null : (KillAura) Leader.moduleManager.getModule(KillAura.class);
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer || entity.isDead) continue;
            EntityLivingBase target = (EntityLivingBase) entity;
            if (target instanceof EntityPlayer && ((EntityPlayer) target).isSpectator()) continue;
            if (target.deathTime > 0) continue;
            double distance = RotationUtil.distanceToEntity(target);
            if (!Double.isFinite(distance) || distance > range) continue;
            if (aura != null && aura.isEnabled()) {
                if (aura.isVelocityRangeTarget(target)) return true;
            } else if (target instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) target;
                if (!TeamUtil.isFriend(player) && !TeamUtil.isSameTeam(player) && !TeamUtil.isBot(player)) return true;
            }
        }
        return false;
    }

    private void useSkipDelayOnly() {
        boolean pendingJump = jumpFlag;
        Stuck stuck = stuckModule();
        if (skipFreezeActive && stuck != null && stuck.isExternalSkipFreezeActive()
                && !stuck.isTimedSkipFreezeActive()) stuck.endExternalSkipFreeze();
        skipTimer.release();
        skipFreezeActive = skipPendingFreeze = skipAttackWindow = skipWaitingForMovement = false;
        skipSuppressPackets = skipSequence = false;
        skipBoostTicks = 0;
        cancellingKillAuraAttack = false;
        if (!skipOwnsDelay || Leader.delayManager.getDelayModule() != DelayModules.VELOCITY_SKIP_TICK) {
            resetSkipState(false);
            jumpFlag = pendingJump;
            return;
        }
        Leader.delayManager.setDelayState(true, DelayModules.VELOCITY);
        skipDelayOnly = true;
    }

    private void queuePredictionDelay(Packet<INetHandlerPlayClient> packet) {
        Leader.delayManager.setDelayState(true, DelayModules.VELOCITY);
        dbg(Leader.clientName + "Delay/Buffer Active");
        Leader.delayManager.delayedPacket.offer(packet);
    }

    private boolean shouldReleasePredictionDelay(boolean delayEnabled, int ticks) {
        return (!forceDelayRisingToFalling.getValue() || mc.thePlayer.motionY <= 0.0)
                && ((delayEnabled && (isInLiquidOrWeb()
                || Leader.delayManager.getDelay() >= (long) ticks && !airBuffer.getValue())
                || mc.thePlayer.onGround && !groundDelay.getValue() && !airBuffer.getValue())
                || airBuffer.getValue() && mc.thePlayer.onGround);
    }

    private void finishSkipDelayOnly() {
        boolean shouldJump = mc.thePlayer != null && mc.theWorld != null
                && mc.thePlayer == skipPlayer && mc.theWorld == skipWorld && mc.getNetHandler() != null
                && !mc.thePlayer.isDead && jump.getValue() && mc.thePlayer.onGround && !mc.thePlayer.isBurning();
        resetSkipState(true);
        jumpFlag = shouldJump;
    }

    private void beginSkipBoost() {
        if (skipBoostTicks > 0 || mc.thePlayer == null) return;
        if (!skipTimer.acquire(skipBoostTotal, skipBoostTotal)) {
            resetSkipState(false);
            return;
        }

        skipWaitingForMovement = false;
        skipBoostTicks = skipBoostTotal;
        skipAttackWindow = true;
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (mode.getValue() == 3) {
            clearSkipHurtConfirmation();
            if (skipReleasingDelay && event.getY() > 0.0) {
                jumpFlag = jump.getValue() && !mc.thePlayer.isBurning();
            }

            return;
        }
        if (isStuckActive()) {
            resetForStuck();
            return;
        }
        if (!allowNext || !(Boolean) fakeCheck.getValue()) {
            allowNext = true;
            if (pendingExplosion) {
                if (mode.getValue() == 0) {
                    pendingExplosion = false;
                    if (explosionHorizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) explosionHorizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) explosionHorizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (explosionVertical.getValue() > 0) {
                        event.setY(event.getY() * (double) explosionVertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            } else {
                if (!isEnabled() || event.isCancelled()) {
                    pendingExplosion = false;
                    allowNext = true;
                    return;
                }
                if (this.mode.getValue() == 1 && this.rotate.getValue() && event.getY() > 0.0) {
                    this.knockbackX = event.getX();
                    this.knockbackZ = event.getZ();
                    if (Math.abs(this.knockbackX) > 0.01 || Math.abs(this.knockbackZ) > 0.01) {
                        this.rotateTickCounter = 1;
                    }
                }
                if (mode.getValue() == 1 && smartTimes.getValue()){
                    hitCount = computeReduceTicks((int) event.getX(), (int) event.getZ());
                }
                if (delay.getValue() && event.getY() > 0.0){
                    if (jump.getValue() && (this.mode.getValue() == 1 || this.mode.getValue() == 2) && !mc.thePlayer.isBurning()){
                        jumpFlag = true;
                    }
                    ticksSinceVelocity = 0;
                }
                if (!delay.getValue())ticksSinceVelocity = 0;
                chanceCounter = chanceCounter % 100 + chance.getValue();
                if (chanceCounter >= 100) {
                    if (mode.getValue() == 0) {
                        if (horizontal.getValue() > 0) {
                            event.setX(event.getX() * (double) horizontal.getValue() / 100.0);
                            event.setZ(event.getZ() * (double) horizontal.getValue() / 100.0);
                        } else {
                            event.setX(mc.thePlayer.motionX);
                            event.setZ(mc.thePlayer.motionZ);
                        }
                        if (vertical.getValue() > 0) {
                            event.setY(event.getY() * (double) vertical.getValue() / 100.0);
                        } else {
                            event.setY(mc.thePlayer.motionY);
                        }
                    }
                }
            }
        }

    }

    @EventTarget(Priority.LOWEST)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mode.getValue() == 3 && skipSequence) return;
        if (isStuckActive()) return;
        if (this.isEnabled() && this.jumpFlag) {
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.thePlayer.isPotionActive(Potion.jump)
                    && !this.isInLiquidOrWeb() && mc.thePlayer.isSprinting()) {
                mc.thePlayer.movementInput.jump = true;
            }
            this.jumpFlag = false;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onSkipTickAttack(UpdateEvent event) {
        if (event.getType() != EventType.PRE || !canPerformSkipTickAttack()) return;
        KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
        if (killAura != null) killAura.performVelocityAttack(event.getNewYaw(), event.getNewPitch());
    }

    @EventTarget(Priority.HIGHEST)
    public void onCombatTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        combatTick++;
        cancellingKillAuraAttack = false;
    }

    @EventTarget(Priority.LOWEST)
    public synchronized void onTick(TickEvent event){
        if (mode.getValue() == 3) {
            if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead
                    || mc.currentScreen != null || !mc.inGameHasFocus
                    || (skipSequence || skipAttackWindow || skipWaitingForMovement || skipDelayOnly)
                    && (mc.thePlayer != skipPlayer || mc.theWorld != skipWorld)) {
                if (skipSequence || skipAttackWindow || skipWaitingForMovement || skipDelayOnly) resetSkipState(true);
                return;
            }
            if (event.getType() == EventType.PRE) {
                cancellingKillAuraAttack = skipSequence || skipAttackWindow || skipWaitingForMovement;
                if ((skipSequence || skipWaitingForMovement) && System.nanoTime() - skipFreezeStarted > 2000000000L) {
                    resetSkipState(true);
                    return;
                }
                if (skipPendingFreeze && mc.thePlayer.hurtTime <= 0) {
                    resetSkipState(true);
                    return;
                }
                if (!skipDelayOnly && (skipSequence || skipAttackWindow || skipWaitingForMovement)
                        && !isSkipTargetInRange()) useSkipDelayOnly();
                if (skipDelayOnly) return;
                if (skipPendingFreeze) {
                    KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                    if (killAura != null && killAura.isEnabled()) killAura.prepareVelocityAttackWindow();
                    Stuck stuck = stuckModule();
                    if (stuck == null || !stuck.beginExternalSkipFreeze(skipBoostTotal)) {

                        resetSkipState(true);
                    } else {
                        skipPendingFreeze = false;
                        skipFreezeActive = true;
                        skipSuppressPackets = true;
                        skipFreezeTicksRemaining = skipBoostTotal;
                    }
                }
                if (skipAttackWindow && (isStuckActive() || !skipTimer.isActive())) resetSkipState(true);
            } else if (event.getType() == EventType.POST) {
                if (skipDelayOnly) {
                    if (combatTick >= skipDelayDeadlineTick) finishSkipDelayOnly();
                    return;
                }
                if (skipFreezeActive) {
                    Stuck stuck = stuckModule();
                    if (stuck == null) {
                        resetSkipState(true);
                        return;
                    }
                    if (!stuck.isExternalSkipFreezeActive()) {
                        resetSkipState(true);
                        return;
                    }

                    skipFreezeTicksRemaining = stuck.getFreezeTicksRemaining();
                    if (skipFreezeTicksRemaining > 0) return;
                    stuck.endExternalSkipFreeze();

                    {
                        skipFreezeActive = false;
                        skipReleasingDelay = true;
                        try {
                            if (Leader.delayManager.getDelayModule() != DelayModules.VELOCITY_SKIP_TICK) {
                                resetSkipState(false);
                                return;
                            }
                            Leader.delayManager.releaseDelay(DelayModules.VELOCITY_SKIP_TICK);
                        } finally { skipReleasingDelay = false; skipOwnsDelay = false; }
                        skipSequence = false;
                        skipSuppressPackets = false;
                        if (mc.thePlayer != skipPlayer || mc.theWorld != skipWorld || mc.thePlayer.isDead) {
                            resetSkipState(false); return;
                        }
                        jumpFlag = jump.getValue() && mc.thePlayer.onGround && !mc.thePlayer.isBurning();
                        skipWaitingForMovement = true;
                        skipReleaseMovementSequence = Leader.playerStateManager.getMovementSequence();
                        KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                        if (killAura != null && killAura.isEnabled()) killAura.prepareVelocityAttackWindow();
                        return;
                    }
                }
                if (skipWaitingForMovement) {
                    if (Leader.playerStateManager.getMovementSequence() > skipReleaseMovementSequence) beginSkipBoost();
                    return;
                }
                if (skipAttackWindow && --skipBoostTicks <= 0) {
                    resetSkipState(false);
                }
                if (!skipSequence && !skipAttackWindow && !skipWaitingForMovement) cancellingKillAuraAttack = false;
            }
            return;
        }
        if (this.isEnabled() && !isStuckActive()){
            if (testMode.getValue() && this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0){
                if (ticksSinceVelocity >= stopBlockHurtTime.getValue()){
                    hasReceivedVelocity = true;
                    stoppedBlock = true;
                }
            }
            if (ticksSinceVelocity >= 0) {
                ticksSinceVelocity++;
            }
            if (ticksSinceVelocity >= 10) {
                ticksSinceVelocity = -1;
            }
        }
    }
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mode.getValue() == 3) {
            if (!isEnabled()) return;
            if (event.getType() == EventType.PRE) {
                cancellingKillAuraAttack = skipSequence || skipAttackWindow || skipWaitingForMovement;
            } else if (event.getType() == EventType.POST) {
                synchronized (this) {
                    if (skipDelayOnly && shouldReleasePredictionDelay(true, skipBoostTotal)) finishSkipDelayOnly();
                }
            }
            return;
        }
        if (!isEnabled()) return;
        if (isStuckActive()) {
            resetForStuck();
            return;
        }
        if (event.getType() == EventType.PRE) {
            int maxTick = this.rotateTick.getValue();
            if (this.rotateTickCounter > 0 && this.rotateTickCounter <= maxTick) {
                if (this.rotateTickCounter == 1) {
                    double deltaX = -this.knockbackX;
                    double deltaZ = -this.knockbackZ;
                    this.targetRotation = RotationUtil.getRotationsTo(deltaX, 0, deltaZ, event.getYaw(), event.getPitch());
                }
                if (this.targetRotation != null && !Leader.moduleManager.getModule(Scaffold.class).isEnabled()) {
                    event.setRotation(this.targetRotation[0], this.targetRotation[1], 2);
                    event.setPervRotation(this.targetRotation[0], 2);
                }
            }
        }
        if (event.getType() == EventType.PRE){
            int maxTick = this.rotateTick.getValue();
            if (this.rotateTickCounter > 0 && this.rotateTickCounter <= maxTick) {
                this.rotateTickCounter++;
                if (this.rotateTickCounter > maxTick) {
                    this.rotateTickCounter = 0;
                    this.targetRotation = null;
                    this.knockbackX = 0;
                    this.knockbackZ = 0;
                }
            }
        }
        if (mode.getValue() == 1) {
            if (this.reduce.getValue() && this.airPush.getValue()
                    && event.getType() == EventType.PRE
                    && hasReceivedVelocity) {
                this.tryAirPush(event);
            }
            if (reduce.getValue() && reduceMode.getValue() == 0) {
                if (event.getType() == EventType.PRE) {
                    if (velocityAttacked) {
                        KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                        if (killAura.getTarget() != null && killAura.isEnabled() && mc.thePlayer.isSprinting()) {
                            if (cancelKillAuraAttack.getValue()) suppressKillAuraForTick();
                            ChatUtil.sendFormatted("Attack");
                            EventManager.call(new AttackEvent(killAura.getTarget()));
                            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                            if (killAura.getTarget() != mc.thePlayer) {
                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(killAura.getTarget(), C02PacketUseEntity.Action.ATTACK));
                            } else {
                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(killAura.getTarget()), C02PacketUseEntity.Action.ATTACK));
                            }
                            applyHitSlowDown(false);
                        }
                        else {
                            extraAttacked = false;
                        }
                        velocityAttacked = false;
                    }
                    if (hasReceivedVelocity) {
                        if (smartTimes.getValue()){
                            if (reduceTick >= hitCount) {
                                reduceTick = 0;
                                hasReceivedVelocity = false;
                                stoppedBlock = false;
                            }
                        }else {
                            if (reduceTick >= attackTimes.getValue()) {
                                reduceTick = 0;
                                hasReceivedVelocity = false;
                                stoppedBlock = false;
                            }
                        }
                        RayCastUtil.RayCastResult targetA = RayCastUtil.rayCast(new RotationUtil.RotationVec(event.getYaw(), event.getPitch()), 3);
                        if (targetA != null && reduceMode.getValue() == 0) {
                            if (targetA.entityHit instanceof EntityPlayer && targetA.entityHit != mc.thePlayer) {
                                if (mc.thePlayer.isSprinting() || !this.onlySprinting.getValue()) {
                                    KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                                    if (killAura.getTarget() != null) {
                                        if (!reduceWhenCanAttack.getValue()
                                                || killAura.velocityCanReduce(0, killAura.blockTick)) {
                                            if (cancelKillAuraAttack.getValue()) suppressKillAuraForTick();
                                            EventManager.call(new AttackEvent(killAura.getTarget()));
                                            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                                            if (killAura.getTarget() != mc.thePlayer) {
                                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(killAura.getTarget(), C02PacketUseEntity.Action.ATTACK));
                                            } else {
                                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(killAura.getTarget()), C02PacketUseEntity.Action.ATTACK));
                                            }
                                            applyHitSlowDown(true);
                                        }
                                    } else {
                                        if (cancelKillAuraAttack.getValue()) suppressKillAuraForTick();
                                        EventManager.call(new AttackEvent(targetA.entityHit));
                                        mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                                        if (targetA.entityHit != mc.thePlayer) {
                                            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(targetA.entityHit, C02PacketUseEntity.Action.ATTACK));
                                        } else {
                                            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(targetA.entityHit), C02PacketUseEntity.Action.ATTACK));
                                        }
                                        applyHitSlowDown(true);
                                    }
                                }
                            }
                        }
                        reduceTick++;
                    }
                }
            }
        }
        if (mode.getValue() == 1 || mode.getValue() == 2) {
            if (event.getType() == EventType.POST) {

                KillAura killAura = (KillAura)Leader.moduleManager.getModule(KillAura.class);
                if (delayFlag && shouldReleasePredictionDelay(delay.getValue(), delayTicks.getValue()) || (reduceMode.getValue() == 1
                        && killAura.velocityCanReduce(1, killAura.blockTick)
                        && killAura.shouldAutoBlock() && reduce.getValue() && delayFlag) || (reduceMode.getValue() == 2
                        && killAura.velocityCanReduce(2, killAura.blockTick)
                        && killAura.shouldAutoBlock() && reduce.getValue() && delayFlag)
                || (reduceMode.getValue() == 3 && mc.thePlayer.isSprinting() && Leader.delayManager.getDelayModule() == DelayModules.VELOCITY && reduce.getValue() && delayFlag)) {
                    ticksSinceVelocity = 0;
                    if (killAura.getTarget() != null) {
                        if (extraAttack.getValue() && reduce.getValue() && reduceMode.getValue() != 0) {
                            if (!extraAttacked) {
                                extraAttacked = true;
                                velocityAttacked = true;
                            }
                        }
                    }
                    if (!testMode.getValue()) {
                        hasReceivedVelocity = true;
                    }
                    dbg(Leader.clientName + "Delay/Buffer " + Leader.delayManager.getDelay() + " Ticks");
                    Leader.delayManager.setDelayState(false, DelayModules.VELOCITY);
                    delayFlag = false;
                    if (jump.getValue() && (this.mode.getValue() == 1 || this.mode.getValue() == 2)){
                        jumpFlag = true;
                    }
                    if (this.mode.getValue() == 2) {
                        grimActive = true;
                        grimTick = 0;
                    }
                }
            }
        }
        if (mode.getValue() == 2) {
            if (event.getType() == EventType.PRE && grimActive) {
                suppressKillAuraForTick();
                KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                EntityLivingBase target = killAura != null ? killAura.getTarget() : null;
                if (target != null && target != mc.thePlayer && mc.thePlayer.isSprinting() && killAura.isEnabled()) {
                    EventManager.call(new AttackEvent(target));
                    mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                    mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                    applyHitSlowDown(false);
                }
                grimTick++;
                if (grimTick >= grimReduceTicks.getValue()) {
                    grimActive = false;
                }
            }
        }
    }
    private EntityLivingBase getKillAuraTarget() {
        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed()) {
            EntityLivingBase entityLivingBase = killAura.getTarget();
            return !TeamUtil.isEntityLoaded(entityLivingBase) ? null : entityLivingBase;
        } else {
            return null;
        }
    }
    private void tryAirPush(UpdateEvent event) {
        if (!this.airPush.getValue()) return;
        if (getKillAuraTarget() != null) return;
        if (this.airPushLeft <= 0) return;
        RayCastUtil.RayCastResult ray = RayCastUtil.rayCast(new RotationUtil.RotationVec(event.getYaw(), event.getPitch()), 3);
        if (ray != null && ray.entityHit instanceof EntityPlayer && ray.entityHit != mc.thePlayer) return;
        EntityPlayer airTarget = this.getAirPushTarget();
        if (airTarget == null) return;
        this.airPushLeft--;
        mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
        mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(airTarget, C02PacketUseEntity.Action.ATTACK));
        applyHitSlowDown(true);
    }

    private EntityPlayer getAirPushTarget() {
        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        double nearRange = killAura != null ? killAura.attackRange.getValue() : 3.0F;
        EntityPlayer farthest = null;
        double farthestDistance = 11.0;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.deathTime > 0) continue;
            double distance = RotationUtil.distanceToEntity(player);
            if (distance <= nearRange) return null;
            if (distance < farthestDistance) continue;
            farthestDistance = distance;
            farthest = player;
        }
        return farthest;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mode.getValue() == 3) {
            if (!isEnabled() || event.getType() != EventType.RECEIVE) return;
            if (event.getPacket() instanceof S08PacketPlayerPosLook) {
                clearSkipHurtConfirmation();
                return;
            }
            if (event.getPacket() instanceof S19PacketEntityStatus) {
                S19PacketEntityStatus status = (S19PacketEntityStatus) event.getPacket();
                if (mc.thePlayer != null && mc.theWorld != null && status.getOpCode() == 2
                        && status.getEntity(mc.theWorld) == mc.thePlayer) {
                    if (event.isCancelled()) clearSkipHurtConfirmation();
                    else recordSkipHurtConfirmation();
                }
                return;
            }
            if (!(event.getPacket() instanceof S12PacketEntityVelocity) || mc.thePlayer == null) return;
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;
            boolean hurtConfirmed = consumeSkipHurtConfirmation();
            if (!event.isCancelled() && !skipReleasingDelay && !isStuckActive()
                    && (!fakeCheck.getValue() || hurtConfirmed)
                    && (packet.getMotionX() != 0 || packet.getMotionY() != 0 || packet.getMotionZ() != 0)
                    && beginSkipSequence(packet, hurtConfirmed)) {
                event.setCancelled(true);
            }
            return;
        }
        if (isStuckActive()) return;
        if (isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    this.airPushLeft = computeReduceTicks(packet.getMotionX(), packet.getMotionZ());
                    if (!testMode.getValue()) {
                        if (!delay.getValue()) {
                            hasReceivedVelocity = true;
                        }
                        if (delay.getValue() && !groundDelay.getValue() && mc.thePlayer.onGround) {
                            hasReceivedVelocity = true;
                        }
                    }
                    LongJump longJump = (LongJump) Leader.moduleManager.modules.get(LongJump.class);
                    if ((mode.getValue() == 1 || mode.getValue() == 2)
                            && !delayFlag
                            && !isInLiquidOrWeb()
                            && !pendingExplosion
                            && !Leader.moduleManager.getModule(Stuck.class).isEnabled()
                            && (!allowNext || !(Boolean) fakeCheck.getValue())
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        if ((airBuffer.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && groundDelay.getValue() && !airBuffer.getValue())) {
                            queuePredictionDelay(packet);
                            event.setCancelled(true);
                            delayFlag = true;
                        }
                    }
                }
            } else if (!(event.getPacket() instanceof S27PacketExplosion)) {
                if (event.getPacket() instanceof S19PacketEntityStatus) {
                    S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                    Entity entity = packet.getEntity(mc.theWorld);
                    if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                        allowNext = false;
                    }
                }
            } else if (mode.getValue() == 0) {
                S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
                if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                    pendingExplosion = true;
                    if (explosionHorizontal.getValue() == 0 || explosionVertical.getValue() == 0) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled() && !isStuckActive() && this.rotateTickCounter > 0 && this.rotateTickCounter <= this.rotateTick.getValue()) {
            if (this.autoMove.getValue()) {
                mc.thePlayer.movementInput.moveForward = 1.0F;
            }
        }
    }
    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        resetSkipState(false);
        onDisabled();
    }

    @Override
    public void verifyValue(String name) {
        if ("Mode".equals(name)) clearSkipHurtConfirmation();
        if ("Mode".equals(name) && mode.getValue() != 3
                && (skipSequence || skipAttackWindow || skipWaitingForMovement || skipDelayOnly)) resetSkipState(true);
    }

    public static boolean isSkipTickAttackWindow() {
        if (Leader.moduleManager == null) return false;
        Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
        return velocity != null && velocity.isEnabled() && velocity.mode.getValue() == 3
                && !velocity.skipDelayOnly
                && (velocity.skipSequence || velocity.skipWaitingForMovement || velocity.skipAttackWindow);
    }

    public static boolean isSkipTickCombatActive() {
        return isSkipTickAttackWindow();
    }

    private void suppressKillAuraForTick() {
        killAuraSuppressedTick = combatTick;
        cancellingKillAuraAttack = true;
    }

    public static boolean shouldCancelKillAuraAttack() {
        if (Leader.moduleManager == null) return false;
        Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
        return velocity != null && velocity.isEnabled()
                && (cancellingKillAuraAttack || velocity.killAuraSuppressedTick == velocity.combatTick
                || velocity.mode.getValue() == 2 && velocity.grimActive
                || isSkipTickCombatActive());
    }

    public static boolean canPerformSkipTickAttack() {
        if (Leader.moduleManager == null || mc.thePlayer == null || mc.theWorld == null
                || !mc.isCallingFromMinecraftThread() || mc.currentScreen != null || !mc.inGameHasFocus) return false;
        Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
        return velocity != null && velocity.isEnabled() && velocity.mode.getValue() == 3
                && velocity.skipAttackWindow && !velocity.skipSequence && !velocity.skipSuppressPackets
                && mc.thePlayer == velocity.skipPlayer && mc.theWorld == velocity.skipWorld
                && !mc.thePlayer.isDead && mc.thePlayer.hurtTime > 0 && !velocity.isStuckActive()
                && Leader.playerStateManager != null && !Leader.playerStateManager.digging
                && !Leader.playerStateManager.placing && !Leader.playerStateManager.hasInputAction()
                && velocity.isSkipTargetInRange();
    }

    public static boolean tryClaimSkipTickAttack() {
        if (!canPerformSkipTickAttack()) return false;
        Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
        if (velocity.skipAttackClaimTick == velocity.combatTick) return false;
        velocity.skipAttackClaimTick = velocity.combatTick;
        velocity.suppressKillAuraForTick();
        return true;
    }

    public static boolean isSkipTickSequenceActive() {
        if (Leader.moduleManager == null) return false;
        Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
        return velocity != null && velocity.isEnabled() && velocity.mode.getValue() == 3
                && !velocity.skipDelayOnly
                && (velocity.skipSuppressPackets || velocity.skipSequence || velocity.skipPendingFreeze || velocity.skipFreezeActive
                || velocity.skipReleasingDelay || velocity.skipWaitingForMovement || velocity.skipAttackWindow);
    }

    public static boolean shouldSuppressSkipTickPacket(Packet<?> packet) {
        return packet != null && isSkipTickPacketFreezeActive() && SkipTickPacketPolicy.isGameplayPacket(packet);
    }

    public static boolean isSkipTickPacketFreezeActive() {
        if (Leader.moduleManager == null) return false;
        Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
        return velocity != null && velocity.isEnabled() && velocity.mode.getValue() == 3
                && velocity.skipSuppressPackets;
    }

    public static void abortSkipTickForCorrection() {
        if (!isSkipTickSequenceActive()) return;
        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(Velocity::abortSkipTickForCorrection);
            return;
        }
        Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
        if (velocity != null) velocity.resetSkipState(false);
    }

    public void dbg(String msg) {
        if (debug.getValue()) ChatUtil.sendFormatted(msg);
    }

    private void applyHitSlowDown(boolean respectKeepSprint) {
        HitSlowDownEvent hitSlowDownEvent = (HitSlowDownEvent) EventManager.call(new HitSlowDownEvent());
        if (mc.thePlayer.isSprinting()) {
            mc.thePlayer.motionX *= hitSlowDownEvent.getSlowDown();
            mc.thePlayer.motionZ *= hitSlowDownEvent.getSlowDown();
            if (!hitSlowDownEvent.getSprint() && (!respectKeepSprint || !keepSprint.getValue())) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @Override
    public void onEnabled() {
        clearSkipHurtConfirmation();
        combatTick = 0;
        killAuraSuppressedTick = skipAttackClaimTick = Long.MIN_VALUE;
        cancellingKillAuraAttack = false;
        hasReceivedVelocity = false;
        airPushLeft = 0;
        grimActive = false;
        grimTick = 0;
        this.rotateTickCounter = 0;
        this.targetRotation = null;
        this.knockbackX = 0;
        this.knockbackZ = 0;
    }

    @Override
    public void onDisabled() {
        resetSkipState(true);
        pendingExplosion = false;
        stoppedBlock = false;
        allowNext = true;
        hasReceivedVelocity = false;
        airPushLeft = 0;
        cancellingKillAuraAttack = false;
        grimActive = false;
        grimTick = 0;
        Leader.delayManager.setDelayState(false, DelayModules.VELOCITY);
    }

    @Override
    public String[] getSuffix() {
        if (mode.getValue() == 0) {
            return new String[]{
                    String.format("%d%%", horizontal.getValue()),
                    String.format("%d%%", vertical.getValue())
            };
        } else {
            return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
        }
    }
}
