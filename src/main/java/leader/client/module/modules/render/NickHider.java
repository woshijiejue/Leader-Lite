package leader.client.module.modules.render;

import leader.client.module.Module;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.StringValue;

import java.util.regex.Matcher;

import leader.client.util.misc.ChatColors;

public class NickHider extends Module {

    public final StringValue protectName = new StringValue("name", "You", this);
    public final BoolValue scoreboard = new BoolValue("scoreboard", true, this);
    public final BoolValue level = new BoolValue("level", true, this);

    public NickHider() {
        super("NickHider", false, true);
    }

    public String replaceNick(String input) {
        if (input != null && mc.thePlayer != null) {
            if (this.scoreboard.getValue() && input.matches("§7\\d{2}/\\d{2}/\\d{2}(?:\\d{2})?  ?§8.*")) {
                input = input.replaceAll("§8", "§8§k").replaceAll("[^\\x00-\\x7F§]", "?");
            }
            return input.replaceAll(
                    mc.thePlayer.getName(), Matcher.quoteReplacement(ChatColors.formatColor(this.protectName.getValue()))
            );
        } else {
            return input;
        }
    }
}
