package leader.client.management;

import leader.client.enums.ChatColors;

import java.awt.*;
import java.io.File;

public class TargetManager extends PlayerFileManager {
    public TargetManager() {
        super(new File("./config/Leader/", "enemies.txt"), new Color(ChatColors.DARK_RED.toAwtColor()));
    }
}
