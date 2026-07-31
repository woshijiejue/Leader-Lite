package leader.client.module.modules.player;

import leader.client.Leader;
import leader.client.component.impl.network.blink.BlinkType;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.LoadWorldEvent;
import leader.client.events.TickEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;

public class Blink extends Module {
    public final ListValue mode = new ListValue("mode", new String[]{"DEFAULT", "PULSE"}, "DEFAULT", this);
    public final SliderValue ticks = new SliderValue("ticks", 20, 0, 1200, Representation.INT, this);

    public Blink() {
        super("Blink", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            if (!Leader.blinkComponent.getBlinkingModule().equals(BlinkType.BLINK)) {
                this.setEnabled(false);
            } else {
                if (this.ticks.getValue() > 0 && Leader.blinkComponent.countMovement() > this.ticks.getValue().longValue()) {
                    if (this.mode.is("DEFAULT")) {
                        this.setEnabled(false);
                    } else if (this.mode.is("PULSE")) {
                        Leader.blinkComponent.setBlinkState(false, BlinkType.BLINK);
                        Leader.blinkComponent.setBlinkState(true, BlinkType.BLINK);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.setEnabled(false);
    }

    @Override
    public void onEnabled() {
        Leader.blinkComponent.setBlinkState(false, Leader.blinkComponent.getBlinkingModule());
        Leader.blinkComponent.setBlinkState(true, BlinkType.BLINK);
    }

    @Override
    public void onDisabled() {
        Leader.blinkComponent.setBlinkState(false, BlinkType.BLINK);
    }
}
