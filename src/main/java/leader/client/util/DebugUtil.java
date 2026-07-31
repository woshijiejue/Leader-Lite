package leader.client.util;

import leader.client.util.misc.ChatColors;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

public class DebugUtil implements InstanceAccess {
    
    public static void send(IChatComponent iChatComponent) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(iChatComponent);
        }
    }

    public static void sendFormatted(String string) {
        send(new ChatComponentText(ChatColors.formatColor(string)));
    }

    public static void sendRaw(String string) {
        send(new ChatComponentText(string));
    }

    public static void sendMessage(String string) {
        if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage(string);
        }
    }
}
