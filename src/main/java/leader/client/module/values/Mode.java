package leader.client.module.values;

import leader.client.event.EventManager;
import leader.client.util.InstanceAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * A named sub-mode that owns its own values and can register/unregister itself
 * as an event listener. Adapted from Sayori to Leader-Lite's static
 * {@link EventManager} and no-i18n stack ({@code internalName} is the name).
 */
public class Mode<T> implements InstanceAccess, ValueHolder {
    private final String internalName;
    private final T parent;
    private final List<Value<?>> values;

    public Mode(String internalName, T parent) {
        this.internalName = internalName;
        this.parent = parent;
        this.values = new ArrayList<>();
    }

    public String getInternalName() {
        return internalName;
    }

    public String getName() {
        return internalName;
    }

    public T getParent() {
        return parent;
    }

    @Override
    public List<Value<?>> getValues() {
        return values;
    }

    public void register() {
        EventManager.register(this);
        onEnable();
    }

    public void unregister() {
        EventManager.unregister(this);
        onDisable();
    }

    public void onEnable() {
    }

    public void onDisable() {
    }
}
