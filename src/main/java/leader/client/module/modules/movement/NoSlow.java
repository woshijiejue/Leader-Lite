package leader.client.module.modules.movement;

import com.google.common.base.CaseFormat;
import leader.mixin.IAccessorPlayerControllerMP;
import leader.client.util.player.BlockUtil;
import leader.client.util.player.ItemUtil;
import leader.client.util.player.PlayerUtil;
import leader.client.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import leader.client.Leader;
import leader.client.enums.BlinkModules;
import leader.client.enums.FloatModules;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.*;
import leader.client.module.Module;
import leader.client.module.modules.combat.KillAura;
import leader.client.module.modules.misc.Disabler;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.IntProperty;
import leader.client.property.properties.ModeProperty;
import leader.client.property.properties.PercentProperty;
import leader.client.util.*;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty swordMode = new ModeProperty("Sword Mode", 1, new String[]{"None", "Vanilla", "BlinkSemi","Prediction","PredictionSemi"});
    public final BooleanProperty tick0 = new BooleanProperty("Tick 0", true, () -> swordMode.getValue() == 4);
    public final BooleanProperty tick1 = new BooleanProperty("Tick 1", true, () -> swordMode.getValue() == 4);
    public final BooleanProperty tick2 = new BooleanProperty("Tick 2", false, () -> swordMode.getValue() == 4);
    public final BooleanProperty tick3 = new BooleanProperty("Tick 3", false, () -> swordMode.getValue() == 4);
    public final BooleanProperty slowOnRelease = new BooleanProperty("SlowOnRelease",true,() -> this.swordMode.getValue() == 3);
    public final IntProperty swapDelay = new IntProperty("Slow Delay", 0, 0, 3, () -> swordMode.getValue() == 3);
    public final PercentProperty swordMotion = new PercentProperty("Sword Motion", 100, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty swordSprint = new BooleanProperty("Sword Sprint", true, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty onlyKillAuraAutoBlock = new BooleanProperty("Only Kill Aura Auto Block", false, () -> this.swordMode.getValue() != 0);
    public final ModeProperty foodMode = new ModeProperty("Food Mode", 0, new String[]{"None", "Vanilla", "Float"});
    public final PercentProperty foodMotion = new PercentProperty("Food Motion", 100, () -> this.foodMode.getValue() != 0);
    public final BooleanProperty foodSprint = new BooleanProperty("Food Sprint", true, () -> this.foodMode.getValue() != 0);
    public final ModeProperty bowMode = new ModeProperty("Bow Mode", 0, new String[]{"None", "Vanilla", "Float"});
    public final PercentProperty bowMotion = new PercentProperty("Bow Motion", 100, () -> this.bowMode.getValue() != 0);
    public final BooleanProperty bowSprint = new BooleanProperty("Bow Sprint", true, () -> this.bowMode.getValue() != 0);
    private int lastSlot = -1;
    private int delay = 0;
    private int blinkDelay = 0;

    public NoSlow() {
        super("NoSlow", false);
    }

    public boolean isSwordActive() {
        return this.swordMode.getValue() != 0 && ItemUtil.isHoldingSword() && (!this.onlyKillAuraAutoBlock.getValue() || this.isKillAuraAutoBlocking());
    }

    public boolean isFoodActive() {
        return this.foodMode.getValue() != 0 && ItemUtil.isEating();
    }

    public boolean isBowActive() {
        return this.bowMode.getValue() != 0 && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.foodMode.getValue() == 2 && ItemUtil.isEating()
                || this.bowMode.getValue() == 2 && ItemUtil.isUsingBow();
    }

    private boolean isKillAuraAutoBlocking() {
        KillAura aura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (!aura.shouldAutoBlock() || !aura.isEnabled()) {
            return false;
        }
        return aura.isBlocking;
    }

    public boolean isAnyActive() {
        if (this.swordMode.getValue() != 2 && this.swordMode.getValue() != 3 && this.swordMode.getValue() != 4) {
            return mc.thePlayer.isUsingItem() && (this.isSwordActive() || this.isFoodActive() || this.isBowActive());
        } else if (this.swordMode.getValue() == 2 && isSwordActive()) {
            return blinkDelay == 2;
        } else if (swordMode.getValue() == 3 && isSwordActive()) {
            KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
            if (!slowOnRelease.getValue() || killAura.blockTick != 0) {
                return delay == 0;
            }
        } else if (this.swordMode.getValue() == 4 && isSwordActive()) {
            KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
            return killAura.isEnabled() && killAura.shouldAutoBlock()
                && ((tick0.getValue() && killAura.blockTick == 0)
                    || (tick1.getValue() && killAura.blockTick == 1)
                    || (tick2.getValue() && killAura.blockTick == 2)
                    || (tick3.getValue() && killAura.blockTick == 3));
        }
        return false;
    }

    public boolean canSprint() {
        return this.isSwordActive() && this.swordSprint.getValue()
                || this.isFoodActive() && this.foodSprint.getValue()
                || this.isBowActive() && this.bowSprint.getValue();
    }

    public int getMotionMultiplier() {
        if (ItemUtil.isHoldingSword()) {
            return this.swordMotion.getValue();
        } else if (ItemUtil.isEating()) {
            return this.foodMotion.getValue();
        } else {
            return ItemUtil.isUsingBow() ? this.bowMotion.getValue() : 100;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
        if (isSwordActive() && PlayerUtil.isUsingItem()) {
            if (this.swordMode.getValue() == 3) {
                if (event.getType() == EventType.PRE) {
                    delay--;
                    if (delay < 0) {
                        if (!this.slowOnRelease.getValue() || killAura.blockTick != 0) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                        }
                        delay = swapDelay.getValue();
                    }
                }
            }
            if (this.swordMode.getValue() == 2) {
                if (event.getType() == EventType.PRE) {
                    if (blinkDelay == 2) {
                        int randomSlot = Disabler.getSwapSlot();
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                        mc.thePlayer.stopUsingItem();
                        blinkDelay = 0;
                    }
                    else {
                        if (!isKillAuraAutoBlocking() && blinkDelay == 0) {
                            Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(),mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                            Leader.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        }
                        blinkDelay++;
                    }
                }
            }
        }
        else
        {
            if (blinkDelay >= 0 && this.swordMode.getValue() == 2) {
                Leader.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                blinkDelay = -1;
            }
        }
    }
    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.isAnyActive()) {
            float multiplier = (float) this.getMotionMultiplier() / 100.0F;
            mc.thePlayer.movementInput.moveForward *= multiplier;
            mc.thePlayer.movementInput.moveStrafe *= multiplier;
            if (!this.canSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled() && this.isFloatMode()) {
            int item = mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != item && PlayerUtil.isUsingItem()) {
                this.lastSlot = item;
                Leader.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            Leader.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            if (mc.objectMouseOver != null) {
                switch (mc.objectMouseOver.typeOfHit) {
                    case BLOCK:
                        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                        if (BlockUtil.isInteractable(blockPos) && !PlayerUtil.isSneaking()) {
                            return;
                        }
                        break;
                    case ENTITY:
                        Entity entityHit = mc.objectMouseOver.entityHit;
                        if (entityHit instanceof EntityVillager) {
                            return;
                        }
                        if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                            return;
                        }
                }
            }
            if (this.isFloatMode() && !Leader.floatManager.isPredicted() && mc.thePlayer.onGround) {
                event.setCancelled(true);
                mc.thePlayer.motionY = 0.42F;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.swordMode.getModeString())};
    }
}
