package leader.client.module.modules.render;

import leader.client.module.Module;
import leader.client.property.properties.BooleanProperty;

public class BetterFPS extends Module {
    public BetterFPS() {
        super("BetterFPS", false);
    }
    public static BooleanProperty fastLoad = new BooleanProperty("FastLoad", true);
    public static boolean using = false;
    @Override
    public void onEnabled() {
        using = true;
    }

    @Override
    public void onDisabled() {
        using = false;
    }
}
