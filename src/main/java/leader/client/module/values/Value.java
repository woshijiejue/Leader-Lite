package leader.client.module.values;

import java.util.List;
import java.util.function.Supplier;

/**
 * Base setting value. Adapted from the Sayori Value system to the Leader-Lite
 * (Java 8 / no i18n) stack: {@code internalName} doubles as the display name and
 * there is no {@code Localizable}. Value change notifications go through
 * {@link #onChanged(Runnable)} instead of the old {@code Module.verifyValue}.
 */
public class Value<T> {
    private final String internalName;
    private String displayName;

    protected T value;
    protected ValueHolder parent;
    private Supplier<Boolean> visible;
    private Runnable onChanged;

    public Value(String internalName, T value, ValueHolder parent) {
        this(internalName, value, parent, () -> true);
    }

    public Value(String internalName, T value, ValueHolder parent, Supplier<Boolean> visible) {
        this.internalName = internalName;
        this.displayName = internalName;
        this.value = value;
        this.visible = visible;
        this.parent = parent;

        if (parent != null) {
            List<Value<?>> parentValues = parent.getValues();
            if (parentValues != null && !parentValues.contains(this)) {
                parentValues.add(this);
            }
        }
    }

    public String getInternalName() {
        return internalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Sets the user-facing display name and returns this for chaining. */
    public Value<T> displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
        if (this.onChanged != null) {
            this.onChanged.run();
        }
    }

    public ValueHolder getParent() {
        return parent;
    }

    public void setParent(ValueHolder parent) {
        this.parent = parent;
    }

    public Supplier<Boolean> getVisible() {
        return visible;
    }

    public void setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
    }

    public Boolean canDisplay() {
        return this.visible != null && this.visible.get();
    }

    /**
     * Registers a callback fired after every successful {@link #setValue}.
     * Replaces the old module-level {@code verifyValue(String)} hook.
     */
    public Value<T> onChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }
}
