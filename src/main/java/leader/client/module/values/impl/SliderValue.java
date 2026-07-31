package leader.client.module.values.impl;

import leader.client.module.values.Representation;
import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.util.function.Supplier;

public class SliderValue extends Value<Float> {
    private final float min, max, inc;
    private final Representation representation;

    public SliderValue(String name, double value, double min, double max, Representation representation, ValueHolder parent) {
        this(name, value, min, max, 1, () -> true, representation, parent);
    }

    public SliderValue(String name, double value, double min, double max, double inc, Representation representation, ValueHolder parent) {
        this(name, value, min, max, inc, () -> true, representation, parent);
    }

    public SliderValue(String name, double value, double min, double max, Supplier<Boolean> visible, Representation representation, ValueHolder parent) {
        this(name, value, min, max, 1, visible, representation, parent);
    }

    public SliderValue(String name, double value, double min, double max, double inc, Supplier<Boolean> visible, Representation representation, ValueHolder parent) {
        super(name, (float) value, parent, visible);
        this.min = (float) min;
        this.max = (float) max;
        this.inc = (float) inc;
        this.representation = representation;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    public float getInc() {
        return inc;
    }

    public Representation getRepresentation() {
        return representation;
    }

    @Override
    public Float getValue() {
        return round(Math.min(Math.max(this.value, min), max));
    }

    @Override
    public void setValue(Float value) {
        // store the clamped/rounded value and fire onChanged through the base class
        super.setValue(round(Math.min(Math.max(value, min), max)));
    }

    private float round(float val) {
        switch (representation) {
            case INT:
            case MILLISECONDS:
                return Math.round(val);
            case DOUBLE:
            case DECIMAL:
                return Math.round(val * 100f) / 100f;
            default:
                return Math.round(val * 10f) / 10f;
        }
    }
}
