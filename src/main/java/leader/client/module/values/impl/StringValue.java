package leader.client.module.values.impl;

import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.util.function.Supplier;

public class StringValue extends Value<String> {

    public StringValue(String name, String text, ValueHolder module) {
        this(name, text, () -> true, module);
    }

    public StringValue(String name, String text, Supplier<Boolean> visible, ValueHolder module) {
        super(name, text, module, visible);
    }
}
