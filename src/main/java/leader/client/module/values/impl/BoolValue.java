package leader.client.module.values.impl;

import leader.client.module.Module;
import leader.client.module.values.Mode;
import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.util.List;
import java.util.function.Supplier;

public class BoolValue extends Value<Boolean> {
    private final Mode<?> mode;

    public BoolValue(String name, boolean value, ValueHolder module) {
        this(name, value, module, null);
    }

    public BoolValue(String name, boolean value, Supplier<Boolean> visible, ValueHolder module) {
        this(name, value, module, visible, null);
    }

    public BoolValue(String name, boolean value, ValueHolder module, Mode<?> mode) {
        this(name, value, module, () -> true, mode);
    }

    public BoolValue(String name, boolean value, ValueHolder module, Supplier<Boolean> visible, Mode<?> mode) {
        super(name, value, module, visible);
        this.mode = mode;

        if (mode != null) {
            List<Value<?>> modeValues = mode.getValues();
            if (modeValues != null) {
                for (Value<?> v : modeValues) {
                    v.setVisible(this::getValue);
                }
            }
        }
    }

    public Mode<?> getMode() {
        return mode;
    }

    @Override
    public void setValue(final Boolean value) {
        super.setValue(value);

        if (this.mode != null && this.getParent() instanceof Module) {
            Module module = (Module) this.getParent();
            if (module.isEnabled()) {
                if (this.getValue()) {
                    this.mode.register();
                } else {
                    this.mode.unregister();
                }
            }
        }
    }
}
