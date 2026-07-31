package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.util.DebugUtil;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class VclipCommand extends Command {
    
    private static final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));

    public VclipCommand() {
        super(new ArrayList<>(Collections.singletonList("vclip")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() >= 2) {
            double distance = 0.0;
            try {
                distance = Double.parseDouble(args.get(1));
            } catch (NumberFormatException e) {
                // e.printStackTrace();
            } finally {
                mc.thePlayer.setPositionAndUpdate(mc.thePlayer.posX, mc.thePlayer.posY + distance, mc.thePlayer.posZ);
                DebugUtil.sendFormatted(String.format("%sClipped (%s blocks)", Leader.clientName, df.format(distance)));
            }
            return;
        }
        DebugUtil.sendFormatted(
                String.format("%sUsage: .%s <&odistance&r>&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT))
        );
    }
}
