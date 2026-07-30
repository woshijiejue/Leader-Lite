package leader.client.management;

import leader.client.enums.ChatColors;

import java.awt.*;
import java.io.File;

public class FriendManager extends PlayerFileManager {
    public FriendManager() {
        super(new File("./config/Leader/", "friends.txt"), new Color(ChatColors.DARK_GREEN.toAwtColor()));
    }
}
