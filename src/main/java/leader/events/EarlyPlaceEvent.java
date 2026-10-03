package leader.events;

import leader.event.events.Event;

public class EarlyPlaceEvent implements Event {
    private float yaw;
    private float pitch;
    private boolean placed;

    public EarlyPlaceEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.placed = false;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void markPlaced() {
        this.placed = true;
    }

    public boolean isPlaced() {
        return this.placed;
    }
}
