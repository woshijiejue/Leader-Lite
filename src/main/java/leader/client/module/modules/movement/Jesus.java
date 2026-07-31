package leader.client.module.modules.movement;

import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Jesus extends Module {
    private static final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));
    public final SliderValue speed = new SliderValue("speed", 2.5, 0.0, 3.0, Representation.FLOAT, this);
    public final BoolValue noPush = new BoolValue("no-push", true, this);
    public final BoolValue groundOnly = new BoolValue("ground-only", true, this);

    public Jesus() {
        super("Jesus", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.speed.getValue())};
    }
}
