package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.util.misc.ChatColors;
import leader.client.util.DebugUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class FriendCommand extends Command {
    public FriendCommand() {
        super(new ArrayList<>(Arrays.asList("friend", "f")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() >= 2) {
            String subCommand = args.get(1).toLowerCase(Locale.ROOT);
            switch (subCommand) {
                case "a":
                case "add":
                    if (args.size() < 3) {
                        DebugUtil.sendFormatted(
                                String.format("%sUsage: .%s add <&oname&r> [&oname&r] ...&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
                        );
                        return;
                    }
                    for (String name: args.subList(2, args.size())) {
                        String added = Leader.friendComponent.add(name);
                        if (added == null) {
                            DebugUtil.sendFormatted(String.format("%s&o%s&r is already in your friend list&r", Leader.clientName, name));
                        } else {
                            DebugUtil.sendFormatted(String.format("%sAdded &o%s&r to your friend list&r", Leader.clientName, added));
                        }
                    }
                    return;
                case "r":
                case "remove":
                    if (args.size() < 3) {
                        DebugUtil.sendFormatted(
                                String.format("%sUsage: .%s remove <&oname&r> [&oname&r] ...&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
                        );
                        return;
                    }
                    for (String name: args.subList(2, args.size())){
                        String removed = Leader.friendComponent.remove(name);
                        if (removed == null) {
                            DebugUtil.sendFormatted(String.format("%s&o%s&r is not in your friend list&r", Leader.clientName, name));
                        } else {
                            DebugUtil.sendFormatted(String.format("%sRemoved &o%s&r from your friend list&r", Leader.clientName, removed));
                        }
                    }
                    return;
                case "l":
                case "list":
                    ArrayList<String> list = Leader.friendComponent.getPlayers();
                    if (list.isEmpty()) {
                        DebugUtil.sendFormatted(String.format("%sNo friends&r", Leader.clientName));
                        return;
                    }
                    DebugUtil.sendFormatted(String.format("%sFriends:&r", Leader.clientName));
                    for (String friend : list) {
                        DebugUtil.sendRaw(String.format(ChatColors.formatColor("   &o%s&r"), friend));
                    }
                    return;
                case "c":
                case "clear":
                    Leader.friendComponent.clear();
                    DebugUtil.sendFormatted(String.format("%sCleared your friend list&r", Leader.clientName));
                    return;
                default:
                    if (args.size() == 2) {
                        if (Leader.friendComponent.isFriend(args.get(1))) {
                            runCommand(new ArrayList<>(Arrays.asList(args.get(0), "remove", args.get(1))));
                        } else {
                            runCommand(new ArrayList<>(Arrays.asList(args.get(0), "add", args.get(1))));
                        }
                        return;
                    }
            }
        }
        DebugUtil.sendFormatted(
                String.format("%sUsage: .%s <&oa(dd)&r/&or(emove)&r/&ol(ist)&r/&oc(lear)&r>&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
        );
    }
}
