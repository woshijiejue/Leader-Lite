package leader.client.module.modules.player;

import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;

public class BlinkSettings extends Module {

    public final BoolValue slowRelease = new BoolValue("SlowRelease", false, this);
    public final ListValue slowReleaseTime = new ListValue("SlowReleaseTime", new String[]{"Start Blink", "Stop Blink"}, "Start Blink", slowRelease::getValue, this);
    public final SliderValue slowReleaseDelay = new SliderValue("DelayBetweenSlowRelease", 0, 0, 10, slowRelease::getValue, Representation.INT, this);
    public final SliderValue maxPacketsPerTick = new SliderValue("MaxPacketPerTick", 5, 1, 30, slowRelease::getValue, Representation.INT, this);
    public final SliderValue maxC03PacketsPerTick = new SliderValue("MaxC03PacketPerTick", 1, 1, 5, slowRelease::getValue, Representation.INT, this);

    public BlinkSettings(){super("BlinkSettings",true,false);}
}
