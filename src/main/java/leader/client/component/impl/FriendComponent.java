package leader.client.component.impl;

import leader.client.util.misc.ChatColors;

import java.awt.*;

public class FriendComponent extends PlayerComponent {
    public FriendComponent() {
        super("friends", new Color(ChatColors.DARK_GREEN.toAwtColor()));
    }
}