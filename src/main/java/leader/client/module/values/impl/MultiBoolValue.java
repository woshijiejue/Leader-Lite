package leader.client.module.values.impl;

import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MultiBoolValue extends Value<List<BoolValue>> {
    public int index;

    public MultiBoolValue(String name, List<BoolValue> value, ValueHolder module) {
        super(name, new ArrayList<>(), module);
        this.value = value;
        this.index = value.size();
        this.removeDuplicateParentReferences();
    }

    public MultiBoolValue(String name, List<BoolValue> value, Supplier<Boolean> visible, ValueHolder module) {
        super(name, new ArrayList<>(), module, visible);
        this.value = value;
        this.index = value.size();
        this.removeDuplicateParentReferences();
    }

    private void removeDuplicateParentReferences() {
        if (this.parent != null) {
            List<Value<?>> parentValues = this.parent.getValues();
            if (parentValues != null) {
                for (BoolValue boolValue : this.value) {
                    parentValues.remove(boolValue);
                }
            }
        }
    }

    public List<BoolValue> getValues() {
        return this.value;
    }

    public boolean isEnabled(String name) {
        BoolValue foundValue = this.value.stream()
                .filter(option -> option.getInternalName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);

        return foundValue != null && foundValue.getValue();
    }

    public void setValue(String name, boolean value) {
        this.value.stream()
                .filter(option -> option.getInternalName().equalsIgnoreCase(name))
                .findFirst().ifPresent(foundValue -> foundValue.setValue(value));
    }

    public List<BoolValue> getToggled() {
        return this.value.stream().filter(BoolValue::getValue).collect(Collectors.toList());
    }

    public String isEnabled() {
        List<String> included = new ArrayList<>();
        for (BoolValue option : value) {
            if (option.getValue()) {
                included.add(option.getDisplayName());
            }
        }
        return String.join(", ", included);
    }
}
