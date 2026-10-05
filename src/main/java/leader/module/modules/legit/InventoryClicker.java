package leader.module.modules.legit;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.TickEvent;
import leader.mixin.IAccessorGuiScreen;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryEnderChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.scoreboard.ScoreObjective;
import java.util.Locale;
import org.lwjgl.input.Mouse;

public class InventoryClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty triggerTicks = new IntProperty("ticks", 2, 0, 20);
    public final BooleanProperty inInventory = new BooleanProperty("in-inventory", true);
    public final BooleanProperty inChest = new BooleanProperty("in-chest", true);
    public final BooleanProperty inEnderChest = new BooleanProperty("in-ender-chest", true);
    public final BooleanProperty inOther = new BooleanProperty("in-other-containers", true);
    public final BooleanProperty inBedwarsItemShop = new BooleanProperty("in-bedwars-item-shop", false);
    public final BooleanProperty inBedwarsUpgradeShop = new BooleanProperty("in-bedwars-upgrade-shop", false);
    public int ticks;
    private GuiScreen lastScreen;

    public InventoryClicker() {
        super("InventoryClicker", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{triggerTicks.getValue().toString() + " ticks"};
    }

    private boolean isEnderChest(IInventory inventory) {
        if (inventory instanceof InventoryEnderChest) return true;
        String name = inventory.getName();
        if (name == null) return false;
        String plain = name.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
        return plain.equals("container.enderchest")
                || plain.equalsIgnoreCase(I18n.format("container.enderchest"))
                || plain.equalsIgnoreCase("Ender Chest");
    }

    private boolean isAllowed(GuiScreen screen) {
        if (screen instanceof GuiInventory || screen instanceof GuiContainerCreative) {
            return this.inInventory.getValue();
        }
        if (screen instanceof GuiChest) {
            GuiChest chest = (GuiChest) screen;
            int shop = bedwarsShopType(chest);
            if (shop == 1) return inBedwarsItemShop.getValue();
            if (shop == 2) return inBedwarsUpgradeShop.getValue();
            if (chest.inventorySlots instanceof ContainerChest
                    && this.isEnderChest(((ContainerChest) chest.inventorySlots).getLowerChestInventory())) {
                return this.inEnderChest.getValue();
            }
            return this.inChest.getValue();
        }
        return screen instanceof GuiContainer && this.inOther.getValue();
    }

    private boolean inBedwars() {
        if (mc.theWorld == null) return false;
        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        if (objective == null) return false;
        String name = plain(objective.getDisplayName());
        return name.contains("bed wars") || name.contains("bedwars") || name.contains("起床战争") || name.contains("起床戰爭");
    }

    private String plain(String text) {
        return text == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(text)
                .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Bedwars menus are server-created chests, not ordinary storage containers. */
    private int bedwarsShopType(GuiChest screen) {
        if (!inBedwars() || !(screen.inventorySlots instanceof ContainerChest)) return 0;
        IInventory inventory = ((ContainerChest) screen.inventorySlots).getLowerChestInventory();
        String title = plain(inventory.getName());
        if (title.equals("upgrades & traps") || title.equals("upgrades and traps") || title.equals("team upgrades")
                || title.equals("upgrade shop") || title.equals("upgrades") || title.equals("traps")
                || title.equals("升级与陷阱") || title.equals("升级和陷阱") || title.equals("團隊升級")
                || title.equals("团队升级") || title.equals("升級與陷阱") || title.equals("升级商店")
                || title.equals("升級商店")) return 2;
        if (title.equals("quick buy") || title.equals("item shop") || title.equals("blocks") || title.equals("melee")
                || title.equals("armor") || title.equals("armour") || title.equals("tools") || title.equals("ranged")
                || title.equals("potions") || title.equals("utility") || title.equals("rotating items")
                || title.equals("favorites") || title.contains("快捷购买") || title.contains("快捷購買")
                || title.contains("物品商店")) return 1;
        // Fallback for translated/category titles: require actual shop lore, not just "shop" in a name.
        boolean purchase = false, navigation = false, upgrade = false;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack == null || !stack.hasTagCompound()) continue;
            String itemName = plain(stack.getDisplayName());
            navigation |= itemName.equals("quick buy") || itemName.contains("快捷购买") || itemName.contains("快捷購買");
            upgrade |= itemName.contains("reinforced armor") || itemName.contains("sharpened swords")
                    || itemName.contains("miner fatigue trap") || itemName.contains("强化盔甲") || itemName.contains("锋利宝剑");
            NBTTagList lore = stack.getTagCompound().getCompoundTag("display").getTagList("Lore", 8);
            for (int j = 0; j < lore.tagCount(); j++) {
                String line = plain(lore.getStringTagAt(j));
                purchase |= line.contains("click to purchase") || line.contains("click to buy") || line.contains("点击购买")
                        || line.contains("點擊購買") || line.contains("not enough diamonds") || line.contains("not enough iron")
                        || line.contains("not enough gold") || line.contains("not enough emeralds");
                navigation |= line.contains("add to quick buy") || line.contains("remove from quick buy");
            }
        }
        if (purchase && upgrade) return 2;
        return purchase && navigation ? 1 : 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.currentScreen != lastScreen) { ticks = 0; lastScreen = mc.currentScreen; }
        if (!this.isEnabled() || mc.thePlayer == null) { ticks = 0; return; }
        if (!(mc.currentScreen instanceof GuiContainer) || !this.isAllowed(mc.currentScreen) || !Mouse.isButtonDown(0)) {
            ticks = 0;
            return;
        }
        GuiContainer screen = (GuiContainer) mc.currentScreen;
        final int mouseX = Mouse.getX() * screen.width / mc.displayWidth;
        final int mouseY = screen.height - Mouse.getY() * screen.height / mc.displayHeight - 1;
        ticks++;
        if (ticks > triggerTicks.getValue()) {
            ((IAccessorGuiScreen) screen).callMouseClicked(mouseX, mouseY, 0);
        }
    }

    @Override
    public void onDisabled() { ticks = 0; lastScreen = null; }
}
