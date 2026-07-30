package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.enums.ChatColors;
import leader.client.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;

public class ItemCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

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
            ChatUtil.sendRaw(String.format("%s%s (%s) %s", ChatColors.formatColor(Leader.clientName), display, registryName, compound));
        }
    }
}
