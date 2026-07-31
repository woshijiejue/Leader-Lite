package leader.client.module.values.impl;

import leader.client.module.Module;
import leader.client.module.values.Mode;
import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * A single-choice list of string modes, optionally backed by {@link Mode} objects
 * that register/unregister as event listeners when selected. Adapted from Sayori
 * to Leader-Lite (Java 8, no i18n, no Widget).
 */
public class ListValue extends Value<String> {
    private final List<String> modes = new ArrayList<>();
    private final List<Mode<?>> modeObjects = new ArrayList<>();

    public ListValue(String name, String[] modes, String current, ValueHolder module) {
        this(name, modes, current, () -> true, module);
    }

    public ListValue(String name, String[] modes, String current, Supplier<Boolean> visible, ValueHolder module) {
        super(name, current, module, visible);
        this.modes.addAll(Arrays.asList(modes));
    }

    public List<String> getModes() {
        return modes;
    }

    public List<Mode<?>> getModeObjects() {
        return modeObjects;
    }

    public ListValue add(Mode<?>... modes) {
        for (Mode<?> mode : modes) {
            if (mode == null) continue;
            this.modeObjects.add(mode);
            Object parent = mode.getParent();
            if (parent instanceof ValueHolder) {
                ValueHolder parentHolder = (ValueHolder) parent;
                this.modes.add(mode.getInternalName());
                List<Value<?>> parentValues = parentHolder.getValues();
                List<Value<?>> modeValues = mode.getValues();
                if (parentValues != null && modeValues != null) {
                    for (Value<?> modeValue : modeValues) {
                        if (!parentValues.contains(modeValue)) {
                            modeValue.setParent(parentHolder);
                            modeValue.setVisible(() -> this.is(mode.getInternalName()));
                            parentValues.add(modeValue);
                        }
                    }
                }
            }
        }
        return this;
    }

    public ListValue setDefault(String modeName) {
        for (Mode<?> mode : modeObjects) {
            if (modeName.equals(mode.getInternalName())) {
                setValue(mode.getInternalName());
                return this;
            }
        }
        if (modes.contains(modeName)) {
            setValue(modeName);
        }
        return this;
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);

        boolean shouldRegister = false;
        if (this.getParent() instanceof Module) {
            shouldRegister = ((Module) this.getParent()).isEnabled();
        }

        if (shouldRegister && !modeObjects.isEmpty()) {
            for (Mode<?> mode : modeObjects) {
                mode.unregister();
            }
            for (Mode<?> mode : modeObjects) {
                if (mode.getInternalName().equals(value)) {
                    mode.register();
                    break;
                }
            }
        }
    }

    public boolean is(String mode) {
        return getValue() != null && getValue().equalsIgnoreCase(mode);
    }

    public int getIndex() {
        return modes.indexOf(getValue());
    }

    public Mode<?> getMode() {
        for (Mode<?> mode : modeObjects) {
            if (mode.getInternalName().equals(getValue())) {
                return mode;
            }
        }
        return null;
    }

    public void nextMode() {
        if (modes.isEmpty()) return;
        int next = getIndex() + 1;
        if (next >= modes.size() || next < 0) {
            next = 0;
        }
        setValue(modes.get(next));
    }

    public void previousMode() {
        if (modes.isEmpty()) return;
        int prev = getIndex() - 1;
        if (prev < 0) {
            prev = modes.size() - 1;
        }
        setValue(modes.get(prev));
    }
}
