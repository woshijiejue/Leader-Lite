package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.module.Module;
import leader.client.util.DebugUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class HideCommand extends Command {
    public HideCommand() {
        super(new ArrayList<>(Arrays.asList("hide", "h")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            DebugUtil.sendFormatted(
                    String.format("%sUsage: .%s <&omodule&r>&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
            );
        } else if (!args.get(1).equals("*")) {
            Module module = Leader.moduleManager.getModule(args.get(1));
            if (module == null) {
                DebugUtil.sendFormatted(String.format("%sModule &o%s&r not found&r", Leader.clientName, args.get(1)));
            } else if (module.isHidden()) {
                DebugUtil.sendFormatted(String.format("%s&o%s&r is already hidden in HUD&r", Leader.clientName, module.getName()));
            } else {
                module.setHidden(true);
                DebugUtil.sendFormatted(String.format("%s&o%s&r has been hidden in HUD&r", Leader.clientName, module.getName()));
            }
        } else {
            for (Module module : Leader.moduleManager.modules.values()) {
                module.setHidden(true);
            }
            DebugUtil.sendFormatted(String.format("%sAll modules have been hidden in HUD&r", Leader.clientName));
        }
    }
}
