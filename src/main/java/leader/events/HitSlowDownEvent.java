package leader.events;

import leader.event.events.Event;

public class HitSlowDownEvent implements Event {
    private double slowDown;
    private boolean sprint;

    public HitSlowDownEvent() {
        this.slowDown = 0.6;
        this.sprint = false;
    }

    public double getSlowDown() {
        return this.slowDown;
    }

    public void setSlowDown(double slowDown) {
        this.slowDown = slowDown;
    }

    public boolean getSprint() {
        return this.sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }
}
