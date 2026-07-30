package leader.client.module.modules.player;

import leader.client.module.Module;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.ModeProperty;
import leader.client.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla"});
    public final PercentProperty slowdown = new PercentProperty("Slowdown", 0, () -> mode.getValue() == 0);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", false, () -> mode.getValue() == 0);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach Only", false, () -> mode.getValue() == 0);


    public KeepSprint() {
        super("KeepSprint", false);
    }

    public boolean shouldKeepSprint() {
        if (mode.getValue() == 0) {
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
        if (mode.getValue() == 1) return new String[]{"Vanilla"};
        return new String[]{mode.getModeString()};
    }
}
