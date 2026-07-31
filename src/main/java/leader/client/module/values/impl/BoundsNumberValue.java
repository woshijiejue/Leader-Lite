package leader.client.module.values.impl;

import leader.client.module.values.Representation;
import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.util.function.Supplier;

public class BoundsNumberValue extends Value<Float> {
    private float secondValue;
    private final float min, max, inc;
    private final Representation representation;

    public BoundsNumberValue(String name, float value, float secondValue, float min, float max, float inc, Representation representation, ValueHolder parent) {
        this(name, value, secondValue, min, max, inc, () -> true, representation, parent);
    }

    public BoundsNumberValue(String name, float value, float secondValue, float min, float max, float inc, Supplier<Boolean> visible, Representation representation, ValueHolder parent) {
        super(name, value, parent, visible);
        this.secondValue = secondValue;
        this.min = min;
        this.max = max;
        this.inc = inc;
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
        return clamp(this.value);
    }

    public float getSecondValue() {
        return clamp(this.secondValue);
    }

    @Override
    public void setValue(Float value) {
        super.setValue(clamp(value));
    }

    public void setSecondValue(float secondValue) {
        this.secondValue = clamp(secondValue);
    }

    private float clamp(float val) {
        float clamped = Math.min(Math.max(val, min), max);
        switch (representation) {
            case INT:
            case MILLISECONDS:
                return Math.round(clamped);
            case DOUBLE:
            case DECIMAL:
                return Math.round(clamped * 100f) / 100f;
            default:
                return Math.round(clamped * 10f) / 10f;
        }
    }

    public float getRandomBetween() {
        float v1 = getValue();
        float v2 = getSecondValue();
        float minVal = Math.min(v1, v2);
        float maxVal = Math.max(v1, v2);
        return minVal + (float) (Math.random() * (maxVal - minVal));
    }
}
