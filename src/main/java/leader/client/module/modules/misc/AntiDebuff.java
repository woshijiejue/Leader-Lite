package leader.client.module.modules.misc;

import leader.client.module.Module;
import leader.client.module.values.impl.BoolValue;

public class AntiDebuff extends Module {
    public final BoolValue blindness = new BoolValue("blindness", true, this);
    public final BoolValue nausea = new BoolValue("nausea", true, this);

    public AntiDebuff() {
        super("AntiDebuff", false);
    }
}
