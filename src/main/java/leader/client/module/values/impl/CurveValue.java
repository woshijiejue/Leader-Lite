package leader.client.module.values.impl;

import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.util.function.Supplier;

public class CurveValue extends Value<CurveValue.Curve> {

    public CurveValue(String name, float initial, float h1X, float h1Y, float h2X, float h2Y, float finalStage, float maximum, ValueHolder parent) {
        this(name, initial, h1X, h1Y, h2X, h2Y, finalStage, maximum, parent, () -> true);
    }

    public CurveValue(String name, float initial, float h1X, float h1Y, float h2X, float h2Y, float finalStage, float maximum, ValueHolder parent, Supplier<Boolean> visibility) {
        super(name, new Curve(initial, h1X, h1Y, h2X, h2Y, finalStage, maximum), parent, visibility);
    }

    public static class Curve {
        public float initial, h1X, h1Y, h2X, h2Y, finalStage, maximum;

        public Curve(float initial, float h1X, float h1Y, float h2X, float h2Y, float finalStage, float maximum) {
            this.initial = initial;
            this.h1X = h1X;
            this.h1Y = h1Y;
            this.h2X = h2X;
            this.h2Y = h2Y;
            this.finalStage = finalStage;
            this.maximum = maximum;
        }
    }
}
