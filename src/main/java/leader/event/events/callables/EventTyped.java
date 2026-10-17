package leader.event.events.callables;

import leader.event.events.Event;
import leader.event.events.Typed;

public abstract class EventTyped implements Event, Typed {
    private final byte type;

    protected EventTyped(byte eventType) {
        type = eventType;
    }

    @Override
    public byte getType() {
        return type;
    }
}
