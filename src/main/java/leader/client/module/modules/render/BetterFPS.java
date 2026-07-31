package leader.client.module.modules.render;

import leader.client.module.Module;
import leader.client.module.values.impl.BoolValue;

public class BetterFPS extends Module {
    public BetterFPS() {
        super("BetterFPS", false);
    }
    public static BoolValue fastLoad = new BoolValue("FastLoad", true, null);
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
