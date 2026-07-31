package leader.client.module.values.impl;

import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CompositeValue extends Value<Void> implements ValueHolder {
    private final List<Value<?>> children = new ArrayList<>();

    public CompositeValue(String name, ValueHolder parent) {
        this(name, parent, () -> true);
    }

    public CompositeValue(String name, ValueHolder parent, Supplier<Boolean> visible) {
        super(name, null, parent, visible);
    }

    @Override
    public List<Value<?>> getValues() {
        return children;
    }

    public CompositeValue add(Value<?>... values) {
        for (Value<?> value : values) {
            if (value == null) continue;
            children.add(value);
            if (this.parent != null && value.getParent() == this.parent) {
                List<Value<?>> parentValues = this.parent.getValues();
                if (parentValues != null) parentValues.remove(value);
            }
            value.setParent(this);
        }
        return this;
    }
}
