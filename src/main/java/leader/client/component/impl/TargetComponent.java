package leader.client.component.impl;

import leader.client.util.misc.ChatColors;

import java.awt.*;

public class TargetComponent extends PlayerComponent {
    public TargetComponent() {
        super("enemies", new Color(ChatColors.DARK_RED.toAwtColor()));
    }
}