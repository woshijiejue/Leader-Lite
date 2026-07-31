package leader.client.module.values.impl;

import leader.client.module.values.Value;
import leader.client.module.values.ValueHolder;

import java.awt.Color;
import java.util.function.Supplier;

public class ColorValue extends Value<Color> {
    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;

    public ColorValue(String name, Color value, ValueHolder module) {
        this(name, value, () -> true, module);
    }

    public ColorValue(String name, Color value, Supplier<Boolean> visible, ValueHolder module) {
        super(name, value, module, visible);
        float[] hsb = Color.RGBtoHSB(value.getRed(), value.getGreen(), value.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = value.getAlpha() / 255.0f;
    }

    public float getHue() {
        return hue;
    }

    public void setHue(float hue) {
        this.hue = hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    @Override
    public Color getValue() {
        Color hsbColor = Color.getHSBColor(hue, saturation, brightness);
        return new Color(
                hsbColor.getRed(),
                hsbColor.getGreen(),
                hsbColor.getBlue(),
                (int) (alpha * 255)
        );
    }

    /** Convenience: packed ARGB int, matching the old ColorProperty storage. */
    public int getRGB() {
        return getValue().getRGB();
    }

    @Override
    public void setValue(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = color.getAlpha() / 255.0f;
        super.setValue(getValue());
    }
}
