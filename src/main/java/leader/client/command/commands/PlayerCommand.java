package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.enums.ChatColors;
import leader.client.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.util.ArrayList;
import java.util.Arrays;

public class PlayerCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public PlayerCommand() {
        super(new ArrayList<>(Arrays.asList("playerlist", "players")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        ArrayList<String> players = new ArrayList<>();
        for (NetworkPlayerInfo playerInfo : mc.getNetHandler().getPlayerInfoMap()) {
            players.add(playerInfo.getGameProfile().getName().replace("§", "&"));
        }
        if (players.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sNo players&r", Leader.clientName));
        } else {
            ChatUtil.sendRaw(
                    String.format(
                            ChatColors.formatColor("%sPlayers:&r %s"),
                            ChatColors.formatColor(Leader.clientName),
                            String.join(", ", players)
                    )
            );
        }
    }
}
