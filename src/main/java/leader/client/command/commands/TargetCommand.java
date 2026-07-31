package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.util.misc.ChatColors;
import leader.client.util.DebugUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class TargetCommand extends Command {
    public TargetCommand() {
        super(new ArrayList<>(Arrays.asList("enemy", "e", "target")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() >= 2) {
            String subCommand = args.get(1).toLowerCase(Locale.ROOT);
            switch (subCommand) {
                case "add":
                    if (args.size() < 3) {
                        DebugUtil.sendFormatted(
                                String.format("%sUsage: .%s add <&oname&r>&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
                        );
                        return;
                    }
                    String added = Leader.targetComponent.add(args.get(2));
                    if (added == null) {
                        DebugUtil.sendFormatted(String.format("%s&o%s&r is already in your enemy list&r", Leader.clientName, args.get(2)));
                        return;
                    }
                    DebugUtil.sendFormatted(String.format("%sAdded &o%s&r to your enemy list&r", Leader.clientName, added));
                    return;
                case "remove":
                    if (args.size() < 3) {
                        DebugUtil.sendFormatted(
                                String.format("%sUsage: .%s remove <&oname&r>&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
                        );
                        return;
                    }
                    String removed = Leader.targetComponent.remove(args.get(2));
                    if (removed == null) {
                        DebugUtil.sendFormatted(String.format("%s&o%s&r is not in your enemy list&r", Leader.clientName, args.get(2)));
                        return;
                    }
                    DebugUtil.sendFormatted(String.format("%sRemoved &o%s&r from your enemy list&r", Leader.clientName, removed));
                    return;
                case "list":
                    ArrayList<String> list = Leader.targetComponent.getPlayers();
                    if (list.isEmpty()) {
                        DebugUtil.sendFormatted(String.format("%sNo enemies&r", Leader.clientName));
                        return;
                    }
                    DebugUtil.sendFormatted(String.format("%sEnemies:&r", Leader.clientName));
                    for (String player : list) {
                        DebugUtil.sendRaw(String.format(ChatColors.formatColor("   &o%s&r"), player));
                    }
                    return;
                case "clear":
                    Leader.targetComponent.clear();
                    DebugUtil.sendFormatted(String.format("%sCleared your enemy list&r", Leader.clientName));
                    return;
            }
        }
        DebugUtil.sendFormatted(
                String.format("%sUsage: .%s <&oadd&r/&oremove&r/&olist&r/&oclear&r>&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
        );
    }
}
