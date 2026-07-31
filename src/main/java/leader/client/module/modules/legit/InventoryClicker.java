package leader.client.module.modules.legit;

import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.TickEvent;
import leader.mixin.accessor.IAccessorGuiScreen;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.SliderValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Mouse;

public class InventoryClicker extends Module {
    public final SliderValue triggerTicks = new SliderValue("ticks", 2, 0, 20, Representation.INT, this);
    public int ticks;

    public InventoryClicker() {
        super("InventoryClicker", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{triggerTicks.getValue().intValue() + " ticks"};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && mc.thePlayer != null && event.getType() == EventType.PRE) {
            if (mc.currentScreen instanceof GuiContainer) {
                GuiContainer screen = ((GuiContainer) mc.currentScreen);
                final int mouseX = Mouse.getEventX() * screen.width / mc.displayWidth;
                final int mouseY = screen.height - Mouse.getEventY() * screen.height / mc.displayHeight - 1;
                if (Mouse.isButtonDown(0)) {
                    ticks++;
                    if (ticks > triggerTicks.getValue().intValue()) {
                        ((IAccessorGuiScreen) screen).callMouseClicked(mouseX, mouseY, 0);
                    }
                } else {
                    ticks = 0;
                }
            }
        }
    }
}
