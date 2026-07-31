package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.module.Module;
import leader.client.util.DebugUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super(new ArrayList<>(Arrays.asList("toggle", "t")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            DebugUtil.sendFormatted(
                    String.format("%sUsage: .%s <&omodule&r>&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
            );
        } else {
            Module module = Leader.moduleManager.getModule(args.get(1));
            if (module == null) {
                DebugUtil.sendFormatted(String.format("%sModule not found (&o%s&r)&r", Leader.clientName, args.get(1)));
            } else {
                boolean changed = true;
                if (args.size() >= 3) {
                    if (args.get(2).equalsIgnoreCase("true")
                            || args.get(2).equalsIgnoreCase("on")
                            || args.get(2).equalsIgnoreCase("1")) {
                        changed = !module.isEnabled();
                    } else if (args.get(2).equalsIgnoreCase("false")
                            || args.get(2).equalsIgnoreCase("off")
                            || args.get(2).equalsIgnoreCase("0")) {
                        changed = module.isEnabled();
                    }
                }
                if (changed && module.toggle()) {
                    DebugUtil.sendFormatted(String.format("%s%s: %s&r", Leader.clientName, module.getName(), module.isEnabled() ? "&a&lON" : "&c&lOFF"));
                }
            }
        }
    }
}
