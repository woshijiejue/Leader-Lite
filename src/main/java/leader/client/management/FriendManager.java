package leader.client.management;

import leader.client.enums.ChatColors;

import java.awt.*;

public class FriendManager extends PlayerFileManager {
    public FriendManager() {
        super("friends", new Color(ChatColors.DARK_GREEN.toAwtColor()));
    }
}