package leader.client.module.modules.player;

import com.google.common.base.CaseFormat;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.TickEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;
import leader.client.util.timer.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;

public class Refill extends Module {
    
    public final SliderValue delay = new SliderValue("delay", 1, 0, 20, Representation.INT, this);
    public final ListValue mode = new ListValue("mode", new String[]{"SOUP", "POT"}, "POT", this);
    private final TimerUtil time = new TimerUtil();

    public Refill() {
        super("Refill", false);
    }

    @EventTarget
    public void onUpdate(TickEvent event) {
        if (this.isEnabled() && mc.thePlayer != null && event.getType() == EventType.PRE) {
            if (mode.is("SOUP")) {
                this.refill(Items.mushroom_stew);
            } else if (mode.is("POT")) {
                this.refill(ItemPotion.getItemById(373));
            }
        }
    }

    private void refill(Item targetItem) {
        if (mc.currentScreen instanceof GuiInventory) {
            if (!isHotbarFull() && this.time.hasTimeElapsed(delay.getValue().longValue() * 50)) {
                for (int i = 9; i < 36; ++i) {
                    ItemStack itemstack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                    if (itemstack != null && itemstack.getItem() == targetItem) {
                        mc.playerController.windowClick(0, i, 0, 1, mc.thePlayer);
                        break;
                    }
                }
                this.time.reset();
            }
        }
    }

    public static boolean isHotbarFull() {
        for (int i = 0; i <= 36; ++i) {
            ItemStack itemstack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemstack == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getValue())};
    }
}
