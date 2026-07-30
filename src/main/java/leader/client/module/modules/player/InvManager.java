package leader.client.module.modules.player;

import leader.client.Leader;
import leader.client.module.modules.misc.Disabler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.*;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.UpdateEvent;
import leader.client.events.WindowClickEvent;
import leader.client.module.Module;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.IntProperty;
import leader.client.property.properties.ModeProperty;
import leader.client.util.player.ItemUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class InvManager extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty minDelay = new IntProperty("Min Delay", 0, 0, 20);
    public final IntProperty maxDelay = new IntProperty("Max Delay", 0, 0, 20);
    public final IntProperty openDelay = new IntProperty("Open Delay", 0, 0, 20);
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Normal", "Instant"});
    public final BooleanProperty autoClose = new BooleanProperty("Auto Close", false);
    public final BooleanProperty autoArmor = new BooleanProperty("Auto Armor", true);
    public final BooleanProperty dropTrash = new BooleanProperty("Drop Trash", true);
    public final IntProperty swordSlot = new IntProperty("Sword Slot", 1, 0, 9);
    public final IntProperty pickaxeSlot = new IntProperty("Pickaxe Slot", 8, 0, 9);
    public final IntProperty shovelSlot = new IntProperty("Shovel Slot", 7, 0, 9);
    public final IntProperty axeSlot = new IntProperty("Axe Slot", 9, 0, 9);
    public final IntProperty blocksSlot = new IntProperty("Blocks Slot", 2, 0, 9);
    public final IntProperty blocks = new IntProperty("Blocks", 128, 64, 2304);
    public final IntProperty throwsSlot = new IntProperty("Throws Slot", 4, 0, 9);
    public final IntProperty throwsAmount = new IntProperty("Throws Amount", 64, 16, 320);
    public final IntProperty gappleSlot = new IntProperty("Gapple Slot", 3, 0, 9);
    public final BooleanProperty keepOre = new BooleanProperty("Keep Ore", true);
    public final BooleanProperty keepWaterBucket = new BooleanProperty("Keep Water Bucket", true);
    public final BooleanProperty keepBowAndArrows = new BooleanProperty("Keep Bow And Arrows", true);
    public final IntProperty bowSlot = new IntProperty("Bow Slot", 5, 0, 9, keepBowAndArrows::getValue);

    private int actionDelay = 0;
    private int oDelay = 0;
    private boolean inventoryOpen = false;

    public InvManager() {
        super("InvManager", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private int convertSlotIndex(int slot) {
        if (slot >= 36) {
            return 8 - (slot - 36);
        } else {
            return slot <= 8 ? slot + 36 : slot;
        }
    }

    private void clickSlot(int integer1, int integer2, int integer3, int integer4) {
        mc.playerController.windowClick(integer1, integer2, integer3, integer4, mc.thePlayer);
    }

    private int getStackSize(int slot) {
        if (slot == -1) {
            return 0;
        } else {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack != null ? stack.stackSize : 0;
        }
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() instanceof ItemSnowball || stack.getItem() instanceof ItemEgg;
    }

    private boolean isGapple(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() instanceof ItemAppleGold;
    }

    private boolean isOre(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item instanceof net.minecraft.item.ItemBlock) {
            net.minecraft.block.Block block = ((net.minecraft.item.ItemBlock) item).getBlock();
            if (block instanceof net.minecraft.block.BlockOre) {
                return true;
            }
        }
        return item == net.minecraft.init.Items.diamond
                || item == net.minecraft.init.Items.emerald
                || item == net.minecraft.init.Items.iron_ingot
                || item == net.minecraft.init.Items.gold_ingot
                || item == net.minecraft.init.Items.coal
                || item == net.minecraft.init.Items.quartz
                || item == net.minecraft.init.Items.redstone;
    }

    private boolean isWaterBucket(ItemStack stack) {
        return stack != null && stack.getItem() == net.minecraft.init.Items.water_bucket;
    }

    private boolean isBow(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBow;
    }

    private int findThrowableSlot(int preferredSlot, boolean hotbarOnly) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isThrowable(stack)) {
                return preferredSlot;
            }
        }
        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isThrowable(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findGappleSlot(int preferredSlot, boolean hotbarOnly) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isGapple(stack)) {
                return preferredSlot;
            }
        }
        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isGapple(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findBowSlot(int preferredSlot, boolean hotbarOnly) {
        if (!keepBowAndArrows.getValue()) return -1;
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isBow(stack)) {
                return preferredSlot;
            }
        }
        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isBow(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int getTotalThrowsCount() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isThrowable(stack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private boolean isInventorySorted() {
        if (!isValidGameMode()) return true;

        int preferredSwordHotbarSlot = this.swordSlot.getValue() - 1;
        int equippedSwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, true);
        int inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, false);

        // Disabler C09: compute second sword for drop protection in isSorted check
        int secondSwordSlot = -1;
        int prefSecondSwordSlot = -1;
        Disabler disabler = (Disabler) Leader.moduleManager.getModule(Disabler.class);
        if (disabler != null && disabler.isEnabled() && disabler.c09.getValue()) {
            int mainSword = equippedSwordSlot != -1 ? equippedSwordSlot : inventorySwordSlot;
            if (mainSword != -1) {
                secondSwordSlot = Disabler.findSecondSwordSlot(mainSword);
                prefSecondSwordSlot = disabler.secondSwordSlot.getValue() - 1;
            }
        }

        int preferredPickaxeHotbarSlot = this.pickaxeSlot.getValue() - 1;
        int equippedPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, true);
        int inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, false);

        int preferredShovelHotbarSlot = this.shovelSlot.getValue() - 1;
        int equippedShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, true);
        int inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, false);

        int preferredAxeHotbarSlot = this.axeSlot.getValue() - 1;
        int equippedAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, true);
        int inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, false);

        int preferredBlocksHotbarSlot = this.blocksSlot.getValue() - 1;
        int inventoryBlocksSlot = ItemUtil.findInventorySlot(preferredBlocksHotbarSlot);

        int preferredThrowsHotbarSlot = this.throwsSlot.getValue() - 1;
        int equippedThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, true);
        int inventoryThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, false);

        int preferredGappleHotbarSlot = this.gappleSlot.getValue() - 1;
        int equippedGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, true);
        int inventoryGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, false);

        int preferredBowHotbarSlot = this.bowSlot.getValue() - 1;
        int equippedBowSlot = -1;
        int inventoryBowSlot = -1;
        if (keepBowAndArrows.getValue()) {
            int bestBow = ItemUtil.getBestBowSlot();
            if (bestBow != -1) {
                if (bestBow <= 8) equippedBowSlot = bestBow;
                else inventoryBowSlot = bestBow;
            }
        }
        if (this.autoArmor.getValue()) {
            for (int i = 0; i < 4; i++) {
                int equippedSlot = ItemUtil.findArmorInventorySlot(i, true);
                int inventorySlot = ItemUtil.findArmorInventorySlot(i, false);
                int playerArmorSlot = 39 - i;
                if (equippedSlot != -1 || inventorySlot != -1) {
                    if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
                        return false;
                    }
                }
            }
        }

        if (preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8 && (equippedSwordSlot != -1 || inventorySwordSlot != -1)) {
            if (equippedSwordSlot != preferredSwordHotbarSlot && inventorySwordSlot != preferredSwordHotbarSlot)
                return false;
        }
        if (preferredPickaxeHotbarSlot >= 0 && preferredPickaxeHotbarSlot <= 8 && (equippedPickaxeSlot != -1 || inventoryPickaxeSlot != -1)) {
            if (equippedPickaxeSlot != preferredPickaxeHotbarSlot && inventoryPickaxeSlot != preferredPickaxeHotbarSlot)
                return false;
        }
        if (preferredShovelHotbarSlot >= 0 && preferredShovelHotbarSlot <= 8 && (equippedShovelSlot != -1 || inventoryShovelSlot != -1)) {
            if (equippedShovelSlot != preferredShovelHotbarSlot && inventoryShovelSlot != preferredShovelHotbarSlot)
                return false;
        }
        if (preferredAxeHotbarSlot >= 0 && preferredAxeHotbarSlot <= 8 && (equippedAxeSlot != -1 || inventoryAxeSlot != -1)) {
            if (equippedAxeSlot != preferredAxeHotbarSlot && inventoryAxeSlot != preferredAxeHotbarSlot)
                return false;
        }
        if (preferredBlocksHotbarSlot >= 0 && preferredBlocksHotbarSlot <= 8 && inventoryBlocksSlot != -1) {
            if (inventoryBlocksSlot != preferredBlocksHotbarSlot) return false;
        }
        if (preferredThrowsHotbarSlot >= 0 && preferredThrowsHotbarSlot <= 8 && (equippedThrowsSlot != -1 || inventoryThrowsSlot != -1)) {
            if (equippedThrowsSlot != preferredThrowsHotbarSlot && inventoryThrowsSlot != preferredThrowsHotbarSlot)
                return false;
        }
        if (preferredGappleHotbarSlot >= 0 && preferredGappleHotbarSlot <= 8 && (equippedGappleSlot != -1 || inventoryGappleSlot != -1)) {
            if (equippedGappleSlot != preferredGappleHotbarSlot && inventoryGappleSlot != preferredGappleHotbarSlot)
                return false;
        }
        if (keepBowAndArrows.getValue() && preferredBowHotbarSlot >= 0 && preferredBowHotbarSlot <= 8 && (equippedBowSlot != -1 || inventoryBowSlot != -1)) {
            if (equippedBowSlot != preferredBowHotbarSlot && inventoryBowSlot != preferredBowHotbarSlot)
                return false;
        }

        if (this.dropTrash.getValue()) {
            ArrayList<Integer> equippedArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
            ArrayList<Integer> inventoryArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
            for (int i = 0; i < 4; i++) {
                equippedArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, true));
                inventoryArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, false));
            }

            int currentBlockCount = this.getStackSize(inventoryBlocksSlot);
            int totalThrowsCount = this.getTotalThrowsCount();

            if (totalThrowsCount > this.throwsAmount.getValue()) {
                for (int i = 0; i < 36; i++) {
                    if (!equippedArmorSlots.contains(i) && !inventoryArmorSlots.contains(i)
                            && equippedSwordSlot != i && inventorySwordSlot != i && secondSwordSlot != i && prefSecondSwordSlot != i
                            && equippedPickaxeSlot != i && inventoryPickaxeSlot != i
                            && equippedShovelSlot != i && inventoryShovelSlot != i
                            && equippedAxeSlot != i && inventoryAxeSlot != i
                            && inventoryBlocksSlot != i && equippedThrowsSlot != i
                            && inventoryThrowsSlot != i && equippedGappleSlot != i
                            && inventoryGappleSlot != i
                            && equippedBowSlot != i && inventoryBowSlot != i) {
                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                        if (this.isThrowable(stack)) return false;
                    }
                }
            }

            for (int i = 0; i < 36; i++) {
                if (!equippedArmorSlots.contains(i) && !inventoryArmorSlots.contains(i)
                        && equippedSwordSlot != i && inventorySwordSlot != i && secondSwordSlot != i && prefSecondSwordSlot != i
                        && equippedPickaxeSlot != i && inventoryPickaxeSlot != i
                        && equippedShovelSlot != i && inventoryShovelSlot != i
                        && equippedAxeSlot != i && inventoryAxeSlot != i
                        && inventoryBlocksSlot != i && equippedThrowsSlot != i
                        && inventoryThrowsSlot != i && equippedGappleSlot != i
                        && inventoryGappleSlot != i
                        && equippedBowSlot != i && inventoryBowSlot != i) {
                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                    if (stack != null) {
                        boolean isBlock = ItemUtil.isBlock(stack);
                        boolean isThrowable = this.isThrowable(stack);
                        boolean isGapple = this.isGapple(stack);
                        boolean isOre = this.isOre(stack);
                        boolean isProtectedWater = keepWaterBucket.getValue() && isWaterBucket(stack);
                        boolean isProtectedBowArrow = false;
                        if (keepBowAndArrows.getValue()) {
                            if (ItemUtil.isArrow(stack)) {
                                isProtectedBowArrow = true;
                            } else if (isBow(stack)) {
                                int bestBow = ItemUtil.getBestBowSlot();
                                isProtectedBowArrow = (bestBow == -1 || i == bestBow);
                            }
                        }

                        if (!keepOre.getValue() && isOre) return false;
                        else if (!isThrowable && !isOre && !isGapple && !isProtectedWater && !isProtectedBowArrow
                                && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= this.blocks.getValue())))
                            return false;
                        if (isBlock) currentBlockCount += stack.stackSize;
                    }
                }
            }
        }
        return true;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.actionDelay > 0) {
                this.actionDelay--;
            }
            if (this.oDelay > 0) {
                this.oDelay--;
            }

            boolean isInventoryOpen = (mc.currentScreen instanceof GuiInventory);
            if (!isInventoryOpen) {
                this.inventoryOpen = false;
            } else if ((mc.currentScreen instanceof GuiInventory) && !(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer)) {
                this.inventoryOpen = false;
            } else {
                if (!this.inventoryOpen) {
                    this.inventoryOpen = true;
                    this.oDelay = this.openDelay.getValue() + 1;
                }
                if (this.oDelay <= 0 && (this.mode.getValue() == 1 || this.actionDelay <= 0)) {
                    if (this.isEnabled() && this.isValidGameMode()) {
                        ArrayList<Integer> equippedArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                        ArrayList<Integer> inventoryArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                        for (int i = 0; i < 4; i++) {
                            equippedArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, true));
                            inventoryArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, false));
                        }
                        int preferredSwordHotbarSlot = this.swordSlot.getValue() - 1;
                        int equippedSwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, true);
                        int inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, false);
                        int secondSwordSlot = -1;
                        int preferredPickaxeHotbarSlot = this.pickaxeSlot.getValue() - 1;
                        int equippedPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, true);
                        int inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, false);
                        int preferredShovelHotbarSlot = this.shovelSlot.getValue() - 1;
                        int equippedShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, true);
                        int inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, false);
                        int preferredAxeHotbarSlot = this.axeSlot.getValue() - 1;
                        int equippedAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, true);
                        int inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, false);
                        int preferredBlocksHotbarSlot = this.blocksSlot.getValue() - 1;
                        int inventoryBlocksSlot = ItemUtil.findInventorySlot(preferredBlocksHotbarSlot);
                        int preferredThrowsHotbarSlot = this.throwsSlot.getValue() - 1;
                        int equippedThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, true);
                        int inventoryThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, false);
                        int preferredGappleHotbarSlot = this.gappleSlot.getValue() - 1;
                        int equippedGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, true);
                        int inventoryGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, false);
                        int preferredBowHotbarSlot = this.bowSlot.getValue() - 1;
                        int equippedBowSlot = -1;
                        int inventoryBowSlot = -1;
                        if (keepBowAndArrows.getValue()) {
                            int bestBow = ItemUtil.getBestBowSlot();
                            if (bestBow != -1) {
                                if (bestBow <= 8) equippedBowSlot = bestBow;
                                else inventoryBowSlot = bestBow;
                            }
                        }

                        // Disabler C09: compute second sword and second sword's preferred slot
                        Disabler disabler = (Disabler) Leader.moduleManager.getModule(Disabler.class);
                        int prefSecondSwordSlot = -1;
                        if (disabler != null && disabler.isEnabled() && disabler.c09.getValue()) {
                            int mainSword = equippedSwordSlot != -1 ? equippedSwordSlot : inventorySwordSlot;
                            if (mainSword != -1) {
                                secondSwordSlot = Disabler.findSecondSwordSlot(mainSword);
                                prefSecondSwordSlot = disabler.secondSwordSlot.getValue() - 1;
                            }
                        }

                        if (this.mode.getValue() == 0) {
                            if (this.autoArmor.getValue()) {
                            for (int i = 0; i < 4; i++) {
                                int equippedSlot = equippedArmorSlots.get(i);
                                int inventorySlot = inventoryArmorSlots.get(i);
                                if (equippedSlot != -1 || inventorySlot != -1) {
                                    int playerArmorSlot = 39 - i;
                                    if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
                                        if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
                                            if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 0, 1);
                                            } else {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 1, 4);
                                            }
                                        } else {
                                            int armorToEquipSlot = equippedSlot != -1 ? equippedSlot : inventorySlot;
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(armorToEquipSlot), 0, 1);
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                            LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
                            if (preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8 && (equippedSwordSlot != -1 || inventorySwordSlot != -1)) {
                                usedHotbarSlots.add(preferredSwordHotbarSlot);
                                if (equippedSwordSlot != preferredSwordHotbarSlot && inventorySwordSlot != preferredSwordHotbarSlot) {
                                    int slot = equippedSwordSlot != -1 ? equippedSwordSlot : inventorySwordSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredSwordHotbarSlot, 2);
                                    return;
                                }
                            }
                            // Disabler C09: sort second sword AFTER first (best) sword is in place
                            if (secondSwordSlot != -1 && prefSecondSwordSlot >= 0 && prefSecondSwordSlot <= 8
                                    && prefSecondSwordSlot != preferredSwordHotbarSlot) {
                                usedHotbarSlots.add(prefSecondSwordSlot);
                                if (secondSwordSlot != prefSecondSwordSlot) {
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(secondSwordSlot), prefSecondSwordSlot, 2);
                                    return;
                                }
                            }
                            if (preferredPickaxeHotbarSlot >= 0 && preferredPickaxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredPickaxeHotbarSlot) && (equippedPickaxeSlot != -1 || inventoryPickaxeSlot != -1)) {
                                usedHotbarSlots.add(preferredPickaxeHotbarSlot);
                                if (equippedPickaxeSlot != preferredPickaxeHotbarSlot && inventoryPickaxeSlot != preferredPickaxeHotbarSlot) {
                                    int slot = equippedPickaxeSlot != -1 ? equippedPickaxeSlot : inventoryPickaxeSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredPickaxeHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredShovelHotbarSlot >= 0 && preferredShovelHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredShovelHotbarSlot) && (equippedShovelSlot != -1 || inventoryShovelSlot != -1)) {
                                usedHotbarSlots.add(preferredShovelHotbarSlot);
                                if (equippedShovelSlot != preferredShovelHotbarSlot && inventoryShovelSlot != preferredShovelHotbarSlot) {
                                    int slot = equippedShovelSlot != -1 ? equippedShovelSlot : inventoryShovelSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredShovelHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredAxeHotbarSlot >= 0 && preferredAxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredAxeHotbarSlot) && (equippedAxeSlot != -1 || inventoryAxeSlot != -1)) {
                                usedHotbarSlots.add(preferredAxeHotbarSlot);
                                if (equippedAxeSlot != preferredAxeHotbarSlot && inventoryAxeSlot != preferredAxeHotbarSlot) {
                                    int slot = equippedAxeSlot != -1 ? equippedAxeSlot : inventoryAxeSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredAxeHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredBlocksHotbarSlot >= 0 && preferredBlocksHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBlocksHotbarSlot) && inventoryBlocksSlot != -1) {
                                usedHotbarSlots.add(preferredBlocksHotbarSlot);
                                if (inventoryBlocksSlot != preferredBlocksHotbarSlot) {
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryBlocksSlot), preferredBlocksHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredThrowsHotbarSlot >= 0 && preferredThrowsHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredThrowsHotbarSlot) && (equippedThrowsSlot != -1 || inventoryThrowsSlot != -1)) {
                                usedHotbarSlots.add(preferredThrowsHotbarSlot);
                                if (equippedThrowsSlot != preferredThrowsHotbarSlot && inventoryThrowsSlot != preferredThrowsHotbarSlot) {
                                    int slot = equippedThrowsSlot != -1 ? equippedThrowsSlot : inventoryThrowsSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredThrowsHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredGappleHotbarSlot >= 0 && preferredGappleHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredGappleHotbarSlot) && (equippedGappleSlot != -1 || inventoryGappleSlot != -1)) {
                                usedHotbarSlots.add(preferredGappleHotbarSlot);
                                if (equippedGappleSlot != preferredGappleHotbarSlot && inventoryGappleSlot != preferredGappleHotbarSlot) {
                                    int slot = equippedGappleSlot != -1 ? equippedGappleSlot : inventoryGappleSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredGappleHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (keepBowAndArrows.getValue() && preferredBowHotbarSlot >= 0 && preferredBowHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBowHotbarSlot) && (equippedBowSlot != -1 || inventoryBowSlot != -1)) {
                                usedHotbarSlots.add(preferredBowHotbarSlot);
                                if (equippedBowSlot != preferredBowHotbarSlot && inventoryBowSlot != preferredBowHotbarSlot) {
                                    int slot = equippedBowSlot != -1 ? equippedBowSlot : inventoryBowSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredBowHotbarSlot, 2);
                                    return;
                                }
                            }

                            if (this.dropTrash.getValue()) {
                                int currentBlockCount = this.getStackSize(inventoryBlocksSlot);
                                int totalThrowsCount = this.getTotalThrowsCount();
                                if (totalThrowsCount > this.throwsAmount.getValue()) {
                                    for (int i = 35; i >= 0; i--) {
                                        if (!equippedArmorSlots.contains(i) && !inventoryArmorSlots.contains(i)
                                                && equippedSwordSlot != i && inventorySwordSlot != i && secondSwordSlot != i && prefSecondSwordSlot != i && prefSecondSwordSlot != i
                                                && equippedPickaxeSlot != i && inventoryPickaxeSlot != i
                                                && equippedShovelSlot != i && inventoryShovelSlot != i
                                                && equippedAxeSlot != i && inventoryAxeSlot != i
                                                && inventoryBlocksSlot != i && equippedThrowsSlot != i
                                                && inventoryThrowsSlot != i && equippedGappleSlot != i
                                                && inventoryGappleSlot != i
                                                && equippedBowSlot != i && inventoryBowSlot != i) {
                                            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                            if (this.isThrowable(stack)) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                                return;
                                            }
                                        }
                                    }
                                }
                                for (int i = 0; i < 36; i++) {
                                    if (!equippedArmorSlots.contains(i) && !inventoryArmorSlots.contains(i)
                                            && equippedSwordSlot != i && inventorySwordSlot != i && secondSwordSlot != i && prefSecondSwordSlot != i && prefSecondSwordSlot != i
                                            && equippedPickaxeSlot != i && inventoryPickaxeSlot != i
                                            && equippedShovelSlot != i && inventoryShovelSlot != i
                                            && equippedAxeSlot != i && inventoryAxeSlot != i
                                            && inventoryBlocksSlot != i && equippedThrowsSlot != i
                                            && inventoryThrowsSlot != i && equippedGappleSlot != i
                                            && inventoryGappleSlot != i
                                            && equippedBowSlot != i && inventoryBowSlot != i) {
                                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                        if (stack != null) {
                                            boolean isBlock = ItemUtil.isBlock(stack);
                                            boolean isThrowable = this.isThrowable(stack);
                                            boolean isGapple = this.isGapple(stack);
                                            boolean isOre = this.isOre(stack);
                                            boolean isProtectedWater = keepWaterBucket.getValue() && isWaterBucket(stack);
                                            boolean isProtectedBowArrow = false;
                                            if (keepBowAndArrows.getValue()) {
                                                if (ItemUtil.isArrow(stack)) {
                                                    isProtectedBowArrow = true;
                                                } else if (isBow(stack)) {
                                                    int bestBow = ItemUtil.getBestBowSlot();
                                                    isProtectedBowArrow = (bestBow == -1 || i == bestBow);
                                                }
                                            }

                                            if (!keepOre.getValue() && isOre) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                                return;
                                            } else if (!isThrowable && !isOre && !isGapple && !isProtectedWater && !isProtectedBowArrow
                                                    && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= this.blocks.getValue()))) {
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                                return;
                                            }
                                            if (isBlock) currentBlockCount += stack.stackSize;
                                        }
                                    }
                                }
                            }
                        } else if (this.mode.getValue() == 1) {
                            if (this.autoArmor.getValue()) {
                                for (int i = 0; i < 4; i++) {
                                    int equippedSlot = ItemUtil.findArmorInventorySlot(i, true);
                                    int inventorySlot = ItemUtil.findArmorInventorySlot(i, false);
                                    if (equippedSlot != -1 || inventorySlot != -1) {
                                        int playerArmorSlot = 39 - i;
                                        if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
                                            if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
                                                if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                                                    clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(playerArmorSlot), 0, 1);
                                                } else {
                                                    clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(playerArmorSlot), 1, 4);
                                                }
                                            } else {
                                                int armorToEquipSlot = equippedSlot != -1 ? equippedSlot : inventorySlot;
                                                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(armorToEquipSlot), 0, 1);
                                            }
                                        }
                                    }
                                }
                            }
                            LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
                            int prefSword = swordSlot.getValue() - 1;
                            if (prefSword >= 0 && prefSword <= 8) {
                                int eqSword = ItemUtil.findSwordInInventorySlot(prefSword, true);
                                int invSword = ItemUtil.findSwordInInventorySlot(prefSword, false);
                                if (eqSword != -1 || invSword != -1) {
                                    usedHotbarSlots.add(prefSword);
                                    if (eqSword != prefSword && invSword != prefSword) {
                                        int slot = eqSword != -1 ? eqSword : invSword;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefSword, 2);
                                    }
                                }
                                // Disabler C09: sort second sword in Instant mode
                                if (secondSwordSlot != -1 && prefSecondSwordSlot >= 0 && prefSecondSwordSlot <= 8
                                        && prefSecondSwordSlot != prefSword) {
                                    usedHotbarSlots.add(prefSecondSwordSlot);
                                    if (secondSwordSlot != prefSecondSwordSlot) {
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(secondSwordSlot), prefSecondSwordSlot, 2);
                                    }
                                }
                            }
                            int prefPick = pickaxeSlot.getValue() - 1;
                            if (prefPick >= 0 && prefPick <= 8 && !usedHotbarSlots.contains(prefPick)) {
                                int eqPick = ItemUtil.findInventorySlot("pickaxe", prefPick, true);
                                int invPick = ItemUtil.findInventorySlot("pickaxe", prefPick, false);
                                if (eqPick != -1 || invPick != -1) {
                                    usedHotbarSlots.add(prefPick);
                                    if (eqPick != prefPick && invPick != prefPick) {
                                        int slot = eqPick != -1 ? eqPick : invPick;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefPick, 2);
                                    }
                                }
                            }
                            int prefShovel = shovelSlot.getValue() - 1;
                            if (prefShovel >= 0 && prefShovel <= 8 && !usedHotbarSlots.contains(prefShovel)) {
                                int eqShovel = ItemUtil.findInventorySlot("shovel", prefShovel, true);
                                int invShovel = ItemUtil.findInventorySlot("shovel", prefShovel, false);
                                if (eqShovel != -1 || invShovel != -1) {
                                    usedHotbarSlots.add(prefShovel);
                                    if (eqShovel != prefShovel && invShovel != prefShovel) {
                                        int slot = eqShovel != -1 ? eqShovel : invShovel;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefShovel, 2);
                                    }
                                }
                            }
                            int prefAxe = axeSlot.getValue() - 1;
                            if (prefAxe >= 0 && prefAxe <= 8 && !usedHotbarSlots.contains(prefAxe)) {
                                int eqAxe = ItemUtil.findInventorySlot("axe", prefAxe, true);
                                int invAxe = ItemUtil.findInventorySlot("axe", prefAxe, false);
                                if (eqAxe != -1 || invAxe != -1) {
                                    usedHotbarSlots.add(prefAxe);
                                    if (eqAxe != prefAxe && invAxe != prefAxe) {
                                        int slot = eqAxe != -1 ? eqAxe : invAxe;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefAxe, 2);
                                    }
                                }
                            }
                            int prefBlocks = blocksSlot.getValue() - 1;
                            if (prefBlocks >= 0 && prefBlocks <= 8 && !usedHotbarSlots.contains(prefBlocks)) {
                                int invBlocks = ItemUtil.findInventorySlot(prefBlocks);
                                if (invBlocks != -1) {
                                    usedHotbarSlots.add(prefBlocks);
                                    if (invBlocks != prefBlocks) {
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(invBlocks), prefBlocks, 2);
                                    }
                                }
                            }
                            int prefThrows = throwsSlot.getValue() - 1;
                            if (prefThrows >= 0 && prefThrows <= 8 && !usedHotbarSlots.contains(prefThrows)) {
                                int eqThrows = findThrowableSlot(prefThrows, true);
                                int invThrows = findThrowableSlot(prefThrows, false);
                                if (eqThrows != -1 || invThrows != -1) {
                                    usedHotbarSlots.add(prefThrows);
                                    if (eqThrows != prefThrows && invThrows != prefThrows) {
                                        int slot = eqThrows != -1 ? eqThrows : invThrows;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefThrows, 2);
                                    }
                                }
                            }
                            int prefGapple = gappleSlot.getValue() - 1;
                            if (prefGapple >= 0 && prefGapple <= 8 && !usedHotbarSlots.contains(prefGapple)) {
                                int eqGapple = findGappleSlot(prefGapple, true);
                                int invGapple = findGappleSlot(prefGapple, false);
                                if (eqGapple != -1 || invGapple != -1) {
                                    usedHotbarSlots.add(prefGapple);
                                    if (eqGapple != prefGapple && invGapple != prefGapple) {
                                        int slot = eqGapple != -1 ? eqGapple : invGapple;
                                        clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefGapple, 2);
                                    }
                                }
                            }
                            if (keepBowAndArrows.getValue()) {
                                int prefBow = bowSlot.getValue() - 1;
                                if (prefBow >= 0 && prefBow <= 8 && !usedHotbarSlots.contains(prefBow)) {
                                    int eqBow = findBowSlot(prefBow, true);
                                    int invBow = findBowSlot(prefBow, false);
                                    if (eqBow != -1 || invBow != -1) {
                                        usedHotbarSlots.add(prefBow);
                                        if (eqBow != prefBow && invBow != prefBow) {
                                            int slot = eqBow != -1 ? eqBow : invBow;
                                            clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), prefBow, 2);
                                        }
                                    }
                                }
                            }
                            ArrayList<Integer> itemsToDrop = new ArrayList<>();
                            ArrayList<Integer> eqArmor = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                            ArrayList<Integer> invArmor = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                            for (int i = 0; i < 4; i++) {
                                eqArmor.set(i, ItemUtil.findArmorInventorySlot(i, true));
                                invArmor.set(i, ItemUtil.findArmorInventorySlot(i, false));
                            }
                            int eqSwordDrop = ItemUtil.findSwordInInventorySlot(swordSlot.getValue() - 1, true);
                            int invSwordDrop = ItemUtil.findSwordInInventorySlot(swordSlot.getValue() - 1, false);
                            int eqPickDrop = ItemUtil.findInventorySlot("pickaxe", pickaxeSlot.getValue() - 1, true);
                            int invPickDrop = ItemUtil.findInventorySlot("pickaxe", pickaxeSlot.getValue() - 1, false);
                            int eqShovelDrop = ItemUtil.findInventorySlot("shovel", shovelSlot.getValue() - 1, true);
                            int invShovelDrop = ItemUtil.findInventorySlot("shovel", shovelSlot.getValue() - 1, false);
                            int eqAxeDrop = ItemUtil.findInventorySlot("axe", axeSlot.getValue() - 1, true);
                            int invAxeDrop = ItemUtil.findInventorySlot("axe", axeSlot.getValue() - 1, false);
                            int invBlocksDrop = ItemUtil.findInventorySlot(blocksSlot.getValue() - 1);
                            int eqThrowsDrop = findThrowableSlot(throwsSlot.getValue() - 1, true);
                            int invThrowsDrop = findThrowableSlot(throwsSlot.getValue() - 1, false);
                            int eqGappleDrop = findGappleSlot(gappleSlot.getValue() - 1, true);
                            int invGappleDrop = findGappleSlot(gappleSlot.getValue() - 1, false);
                            int eqBowDrop = findBowSlot(bowSlot.getValue() - 1, true);
                            int invBowDrop = findBowSlot(bowSlot.getValue() - 1, false);

                            int currentBlockCount = getStackSize(invBlocksDrop);
                            int totalThrowsCount = getTotalThrowsCount();

                            if (totalThrowsCount > throwsAmount.getValue()) {
                                for (int i = 35; i >= 0; i--) {
                                    if (!eqArmor.contains(i) && !invArmor.contains(i)
                                            && eqSwordDrop != i && invSwordDrop != i && secondSwordSlot != i && prefSecondSwordSlot != i && prefSecondSwordSlot != i
                                            && eqPickDrop != i && invPickDrop != i
                                            && eqShovelDrop != i && invShovelDrop != i
                                            && eqAxeDrop != i && invAxeDrop != i
                                            && invBlocksDrop != i && eqThrowsDrop != i
                                            && invThrowsDrop != i && eqGappleDrop != i
                                            && invGappleDrop != i
                                            && eqBowDrop != i && invBowDrop != i) {
                                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                        if (isThrowable(stack)) {
                                            itemsToDrop.add(i);
                                        }
                                    }
                                }
                            }
                            for (int i = 0; i < 36; i++) {
                                if (!eqArmor.contains(i) && !invArmor.contains(i)
                                        && eqSwordDrop != i && invSwordDrop != i && secondSwordSlot != i && prefSecondSwordSlot != i && prefSecondSwordSlot != i
                                        && eqPickDrop != i && invPickDrop != i
                                        && eqShovelDrop != i && invShovelDrop != i
                                        && eqAxeDrop != i && invAxeDrop != i
                                        && invBlocksDrop != i && eqThrowsDrop != i
                                        && invThrowsDrop != i && eqGappleDrop != i
                                        && invGappleDrop != i
                                        && !itemsToDrop.contains(i)) {
                                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                    if (stack != null) {
                                        if (keepBowAndArrows.getValue() && isBow(stack)) {
                                            int bestBow = ItemUtil.getBestBowSlot();
                                            if (bestBow != -1 && i != bestBow) {
                                                itemsToDrop.add(i);
                                            }
                                            continue;
                                        }
                                        if (eqBowDrop != i && invBowDrop != i) {
                                            boolean isBlock = ItemUtil.isBlock(stack);
                                            boolean isThrowable = isThrowable(stack);
                                            boolean isGapple = isGapple(stack);
                                            boolean isOre = isOre(stack);
                                            boolean isProtectedWater = keepWaterBucket.getValue() && isWaterBucket(stack);
                                            boolean isProtectedBowArrow = keepBowAndArrows.getValue() && ItemUtil.isArrow(stack);

                                            if (!keepOre.getValue() && isOre) {
                                                itemsToDrop.add(i);
                                            } else if (!isThrowable && !isOre && !isGapple && !isProtectedWater && !isProtectedBowArrow
                                                    && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= blocks.getValue()))) {
                                                itemsToDrop.add(i);
                                            }
                                            if (isBlock) currentBlockCount += stack.stackSize;
                                        }
                                    }
                                }
                            }
                            for (int slot : itemsToDrop) {
                                clickSlot(mc.thePlayer.inventoryContainer.windowId, convertSlotIndex(slot), 1, 4);
                            }
                        }

                        if (this.autoClose.getValue() && isInventorySorted()) {
                            mc.thePlayer.closeScreen();
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onClick(WindowClickEvent event) {
        if (this.minDelay.getValue() == 0 && this.maxDelay.getValue() == 0) {
            this.actionDelay = 0;
        } else {
            this.actionDelay = RandomUtils.nextInt(
                    this.minDelay.getValue() + 1,
                    this.maxDelay.getValue() + 2
            );
        }
    }

    @Override
    public void verifyValue(String string) {
        switch (string) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }
}
