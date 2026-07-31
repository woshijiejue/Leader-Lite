package leader.client.module.modules.render;

import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.SliderValue;

public class NoHurtCam extends Module {
    public final SliderValue multiplier = new SliderValue("multiplier", 0, 0, 100, Representation.INT, this);

    public NoHurtCam() {
        super("NoHurtCam", false, true);
    }
}
