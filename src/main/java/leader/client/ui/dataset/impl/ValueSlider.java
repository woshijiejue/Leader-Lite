package leader.client.ui.dataset.impl;

import leader.client.module.values.Representation;
import leader.client.module.values.ValueFormat;
import leader.client.module.values.impl.SliderValue;
import leader.client.ui.dataset.Slider;
import leader.client.util.misc.ChatColors;

/**
 * Adapts a Sayori-style {@link SliderValue} to the existing GUI {@link Slider}
 * abstraction, so the shared {@code SliderComponent} renders it unchanged.
 */
public class ValueSlider extends Slider {
    private final SliderValue value;

    public ValueSlider(SliderValue value) {
        this.value = value;
    }

    private boolean isInteger() {
        return value.getRepresentation() == Representation.INT
                || value.getRepresentation() == Representation.MILLISECONDS;
    }

    @Override
    public double getInput() {
        return value.getValue();
    }

    @Override
    public double getMin() {
        return value.getMin();
    }

    @Override
    public double getMax() {
        return value.getMax();
    }

    @Override
    public void setValue(double v) {
        value.setValue((float) v);
    }

    @Override
    public void setValueString(String string) {
        try {
            value.setValue(Float.parseFloat(string));
        } catch (Exception ignore) {
        }
    }

    @Override
    public String getName() {
        return value.getDisplayName().replace("-", " ");
    }

    @Override
    public String getValueString() {
        return isInteger()
                ? String.valueOf((int) (float) value.getValue())
                : value.getValue().toString();
    }

    @Override
    public String getValueColorString() {
        return ChatColors.formatColor(ValueFormat.format(value));
    }

    @Override
    public double getIncrement() {
        return value.getInc();
    }

    @Override
    public boolean isVisible() {
        return value.canDisplay();
    }

    @Override
    public void stepping(boolean increment) {
        float inc = value.getInc() <= 0 ? (isInteger() ? 1f : 0.1f) : value.getInc();
        if (increment) {
            if (value.getValue() >= value.getMax()) return;
            value.setValue(value.getValue() + inc);
        } else {
            if (value.getValue() <= value.getMin()) return;
            value.setValue(value.getValue() - inc);
        }
    }
}
