package leader.client.module.modules.player;

import leader.client.Leader;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.player.inventory.ContainerLocalMenu;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.UpdateEvent;
import leader.client.events.WindowClickEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;
import leader.client.util.DebugUtil;
import leader.client.util.player.ItemUtil;

import java.util.*;

public class ChestStealer extends Module {

    public final ListValue mode = new ListValue("Mode", new String[]{"Normal", "Instant"}, "Normal", this);

    public final SliderValue minDelay = (SliderValue) new SliderValue("Min Delay", 1, 0, 20, Representation.INT, this)
            .onChanged(() -> { if (this.minDelay.getValue() > this.maxDelay.getValue()) this.maxDelay.setValue(this.minDelay.getValue().floatValue()); });
    public final SliderValue maxDelay = (SliderValue) new SliderValue("Max Delay", 2, 0, 20, Representation.INT, this)
            .onChanged(() -> { if (this.minDelay.getValue() > this.maxDelay.getValue()) this.minDelay.setValue(this.maxDelay.getValue().floatValue()); });
    public final SliderValue openDelay = new SliderValue("Open Delay", 1, 0, 20, Representation.INT, this);
    public final BoolValue autoClose = new BoolValue("Auto Close", true, this);
    public final BoolValue nameCheck = new BoolValue("Name Check", true, this);
    public final BoolValue skipTrash = new BoolValue("Skip Trash", true, this);
    public final BoolValue keepProjectiles = new BoolValue("Skip Projectiles", true, this);

    private int clickDelay = 0;
    private int oDelay = 0;
    private boolean inChest = false;
    private boolean warnedFull = false;
    private boolean instantExecuted = false;

    public ChestStealer() {
        super("ChestStealer", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getValue()};
    }

