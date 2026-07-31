package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.util.DebugUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class HelpCommand extends Command {
    public HelpCommand() {
        super(new ArrayList<>(Arrays.asList("help", "commands")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!Leader.moduleManager.modules.isEmpty()) {
            DebugUtil.sendFormatted(String.format("%sCommands:&r", Leader.clientName));
            for (Command command : Leader.commandManager.commands) {
                if (!(command instanceof ModuleCommand)) {
                    DebugUtil.sendFormatted(String.format("&7»&r .%s&r", String.join(" &7/&r .", command.names)));
                }
            }
        }
    }
}
