package leader.client.module.modules.combat;

import com.google.common.base.CaseFormat;
import leader.client.event.EventManager;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.*;
import leader.mixin.accessor.IAccessorEntity;
import leader.client.module.Module;
import leader.client.module.modules.movement.LongJump;
import leader.client.module.modules.movement.Stuck;
import leader.client.module.modules.player.Scaffold;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;
import leader.client.util.DebugUtil;
import leader.client.util.player.MoveUtil;
import leader.client.util.player.RayCastUtil;
import leader.client.util.player.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import leader.client.Leader;
import leader.client.component.impl.network.delay.DelayType;
import net.minecraftforge.fml.common.gameevent.TickEvent;


import java.util.Objects;

public class Velocity extends Module {
    public final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "Prediction"}, "Vanilla", this);
    public final BoolValue reduce = new BoolValue("Reduce", true, () -> mode.is("Prediction"), this);
    public final ListValue reduceMode = new ListValue("ReduceMode", new String[]{"Attack", "ReleaseWhenCanAttack", "ReleaseBeforeCanAttack"}, "Attack", () -> mode.is("Prediction") && reduce.getValue(), this);
    private final BoolValue extraAttack = new BoolValue("ExtraAttack", false, () -> mode.is("Prediction") && reduce.getValue() && !reduceMode.is("Attack"), this);
    private final BoolValue reduceWhenCanAttack = new BoolValue("Reduce When Can Attack", true, () -> mode.is("Prediction") && reduce.getValue() && reduceMode.is("Attack"), this);
    private final BoolValue onlySprinting = new BoolValue("Only Sprinting", true, () -> mode.is("Prediction") && reduceMode.is("Attack") && reduce.getValue(), this);
    public final BoolValue smartTimes = new BoolValue("SmartTimes", true, () -> this.mode.is("Prediction") && this.reduce.getValue() && reduceMode.is("Attack"), this);
    public final SliderValue attackTimes = new SliderValue("Attack Times", 1, 1, 5, () -> this.mode.is("Prediction") && this.reduce.getValue() && reduceMode.is("Attack") && !smartTimes.getValue(), Representation.INT, this);
    public final BoolValue testMode = new BoolValue("TestMode", false, () -> this.mode.is("Prediction") && this.reduce.getValue() && reduceMode.is("Attack"), this);
    private final SliderValue stopBlockHurtTime = new SliderValue("StopBlockHurtTime", 2, 0, 10, () -> this.mode.is("Prediction") && this.reduce.getValue() && reduceMode.is("Attack") && testMode.getValue(), Representation.INT, this);

    public final BoolValue jump = new BoolValue("Jump", true, () -> mode.is("Prediction"), this);
    public final BoolValue delay = new BoolValue("Delay", false, () -> mode.is("Prediction"), this);
    public final SliderValue delayTicks = new SliderValue("Delay Ticks", 1, 1, 5, () -> mode.is("Prediction") && delay.getValue() && !this.airBuffer.getValue(), Representation.INT, this);
    public final BoolValue airBuffer = new BoolValue("Delay Till On Ground", true, () -> mode.is("Prediction") && delay.getValue(), this);
    public final BoolValue groundDelay = new BoolValue("Ground Delay", false, () -> mode.is("Prediction") && delay.getValue() && !airBuffer.getValue(), this);
    public final BoolValue rotate = new BoolValue("Rotate", false, () -> this.mode.is("Prediction"), this);
    public final SliderValue rotateTick = new SliderValue("Rotate Ticks", 3, 1, 12, () -> this.mode.is("Prediction") && this.rotate.getValue(), Representation.INT, this);
    public final BoolValue autoMove = new BoolValue("Auto Move", false, () -> this.mode.is("Prediction") && this.rotate.getValue(), this);
    public final SliderValue chance = new SliderValue("Chance", 100, 0, 100, () -> mode.is("Vanilla"), Representation.INT, this);
    public final SliderValue horizontal = new SliderValue("Horizontal", 100, 0, 100, () -> mode.is("Vanilla"), Representation.INT, this);
    public final SliderValue vertical = new SliderValue("Vertical", 100, 0, 100, () -> mode.is("Vanilla"), Representation.INT, this);
    public final SliderValue explosionHorizontal = new SliderValue("Explosions Horizontal", 100, 0, 100, () -> mode.is("Vanilla"), Representation.INT, this);
    public final SliderValue explosionVertical = new SliderValue("Explosions Vertical", 100, 0, 100, () -> mode.is("Vanilla"), Representation.INT, this);
    public final BoolValue fakeCheck = new BoolValue("Fake Check", true, this);
    public final BoolValue debug = new BoolValue("Debug", false, this);
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
    public static boolean extraAttacked, velocityAttacked = false;
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
        if (!allowNext || !fakeCheck.getValue()) {
            allowNext = true;
            if (pendingExplosion) {
                if (mode.is("Vanilla")) {
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
                if (this.mode.is("Prediction") && this.rotate.getValue() && event.getY() > 0.0) {
                    this.knockbackX = event.getX();
                    this.knockbackZ = event.getZ();
                    if (Math.abs(this.knockbackX) > 0.01 || Math.abs(this.knockbackZ) > 0.01) {
                        this.rotatoTickCounter = 1;
                    }
                }
                if (mode.is("Prediction") && smartTimes.getValue()) {
                    hitCount = computeReduceTicks((int) event.getX(), (int) event.getZ());
                }
                if (delay.getValue() && !groundDelay.getValue() && mc.thePlayer.onGround) {
                    if (jump.getValue() && this.mode.is("Prediction")) {
                        jumpFlag = true;
                    }
                    ticksSinceVelocity = 0;
                }
                if (!delay.getValue()) ticksSinceVelocity = 0;
                chanceCounter = chanceCounter % 100 + chance.getValue().intValue();
                if (chanceCounter >= 100) {
                    if (mode.is("Vanilla")) {
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
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            if (testMode.getValue() && this.mode.is("Prediction") && this.reduce.getValue() && reduceMode.is("Attack")) {
                if (ticksSinceVelocity >= stopBlockHurtTime.getValue().intValue()) {
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
            int maxTick = this.rotateTick.getValue().intValue();
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
        if (event.getType() == EventType.PRE) {
            int maxTick = this.rotateTick.getValue().intValue();
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
        if (mode.is("Prediction")) {
            if (reduce.getValue() && reduceMode.is("Attack")) {
                if (event.getType() == EventType.PRE) {
                    if (velocityAttacked) {
                        KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                        if (killAura.getTarget() != null && killAura.isEnabled() && mc.thePlayer.isSprinting()) {
                            DebugUtil.sendFormatted("Attack");
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
                        } else {
                            extraAttacked = false;
                        }
                        velocityAttacked = false;
                    }
                    if (hasReceivedVelocity) {
                        if (smartTimes.getValue()) {
                            if (reduceTick >= hitCount) {
                                reduceTick = 0;
                                hasReceivedVelocity = false;
                                stoppedBlock = false;
                            }
                        } else {
                            if (reduceTick >= attackTimes.getValue().intValue()) {
                                reduceTick = 0;
                                hasReceivedVelocity = false;
                                stoppedBlock = false;
                            }
                        }
                        RayCastUtil.RayCastResult targetA = RayCastUtil.rayCast(new RotationUtil.RotationVec(event.getYaw(), event.getPitch()), 3);
                        if (targetA != null && reduceMode.is("Attack")) {
                            if (targetA.entityHit instanceof EntityPlayer && targetA.entityHit != mc.thePlayer) {
                                if (mc.thePlayer.isSprinting() || !this.onlySprinting.getValue()) {
                                    KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                                    if (killAura.getTarget() != null) {
                                        if (!reduceWhenCanAttack.getValue()
                                                || killAura.autoBlock.is("None")
                                                || killAura.autoBlock.is("Vanilla")
                                                || killAura.autoBlock.is("Fake")
                                                || (killAura.autoBlock.is("OldHypixel") && killAura.blockTick == 0)
                                                || (killAura.autoBlock.is("Hypixel(Without NoSlow)") && killAura.blockTick == 0)
                                                || (killAura.autoBlock.is("Hypixel Custom") && killAura.blockTick == killAura.attackTick.getValue().intValue())
                                                || (killAura.autoBlock.is("HypixelTest") && (killAura.blockTick == 0 || killAura.blockTick == 2))
                                                || (killAura.autoBlock.is("HypixelLag") && (killAura.blockTick == 0 || killAura.blockTick == 2))
                                                || (killAura.autoBlock.is("Legit") && killAura.blockTick == 0)) {
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
                KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                if (delayFlag && ((delay.getValue()
                        && (isInLiquidOrWeb() || Leader.delayComponent.getDelay() >= delayTicks.getValue().longValue() && !airBuffer.getValue()) || (mc.thePlayer.onGround && !groundDelay.getValue() && !airBuffer.getValue()))
                        || (airBuffer.getValue() && mc.thePlayer.onGround && delayFlag)) || (reduceMode.is("ReleaseWhenCanAttack")
                        && (killAura.autoBlock.is("None")
                            || killAura.autoBlock.is("Vanilla")
                            || killAura.autoBlock.is("Fake")
                            || (killAura.autoBlock.is("OldHypixel") && killAura.blockTick == 0)
                            || (killAura.autoBlock.is("Hypixel(Without NoSlow)") && killAura.blockTick == 0)
                            || (killAura.autoBlock.is("Hypixel Custom") && killAura.blockTick == killAura.attackTick.getValue().intValue() % Math.max(1, killAura.maxTick.getValue().intValue() - 1))
                            || (killAura.autoBlock.is("HypixelTest") && killAura.blockTick == 0)
                            || (killAura.autoBlock.is("HypixelLag") && killAura.blockTick == 0)
                            || (killAura.autoBlock.is("Legit") && killAura.blockTick == 0))
                        && killAura.shouldAutoBlock() && reduce.getValue()) || (reduceMode.is("ReleaseBeforeCanAttack")
                        && (killAura.autoBlock.is("None")
                            || killAura.autoBlock.is("Vanilla")
                            || killAura.autoBlock.is("Fake")
                            || (killAura.autoBlock.is("OldHypixel") && killAura.blockTick == 2)
                            || (killAura.autoBlock.is("Hypixel(Without NoSlow)") && killAura.blockTick == 2)
                            || (killAura.autoBlock.is("Hypixel Custom") && killAura.blockTick == (killAura.attackTick.getValue().intValue() - 2 + killAura.maxTick.getValue().intValue() - 1) % Math.max(1, killAura.maxTick.getValue().intValue() - 1))
                            || (killAura.autoBlock.is("HypixelTest") && killAura.blockTick == 1)
                            || (killAura.autoBlock.is("HypixelLag") && killAura.blockTick == 2)
                            || (killAura.autoBlock.is("Legit") && killAura.blockTick == 1))
                        && killAura.shouldAutoBlock() && reduce.getValue())) {
                    ticksSinceVelocity = 0;
                    if (killAura.getTarget() != null) {
                        if (extraAttack.getValue() && reduce.getValue() && !reduceMode.is("Attack")) {
                            if (!extraAttacked) {
                                extraAttacked = true;
                                velocityAttacked = true;
                            }
                        }
                    }
                    if (!testMode.getValue()) {
                        hasReceivedVelocity = true;
                    }
                    dbg(Leader.clientName + "Delay/Buffer " + Leader.delayComponent.getDelay() + " Ticks");
                    Leader.delayComponent.setDelayState(false, DelayType.VELOCITY);
                    delayFlag = false;
                    if (jump.getValue() && this.mode.is("Prediction")) {
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
                    if (mode.is("Prediction")
                            && !delayFlag
                            && !isInLiquidOrWeb()
                            && !pendingExplosion
                            && !Leader.moduleManager.getModule(Stuck.class).isEnabled()
                            && (!allowNext || !fakeCheck.getValue())
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        if ((airBuffer.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && groundDelay.getValue() && !airBuffer.getValue())) {
                            Leader.delayComponent.setDelayState(true, DelayType.VELOCITY);
                            dbg(Leader.clientName + "Delay/Buffer Active");
                            Leader.delayComponent.delayedPacket.offer(packet);
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
            } else if (mode.is("Vanilla")) {
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
        if (this.isEnabled() && this.rotatoTickCounter > 0 && this.rotatoTickCounter <= this.rotateTick.getValue().intValue()) {
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
        if (debug.getValue()) DebugUtil.sendFormatted(msg);
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
        if (mode.is("Vanilla")) {
            return new String[]{
                    String.format("%d%%", horizontal.getValue().intValue()),
                    String.format("%d%%", vertical.getValue().intValue())
            };
        } else {
            return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getValue())};
        }
    }
}