    private boolean isProjectileStack(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return item instanceof ItemSnowball || item instanceof ItemEgg || item instanceof ItemFishingRod;
    }

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private void shiftClick(int windowId, int slotId) {
        mc.playerController.windowClick(windowId, slotId, 0, 1, mc.thePlayer);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {

            if (this.mode.is("Normal")) {
                if (this.clickDelay > 0) {
                    this.clickDelay--;
                }
                if (this.oDelay > 0) {
                    this.oDelay--;
                }
            }

            if (!(mc.currentScreen instanceof GuiChest)) {
                this.inChest = false;
                this.instantExecuted = false;
            } else {
                Container container = ((GuiChest) mc.currentScreen).inventorySlots;
                if (!(container instanceof ContainerChest)) {
                    this.inChest = false;
                    this.instantExecuted = false;
                } else {
                    if (!this.inChest) {
                        this.inChest = true;
                        this.warnedFull = false;
                        if (this.mode.is("Normal")) {
                            this.oDelay = this.openDelay.getValue().intValue() + 1;
                        }
                        this.instantExecuted = false;
                    }

                    if (!this.isEnabled() || !this.isValidGameMode()) {
                        return;
                    }

                    IInventory inventory = ((ContainerChest) container).getLowerChestInventory();

                    if (this.nameCheck.getValue()) {
                        if ((!(inventory instanceof ContainerLocalMenu)))return;
                    }

                    if (this.mode.is("Instant") && !this.instantExecuted) {
                        if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
                            if (!this.warnedFull) {
                                DebugUtil.sendFormatted(String.format("%s%s: &cYour inventory is full!&r", Leader.clientName, this.getName()));
                                this.warnedFull = true;
                            }
                            if (this.autoClose.getValue()) {
                                mc.thePlayer.closeScreen();
                            }
                        } else {
                            List<Integer> slotsToTake = new ArrayList<>();
                            Set<Integer> takenSlots = new HashSet<>();
                            int invSize = inventory.getSizeInventory();
                            if (this.skipTrash.getValue()) {
                                if (this.keepProjectiles.getValue()) {
                                    for (int i = 0; i < invSize; i++) {
                                        if (container.getSlot(i).getHasStack()) {
                                            if (this.isProjectileStack(container.getSlot(i).getStack())) {
                                                slotsToTake.add(i);
                                                takenSlots.add(i);
                                            }
                                        }
                                    }
                                }

                                int bestSword = -1;
                                double bestDamage = 0.0;
                                int[] bestArmorSlots = new int[]{-1, -1, -1, -1};
                                double[] bestArmorProtection = new double[]{0.0, 0.0, 0.0, 0.0};
                                int bestPickaxeSlot = -1;
                                float bestPickaxeEfficiency = 1.0F;
                                int bestShovelSlot = -1;
                                float bestShovelEfficiency = 1.0F;
                                int bestAxeSlot = -1;
                                float bestAxeEfficiency = 1.0F;

                                for (int i = 0; i < invSize; i++) {
                                    if (container.getSlot(i).getHasStack()) {
                                        ItemStack stack = container.getSlot(i).getStack();
                                        Item item = stack.getItem();
                                        if (item instanceof ItemSword) {
                                            double damage = ItemUtil.getAttackBonus(stack);
                                            if (bestSword == -1 || damage > bestDamage) {
                                                bestSword = i;
                                                bestDamage = damage;
                                            }
                                        } else if (item instanceof ItemArmor) {
                                            int armorType = ((ItemArmor) item).armorType;
                                            double protectionLevel = ItemUtil.getArmorProtection(stack);
                                            if (bestArmorSlots[armorType] == -1 || protectionLevel > bestArmorProtection[armorType]) {
                                                bestArmorSlots[armorType] = i;
                                                bestArmorProtection[armorType] = protectionLevel;
                                            }
                                        } else if (item instanceof ItemPickaxe) {
                                            float efficiency = ItemUtil.getToolEfficiency(stack);
                                            if (bestPickaxeSlot == -1 || efficiency > bestPickaxeEfficiency) {
                                                bestPickaxeSlot = i;
                                                bestPickaxeEfficiency = efficiency;
                                            }
                                        } else if (item instanceof ItemSpade) {
                                            float efficiency = ItemUtil.getToolEfficiency(stack);
                                            if (bestShovelSlot == -1 || efficiency > bestShovelEfficiency) {
                                                bestShovelSlot = i;
                                                bestShovelEfficiency = efficiency;
                                            }
                                        } else if (item instanceof ItemAxe) {
                                            float efficiency = ItemUtil.getToolEfficiency(stack);
                                            if (bestAxeSlot == -1 || efficiency > bestAxeEfficiency) {
                                                bestAxeSlot = i;
                                                bestAxeEfficiency = efficiency;
                                            }
                                        }
                                    }
                                }
                                int swordInvSlot = ItemUtil.findSwordInInventorySlot(0, true);
                                double currentDmg = swordInvSlot != -1 ? ItemUtil.getAttackBonus(mc.thePlayer.inventory.getStackInSlot(swordInvSlot)) : 0.0;
                                if (bestDamage > currentDmg && !takenSlots.contains(bestSword)) {
                                    slotsToTake.add(bestSword);
                                    takenSlots.add(bestSword);
                                }

                                for (int armorType = 0; armorType < 4; armorType++) {
                                    int currentArmorSlot = ItemUtil.findArmorInventorySlot(armorType, true);
                                    double currentProt = currentArmorSlot != -1 ? ItemUtil.getArmorProtection(mc.thePlayer.inventory.getStackInSlot(currentArmorSlot)) : 0.0;
                                    int bestSlot = bestArmorSlots[armorType];
                                    if (bestArmorProtection[armorType] > currentProt && !takenSlots.contains(bestSlot)) {
                                        slotsToTake.add(bestSlot);
                                        takenSlots.add(bestSlot);
                                    }
                                }

                                int currentPick = ItemUtil.findInventorySlot("pickaxe", 0, true);
                                float currentPickEff = currentPick != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(currentPick)) : 1.0F;
                                if (bestPickaxeEfficiency > currentPickEff && !takenSlots.contains(bestPickaxeSlot)) {
                                    slotsToTake.add(bestPickaxeSlot);
                                    takenSlots.add(bestPickaxeSlot);
                                }

                                int currentShovel = ItemUtil.findInventorySlot("shovel", 0, true);
                                float currentShovelEff = currentShovel != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(currentShovel)) : 1.0F;
                                if (bestShovelEfficiency > currentShovelEff && !takenSlots.contains(bestShovelSlot)) {
                                    slotsToTake.add(bestShovelSlot);
                                    takenSlots.add(bestShovelSlot);
                                }

                                int currentAxe = ItemUtil.findInventorySlot("axe", 0, true);
                                float currentAxeEff = currentAxe != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(currentAxe)) : 1.0F;
                                if (bestAxeEfficiency > currentAxeEff && !takenSlots.contains(bestAxeSlot)) {
                                    slotsToTake.add(bestAxeSlot);
                                    takenSlots.add(bestAxeSlot);
                                }

                                for (int i = 0; i < invSize; i++) {
                                    if (takenSlots.contains(i)) continue;
                                    if (container.getSlot(i).getHasStack()) {
                                        ItemStack stack = container.getSlot(i).getStack();
                                        if (this.keepProjectiles.getValue() && this.isProjectileStack(stack)) continue;
                                        if (ItemUtil.isNotSpecialItem(stack)) continue;
                                        slotsToTake.add(i);
                                        takenSlots.add(i);
                                    }
                                }
                            } else {
                                for (int i = 0; i < invSize; i++) {
                                    if (container.getSlot(i).getHasStack()) {
                                        ItemStack stack = container.getSlot(i).getStack();
                                        if (this.keepProjectiles.getValue() && this.isProjectileStack(stack)) continue;
                                        slotsToTake.add(i);
                                    }
                                }
                            }
                            boolean inventoryFull = false;
                            for (int slot : slotsToTake) {
                                if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
                                    inventoryFull = true;
                                    break;
                                }
                                this.shiftClick(container.windowId, slot);
                            }

                            this.instantExecuted = true;

                            if (inventoryFull) {
                                if (!this.warnedFull) {
                                    DebugUtil.sendFormatted(String.format("%s%s: &cYour inventory is full!&r", Leader.clientName, this.getName()));
                                    this.warnedFull = true;
                                }
                                if (this.autoClose.getValue()) {
                                    mc.thePlayer.closeScreen();
                                }
                            } else {
                                boolean allEmpty = true;
                                for (int i = 0; i < invSize; i++) {
                                    if (container.getSlot(i).getHasStack()) {
                                        ItemStack stack = container.getSlot(i).getStack();
                                        if (this.skipTrash.getValue() && ItemUtil.isNotSpecialItem(stack)) continue;
                                        if (this.keepProjectiles.getValue() && this.isProjectileStack(stack)) continue;
                                        allEmpty = false;
                                        break;
                                    }
                                }
                                if (this.autoClose.getValue() && allEmpty) {
                                    mc.thePlayer.closeScreen();
                                }
                            }
                        }
                    }
                    else if (this.mode.is("Normal") && this.oDelay <= 0 && this.clickDelay <= 0) {
                        if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
                            if (!this.warnedFull) {
                                DebugUtil.sendFormatted(String.format("%s%s: &cYour inventory is full!&r", Leader.clientName, this.getName()));
                                this.warnedFull = true;
                            }
                            if (this.autoClose.getValue()) {
                                mc.thePlayer.closeScreen();
                            }
                        } else {
                            if (this.skipTrash.getValue()) {
                                if (this.keepProjectiles.getValue()) {
                                    for (int i = 0; i < inventory.getSizeInventory(); i++) {
                                        if (container.getSlot(i).getHasStack()) {
                                            ItemStack stack = container.getSlot(i).getStack();
                                            if (this.isProjectileStack(stack)) {
                                                this.shiftClick(container.windowId, i);
                                                return;
                                            }
                                        }
                                    }
                                }

                                int bestSword = -1;
                                double bestDamage = 0.0;
                                int[] bestArmorSlots = new int[]{-1, -1, -1, -1};
                                double[] bestArmorProtection = new double[]{0.0, 0.0, 0.0, 0.0};
                                int bestPickaxeSlot = -1;
                                float bestPickaxeEfficiency = 1.0F;
                                int bestShovelSlot = -1;
                                float bestShovelEfficiency = 1.0F;
                                int bestAxeSlot = -1;
                                float bestAxeEfficiency = 1.0F;

                                for (int i = 0; i < inventory.getSizeInventory(); i++) {
                                    if (container.getSlot(i).getHasStack()) {
                                        ItemStack stack = container.getSlot(i).getStack();
                                        Item item = stack.getItem();
                                        if (item instanceof ItemSword) {
                                            double damage = ItemUtil.getAttackBonus(stack);
                                            if (bestSword == -1 || damage > bestDamage) {
                                                bestSword = i;
                                                bestDamage = damage;
                                            }
                                        } else if (item instanceof ItemArmor) {
                                            int armorType = ((ItemArmor) item).armorType;
                                            double protectionLevel = ItemUtil.getArmorProtection(stack);
                                            if (bestArmorSlots[armorType] == -1 || protectionLevel > bestArmorProtection[armorType]) {
                                                bestArmorSlots[armorType] = i;
                                                bestArmorProtection[armorType] = protectionLevel;
                                            }
                                        } else if (item instanceof ItemPickaxe) {
                                            float efficiency = ItemUtil.getToolEfficiency(stack);
                                            if (bestPickaxeSlot == -1 || efficiency > bestPickaxeEfficiency) {
                                                bestPickaxeSlot = i;
                                                bestPickaxeEfficiency = efficiency;
                                            }
                                        } else if (item instanceof ItemSpade) {
                                            float efficiency = ItemUtil.getToolEfficiency(stack);
                                            if (bestShovelSlot == -1 || efficiency > bestShovelEfficiency) {
                                                bestShovelSlot = i;
                                                bestShovelEfficiency = efficiency;
                                            }
                                        } else if (item instanceof ItemAxe) {
                                            float efficiency = ItemUtil.getToolEfficiency(stack);
                                            if (bestAxeSlot == -1 || efficiency > bestAxeEfficiency) {
                                                bestAxeSlot = i;
                                                bestAxeEfficiency = efficiency;
                                            }
                                        }
                                    }
                                }

                                int swordInInventorySlot = ItemUtil.findSwordInInventorySlot(0, true);
                                double damage = swordInInventorySlot != -1 ? ItemUtil.getAttackBonus(mc.thePlayer.inventory.getStackInSlot(swordInInventorySlot)) : 0.0;
                                if (bestDamage > damage) {
                                    this.shiftClick(container.windowId, bestSword);
                                    return;
                                }

                                for (int i = 0; i < 4; i++) {
                                    int slot = ItemUtil.findArmorInventorySlot(i, true);
                                    double protectionLevel = slot != -1
                                            ? ItemUtil.getArmorProtection(mc.thePlayer.inventory.getStackInSlot(slot))
                                            : 0.0;
                                    if (bestArmorProtection[i] > protectionLevel) {
                                        this.shiftClick(container.windowId, bestArmorSlots[i]);
                                        return;
                                    }
                                }

                                int pickaxeSlot = ItemUtil.findInventorySlot("pickaxe", 0, true);
                                float pickaxeEfficiency = pickaxeSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(pickaxeSlot)) : 1.0F;
                                if (bestPickaxeEfficiency > pickaxeEfficiency) {
                                    this.shiftClick(container.windowId, bestPickaxeSlot);
                                    return;
                                }

                                int shovelSlot = ItemUtil.findInventorySlot("shovel", 0, true);
                                float shovelEfficiency = shovelSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(shovelSlot)) : 1.0F;
                                if (bestShovelEfficiency > shovelEfficiency) {
                                    this.shiftClick(container.windowId, bestShovelSlot);
                                    return;
                                }

                                int axeSlot = ItemUtil.findInventorySlot("axe", 0, true);
                                float efficiency = axeSlot != -1 ? ItemUtil.getToolEfficiency(mc.thePlayer.inventory.getStackInSlot(axeSlot)) : 1.0F;
                                if (bestAxeEfficiency > efficiency) {
                                    this.shiftClick(container.windowId, bestAxeSlot);
                                    return;
                                }
                            }

                            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                                if (container.getSlot(i).getHasStack()) {
                                    ItemStack stack = container.getSlot(i).getStack();
                                    if (this.keepProjectiles.getValue() && this.isProjectileStack(stack)) {
                                        continue;
                                    }
                                    if (!this.skipTrash.getValue() || !ItemUtil.isNotSpecialItem(stack)) {
                                        this.shiftClick(container.windowId, i);
                                        return;
                                    }
                                }
                            }

                            if (this.autoClose.getValue()) {
                                mc.thePlayer.closeScreen();
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onWindowClick(WindowClickEvent event) {
        if (this.mode.is("Normal")) {
            this.clickDelay = RandomUtils.nextInt(this.minDelay.getValue().intValue() + 1, this.maxDelay.getValue().intValue() + 2);
        }
        if (this.mode.is("Instant")) {
            this.clickDelay = 0;
        }
    }
}