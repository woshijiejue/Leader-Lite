package leader.client.component.impl.floater;

import leader.client.event.EventTarget;
import leader.client.events.PlayerUpdateEvent;
import leader.client.util.InstanceAccess;

import java.util.LinkedHashMap;

public class FloatComponent implements InstanceAccess {
    private final LinkedHashMap<FloatType, Boolean> activeMap;
    private boolean floating;

    public FloatComponent() {
        this.activeMap = new LinkedHashMap<>();
        this.floating = false;
    }

    public boolean isPredicted() {
        return this.floating;
    }

    public boolean isFalling() {
        return mc.thePlayer.onGround && mc.thePlayer.posY - mc.thePlayer.lastTickPosY < 0.0 && mc.thePlayer.motionY < 0.0;
    }

    public boolean hasActiveModule() {
        return this.activeMap.containsValue(true);
    }

    public void setFloatState(boolean state, FloatType floatType) {
        this.activeMap.put(floatType, state);
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if ((this.hasActiveModule() || this.isPredicted()) && this.isFalling()) {
            mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.001, mc.thePlayer.posZ);
            this.floating = true;
        } else {
            this.floating = false;
        }
    }
}
