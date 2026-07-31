package leader.client.module.modules.misc;

import leader.client.event.EventTarget;
import leader.client.event.types.Priority;
import leader.client.events.*;
import leader.mixin.accessor.IAccessorPlayerControllerMP;
import leader.client.module.Module;
import leader.client.util.server.PacketUtil;
import leader.client.util.timer.TimerUtil;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.potion.Potion;

public class AutoHeal extends Module {

    public final SliderValue health = new SliderValue("health", 35, 0, 100, Representation.INT, this);
    public final SliderValue delay = new SliderValue("delay", 4000, 0, 5000, Representation.INT, this);
    public final BoolValue regenCheck = new BoolValue("regen-check", false, this);
    public final BoolValue hurtCheck = new BoolValue("hurt-check", false, this);
    public final SliderValue hurtTime = new SliderValue("hurt-time", 20, 1, 100, hurtCheck::getValue, Representation.INT, this);
    private final TimerUtil timer = new TimerUtil();
    private boolean shouldHeal = false;
    private int prevSlot = -1;
    private int hurtTick = 0;

    private int findHealingItem() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.hasDisplayName()) {
                String name = stack.getDisplayName();
                if (stack.getItem() instanceof ItemSkull && name.contains("§6") && name.contains("Golden Head")) {
                    return i;
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.hasDisplayName()) {
                String name = stack.getDisplayName();
                if (stack.getItem() instanceof ItemSkull && name.matches("\\S+§c's Head")) {
                    return i;
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.hasDisplayName()) {
                String name = stack.getDisplayName();
                if (stack.getItem() instanceof ItemFood && name.contains("§6Cornucopia")) {
                    return i;
                }
                if (stack.getItem() instanceof ItemSoup
                        && (name.contains("§a") && name.contains("Tasty Soup") || name.contains("§a") && name.contains("Assist Soup"))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean hasRegenEffect() {
        return this.regenCheck.getValue() && mc.thePlayer.isPotionActive(Potion.regeneration);
    }

    public AutoHeal() {
        super("AutoHeal", false);
    }

    public boolean isSwitching() {
        return this.prevSlot != -1;
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            this.prevSlot = -1;
        } else {
            if (hurtCheck.getValue()){
                if (hurtTick > 0) hurtTick--;
                if (mc.thePlayer.hurtTime > 0) {
                    hurtTick = hurtTime.getValue().intValue();
                }
            } else {
                hurtTick = 1;
            }
            switch (event.getType()) {
                case PRE:
                    boolean percent = (float) Math.ceil(mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / mc.thePlayer.getMaxHealth()
                            <= (float) this.health.getValue() / 100.0F;
                    if (this.shouldHeal
                            && percent
                            && !this.hasRegenEffect()
                            && this.timer.hasTimeElapsed(this.delay.getValue().longValue())
                            && hurtTick > 0) {
                        int slot = this.findHealingItem();
                        if (slot != -1) {
                            this.prevSlot = mc.thePlayer.inventory.currentItem;
                            mc.thePlayer.inventory.currentItem = slot;
                            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                            this.timer.reset();
                        }
                    }
                    this.shouldHeal = percent;
                    break;
                case POST:
                    if (this.prevSlot != -1) {
                        mc.thePlayer.inventory.currentItem = this.prevSlot;
                        this.prevSlot = -1;
                    }
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && this.isSwitching()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && this.isSwitching()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled() && this.isSwitching()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && this.isSwitching()) {
            event.setCancelled(true);
        }
    }
}
