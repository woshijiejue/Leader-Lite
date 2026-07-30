package leader.client.management;

import leader.client.enums.ChatColors;

import java.awt.*;

public class TargetManager extends PlayerFileManager {
    public TargetManager() {
        super("enemies", new Color(ChatColors.DARK_RED.toAwtColor()));
    }
}