package leader.client.module.modules.combat;

import com.google.common.base.CaseFormat;
import io.netty.buffer.Unpooled;
import leader.client.Leader;
import leader.client.module.modules.misc.AutoHeal;
import leader.client.module.modules.movement.NoSlow;
import leader.client.module.modules.player.BedNuker;
import leader.client.util.math.RandomUtil;
import leader.client.util.DebugUtil;
import leader.client.util.misc.KeyBindUtil;
import leader.client.util.player.*;
import leader.client.util.server.PacketUtil;
import leader.client.util.timer.TimerUtil;
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
import leader.client.component.impl.network.blink.BlinkType;
import leader.client.event.EventManager;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.*;
import leader.client.component.impl.rotaion.RotationState;
import leader.mixin.accessor.IAccessorMinecraft;
import leader.mixin.accessor.IAccessorPlayerControllerMP;
import leader.client.module.Module;
import leader.client.module.modules.misc.Disabler;
import leader.client.module.modules.player.AutoBlockIn;
import leader.client.module.modules.player.Scaffold;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;

import java.awt.*;
import java.util.ArrayList;

public class KillAura extends Module {
    public final ListValue mode;
    public final ListValue sort;
    public ListValue autoBlock;
    private final BoolValue noStop = new BoolValue("NoSwap", true, () -> this.autoBlock.is("OldHypixel"), this);
    private final BoolValue test = new BoolValue("MoreAttack", false, () -> this.autoBlock.is("OldHypixel"), this);
    private final SliderValue moreAttackDelay = new SliderValue("MoreAttackDelay", 1, 0, 3, () -> this.autoBlock.is("OldHypixel") && test.getValue(), Representation.INT, this);
    public final SliderValue maxTick = new SliderValue("MaxTick", 3, 1, 5, () -> this.autoBlock.is("Hypixel Custom"), Representation.INT, this);
    private final SliderValue startBlinkTick = new SliderValue("StartBlinkTick", 0, 1, 5, () -> this.autoBlock.is("Hypixel Custom"), Representation.INT, this);
    private final SliderValue stopBlinkTick = new SliderValue("StopBlinkTick", 2, 1, 5, () -> this.autoBlock.is("Hypixel Custom"), Representation.INT, this);
    private final SliderValue swapTick = new SliderValue("SwapTick", 2, 1, 5, () -> this.autoBlock.is("Hypixel Custom"), Representation.INT, this);
    private final SliderValue switchBackTick = new SliderValue("SwitchBackTick", 2, 1, 5, () -> this.autoBlock.is("Hypixel Custom"), Representation.INT, this);
    private final SliderValue stopBlockTick = new SliderValue("StopBlockTick", 2, 1, 5, () -> this.autoBlock.is("Hypixel Custom"), Representation.INT, this);
    public final SliderValue attackTick = new SliderValue("AttackTick", 0, 1, 5, () -> this.autoBlock.is("Hypixel Custom"), Representation.INT, this);
    private final SliderValue startBlockTick = new SliderValue("StartBlockTick", 0, 1, 5, () -> this.autoBlock.is("Hypixel Custom"), Representation.INT, this);
    private final BoolValue postStartBlock = new BoolValue("PostBlock", false, () -> this.autoBlock.is("Hypixel Custom"), this);
    private final BoolValue alwaysRenderBlocking = new BoolValue("AlwaysRenderBlocking", true, () -> this.autoBlock.is("HypixelLag"), this);
    private final BoolValue c09Instead = new BoolValue("C09Instead", true, () -> this.autoBlock.is("HypixelLag"), this);
    public final BoolValue autoBlockRequirePress;
    public final SliderValue autoBlockCPS;
    public final SliderValue autoBlockRange;
    public SliderValue swingRange;
    public SliderValue attackRange;
    public final SliderValue fov;
    public SliderValue minCPS;
    public SliderValue maxCPS;
    public final SliderValue switchDelay;
    public final ListValue rotations;
    public final ListValue moveFix;
    public ListValue rotationMode;
    public final SliderValue smoothing;
    public final SliderValue angleStep;
    public final BoolValue throughWalls;
    public final BoolValue requirePress;
    public final BoolValue allowMining;
    public final BoolValue allowPlayerBlocking;
    public final BoolValue weaponsOnly;
    public final BoolValue allowTools;
    public final BoolValue inventoryCheck;
    public final BoolValue lowTimerCheck;
    public final BoolValue botCheck;
    public final BoolValue players;
    public final BoolValue bosses;
    public final BoolValue mobs;
    public final BoolValue animals;
    public final BoolValue golems;
    public final BoolValue silverfish;
    public final BoolValue teams;

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
    private int testAttackTick = 0;

