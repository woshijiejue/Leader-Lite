package leader.client.module.values;

import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ColorValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.StringValue;

/**
 * Produces the legacy-style "&"-coded display strings for Values, matching the
 * old Property.formatValue() coloring so the GUI/commands look identical.
 */
public final class ValueFormat {
    private ValueFormat() {
    }

    public static String format(Value<?> value) {
        if (value instanceof BoolValue) {
            return ((BoolValue) value).getValue() ? "&atrue" : "&cfalse";
        }
        if (value instanceof SliderValue) {
            SliderValue slider = (SliderValue) value;
            switch (slider.getRepresentation()) {
                case INT:
                case MILLISECONDS:
                    return String.format("&e%d", (int) (float) slider.getValue());
                default:
                    return String.format("&6%s", slider.getValue());
            }
        }
        if (value instanceof ListValue) {
            String mode = ((ListValue) value).getValue();
            return mode == null || mode.isEmpty() ? "&4?" : String.format("&9%s", mode);
        }
        if (value instanceof ColorValue) {
            int rgb = ((ColorValue) value).getRGB();
            String hex = String.format("%06X", rgb & 0x00FFFFFF);
            return String.format("&c%s&a%s&9%s", hex.substring(0, 2), hex.substring(2, 4), hex.substring(4, 6));
        }
        if (value instanceof StringValue) {
            return String.format("&f%s", ((StringValue) value).getValue());
        }
        return String.valueOf(value.getValue());
    }
}
