package leader.client.module.modules.player;

import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {

    public final ListValue mode = new ListValue("Mode", new String[]{"Vanilla"}, "Vanilla", this);
    public final SliderValue slowdown = new SliderValue("Slowdown", 0, 0, 100, () -> mode.is("Vanilla"), Representation.INT, this);
    public final BoolValue groundOnly = new BoolValue("Ground Only", false, () -> mode.is("Vanilla"), this);
    public final BoolValue reachOnly = new BoolValue("Reach Only", false, () -> mode.is("Vanilla"), this);

    public KeepSprint() {
        super("KeepSprint", false);
    }

    public boolean shouldKeepSprint() {
        if (mode.is("Vanilla")) {
            if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
                return false;
            }
            return !this.reachOnly.getValue()
                    || mc.objectMouseOver != null
                    && mc.objectMouseOver.hitVec != null
                    && mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0;
        }
        return true;
    }
    @Override
    public String[] getSuffix() {
        return new String[]{mode.getValue()};
    }
}
