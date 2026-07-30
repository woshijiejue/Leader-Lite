package leader.client.module.modules.movement;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.TickEvent;
import leader.mixin.IAccessorEntityLivingBase;
import leader.client.module.Module;
import leader.client.module.modules.player.Scaffold;
import leader.client.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

public class NoJumpDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty delay = new IntProperty("delay", 3, 0, 8);

    public NoJumpDelay() {
        super("NoJumpDelay", false);
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE && !Leader.moduleManager.getModule(Scaffold.class).isEnabled()) {
            ((IAccessorEntityLivingBase) mc.thePlayer)
                    .setJumpTicks(Math.min(((IAccessorEntityLivingBase) mc.thePlayer).getJumpTicks(), this.delay.getValue() + 1));
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.delay.getValue().toString()};
    }
}
