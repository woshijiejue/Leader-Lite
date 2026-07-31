package leader.client.module.modules.movement;

import com.google.common.base.CaseFormat;
import leader.client.util.server.PacketUtil;
import leader.mixin.accessor.IAccessorPlayerControllerMP;
import leader.client.util.player.BlockUtil;
import leader.client.util.player.ItemUtil;
import leader.client.util.player.PlayerUtil;
import leader.client.util.player.TeamUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import leader.client.Leader;
import leader.client.component.impl.network.blink.BlinkType;
import leader.client.component.impl.floater.FloatType;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.*;
import leader.client.module.Module;
import leader.client.module.modules.combat.KillAura;
import leader.client.module.modules.misc.Disabler;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;

public class NoSlow extends Module {
    
    public final ListValue swordMode = new ListValue("Sword Mode", new String[]{"None", "Vanilla", "BlinkSemi", "Prediction", "PredictionSemi"}, "Vanilla", this);
    public final BoolValue tick0 = new BoolValue("Tick 0", true, () -> swordMode.is("PredictionSemi"), this);
    public final BoolValue tick1 = new BoolValue("Tick 1", true, () -> swordMode.is("PredictionSemi"), this);
    public final BoolValue tick2 = new BoolValue("Tick 2", false, () -> swordMode.is("PredictionSemi"), this);
    public final BoolValue tick3 = new BoolValue("Tick 3", false, () -> swordMode.is("PredictionSemi"), this);
    public final BoolValue slowOnRelease = new BoolValue("SlowOnRelease", true, () -> this.swordMode.is("Prediction"), this);
    public final SliderValue swapDelay = new SliderValue("Slow Delay", 0, 0, 3, () -> swordMode.is("Prediction"), Representation.INT, this);
    public final SliderValue swordMotion = new SliderValue("Sword Motion", 100, 0, 100, () -> !this.swordMode.is("None"), Representation.INT, this);
    public final BoolValue swordSprint = new BoolValue("Sword Sprint", true, () -> !this.swordMode.is("None"), this);
    public final BoolValue onlyKillAuraAutoBlock = new BoolValue("Only Kill Aura Auto Block", false, () -> !this.swordMode.is("None"), this);
    public final ListValue foodMode = new ListValue("Food Mode", new String[]{"None", "Vanilla", "Float"}, "None", this);
    public final SliderValue foodMotion = new SliderValue("Food Motion", 100, 0, 100, () -> !this.foodMode.is("None"), Representation.INT, this);
    public final BoolValue foodSprint = new BoolValue("Food Sprint", true, () -> !this.foodMode.is("None"), this);
    public final ListValue bowMode = new ListValue("Bow Mode", new String[]{"None", "Vanilla", "Float"}, "None", this);
    public final SliderValue bowMotion = new SliderValue("Bow Motion", 100, 0, 100, () -> !this.bowMode.is("None"), Representation.INT, this);
    public final BoolValue bowSprint = new BoolValue("Bow Sprint", true, () -> !this.bowMode.is("None"), this);
    private int lastSlot = -1;
    private int delay = 0;
    private int blinkDelay = 0;

    public NoSlow() {
        super("NoSlow", false);
    }

    public boolean isSwordActive() {
        return !this.swordMode.is("None") && ItemUtil.isHoldingSword() && (!this.onlyKillAuraAutoBlock.getValue() || this.isKillAuraAutoBlocking());
    }

    public boolean isFoodActive() {
        return !this.foodMode.is("None") && ItemUtil.isEating();
    }

    public boolean isBowActive() {
        return !this.bowMode.is("None") && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.foodMode.is("Float") && ItemUtil.isEating()
                || this.bowMode.is("Float") && ItemUtil.isUsingBow();
    }

    private boolean isKillAuraAutoBlocking() {
        KillAura aura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (!aura.shouldAutoBlock() || !aura.isEnabled()) {
            return false;
        }
        return aura.isBlocking;
    }

    public boolean isAnyActive() {
        if (!this.swordMode.is("BlinkSemi") && !this.swordMode.is("Prediction") && !this.swordMode.is("PredictionSemi")) {
            return mc.thePlayer.isUsingItem() && (this.isSwordActive() || this.isFoodActive() || this.isBowActive());
        } else if (this.swordMode.is("BlinkSemi") && isSwordActive()) {
            return blinkDelay == 2;
        } else if (swordMode.is("Prediction") && isSwordActive()) {
            KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
            if (!slowOnRelease.getValue() || killAura.blockTick != 0) {
                return delay == 0;
            }
        } else if (this.swordMode.is("PredictionSemi") && isSwordActive()) {
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
            return this.swordMotion.getValue().intValue();
        } else if (ItemUtil.isEating()) {
            return this.foodMotion.getValue().intValue();
        } else {
            return ItemUtil.isUsingBow() ? this.bowMotion.getValue().intValue() : 100;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
        if (isSwordActive() && PlayerUtil.isUsingItem()) {
            if (this.swordMode.is("Prediction")) {
                if (event.getType() == EventType.PRE) {
                    delay--;
                    if (delay < 0) {
                        if (!this.slowOnRelease.getValue() || killAura.blockTick != 0) {
                            int handle = mc.thePlayer.inventory.currentItem;
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(Disabler.getAltSlot(handle)));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                        }
                        delay = swapDelay.getValue().intValue();
                    }
                }
            }
            if (this.swordMode.is("BlinkSemi")) {
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
                            Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(),mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                            Leader.blinkComponent.setBlinkState(true, BlinkType.AUTO_BLOCK);
                        }
                        blinkDelay++;
                    }
                }
            }
        }
        else
        {
            if (blinkDelay >= 0 && this.swordMode.is("BlinkSemi")) {
                Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
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
                Leader.floatComponent.setFloatState(true, FloatType.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            Leader.floatComponent.setFloatState(false, FloatType.NO_SLOW);
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
            if (this.isFloatMode() && !Leader.floatComponent.isPredicted() && mc.thePlayer.onGround) {
                event.setCancelled(true);
                mc.thePlayer.motionY = 0.42F;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.swordMode.getValue())};
    }
}
