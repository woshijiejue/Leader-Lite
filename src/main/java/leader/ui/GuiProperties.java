package leader.ui;

import leader.property.Property;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.PercentProperty;

import java.util.Locale;

public final class GuiProperties {

    private GuiProperties() {
    }

    public static String label(Property<?> property) {
        String name = property.getName().replace("-", " ").replace("_", " ");
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static float sliderRatio(Property<?> property) {
        double min;
        double max;
        double value;
        if (property instanceof FloatProperty) {
            FloatProperty p = (FloatProperty) property;
            min = p.getMinimum();
            max = p.getMaximum();
            value = p.getValue();
        } else if (property instanceof IntProperty) {
            IntProperty p = (IntProperty) property;
            min = p.getMinimum();
            max = p.getMaximum();
            value = p.getValue();
        } else {
            PercentProperty p = (PercentProperty) property;
            min = p.getMinimum();
            max = p.getMaximum();
            value = p.getValue();
        }
        return max - min <= 0.0D ? 0.0F : clamp((float) ((value - min) / (max - min)));
    }

    public static void setSliderRatio(Property<?> property, float ratio) {
        if (property instanceof FloatProperty) {
            FloatProperty p = (FloatProperty) property;
            float min = p.getMinimum();
            float max = p.getMaximum();
            float step = max - min <= 2.0F ? 0.01F : 0.1F;
            float v = min + (max - min) * ratio;
            v = Math.round(v / step) * step;
            v = Math.round(v * 100.0F) / 100.0F;
            p.setValue(Math.max(min, Math.min(max, v)));
        } else if (property instanceof IntProperty) {
            IntProperty p = (IntProperty) property;
            p.setValue((int) Math.round(p.getMinimum() + (p.getMaximum() - p.getMinimum()) * (double) ratio));
        } else if (property instanceof PercentProperty) {
            PercentProperty p = (PercentProperty) property;
            p.setValue((int) Math.round(p.getMinimum() + (p.getMaximum() - p.getMinimum()) * (double) ratio));
        }
    }

    public static void setSliderText(Property<?> property, String text) {
        try {
            if (property instanceof FloatProperty) {
                FloatProperty p = (FloatProperty) property;
                p.setValue(Math.max(p.getMinimum(), Math.min(p.getMaximum(), Float.parseFloat(text.trim()))));
            } else if (property instanceof IntProperty) {
                IntProperty p = (IntProperty) property;
                p.setValue(Math.max(p.getMinimum(), Math.min(p.getMaximum(), Integer.parseInt(text.trim()))));
            } else if (property instanceof PercentProperty) {
                PercentProperty p = (PercentProperty) property;
                p.setValue(Math.max(p.getMinimum(), Math.min(p.getMaximum(), Integer.parseInt(text.trim().replace("%", "")))));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public static String rawValue(Property<?> property) {
        return String.valueOf(property.getValue());
    }

    public static String sliderText(Property<?> property) {
        if (property instanceof FloatProperty) {
            String s = String.format(Locale.US, "%.2f", ((FloatProperty) property).getValue());
            if (s.contains(".")) {
                s = s.replaceAll("0+$", "");
                if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
            }
            return s;
        }
        if (property instanceof PercentProperty) return property.getValue() + "%";
        return String.valueOf(property.getValue());
    }

    public static boolean isSlider(Property<?> property) {
        return property instanceof FloatProperty || property instanceof IntProperty || property instanceof PercentProperty;
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
