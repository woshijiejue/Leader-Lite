package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.util.misc.ChatColors;
import leader.client.util.DebugUtil;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.util.ArrayList;
import java.util.Arrays;

public class PlayerCommand extends Command {
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
            DebugUtil.sendFormatted(String.format("%sNo players&r", Leader.clientName));
        } else {
            DebugUtil.sendRaw(
                    String.format(
                            ChatColors.formatColor("%sPlayers:&r %s"),
                            ChatColors.formatColor(Leader.clientName),
                            String.join(", ", players)
                    )
            );
        }
    }
}
