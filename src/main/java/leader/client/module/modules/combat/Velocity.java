package leader.client.module.modules.combat;

import com.google.common.base.CaseFormat;
import leader.client.event.EventManager;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.*;
import leader.mixin.IAccessorEntity;
import leader.client.module.Module;
import leader.client.module.modules.movement.LongJump;
import leader.client.module.modules.movement.Stuck;
import leader.client.module.modules.player.Scaffold;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.IntProperty;
import leader.client.property.properties.ModeProperty;
import leader.client.property.properties.PercentProperty;
import leader.client.util.ChatUtil;
import leader.client.util.MoveUtil;
import leader.client.util.player.RayCastUtil;
import leader.client.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import leader.client.Leader;
import leader.client.enums.DelayModules;
import net.minecraftforge.fml.common.gameevent.TickEvent;


import java.util.Objects;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla","Prediction"});
    public final BooleanProperty reduce = new BooleanProperty("Reduce", true, () -> mode.getValue() == 1);
    public final ModeProperty reduceMode = new ModeProperty("ReduceMode", 0, new String[]{"Attack", "ReleaseWhenCanAttack","ReleaseBeforeCanAttack"}, () -> mode.getValue() == 1 && reduce.getValue());
    private final BooleanProperty extraAttack = new BooleanProperty("ExtraAttack", false, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() != 0);
    private final BooleanProperty reduceWhenCanAttack = new BooleanProperty("Reduce When Can Attack", true, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() == 0);
    private final BooleanProperty onlySprinting = new BooleanProperty("Only Sprinting", true, () -> mode.getValue() == 1 && reduceMode.getValue() == 0 && reduce.getValue());
    public final BooleanProperty smartTimes = new BooleanProperty("SmartTimes",true, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0);
    public final IntProperty attackTimes = new IntProperty("Attack Times", 1, 1, 5, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0 && !smartTimes.getValue());
    public final BooleanProperty testMode = new BooleanProperty("TestMode",false, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0);
    private final IntProperty stopBlockHurtTime = new IntProperty("StopBlockHurtTime",2,0,10, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0 && testMode.getValue());

    public final BooleanProperty jump = new BooleanProperty("Jump", true, () -> mode.getValue() == 1);
    public final BooleanProperty delay = new BooleanProperty("Delay", false, () -> mode.getValue() == 1);
    public final IntProperty delayTicks = new IntProperty("Delay Ticks", 1, 1, 5, () -> mode.getValue() == 1 && delay.getValue() && !this.airBuffer.getValue());
    public final BooleanProperty airBuffer = new BooleanProperty("Delay Till On Ground", true, () -> mode.getValue() == 1 && delay.getValue());
    public final BooleanProperty groundDelay = new BooleanProperty("Ground Delay", false, () -> mode.getValue() == 1 && delay.getValue() && !airBuffer.getValue());
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
    public boolean knockback = false;
    private int chanceCounter = 0;
    private int rotatoTickCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean delayFlag = false;
    private boolean jumpFlag = false;
    public static boolean hasReceivedVelocity;
    private int ticksSinceVelocity = -1;

    private double knockbackX = 0;
    private float[] targetRotation = null;
    private double knockbackZ = 0;
    private int reduceTick = -1;
    public int hitCount;
    public static boolean extraAttacked,velocityAttacked = false;
    public static boolean stoppedBlock = false;
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
    @EventTarget
    public void onKnockback(KnockbackEvent event) {
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
                        this.rotatoTickCounter = 1;
                    }
                }
                if (mode.getValue() == 1 && smartTimes.getValue()){
                    hitCount = computeReduceTicks((int) event.getX(), (int) event.getZ());
                }
                if (delay.getValue() && !groundDelay.getValue() && mc.thePlayer.onGround){
                    if (jump.getValue() && this.mode.getValue() == 1){
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


    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.jumpFlag) {
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb() && mc.thePlayer.isSprinting()) {
                mc.thePlayer.movementInput.jump = true;
            }
            this.jumpFlag = false;
        }
    }
    @EventTarget
    public void onTick(TickEvent event){
        if (this.isEnabled()){
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
        if (!isEnabled()) return;
        if (event.getType() == EventType.PRE) {
            int maxTick = this.rotateTick.getValue();
            if (this.rotatoTickCounter > 0 && this.rotatoTickCounter <= maxTick) {
                if (this.rotatoTickCounter == 1) {
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
            if (this.rotatoTickCounter > 0 && this.rotatoTickCounter <= maxTick) {
                this.rotatoTickCounter++;
                if (this.rotatoTickCounter > maxTick) {
                    this.rotatoTickCounter = 0;
                    this.targetRotation = null;
                    this.knockbackX = 0;
                    this.knockbackZ = 0;
                }
            }
        }
        if (mode.getValue() == 1) {
            if (reduce.getValue() && reduceMode.getValue() == 0) {
                if (event.getType() == EventType.PRE) {
                    if (velocityAttacked) {
                        KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                        if (killAura.getTarget() != null && killAura.isEnabled() && mc.thePlayer.isSprinting()) {
                            ChatUtil.sendFormatted("Attack");
                            EventManager.call(new AttackEvent(killAura.getTarget()));
                            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                            if (killAura.getTarget() != mc.thePlayer) {
                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(killAura.getTarget(), C02PacketUseEntity.Action.ATTACK));
                            } else {
                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(killAura.getTarget()), C02PacketUseEntity.Action.ATTACK));
                            }
                            mc.thePlayer.motionX *= 0.6D;
                            mc.thePlayer.motionZ *= 0.6D;
                            mc.thePlayer.setSprinting(false);
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
                                                || killAura.autoBlock.getValue() == 0
                                                || killAura.autoBlock.getValue() == 1
                                                || killAura.autoBlock.getValue() == 8
                                                || (killAura.autoBlock.getValue() == 2 && killAura.blockTick == 0)
                                                || (killAura.autoBlock.getValue() == 3 && killAura.blockTick == 0)
                                                || (killAura.autoBlock.getValue() == 4 && killAura.blockTick == killAura.attackTick.getValue())
                                                || (killAura.autoBlock.getValue() == 5 && (killAura.blockTick == 0 || killAura.blockTick == 2))
                                                || (killAura.autoBlock.getValue() == 6 && (killAura.blockTick == 0 || killAura.blockTick == 2))
                                                || (killAura.autoBlock.getValue() == 7 && killAura.blockTick == 0)) {
                                            EventManager.call(new AttackEvent(killAura.getTarget()));
                                            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                                            if (killAura.getTarget() != mc.thePlayer) {
                                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(killAura.getTarget(), C02PacketUseEntity.Action.ATTACK));
                                            } else {
                                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(killAura.getTarget()), C02PacketUseEntity.Action.ATTACK));
                                            }
                                            mc.thePlayer.motionX *= 0.6D;
                                            mc.thePlayer.motionZ *= 0.6D;
                                            mc.thePlayer.setSprinting(false);
                                        }
                                    } else {
                                        EventManager.call(new AttackEvent(targetA.entityHit));
                                        mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                                        if (targetA.entityHit != mc.thePlayer) {
                                            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(targetA.entityHit, C02PacketUseEntity.Action.ATTACK));
                                        } else {
                                            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(targetA.entityHit), C02PacketUseEntity.Action.ATTACK));
                                        }
                                        mc.thePlayer.motionX *= 0.6D;
                                        mc.thePlayer.motionZ *= 0.6D;
                                        mc.thePlayer.setSprinting(false);
                                    }
                                }
                            }
                        }
                        reduceTick++;
                    }
                }
            }
            if (event.getType() == EventType.POST) {
                KillAura killAura = (KillAura)Leader.moduleManager.getModule(KillAura.class);
                if (delayFlag && ((delay.getValue()
                        && (isInLiquidOrWeb() || Leader.delayManager.getDelay() >= (long) delayTicks.getValue() && !airBuffer.getValue()) || (mc.thePlayer.onGround && !groundDelay.getValue() && !airBuffer.getValue()))
                        || (airBuffer.getValue() && mc.thePlayer.onGround && delayFlag)) || (reduceMode.getValue() == 1
                        && (killAura.autoBlock.getValue() == 0
                            || killAura.autoBlock.getValue() == 1
                            || killAura.autoBlock.getValue() == 8
                            || (killAura.autoBlock.getValue() == 2 && killAura.blockTick == 0)
                            || (killAura.autoBlock.getValue() == 3 && killAura.blockTick == 0)
                            || (killAura.autoBlock.getValue() == 4 && killAura.blockTick == killAura.attackTick.getValue() % Math.max(1, killAura.maxTick.getValue() - 1))
                            || (killAura.autoBlock.getValue() == 5 && killAura.blockTick == 0)
                            || (killAura.autoBlock.getValue() == 6 && killAura.blockTick == 0)
                            || (killAura.autoBlock.getValue() == 7 && killAura.blockTick == 0))
                        && killAura.shouldAutoBlock() && reduce.getValue()) || (reduceMode.getValue() == 2
                        && (killAura.autoBlock.getValue() == 0
                            || killAura.autoBlock.getValue() == 1
                            || killAura.autoBlock.getValue() == 8
                            || (killAura.autoBlock.getValue() == 2 && killAura.blockTick == 2)
                            || (killAura.autoBlock.getValue() == 3 && killAura.blockTick == 2)
                            || (killAura.autoBlock.getValue() == 4 && killAura.blockTick == (killAura.attackTick.getValue() - 2 + killAura.maxTick.getValue() - 1) % Math.max(1, killAura.maxTick.getValue() - 1))
                            || (killAura.autoBlock.getValue() == 5 && killAura.blockTick == 1)
                            || (killAura.autoBlock.getValue() == 6 && killAura.blockTick == 2)
                            || (killAura.autoBlock.getValue() == 7 && killAura.blockTick == 1))
                        && killAura.shouldAutoBlock() && reduce.getValue())) {
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
                    if (jump.getValue() && this.mode.getValue() == 1){
                        jumpFlag = true;
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    if (!testMode.getValue()) {
                        if (!delay.getValue()) {
                            hasReceivedVelocity = true;
                        }
                        if (delay.getValue() && !groundDelay.getValue() && mc.thePlayer.onGround) {
                            hasReceivedVelocity = true;
                        }
                    }
                    LongJump longJump = (LongJump) Leader.moduleManager.modules.get(LongJump.class);
                    if (mode.getValue() == 1
                            && !delayFlag
                            && !isInLiquidOrWeb()
                            && !pendingExplosion
                            && !Leader.moduleManager.getModule(Stuck.class).isEnabled()
                            && (!allowNext || !(Boolean) fakeCheck.getValue())
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        if ((airBuffer.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && groundDelay.getValue() && !airBuffer.getValue())) {
                            Leader.delayManager.setDelayState(true, DelayModules.VELOCITY);
                            dbg(Leader.clientName + "Delay/Buffer Active");
                            Leader.delayManager.delayedPacket.offer(packet);
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
        if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) event.getPacket();
                if (velocityPacket.getEntityID() == mc.thePlayer.getEntityId()) {
                    knockback = true;
                }
            }
        }
    }
    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled() && this.rotatoTickCounter > 0 && this.rotatoTickCounter <= this.rotateTick.getValue()) {
            if (this.autoMove.getValue()) {
                mc.thePlayer.movementInput.moveForward = 1.0F;
            }
        }
    }
    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onDisabled();
    }

    public void dbg(String msg) {
        if (debug.getValue()) ChatUtil.sendFormatted(msg);
    }

    @Override
    public void onEnabled() {
        knockback = false;
        hasReceivedVelocity = false;
        this.rotatoTickCounter = 0;
        this.targetRotation = null;
        this.knockbackX = 0;
        this.knockbackZ = 0;
    }

    @Override
    public void onDisabled() {
        pendingExplosion = false;
        stoppedBlock = false;
        allowNext = true;
        hasReceivedVelocity = false;
        knockback = false;
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