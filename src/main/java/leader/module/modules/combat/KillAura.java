package leader.module.modules.combat;

import com.google.common.base.CaseFormat;
import io.netty.buffer.Unpooled;
import leader.Leader;
import leader.module.modules.misc.AutoHeal;
import leader.module.modules.movement.NoSlow;
import leader.module.modules.player.BedNuker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;
import leader.enums.BlinkModules;
import leader.event.EventManager;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.*;
import leader.management.RotationState;
import leader.mixin.IAccessorMinecraft;
import leader.mixin.IAccessorPlayerControllerMP;
import leader.module.Module;
import leader.module.modules.misc.Disabler;
import leader.module.modules.player.AutoBlockIn;
import leader.module.modules.player.KeepSprint;
import leader.module.modules.player.Scaffold;
import leader.property.properties.*;
import leader.util.*;

import java.awt.*;
import java.util.ArrayList;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode;
    public final ModeProperty sort;
    public ModeProperty autoBlock;
    public ModeProperty hypixelMode;
    public ModeProperty lagClass;
    public ModeProperty tickMode;
    public ModeProperty fullMode;
    public ModeProperty swapMode;
    private final BooleanProperty noStop = new BooleanProperty("NoSwap",true,this::isOldHypixel);
    private final BooleanProperty test = new BooleanProperty("MoreAttack",false,this::isOldHypixel);
    private final IntProperty moreAttackDelay = new IntProperty("MoreAttackDelay",1,0,3,() -> this.isOldHypixel() && test.getValue());
    public final IntProperty maxTick = new IntProperty("MaxTick",3,1,5,this::isHypixelCustom);
    private final IntProperty startBlinkTick = new IntProperty("StartBlinkTick",0,1,5,this::isHypixelCustom);
    private final IntProperty stopBlinkTick = new IntProperty("StopBlinkTick",2,1,5,this::isHypixelCustom);
    private final IntProperty swapTick = new IntProperty("SwapTick",2,1,5,this::isHypixelCustom);
    private final IntProperty switchBackTick = new IntProperty("SwitchBackTick",2,1,5,this::isHypixelCustom);
    private final IntProperty stopBlockTick = new IntProperty("StopBlockTick",2,1,5,this::isHypixelCustom);
    public final IntProperty attackTick = new IntProperty("AttackTick",0,1,5,this::isHypixelCustom);
    private final IntProperty startBlockTick = new IntProperty("StartBlockTick",0,1,5,this::isHypixelCustom);
    private final BooleanProperty postStartBlock = new BooleanProperty("PostBlock",false,this::isHypixelCustom);
    private final IntProperty startHurtTime = new IntProperty("StartHurtTime",6,1,10,this::isPredict);
    private final IntProperty holdBlockTick = new IntProperty("HoldBlockTick",2,0,6,this::isPredict);
    private final BooleanProperty alwaysRenderBlocking = new BooleanProperty("AlwaysRenderBlocking",true,this::isLag);
    private final BooleanProperty c09Instead = new BooleanProperty("C09Instead",true,this::isLag3Tick);
    private final BooleanProperty fullC09 = new BooleanProperty("FullC09(Will Cause Damage Less)",false,() -> isLag4Tick() | isLag5Tick());
    private boolean strafeFacing = false;
    private float strafeYaw;
    private float strafeYawOffset;
    public final BooleanProperty autoBlockRequirePress;
    public final IntProperty autoBlockCPS;
    public final FloatProperty autoBlockRange;

    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public ModeProperty rotationMode;
    public final PercentProperty smoothing;
    public final IntProperty angleStep;
    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty allowPlayerBlocking;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty lowTimerCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;

    private final TimerUtil timer = new TimerUtil();
    private AttackData target = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    public boolean blockingState = false;
    public boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    public int blockTick = 0;
    private boolean swapped = false;
    private boolean postBlock = false;
    private boolean postSwap = false;
    private boolean predictBlocking = false;
    private int testAttackTick = 0;
    private boolean bufferPending = false;
    private int holdTicks = 0;
    private boolean postBlink = false;
    private boolean postBlinkReset = false;

    public KillAura(){
        super("KillAura", false);
        this.mode = new ModeProperty("Mode", 0, new String[]{"Single", "Switch"});
        this.sort = new ModeProperty("Sort", 0, new String[]{"Distance", "Health", "Hurt Time", "FOV"});

        this.autoBlock = new ModeProperty(
                "AutoBlock", 0, new String[]{"None", "Vanilla", "Hypixel", "Legit", "Fake"}
        );
        this.hypixelMode = new ModeProperty(
                "HypixelMode", 0, new String[]{"OldHypixel", "Without NoSlow", "Custom", "Lag","Predict"}, () -> this.autoBlock.getValue() == 2
        );
        this.lagClass = new ModeProperty(
                "LagClass", 0, new String[]{"Tick", "Combo", "Full", "Swap", "Stop"}, () -> this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 3
        );
        this.tickMode = new ModeProperty(
                "TickMode", 1, new String[]{"2Tick", "3Tick", "4Tick", "5Tick", "6Tick"}, () -> this.isLag() && this.lagClass.getValue() == 0
        );
        this.fullMode = new ModeProperty(
                "FullMode", 0, new String[]{"3TickFull", "4TickFull"}, () -> this.isLag() && this.lagClass.getValue() == 2
        );
        this.swapMode = new ModeProperty(
                "SwapMode", 0, new String[]{"Swap", "TestPostSwap"}, () -> this.isLag() && this.lagClass.getValue() == 3
        );
        this.autoBlockRequirePress = new BooleanProperty("AutoBlock Require Press", false);
        this.autoBlockCPS = new IntProperty("AutoBlock Aps", 10, 1, 20);
        this.autoBlockRange = new FloatProperty("AutoBlock Range", 6.0F, 3.0F, 8.0F);
        this.swingRange = new FloatProperty("Swing Range", 3.5F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("Attack Range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("Fov", 360, 30, 360);
        this.minCPS = new IntProperty("Min Aps", 14, 1, 20);
        this.maxCPS = new IntProperty("Max Aps", 14, 1, 20);
        this.switchDelay = new IntProperty("Switch Delay", 150, 0, 1000);
        this.rotations = new ModeProperty("Rotations", 2, new String[]{"None", "Legit", "Silent", "Lock View"});
        this.moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent", "Strict", "Strafe"});
        this.rotationMode = new ModeProperty("RotationMode", 2, new String[]{"Normal", "Nearest", "Smart"});
        this.smoothing = new PercentProperty("Smoothing", 0);
        this.angleStep = new IntProperty("Angle Step", 90, 30, 180);
        this.throughWalls = new BooleanProperty("Through Walls", true);
        this.requirePress = new BooleanProperty("Require Press", false);
        this.allowPlayerBlocking = new BooleanProperty("Allow Player Blocking", true);
        this.allowMining = new BooleanProperty("Allow Mining", false);
        this.weaponsOnly = new BooleanProperty("Weapons Only", false);
        this.allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("Inventory Check", true);
        this.lowTimerCheck = new BooleanProperty("Low Timer Check", true);
        this.botCheck = new BooleanProperty("Bot Check", true);
        this.players = new BooleanProperty("Players", true);
        this.bosses = new BooleanProperty("Bosses", false);
        this.mobs = new BooleanProperty("Mobs", false);
        this.animals = new BooleanProperty("Animals", false);
        this.golems = new BooleanProperty("Golems", false);
        this.silverfish = new BooleanProperty("Silverfish", false);
        this.teams = new BooleanProperty("Teams", true);
    }
    private long getAttackDelay() {
        return this.isBlocking ? (long) (1000.0F / this.autoBlockCPS.getValue()) : 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private boolean performAttack(float yaw, float pitch) {
        if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
            if (this.bufferPending) {
                this.bufferPending = false;
                if (this.target != null
                        && this.isValidTarget(this.target.getEntity())
                        && !(this.isPlayerBlocking() && this.autoBlock.getValue() != 1)
                        && !((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.target.getBox()))
                            && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null)) {
                    return this.sendAttackPacket();
                }
                return false;
            }
            if (Velocity.stoppedBlock){
                return false;
            }else if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) {
                return false;
            } else if (this.attackDelayMS > 0L) {
                return false;
            }
            else if (((IAccessorMinecraft)mc).getTimer().timerSpeed < 1F && lowTimerCheck.getValue()){
                return false;
            }
            else if (Leader.moduleManager.getModule(SmartAttack.class).isEnabled() && SmartAttack.shouldCancel && SmartAttack.onKillAura.getValue()){
                return false;
            }
            else if (Velocity.extraAttacked && (this.autoBlock.getValue() == 2 || this.autoBlock.getValue() == 3)){
                ChatUtil.sendFormatted("StoppedAttack");
                Velocity.extraAttacked = false;
                Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
                if (velocity.reduceMode.getValue() == 2) {
                    if (isOldHypixel() || isHypixelWithoutNoSlow()) {
                        blockTick = 0;
                    } else if (isHypixelCustom()) {
                        blockTick = attackTick.getValue();
                    } else if (isLag()) {
                        blockTick = 0;
                    } else if (autoBlock.getValue() == 3) {
                        blockTick = 0;
                    }
                } else if (velocity.reduceMode.getValue() == 1){
                    if (isOldHypixel() || isHypixelWithoutNoSlow()) {
                        blockTick = 2;
                    } else if (isHypixelCustom()) {
                        blockTick = attackTick.getValue();
                    } else if (isLag()) {
                        if (getEffectiveLagMode() == 10) {
                            blockTick = 4;
                        } else if (getEffectiveLagMode() == 0) {
                            blockTick = 1;
                        } else if (getEffectiveLagMode() == 1) {
                            blockTick = 2;
                        } else if (getEffectiveLagMode() == 2) {
                            blockTick = 3;
                        } else if (getEffectiveLagMode() == 5) {
                            blockTick = 5;
                        } else if (getEffectiveLagMode() == 8) {
                            blockTick = 3;
                        } else if (getEffectiveLagMode() == 9) {
                            blockTick = 2;
                        } else {
                            blockTick = 4;
                        }
                    } else if (autoBlock.getValue() == 3) {
                        blockTick = 1;
                    }
                }
                return false;
            }
            else {
                this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
                if (!isBufferEnabled()) mc.thePlayer.swingItem();
                if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.target.getBox()))
                        && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
                    return false;
                } else {
                    if (this.isBufferEnabled()) {
                        mc.thePlayer.setSprinting(false);
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                        if (this.shouldDelayAttack()) {
                            this.bufferPending = true;
                            return false;
                        }
                    }
                    return this.sendAttackPacket();
                }
            }
        } else {
            return false;
        }
    }

    private void sendUseItem() {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        this.startBlock(mc.thePlayer.getHeldItem());
    }

    private boolean sendAttackPacket() {
        if (isBufferEnabled()) mc.thePlayer.swingItem();
        AttackEvent event = new AttackEvent(this.target.getEntity());
        EventManager.call(event);
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(this.target.getEntity());
        }
        this.hitRegistered = true;
        return true;
    }

    private boolean isBufferEnabled() {
        KeepSprint keepSprint = (KeepSprint) Leader.moduleManager.getModule(KeepSprint.class);
        return keepSprint != null && keepSprint.isEnabled() && keepSprint.isBufferMode();
    }

    private boolean shouldDelayAttack() {
        KeepSprint keepSprint = (KeepSprint) Leader.moduleManager.getModule(KeepSprint.class);
        if (keepSprint == null || !keepSprint.isEnabled() || !keepSprint.isBufferMode()) {
            return false;
        }
        return !(mc.thePlayer.hurtTime > 0 && !keepSprint.onHurt.getValue());
    }

    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (this.target != null) {
            MovingObjectPosition mop = RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, 8.0);
            if (mop != null) {
                ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                PacketUtil.sendPacket(
                        new C02PacketUseEntity(
                                this.target.getEntity(),
                                new Vec3(mop.hitVec.xCoord - this.target.getX(), mop.hitVec.yCoord - this.target.getY(), mop.hitVec.zCoord - this.target.getZ())
                        )
                );
                PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                this.blockingState = true;
            }
        }
    }
    private boolean isNormalTargetVisible(AxisAlignedBB box) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        double minTargetY = box.minY + 0.05 * (box.maxY - box.minY);
        double maxTargetY = box.minY + 0.75 * (box.maxY - box.minY);
        double targetY = MathHelper.clamp_double(eyePos.yCoord, minTargetY, maxTargetY);
        double targetX = (box.minX + box.maxX) / 2.0;
        double targetZ = (box.minZ + box.maxZ) / 2.0;
        Vec3 targetPoint = new Vec3(targetX, targetY, targetZ);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyePos, targetPoint, false, true, false);
        return mop == null;
    }
    private boolean canAttack() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
                return false;
            } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                AutoHeal autoHeal = (AutoHeal) Leader.moduleManager.modules.get(AutoHeal.class);
                if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
                    return false;
                } else {
                    BedNuker bedNuker = (BedNuker) Leader.moduleManager.modules.get(BedNuker.class);
                    AutoBlockIn autoBlockIn = (AutoBlockIn) Leader.moduleManager.modules.get(AutoBlockIn.class);
                    if (bedNuker.isEnabled() && bedNuker.isReady()) {
                        return false;
                    } else if (Leader.moduleManager.modules.get(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (autoBlockIn.isEnabled()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttacking();
                    } else {
                        return !this.allowMining.getValue() || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK) || !PlayerUtil.isAttacking();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean canAutoBlock() {
        if (Velocity.stoppedBlock){
            return false;
        }
        else if (Leader.moduleManager.getModule(SmartAttack.class).isEnabled() && SmartAttack.shouldCancel && SmartAttack.cancelAuraBlocking.getValue() && SmartAttack.onKillAura.getValue()){
            return false;
        }
        else if (!ItemUtil.isHoldingSword()) {
            return false;
        } else {
            return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .anyMatch(
                        entity -> entity instanceof EntityLivingBase
                                && this.isValidTarget((EntityLivingBase) entity)
                                && this.isInBlockRange((EntityLivingBase) entity)
                );
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityLivingBase.deathTime > 0) {
                return false;
            } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
                return false;
            } else if (!this.throughWalls.getValue() && !RotationUtil.hasVisiblePoint(entityLivingBase.getEntityBoundingBox())) {
                return false;
            } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
                if (!this.players.getValue()) {
                    return false;
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return false;
                } else {
                    return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)) && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer) entityLivingBase));
                }
            } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
                if (entityLivingBase instanceof EntityAnimal
                        || entityLivingBase instanceof EntityBat
                        || entityLivingBase instanceof EntitySquid
                        || entityLivingBase instanceof EntityVillager) {
                    return this.animals.getValue();
                } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                    return false;
                } else {
                    return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
                }
            } else if (!(entityLivingBase instanceof EntitySilverfish)) {
                return this.mobs.getValue();
            } else {
                return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
            }
        } else {
            return false;
        }
    }

    private boolean isInRange(EntityLivingBase entityLivingBase) {
        return this.isInBlockRange(entityLivingBase) || this.isInSwingRange(entityLivingBase) || this.isInAttackRange(entityLivingBase);
    }

    private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.autoBlockRange.getValue();
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
    }

    private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }



    public EntityLivingBase getTarget() {
        return this.target != null ? this.target.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        } else {
            return false;
        }
    }

    public boolean isOldHypixel() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 0;
    }

    public boolean isHypixelWithoutNoSlow() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 1;
    }

    public boolean isHypixelCustom() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 2;
    }

    public boolean isLag() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 3;
    }

    public boolean isLag3Tick() {
        return this.isLag() && this.getEffectiveLagMode() == 1;
    }

    public boolean isLag4Tick() {
        return this.isLag() && this.getEffectiveLagMode() == 2;
    }
    public boolean isLag5Tick() {
        return this.isLag() && (this.getEffectiveLagMode() == 3 || this.getEffectiveLagMode() == 4 || this.getEffectiveLagMode() == 5 || this.getEffectiveLagMode() ==  6 || this.getEffectiveLagMode() == 7 || this.getEffectiveLagMode() == 8);
    }

    public int getEffectiveLagMode() {
        if (!this.isLag()) {
            return 1;
        }
        switch (this.lagClass.getValue()) {
            case 1: return 3;
            case 2: return this.fullMode.getValue() == 0 ? 6 : 7;
            case 3: return this.swapMode.getValue() == 0 ? 8 : 9;
            case 4: return 10;
            default:
                int tick = this.tickMode.getValue();
                if (tick == 3) return 4;
                if (tick == 4) return 5;
                return tick;
        }
    }

    public boolean isPredict() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 4;
    }
    public boolean shouldAutoBlock() {
        if (this.autoBlock.getValue() <= 1 || this.autoBlock.getValue() == 4) {
            return this.hasValidTarget();
        }
        if (this.isPlayerBlocking() && this.isBlocking) {
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava() && (this.autoBlock.getValue() == 2 || this.autoBlock.getValue() == 3);
        } else {
            return false;
        }
    }

    public boolean velocityCanReduce(int phase, int tick) {
        switch (this.autoBlock.getValue()) {
            case 0:
            case 1:
            case 4:
                return true;
            case 2:
                switch (this.hypixelMode.getValue()) {
                    case 0:
                    case 1:
                        return phase == 2 ? tick == 2 : tick == 0;
                    case 2: {
                        int maxT = Math.max(1, this.maxTick.getValue() - 1);
                        switch (phase) {
                            case 0:
                                return tick == this.attackTick.getValue();
                            case 1:
                                return tick == this.attackTick.getValue() % maxT;
                            default:
                                return tick == (this.attackTick.getValue() - 2 + maxT) % maxT;
                        }
                    }
                    case 3:
                        switch (this.getEffectiveLagMode()) {
                            case 0:
                                return phase == 2 ? tick == 1 : tick == 0;
                            case 1:
                                return phase == 2 ? tick == 2 : phase == 1 ? tick == 0 : (tick == 0 || tick == 2);
                            case 2:
                                return phase == 2 ? tick == 3 : phase == 1 ? tick == 0 : (tick == 0 || tick == 3);
                            case 3:
                                return phase == 2 ? tick == 4 : phase == 1 ? tick == 0 : (tick == 0 || tick == 2 || tick == 4);
                            case 4:
                                return phase == 2 ? tick == 4 : phase == 1 ? tick == 0 : (tick == 0 || tick == 4);
                            case 5:
                                return phase == 2 ? tick == 5 : phase == 1 ? tick == 0 : (tick == 0 || tick == 5);
                            case 8:
                                return phase == 2 ? tick == 3 : phase == 1 ? tick == 0 : (tick == 0 || tick == 3);
                            case 9:
                                return phase == 2 ? tick == 2 : tick == 0;
                            case 10:
                                return phase == 2 ? tick == 4 : phase == 1 ? tick == 0 : (tick == 0 || tick == 4);
                            default:
                                return false;
                        }
                    case 4:
                        return !this.predictBlocking;
                    default:
                        return false;
                }
            case 3:
                return phase == 2 ? tick == 1 : tick == 0;
            default:
                return true;
        }
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) throws AWTException {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.attackDelayMS > 0L) {
                this.attackDelayMS -= 50L;
            }
            boolean attack = this.target != null && this.canAttack();
            boolean block = attack && this.canAutoBlock();
            if (!block) {
                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                if (isOldHypixel() && isBlocking && Leader.moduleManager.getModule(NoSlow.class).isEnabled()) {
                    this.isBlocking = false;
                    stopBlock();
                }
                if (this.predictBlocking) {
                    if (this.isPlayerBlocking()) {
                        this.stopBlock();
                    }
                    this.predictBlocking = false;
                    holdTicks = 0;
                }
                if (swapped){
                    int handle = mc.thePlayer.inventory.currentItem;
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                    swapped = false;
                }
                else this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
            }
            if (attack) {
                this.strafeFacing = false;
                if (predictBlocking){
                    holdTicks++;
                }
                boolean swap = false;
                boolean blocked = false;
                if (block) {
                    switch (this.autoBlock.getValue()) {
                        case 0:
                            if (PlayerUtil.isUsingItem()) {
                                this.isBlocking = true;
                                if (!this.isPlayerBlocking() && !Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                    swap = true;
                                }
                            } else {
                                this.isBlocking = false;
                                if (this.isPlayerBlocking() && !Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                    this.stopBlock();
                                }
                            }
                            Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.fakeBlockState = false;
                            break;
                        case 1:
                            if (this.hasValidTarget()) {
                                if (!this.isPlayerBlocking() && !Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                    swap = true;
                                }
                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 2:
                            switch (this.hypixelMode.getValue()) {
                                case 0:
                                    if (this.hasValidTarget()) {
                                        if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                            switch (this.blockTick) {
                                                case 0:
                                                    if (!this.isPlayerBlocking()) {
                                                        swap = true;
                                                    }
                                                    blocked = true;
                                                    this.blockTick = 1;
                                                    break;
                                                case 1:
                                                    attack = false;
                                                    this.blockTick = 2;
                                                    break;
                                                case 2:
                                                    if (this.isPlayerBlocking()) {
                                                        if (!noStop.getValue()) {
                                                            int handle = mc.thePlayer.inventory.currentItem;
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                        }
                                                        this.stopBlock();
                                                    }
                                                    if (test.getValue()){
                                                        if (testAttackTick >= moreAttackDelay.getValue()){
                                                            testAttackTick = 0;
                                                        }
                                                        else {
                                                            testAttackTick++;
                                                            attack = false;
                                                        }
                                                    }
                                                    else {
                                                        attack = false;
                                                    }
                                                    this.blockTick = 0;
                                                    break;
                                                default:
                                                    this.blockTick = 0;
                                                    break;
                                            }
                                        }
                                        this.isBlocking = true;
                                        this.fakeBlockState = true;
                                    } else {
                                        Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                        this.isBlocking = false;
                                        this.fakeBlockState = false;
                                        PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getSwapSlot()));
                                        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                        Velocity.extraAttacked = false;
                                    }
                                    break;
                                case 1:
                                    if (this.hasValidTarget()) {
                                        if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                            switch (this.blockTick) {
                                                case 0:
                                                    Leader.blinkManager.setBlinkState(false,BlinkModules.AUTO_BLOCK);
                                                    if (!this.isPlayerBlocking()) {
                                                        swap = true;
                                                    }
                                                    this.blockTick = 1;
                                                    break;
                                                case 1:
                                                    attack = false;
                                                    blockTick = 2;
                                                    break;
                                                case 2:
                                                    Leader.blinkManager.setBlinkState(true,BlinkModules.AUTO_BLOCK);
                                                    if (this.isPlayerBlocking()) {
                                                        this.stopBlock();
                                                    }
                                                    if (test.getValue()){
                                                        if (testAttackTick >= moreAttackDelay.getValue()){
                                                            testAttackTick = 0;
                                                        }
                                                        else {
                                                            testAttackTick++;
                                                            attack = false;
                                                        }
                                                    }
                                                    this.blockTick = 0;
                                                    break;
                                                default:
                                                    this.blockTick = 0;
                                            }
                                        }
                                        this.isBlocking = true;
                                        this.fakeBlockState = true;
                                    } else {
                                        Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                        this.isBlocking = false;
                                        this.fakeBlockState = false;
                                        Velocity.extraAttacked = false;
                                    }
                                    break;
                                case 2:
                                    if (this.hasValidTarget()) {
                                        if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                            if (blockTick + 1 == startBlinkTick.getValue()){
                                                blocked = true;
                                            }
                                            if (blockTick + 1 != attackTick.getValue()){
                                                attack = false;
                                            }
                                            if (blockTick + 1 == startBlockTick.getValue()){
                                                if (!this.isPlayerBlocking()) {
                                                    swap = true;
                                                    if (postStartBlock.getValue())postBlock = true;
                                                }
                                            }
                                            if (blockTick + 1 == stopBlinkTick.getValue()){
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                            }
                                            if (blockTick + 1 == swapTick.getValue()){
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getSwapSlot()));
                                                swapped = true;
                                            }
                                            if (blockTick + 1 == switchBackTick.getValue()){
                                                if (swapped){
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                                    swapped = false;
                                                }
                                            }
                                           if (blockTick + 1 == stopBlockTick.getValue()){
                                               if (this.isPlayerBlocking()) {
                                                   this.stopBlock();
                                               }
                                           }
                                            blockTick++;
                                            if (blockTick >= maxTick.getValue() - 1){
                                                blockTick = 0;
                                            }
                                        }
                                        this.isBlocking = true;
                                        this.fakeBlockState = true;
                                    } else {
                                        if (swapped){
                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                            swapped = false;
                                        }
                                        Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                        this.isBlocking = false;
                                        this.fakeBlockState = false;
                                        Velocity.extraAttacked = false;
                                    }
                                    break;
                                case 3:
                                    switch (this.getEffectiveLagMode()) {
                                        case 0:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            Leader.blinkManager.setBlinkState(false,BlinkModules.AUTO_BLOCK);
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            Leader.blinkManager.setBlinkState(true,BlinkModules.AUTO_BLOCK);
                                                            if (this.isPlayerBlocking()) {
                                                               this.stopBlock();
                                                            }
                                                            attack = false;
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 1:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            blocked = true;
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            if (this.isPlayerBlocking()) {
                                                                if (c09Instead.getValue()){
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                }
                                                                else this.stopBlock();
                                                            }
                                                            attack = false;
                                                            blockTick = 2;
                                                            break;
                                                        case 2:
                                                            Leader.blinkManager.setBlinkState(false,BlinkModules.AUTO_BLOCK);
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 2:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            blocked = true;
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            if (this.isPlayerBlocking()) {
                                                                if (fullC09.getValue()) {
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                    this.stopBlock();
                                                                }
                                                            }
                                                            attack = false;
                                                            blockTick = 2;
                                                            break;
                                                        case 2:
                                                            int handle = mc.thePlayer.inventory.currentItem;
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                            this.stopBlock();
                                                            blockTick = 3;
                                                            break;
                                                        case 3:
                                                            Leader.blinkManager.setBlinkState(false,BlinkModules.AUTO_BLOCK);
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 3:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            blocked = true;
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            if (this.isPlayerBlocking()) {
                                                                if (fullC09.getValue()) {
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                }
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            blockTick = 2;
                                                            break;
                                                        case 2:
                                                            blocked = true;
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            blockTick = 3;
                                                            break;
                                                        case 3:
                                                            if (this.isPlayerBlocking()) {
                                                                if (fullC09.getValue()) {
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                }
                                                                this.stopBlock();
                                                            }
                                                            blockTick = 4;
                                                            break;
                                                        case 4:
                                                            Leader.blinkManager.setBlinkState(false,BlinkModules.AUTO_BLOCK);
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 4:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            Leader.blinkManager.setBlinkState(true,BlinkModules.AUTO_BLOCK);
                                                            if (this.isPlayerBlocking()) {
                                                                if (fullC09.getValue()) {
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                }
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            blockTick = 2;
                                                            break;
                                                        case 2:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            blockTick = 3;
                                                            break;
                                                        case 3:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            blockTick = 4;
                                                            break;
                                                        case 4:
                                                            Leader.blinkManager.setBlinkState(false,BlinkModules.AUTO_BLOCK);
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 5:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            Leader.blinkManager.setBlinkState(true,BlinkModules.AUTO_BLOCK);
                                                            if (this.isPlayerBlocking()) {
                                                             if (fullC09.getValue()) {
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                   PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                               }
                                                             this.stopBlock();
                                                            }
                                                           attack = false;
                                                           this.blockTick = 2;
                                                           break;
                                                        case 2:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            blockTick = 3;
                                                            break;
                                                        case 3:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            blockTick = 4;
                                                            break;
                                                        case 4:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            blockTick = 5;
                                                            break;
                                                        case 5:
                                                            Leader.blinkManager.setBlinkState(false,BlinkModules.AUTO_BLOCK);
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 6:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            postBlink = true;
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            Leader.blinkManager.setBlinkState(true,BlinkModules.AUTO_BLOCK);
                                                            if (this.isPlayerBlocking()) {
                                                                if (fullC09.getValue()) {
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                }
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            this.blockTick = 2;
                                                            break;
                                                        case 2:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 7:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            postBlink = true;
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            Leader.blinkManager.setBlinkState(true,BlinkModules.AUTO_BLOCK);
                                                            if (this.isPlayerBlocking()) {
                                                                if (fullC09.getValue()) {
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                }
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            this.blockTick = 2;
                                                            break;
                                                        case 2:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            this.blockTick = 3;
                                                            break;
                                                        case 3:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 8:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            Leader.blinkManager.setBlinkState(true,BlinkModules.AUTO_BLOCK);
                                                            if (this.isPlayerBlocking()) {
                                                                if (fullC09.getValue()) {
                                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                }
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            this.blockTick = 2;
                                                            break;
                                                        case 2:
                                                            if (fullC09.getValue()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            attack = false;
                                                            this.blockTick = 3;
                                                            break;
                                                        case 3:
                                                            postBlink = true;
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 9:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            postBlinkReset = true;
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            Leader.blinkManager.setBlinkState(true,BlinkModules.AUTO_BLOCK);
                                                            if (this.isPlayerBlocking()) {
                                                                postSwap = true;
                                                            }
                                                            attack = false;
                                                            this.blockTick = 2;
                                                            break;
                                                        case 2:
                                                            int handle = mc.thePlayer.inventory.currentItem;
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                            attack = false;
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        case 10:
                                            if (this.hasValidTarget()) {
                                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                                    switch (this.blockTick) {
                                                        case 0:
                                                            Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                            blocked = true;
                                                            if (!this.isPlayerBlocking()) {
                                                                swap = true;
                                                            }
                                                            this.blockTick = 1;
                                                            break;
                                                        case 1:
                                                            attack = false;
                                                            this.blockTick = 2;
                                                            break;
                                                        case 2:
                                                            attack = false;
                                                            if (this.isPlayerBlocking()) {
                                                                int handle = mc.thePlayer.inventory.currentItem;
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                                this.stopBlock();
                                                            }
                                                            this.blockTick = 3;
                                                            break;
                                                        case 3:
                                                            attack = false;
                                                            int handle1 = mc.thePlayer.inventory.currentItem;
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle1)));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle1 % 7 + 2));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle1));
                                                            this.stopBlock();
                                                            this.blockTick = 4;
                                                            break;
                                                        case 4:
                                                            int handle = mc.thePlayer.inventory.currentItem;
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                            this.stopBlock();
                                                            attack = false;
                                                            if (this.attackDelayMS <= 50L) {
                                                                this.blockTick = 0;
                                                            }
                                                            break;
                                                        default:
                                                            this.blockTick = 0;
                                                    }
                                                }
                                                this.isBlocking = true;
                                                this.fakeBlockState = alwaysRenderBlocking.getValue();
                                            } else {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                                this.isBlocking = false;
                                                this.fakeBlockState = false;
                                                Velocity.extraAttacked = false;
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                    break;
                                case 4:
                                    if (this.hasValidTarget()) {
                                        if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                            int hurt = mc.thePlayer.hurtTime;
                                            if (hurt != 0 && hurt <= startHurtTime.getValue()) {
                                                if (!predictBlocking) {
                                                    if (!this.isPlayerBlocking()) {
                                                        swap = true;
                                                    }
                                                    this.predictBlocking = true;
                                                    holdTicks = 0;
                                                }
                                            }
                                            if(holdTicks >= holdBlockTick.getValue() && this.predictBlocking) {
                                                if (this.isPlayerBlocking()) {
                                                    this.stopBlock();
                                                }
                                                predictBlocking = false;
                                                holdTicks = 0;
                                            }
                                        }
                                        this.isBlocking = this.predictBlocking;
                                        this.fakeBlockState = this.predictBlocking;
                                    } else {
                                        if (this.predictBlocking) {
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                            }
                                            holdTicks = 0;
                                            this.predictBlocking = false;
                                        }
                                        Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                        this.isBlocking = false;
                                        this.fakeBlockState = false;
                                        Velocity.extraAttacked = false;
                                    }
                                    break;
                                default:
                                    break;
                            }
                            break;
                        case 3:
                            if (this.hasValidTarget()) {
                                if (!Leader.playerStateManager.digging && !Leader.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                                attack = false;
                                            }
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                                Velocity.extraAttacked = false;
                            }
                            break;
                        case 4:
                            Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = this.hasValidTarget();
                            if (PlayerUtil.isUsingItem()
                                    && !this.isPlayerBlocking()
                                    && !Leader.playerStateManager.digging
                                    && !Leader.playerStateManager.placing) {
                                swap = true;
                            }
                            break;
                    }
                }
                boolean attacked = false;
                if (this.isBoxInSwingRange(this.target.getBox())) {
                    boolean willAttack = attack && this.attackDelayMS <= 0L && hasValidTarget();
                    boolean strafe = this.moveFix.getValue() == 3 && !willAttack;
                    this.strafeFacing = strafe;
                    if (strafe) {
                        this.applyStrafeRotation(event);
                    }
                    if (!strafe && (this.rotations.getValue() == 2 || this.rotations.getValue() == 3)) {
                            AxisAlignedBB box = this.target.getBox();
                            float currentYaw = event.getYaw();
                            float currentPitch = event.getPitch();
                            float angleStep = (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
                            float smooth = (float) this.smoothing.getValue() / 100.0F;
                            float[] rotations;
                            int mode = this.rotationMode.getValue();
                            if (mode == 1) {
                                rotations = RotationUtil.nearestRotation(box, currentYaw, currentPitch, angleStep, smooth);
                            } else if (mode == 2) {
                                if (this.isNormalTargetVisible(box)) {
                                    rotations = RotationUtil.getRotationsToBox(box, currentYaw, currentPitch, angleStep, smooth);
                                } else {
                                    rotations = RotationUtil.nearestRotation(box, currentYaw, currentPitch, angleStep, smooth);
                                }
                            } else {
                                rotations = RotationUtil.getRotationsToBox(box, currentYaw, currentPitch, angleStep, smooth);
                            }
                            if (rotations != null) {
                                event.setRotation(rotations[0], rotations[1], 1);
                            }
                            if (this.rotations.getValue() == 3) {
                                if (rotations != null) {
                                    Leader.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                                }
                            }
                            if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) {
                                if (rotations != null) {
                                    event.setPervRotation(rotations[0], 1);
                                }
                            }
                        }
                    if (attack && !(Velocity.cancellingKillAuraAttack && Leader.moduleManager.getModule(Velocity.class).isEnabled())) {
                        attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
                    }
                }
                if (swap) {
                    if (attacked) {
                        this.interactAttack(event.getNewYaw(), event.getNewPitch());
                    } else {
                        if (!postBlock) this.sendUseItem();
                    }
                }
                if (blocked) {
                    Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    Leader.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                }
            } else if (this.moveFix.getValue() == 3) {
                this.applyStrafeRotation(event);
            }
        }
        if (event.getType() == EventType.POST && this.isEnabled()){
            if (postBlinkReset){
                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                Leader.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                postBlinkReset = false;
            }
            if (postBlink){
                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                postBlink = false;
            }
            if (postSwap){
                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getSwapSlot()));
                mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload("send", new PacketBuffer(Unpooled.buffer())));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                this.stopBlock();
                postSwap = false;
            }
            if (postBlock){
                sendUseItem();
                postBlock = false;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    if (this.target == null
                            || !this.isValidTarget(this.target.getEntity())
                            || !this.isBoxInAttackRange(this.target.getBox())
                            || !this.isBoxInSwingRange(this.target.getBox())
                            || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                        this.timer.reset();
                        ArrayList<EntityLivingBase> targets = new ArrayList<>();
                        for (Entity entity : mc.theWorld.loadedEntityList) {
                            if (entity instanceof EntityLivingBase
                                    && this.isValidTarget((EntityLivingBase) entity)
                                    && this.isInRange((EntityLivingBase) entity)) {
                                targets.add((EntityLivingBase) entity);
                            }
                        }
                        if (targets.isEmpty()) {
                            this.target = null;
                        } else {
                            if (targets.stream().anyMatch(this::isInSwingRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isInAttackRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isPlayerTarget)) {
                                targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
                            }
                            targets.sort(
                                    (entityLivingBase1, entityLivingBase2) -> {
                                        int sortBase = 0;
                                        switch (this.sort.getValue()) {
                                            case 1:
                                                sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                                                break;
                                            case 2:
                                                sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                                                break;
                                            case 3:
                                                sortBase = Float.compare(
                                                        RotationUtil.angleToEntity(entityLivingBase1),
                                                        RotationUtil.angleToEntity(entityLivingBase2)
                                                );
                                        }
                                        return sortBase != 0
                                                ? sortBase
                                                : Double.compare(RotationUtil.distanceToEntity(entityLivingBase1), RotationUtil.distanceToEntity(entityLivingBase2));
                                    }
                            );
                            if (this.mode.getValue() == 1 && this.hitRegistered) {
                                this.hitRegistered = false;
                                this.switchTick++;
                            }
                            if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
                                this.switchTick = 0;
                            }
                            this.target = new AttackData(targets.get(this.switchTick));
                        }
                    }
                    if (this.target != null) {
                        this.target = new AttackData(this.target.getEntity());
                    }
                    break;
                case POST:
                    if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
                        mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                    }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (event.getPacket() instanceof C07PacketPlayerDigging) {
                C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
                if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.blockingState = false;
                }
            }
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                this.blockingState = false;
                if (this.isBlocking) {
                    mc.thePlayer.stopUsingItem();
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!this.isEnabled()) return;
        if (this.moveFix.getValue() == 3 && this.strafeFacing && this.strafeYawOffset != 0.0F
                && RotationState.isActived()) {
            MoveUtil.fixStrafe(this.strafeYaw);
            return;
        }
        if (this.moveFix.getValue() == 1
                && this.rotations.getValue() != 3
                && RotationState.isActived()
                && RotationState.getPriority() == 1.0F
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()
                && this.moveFix.getValue() == 3
                && !this.strafeFacing
                && !mc.thePlayer.isSprinting()) {
            mc.thePlayer.movementInput.jump = false;
        }
    }

    private float getYawOffsetFromKeys() {
        if (mc.gameSettings.keyBindForward.isKeyDown() && mc.gameSettings.keyBindLeft.isKeyDown()) {
            return 45.0F;
        }
        if (mc.gameSettings.keyBindForward.isKeyDown() && mc.gameSettings.keyBindRight.isKeyDown()) {
            return -45.0F;
        }
        if (mc.gameSettings.keyBindBack.isKeyDown() && mc.gameSettings.keyBindLeft.isKeyDown()) {
            return 135.0F;
        }
        if (mc.gameSettings.keyBindBack.isKeyDown() && mc.gameSettings.keyBindRight.isKeyDown()) {
            return -135.0F;
        }
        if (mc.gameSettings.keyBindBack.isKeyDown()) {
            return 180.0F;
        }
        if (mc.gameSettings.keyBindLeft.isKeyDown()) {
            return 90.0F;
        }
        if (mc.gameSettings.keyBindRight.isKeyDown()) {
            return -90.0F;
        }
        return 0.0F;
    }

    private void applyStrafeRotation(UpdateEvent event) {
        this.strafeYawOffset = this.getYawOffsetFromKeys();
        this.strafeYaw = mc.thePlayer.rotationYaw - this.strafeYawOffset;
        this.strafeFacing = this.strafeYawOffset != 0.0F;
        event.setRotation(this.strafeYaw, mc.thePlayer.rotationPitch, 0);
        event.setPervRotation(this.strafeYaw, 0);
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
    }
    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack() && !allowPlayerBlocking.getValue()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.target = null;
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.blockTick = 0;
        this.bufferPending = false;
        this.predictBlocking = false;
    }

    @Override
    public void onDisabled() {
        Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        Velocity.extraAttacked = false;
        this.blockingState = false;
        this.fakeBlockState = false;
        this.bufferPending = false;
        this.predictBlocking = false;
        if (swapped){
            int handle = mc.thePlayer.inventory.currentItem;
            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
            swapped = false;
        }
        if (isOldHypixel() && isBlocking && Leader.moduleManager.getModule(NoSlow.class).isEnabled()) {
            this.isBlocking = false;
            stopBlock();
        }
        else this.isBlocking = false;
    }

    @Override
    public void verifyValue(String mode) {
        if (!this.autoBlock.getName().equals(mode) && !this.autoBlockCPS.getName().equals(mode)) {
            if (this.swingRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.attackRange.setValue(this.swingRange.getValue());
                }
            } else if (this.attackRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.swingRange.setValue(this.attackRange.getValue());
                }
            } else if (this.minCPS.getName().equals(mode)) {
                if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.maxCPS.setValue(this.minCPS.getValue());
                }
            } else {
                if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.minCPS.setValue(this.maxCPS.getValue());
                }
            }
        }
    }
    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;

        public AttackData(EntityLivingBase entityLivingBase) {
            this.entity = entityLivingBase;
            double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}