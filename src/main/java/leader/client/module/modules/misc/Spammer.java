package leader.client.module.modules.misc;

import leader.client.event.EventTarget;
import leader.client.events.Render2DEvent;
import leader.client.module.Module;
import leader.client.util.timer.TimerUtil;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.StringValue;
import net.minecraft.client.Minecraft;

public class Spammer extends Module {

    private final TimerUtil timer = new TimerUtil();
    private int charOffset = 19968;
    public final StringValue text = new StringValue("text", "meow", this);
    public final SliderValue delay = new SliderValue("delay", 3.5, 0.0, 3600.0, Representation.FLOAT, this);
    public final SliderValue random = new SliderValue("random", 0, 0, 10, Representation.INT, this);

    public Spammer() {
        super("Spammer", false);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            if (this.timer.hasTimeElapsed((long) (this.delay.getValue() * 1000.0F))) {
                this.timer.reset();
                String text = this.text.getValue();
                if (this.random.getValue() > 0) {
                    text = String.format("%s ", text);
                    for (int i = 0; i < this.random.getValue(); i++) {
                        text = String.format("%s%s", text, (char) this.charOffset);
                        this.charOffset++;
                        if (this.charOffset > 40959) {
                            this.charOffset = 19968;
                        }
                    }
                }
                mc.thePlayer.sendChatMessage(text);
            }
        }
    }
}
