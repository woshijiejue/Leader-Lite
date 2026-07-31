package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.util.misc.ChatColors;
import leader.client.util.DebugUtil;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;

public class ItemCommand extends Command {
    public ItemCommand() {
        super(new ArrayList<>(Arrays.asList("itemname", "item")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (stack != null) {
            String display = stack.getDisplayName().replace('§', '&');
            String registryName = stack.getItem().getRegistryName();
            String compound = stack.hasTagCompound() ? stack.getTagCompound().toString().replace('§', '&') : "";
            DebugUtil.sendRaw(String.format("%s%s (%s) %s", ChatColors.formatColor(Leader.clientName), display, registryName, compound));
        }
    }
}