    public KillAura() {
        super("KillAura", false);
        this.mode = new ListValue("Mode", new String[]{"Single", "Switch"}, "Single", this);
        this.sort = new ListValue("Sort", new String[]{"Distance", "Health", "Hurt Time", "FOV"}, "Distance", this);

        this.autoBlock = new ListValue(
                "AutoBlock", new String[]{"None", "Vanilla", "OldHypixel", "Hypixel(Without NoSlow)", "Hypixel Custom", "HypixelTest", "HypixelLag", "Legit", "Fake"}, "None", this
        );
        this.autoBlockRequirePress = new BoolValue("AutoBlock Require Press", false, this);
        this.autoBlockCPS = new SliderValue("AutoBlock Aps", 10, 1, 20, Representation.INT, this);
        this.autoBlockRange = new SliderValue("AutoBlock Range", 6.0, 3.0, 8.0, Representation.FLOAT, this);
        this.swingRange = (SliderValue) new SliderValue("Swing Range", 3.5, 3.0, 6.0, Representation.FLOAT, this)
                .onChanged(() -> {
                    if (this.swingRange.getValue() < this.attackRange.getValue()) {
                        this.attackRange.setValue(this.swingRange.getValue());
                    }
                });
        this.attackRange = (SliderValue) new SliderValue("Attack Range", 3.0, 3.0, 6.0, Representation.FLOAT, this)
                .onChanged(() -> {
                    if (this.swingRange.getValue() < this.attackRange.getValue()) {
                        this.swingRange.setValue(this.attackRange.getValue());
                    }
                });
        this.fov = new SliderValue("Fov", 360, 30, 360, Representation.INT, this);
        this.minCPS = (SliderValue) new SliderValue("Min Aps", 14, 1, 20, Representation.INT, this)
                .onChanged(() -> {
                    if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                        this.maxCPS.setValue(this.minCPS.getValue());
                    }
                });
        this.maxCPS = (SliderValue) new SliderValue("Max Aps", 14, 1, 20, Representation.INT, this)
                .onChanged(() -> {
                    if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                        this.minCPS.setValue(this.maxCPS.getValue());
                    }
                });
        this.switchDelay = new SliderValue("Switch Delay", 150, 0, 1000, Representation.INT, this);
        this.rotations = new ListValue("Rotations", new String[]{"None", "Legit", "Silent", "Lock View"}, "Silent", this);
        this.moveFix = new ListValue("Move Fix", new String[]{"None", "Silent", "Strict"}, "Silent", this);
        this.rotationMode = new ListValue("RotationMode", new String[]{"Normal", "Nearest", "Smart"}, "Smart", this);
        this.smoothing = new SliderValue("Smoothing", 0, 0, 100, Representation.INT, this);
        this.angleStep = new SliderValue("Angle Step", 90, 30, 180, Representation.INT, this);
        this.throughWalls = new BoolValue("Through Walls", true, this);
        this.requirePress = new BoolValue("Require Press", false, this);
        this.allowPlayerBlocking = new BoolValue("Allow Player Blocking", true, this);
        this.allowMining = new BoolValue("Allow Mining", false, this);
        this.weaponsOnly = new BoolValue("Weapons Only", false, this);
        this.allowTools = new BoolValue("Allow Tools", false, this.weaponsOnly::getValue, this);
        this.inventoryCheck = new BoolValue("Inventory Check", true, this);
        this.lowTimerCheck = new BoolValue("Low Timer Check", true, this);
        this.botCheck = new BoolValue("Bot Check", true, this);
        this.players = new BoolValue("Players", true, this);
        this.bosses = new BoolValue("Bosses", false, this);
        this.mobs = new BoolValue("Mobs", false, this);
        this.animals = new BoolValue("Animals", false, this);
        this.golems = new BoolValue("Golems", false, this);
        this.silverfish = new BoolValue("Silverfish", false, this);
        this.teams = new BoolValue("Teams", true, this);
    }

    private long getAttackDelay() {
        return this.isBlocking ? (long) (1000.0F / this.autoBlockCPS.getValue().intValue()) : 1000L / RandomUtil.nextLong(this.minCPS.getValue().intValue(), this.maxCPS.getValue().intValue());
    }

    private boolean performAttack(float yaw, float pitch) {
        if (!Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
            if (Velocity.stoppedBlock) {
                return false;
            } else if (this.isPlayerBlocking() && !this.autoBlock.is("Vanilla")) {
                return false;
            } else if (this.attackDelayMS > 0L) {
                return false;
            } else if (((IAccessorMinecraft) mc).getTimer().timerSpeed < 1F && lowTimerCheck.getValue()) {
                return false;
            } else if (Leader.moduleManager.getModule(SmartAttack.class).isEnabled() && SmartAttack.shouldCancel && SmartAttack.onKillAura.getValue()) {
                return false;
            } else if (Velocity.extraAttacked && !this.autoBlock.is("None") && !this.autoBlock.is("Vanilla") && !this.autoBlock.is("Fake")) {
                DebugUtil.sendFormatted("StoppedAttack");
                Velocity.extraAttacked = false;
                Velocity velocity = (Velocity) Leader.moduleManager.getModule(Velocity.class);
                if (velocity.reduceMode.is("ReleaseBeforeCanAttack")) {
                    if (this.autoBlock.is("OldHypixel") || this.autoBlock.is("Hypixel(Without NoSlow)")) {
                        blockTick = 0;
                    } else if (this.autoBlock.is("Hypixel Custom")) {
                        blockTick = attackTick.getValue().intValue();
                    } else if (this.autoBlock.is("HypixelTest")) {
                        blockTick = (blockTick == 3) ? 0 : 2;
                    } else if (this.autoBlock.is("HypixelLag")) {
                        blockTick = 0;
                    } else if (this.autoBlock.is("Legit")) {
                        blockTick = 0;
                    }
                } else if (velocity.reduceMode.is("ReleaseWhenCanAttack")) {
                    if (this.autoBlock.is("OldHypixel") || this.autoBlock.is("Hypixel(Without NoSlow)")) {
                        blockTick = 2;
                    } else if (this.autoBlock.is("Hypixel Custom")) {
                        blockTick = attackTick.getValue().intValue();
                    } else if (this.autoBlock.is("HypixelTest")) {
                        blockTick = (blockTick == 0) ? 1 : 3;
                    } else if (this.autoBlock.is("HypixelLag")) {
                        blockTick = 2;
                    } else if (this.autoBlock.is("Legit")) {
                        blockTick = 1;
                    }
                }
                return false;
            } else {
                this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
                mc.thePlayer.swingItem();
                if ((!this.rotations.is("None") || !this.isBoxInAttackRange(this.target.getBox()))
                        && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
                    return false;
                } else {
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
            }
        } else {
            return false;
        }
    }

    private void sendUseItem() {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        this.startBlock(mc.thePlayer.getHeldItem());
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
        } else if (!this.weaponsOnly.getValue()
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
        if (Velocity.stoppedBlock) {
            return false;
        } else if (Leader.moduleManager.getModule(SmartAttack.class).isEnabled() && SmartAttack.shouldCancel && SmartAttack.cancelAuraBlocking.getValue() && SmartAttack.onKillAura.getValue()) {
            return false;
        } else if (!ItemUtil.isHoldingSword()) {
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

    public boolean shouldAutoBlock() {
        if (this.autoBlock.is("None") || this.autoBlock.is("Vanilla") || this.autoBlock.is("Fake")) {
            return this.hasValidTarget();
        }
        if (this.isPlayerBlocking() && this.isBlocking) {
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()
                    && (this.autoBlock.is("OldHypixel") || this.autoBlock.is("Hypixel(Without NoSlow)")
                        || this.autoBlock.is("Hypixel Custom") || this.autoBlock.is("HypixelTest")
                        || this.autoBlock.is("HypixelLag") || this.autoBlock.is("Legit"));
        } else {
            return false;
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
                Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                if (autoBlock.is("OldHypixel") && isBlocking && Leader.moduleManager.getModule(NoSlow.class).isEnabled()) {
                    this.isBlocking = false;
                    stopBlock();
                } else this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
            }
            if (attack) {
                boolean swap = false;
                boolean postBlink = false;
                boolean blocked = false;
                if (block) {
                    if (this.autoBlock.is("None")) {
                        if (PlayerUtil.isUsingItem()) {
                            this.isBlocking = true;
                            if (!this.isPlayerBlocking() && !Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
                                swap = true;
                            }
                        } else {
                            this.isBlocking = false;
                            if (this.isPlayerBlocking() && !Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
                                this.stopBlock();
                            }
                        }
                        Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                        this.fakeBlockState = false;
                    } else if (this.autoBlock.is("Vanilla")) {
                        if (this.hasValidTarget()) {
                            if (!this.isPlayerBlocking() && !Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
                                swap = true;
                            }
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            this.isBlocking = true;
                            this.fakeBlockState = false;
                        } else {
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                        }
                    } else if (this.autoBlock.is("OldHypixel")) {
                        if (this.hasValidTarget()) {
                            if (!Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
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
                                        if (test.getValue()) {
                                            if (testAttackTick >= moreAttackDelay.getValue().intValue()) {
                                                testAttackTick = 0;
                                            } else {
                                                testAttackTick++;
                                                attack = false;
                                            }
                                        } else {
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
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getSwapSlot()));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                            Velocity.extraAttacked = false;
                        }
                    } else if (this.autoBlock.is("Hypixel(Without NoSlow)")) {
                        if (this.hasValidTarget()) {
                            if (!Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
                                switch (this.blockTick) {
                                    case 0:
                                        Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
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
                                        Leader.blinkComponent.setBlinkState(true, BlinkType.AUTO_BLOCK);
                                        if (this.isPlayerBlocking()) {
                                            this.stopBlock();
                                        }
                                        if (test.getValue()) {
                                            if (testAttackTick >= moreAttackDelay.getValue().intValue()) {
                                                testAttackTick = 0;
                                            } else {
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
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                            Velocity.extraAttacked = false;
                        }
                    } else if (this.autoBlock.is("Hypixel Custom")) {
                        if (this.hasValidTarget()) {
                            if (!Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
                                if (blockTick + 1 == startBlinkTick.getValue().intValue()) {
                                    blocked = true;
                                }
                                if (blockTick + 1 != attackTick.getValue().intValue()) {
                                    attack = false;
                                }
                                if (blockTick + 1 == startBlockTick.getValue().intValue()) {
                                    if (!this.isPlayerBlocking()) {
                                        swap = true;
                                        if (postStartBlock.getValue()) postBlock = true;
                                    }
                                }
                                if (blockTick + 1 == stopBlinkTick.getValue().intValue()) {
                                    Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                                }
                                if (blockTick + 1 == swapTick.getValue().intValue()) {
                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getSwapSlot()));
                                    swapped = true;
                                }
                                if (blockTick + 1 == switchBackTick.getValue().intValue()) {
                                    if (swapped) {
                                        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                        swapped = false;
                                    }
                                }
                                if (blockTick + 1 == stopBlockTick.getValue().intValue()) {
                                    if (this.isPlayerBlocking()) {
                                        this.stopBlock();
                                    }
                                }
                                blockTick++;
                                if (blockTick >= maxTick.getValue().intValue() - 1) {
                                    blockTick = 0;
                                }
                            }
                            this.isBlocking = true;
                            this.fakeBlockState = true;
                        } else {
                            if (swapped) {
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                swapped = false;
                            }
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                            Velocity.extraAttacked = false;
                        }
                    } else if (this.autoBlock.is("HypixelTest")) {
                        if (this.hasValidTarget()) {
                            if (!Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
                                switch (this.blockTick) {
                                    case 0:
                                        postBlink = true;
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
                                        Leader.blinkComponent.setBlinkState(true, BlinkType.AUTO_BLOCK);
                                        if (this.isPlayerBlocking()) {
                                            int handle = mc.thePlayer.inventory.currentItem;
                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                            this.stopBlock();
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
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                            Velocity.extraAttacked = false;
                        }
                    } else if (this.autoBlock.is("HypixelLag")) {
                        if (this.hasValidTarget()) {
                            if (!Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
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
                                            if (c09Instead.getValue()) {
                                                int handle = mc.thePlayer.inventory.currentItem;
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                this.stopBlock();
                                            } else this.stopBlock();
                                        }
                                        attack = false;
                                        blockTick = 2;
                                        break;
                                    case 2:
                                        Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
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
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            if (isBlocking) {
                                this.stopBlock();
                            }
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                            Velocity.extraAttacked = false;
                        }
                    } else if (this.autoBlock.is("Legit")) {
                        if (this.hasValidTarget()) {
                            if (!Leader.playerStateComponent.digging && !Leader.playerStateComponent.placing) {
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
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            this.isBlocking = true;
                            this.fakeBlockState = false;
                        } else {
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                            Velocity.extraAttacked = false;
                        }
                    } else if (this.autoBlock.is("Fake")) {
                        Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                        this.isBlocking = false;
                        this.fakeBlockState = this.hasValidTarget();
                        if (PlayerUtil.isUsingItem()
                                && !this.isPlayerBlocking()
                                && !Leader.playerStateComponent.digging
                                && !Leader.playerStateComponent.placing) {
                            swap = true;
                        }
                    }
                }
                boolean attacked = false;
                if (this.isBoxInSwingRange(this.target.getBox())) {
                    if (this.rotations.is("Silent") || this.rotations.is("Lock View")) {
                        AxisAlignedBB box = this.target.getBox();
                        float currentYaw = event.getYaw();
                        float currentPitch = event.getPitch();
                        float angleStep = (float) this.angleStep.getValue().intValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
                        float smooth = (float) this.smoothing.getValue().intValue() / 100.0F;
                        float[] rotations;
                        if (this.rotationMode.is("Nearest")) {
                            rotations = RotationUtil.nearestRotation(box, currentYaw, currentPitch, angleStep, smooth);
                        } else if (this.rotationMode.is("Smart")) {
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
                        if (this.rotations.is("Lock View")) {
                            if (rotations != null) {
                                Leader.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                            }
                        }
                        if (!this.moveFix.is("None") || this.rotations.is("Lock View")) {
                            if (rotations != null) {
                                event.setPervRotation(rotations[0], 1);
                            }
                        }
                    }
                    if (attack) {
                        attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
                    }
                }
                if (postBlink) {
                    Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                }
                if (swap) {
                    if (attacked) {
                        this.interactAttack(event.getNewYaw(), event.getNewPitch());
                    } else {
                        if (!postBlock) this.sendUseItem();
                    }
                }
                if (blocked) {
                    Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                    Leader.blinkComponent.setBlinkState(true, BlinkType.AUTO_BLOCK);
                }
            }
        }
        if (event.getType() == EventType.POST && this.isEnabled()) {
            if (postSwap) {
                PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getSwapSlot()));
                mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload("send", new PacketBuffer(Unpooled.buffer())));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                this.stopBlock();
                postSwap = false;
            }
            if (postBlock) {
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
                                        if (this.sort.is("Health")) {
                                            sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                                        } else if (this.sort.is("Hurt Time")) {
                                            sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                                        } else if (this.sort.is("FOV")) {
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
                            if (this.mode.is("Switch") && this.hitRegistered) {
                                this.hitRegistered = false;
                                this.switchTick++;
                            }
                            if (this.mode.is("Single") || this.switchTick >= targets.size()) {
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
        if (this.isEnabled()) {
            if (this.moveFix.is("Silent")
                    && !this.rotations.is("Lock View")
                    && RotationState.isActived()
                    && RotationState.getPriority() == 1.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
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
    }

    @Override
    public void onDisabled() {
        Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
        Velocity.extraAttacked = false;
        this.blockingState = false;
        this.fakeBlockState = false;
        if (autoBlock.is("OldHypixel") && isBlocking && Leader.moduleManager.getModule(NoSlow.class).isEnabled()) {
            this.isBlocking = false;
            stopBlock();
        } else this.isBlocking = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getValue())};
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
