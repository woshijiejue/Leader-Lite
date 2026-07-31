package leader.client.module.values;

import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ColorValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.StringValue;

import java.awt.Color;
import java.util.List;

/**
 * Command-line glue for the Value system: value lookup, prompt strings and
 * string parsing. Mirrors the old Property.getValuePrompt()/parseString().
 */
public final class ValueCommands {
    private ValueCommands() {
    }

    public static Value<?> find(ValueHolder holder, String name) {
        List<Value<?>> values = holder.getValues();
        if (values == null) return null;
        String needle = name.replace("-", "");
        for (Value<?> value : values) {
            if (value.getInternalName().replace("-", "").equalsIgnoreCase(needle)) {
                return value;
            }
        }
        return null;
    }

    /** The accepted-input hint shown to the user (old getValuePrompt). */
    public static String prompt(Value<?> value) {
        if (value instanceof BoolValue) {
            return "true/false";
        }
        if (value instanceof SliderValue) {
            SliderValue slider = (SliderValue) value;
            return String.format("%s-%s", trim(slider.getMin()), trim(slider.getMax()));
        }
        if (value instanceof ListValue) {
            return String.join(", ", ((ListValue) value).getModes());
        }
        if (value instanceof ColorValue) {
            return "HEX";
        }
        if (value instanceof StringValue) {
            return "text";
        }
        return "";
    }

    /** Parses a user string into the value. A null string toggles BoolValue. Returns success. */
    public static boolean parse(Value<?> value, String string) {
        if (value instanceof BoolValue) {
            BoolValue bool = (BoolValue) value;
            if (string == null) {
                bool.setValue(!bool.getValue());
                return true;
            }
            if (string.equalsIgnoreCase("true") || string.equalsIgnoreCase("on") || string.equalsIgnoreCase("1")) {
                bool.setValue(true);
                return true;
            }
            if (string.equalsIgnoreCase("false") || string.equalsIgnoreCase("off") || string.equalsIgnoreCase("0")) {
                bool.setValue(false);
                return true;
            }
            return false;
        }
        if (string == null) {
            return false;
        }
        if (value instanceof SliderValue) {
            try {
                ((SliderValue) value).setValue(Float.parseFloat(string.replace("%", "")));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (value instanceof ListValue) {
            ListValue list = (ListValue) value;
            String needle = string.replace("_", "");
            for (String mode : list.getModes()) {
                if (needle.equalsIgnoreCase(mode.replace("_", ""))) {
                    list.setValue(mode);
                    return true;
                }
            }
            return false;
        }
        if (value instanceof ColorValue) {
            String hex = string.replace("#", "").trim();
            if (hex.length() == 6) {
                hex = "FF" + hex;
            }
            try {
                int argb = (int) Long.parseLong(hex, 16);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                ((ColorValue) value).setValue(new Color(r, g, b, a));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (value instanceof StringValue) {
            ((StringValue) value).setValue(string);
            return true;
        }
        return false;
    }

    private static String trim(float f) {
        if (f == Math.rint(f)) {
            return String.valueOf((int) f);
        }
        return String.valueOf(f);
    }
}
