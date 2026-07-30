package leader.client.module.modules.player;

import leader.client.Leader;
import leader.client.enums.BlinkModules;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.LoadWorldEvent;
import leader.client.events.TickEvent;
import leader.client.module.Module;
import leader.client.property.properties.IntProperty;
import leader.client.property.properties.ModeProperty;

public class Blink extends Module {
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"DEFAULT", "PULSE"});
    public final IntProperty ticks = new IntProperty("ticks", 20, 0, 1200);

    public Blink() {
        super("Blink", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            if (!Leader.blinkManager.getBlinkingModule().equals(BlinkModules.BLINK)) {
                this.setEnabled(false);
            } else {
                if (this.ticks.getValue() > 0 && Leader.blinkManager.countMovement() > (long) this.ticks.getValue()) {
                    switch (this.mode.getValue()) {
                        case 0:
                            this.setEnabled(false);
                            break;
                        case 1:
                            Leader.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                            Leader.blinkManager.setBlinkState(true, BlinkModules.BLINK);
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
        Leader.blinkManager.setBlinkState(false, Leader.blinkManager.getBlinkingModule());
        Leader.blinkManager.setBlinkState(true, BlinkModules.BLINK);
    }

    @Override
    public void onDisabled() {
        Leader.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    }
}
