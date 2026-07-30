package leader.client.module.modules.player;

import leader.client.module.Module;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.IntProperty;
import leader.client.property.properties.ModeProperty;

public class BlinkSettings extends Module {

    public BlinkSettings(){super("BlinkSettings",true,false);}

    public final BooleanProperty slowRelease = new BooleanProperty("SlowRelease",false);
    public final ModeProperty slowReleaseTime = new ModeProperty("SlowReleaseTime",0,new String[]{"Start Blink","Stop Blink"},slowRelease::getValue);
    public final IntProperty slowReleaseDelay = new IntProperty("DelayBetweenSlowRelease",0,0,10,slowRelease::getValue);
    public final IntProperty maxPacketsPerTick = new IntProperty("MaxPacketPerTick",5,1,30,slowRelease::getValue);
    public final IntProperty maxC03PacketsPerTick = new IntProperty("MaxC03PacketPerTick",1,1,5,slowRelease::getValue);
}
