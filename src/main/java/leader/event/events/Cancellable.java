package leader.event.events;

public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean state);
}
