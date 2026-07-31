package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.util.DebugUtil;
import net.minecraft.util.Session;
import net.minecraft.util.StringUtils;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;

public class IgnCommand extends Command {
    public IgnCommand() {
        super(new ArrayList<String>(Arrays.asList("username", "name", "ign")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        Session session = mc.getSession();
        if (session != null) {
            String username = session.getUsername();
            if (!StringUtils.isNullOrEmpty(username)) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(username), null);
                    DebugUtil.sendFormatted(String.format("%sYour username has been copied to the clipboard (&o%s&r)&r", Leader.clientName, username));
                } catch (Exception e) {
                    DebugUtil.sendFormatted(String.format("%sFailed to copy&r", Leader.clientName));
                }
            }
        }
    }
}
