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
import org.lwjgl.input.Mouse;

public class InventoryClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty triggerTicks = new IntProperty("ticks", 2, 0, 20);
    public final BooleanProperty inInventory = new BooleanProperty("in-inventory", true);
    public final BooleanProperty inChest = new BooleanProperty("in-chest", true);
    public final BooleanProperty inEnderChest = new BooleanProperty("in-ender-chest", true);
    public final BooleanProperty inOther = new BooleanProperty("in-other-containers", true);
    public int ticks;

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
            if (chest.inventorySlots instanceof ContainerChest
                    && this.isEnderChest(((ContainerChest) chest.inventorySlots).getLowerChestInventory())) {
                return this.inEnderChest.getValue();
            }
            return this.inChest.getValue();
        }
        return screen instanceof GuiContainer && this.inOther.getValue();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.getType() != EventType.PRE) return;
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
}
