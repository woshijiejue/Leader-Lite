package leader.client.module.modules.render;

import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.TickEvent;
import leader.client.module.Module;
import leader.client.module.values.impl.ListValue;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public class FullBright extends Module {

    private float prevGamma = Float.NaN;
    private boolean appliedNightVision = false;
    public final ListValue mode = (ListValue) new ListValue("mode", new String[]{"GAMMA", "EFFECT"}, "GAMMA", this)
            .onChanged(() -> {
                if (this.isEnabled()) {
                    this.onDisabled();
                    this.onEnabled();
                }
            });

    public FullBright() {
        super("Fullbright", true, true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            if (this.mode.is("GAMMA")) {
                mc.gameSettings.gammaSetting = 1000.0F;
            } else if (this.mode.is("EFFECT")) {
                mc.thePlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id, 25940, 0));
            }
        }
    }

    @Override
    public void onEnabled() {
        if (this.mode.is("GAMMA")) {
            this.prevGamma = mc.gameSettings.gammaSetting;
        } else if (this.mode.is("EFFECT")) {
            this.appliedNightVision = true;
        }
    }

    @Override
    public void onDisabled() {
        if (!Float.isNaN(this.prevGamma)) {
            mc.gameSettings.gammaSetting = this.prevGamma;
            this.prevGamma = Float.NaN;
        }
        if (this.appliedNightVision) {
            if (mc.thePlayer != null) {
                mc.thePlayer.removePotionEffectClient(Potion.nightVision.id);
            }
            this.appliedNightVision = false;
        }
    }
}
