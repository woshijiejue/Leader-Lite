package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.module.Module;
import leader.client.util.DebugUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ListCommand extends Command {
    public ListCommand() {
        super(new ArrayList<>(Arrays.asList("list", "l", "modules", "leader")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!Leader.moduleManager.modules.isEmpty()) {
            DebugUtil.sendFormatted(String.format("%sModules:&r", Leader.clientName));
            for (Module module : Leader.moduleManager.modules.values()) {
                DebugUtil.sendFormatted(String.format("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule()));
            }
        }
    }
}
