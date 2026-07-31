package leader.client.module.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.BoundsNumberValue;
import leader.client.module.values.impl.ColorValue;
import leader.client.module.values.impl.CompositeValue;
import leader.client.module.values.impl.CurveValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.MultiBoolValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.StringValue;

import java.awt.Color;

/**
 * JSON (de)serialization for the Value system. Serialization lives here in the
 * config layer rather than inside each Value class (mirrors the old Property
 * read/write but decoupled from the value definitions).
 */
public final class ValueSerializer {
    private ValueSerializer() {
    }

    public static void write(JsonObject object, Value<?> value) {
        String name = value.getInternalName();
        if (value instanceof BoolValue) {
            object.addProperty(name, ((BoolValue) value).getValue());
        } else if (value instanceof SliderValue) {
            object.addProperty(name, ((SliderValue) value).getValue());
        } else if (value instanceof BoundsNumberValue) {
            BoundsNumberValue b = (BoundsNumberValue) value;
            JsonObject bounds = new JsonObject();
            bounds.addProperty("value", b.getValue());
            bounds.addProperty("secondValue", b.getSecondValue());
            object.add(name, bounds);
        } else if (value instanceof ColorValue) {
            object.addProperty(name, String.format("%08X", ((ColorValue) value).getRGB()));
        } else if (value instanceof ListValue) {
            object.addProperty(name, ((ListValue) value).getValue());
        } else if (value instanceof StringValue) {
            object.addProperty(name, ((StringValue) value).getValue());
        } else if (value instanceof CurveValue) {
            CurveValue.Curve c = ((CurveValue) value).getValue();
            JsonObject curve = new JsonObject();
            curve.addProperty("initial", c.initial);
            curve.addProperty("h1X", c.h1X);
            curve.addProperty("h1Y", c.h1Y);
            curve.addProperty("h2X", c.h2X);
            curve.addProperty("h2Y", c.h2Y);
            curve.addProperty("finalStage", c.finalStage);
            curve.addProperty("maximum", c.maximum);
            object.add(name, curve);
        } else if (value instanceof MultiBoolValue) {
            JsonObject multi = new JsonObject();
            for (BoolValue option : ((MultiBoolValue) value).getValues()) {
                multi.addProperty(option.getInternalName(), option.getValue());
            }
            object.add(name, multi);
        } else if (value instanceof CompositeValue) {
            JsonObject composite = new JsonObject();
            for (Value<?> child : ((CompositeValue) value).getValues()) {
                write(composite, child);
            }
            object.add(name, composite);
        }
    }

    public static void read(JsonObject object, Value<?> value) {
        String name = value.getInternalName();
        if (!object.has(name)) {
            return;
        }
        JsonElement element = object.get(name);
        if (value instanceof BoolValue) {
            ((BoolValue) value).setValue(element.getAsBoolean());
        } else if (value instanceof SliderValue) {
            ((SliderValue) value).setValue(element.getAsFloat());
        } else if (value instanceof BoundsNumberValue) {
            if (element.isJsonObject()) {
                JsonObject bounds = element.getAsJsonObject();
                BoundsNumberValue b = (BoundsNumberValue) value;
                if (bounds.has("value")) b.setValue(bounds.get("value").getAsFloat());
                if (bounds.has("secondValue")) b.setSecondValue(bounds.get("secondValue").getAsFloat());
            }
        } else if (value instanceof ColorValue) {
            ((ColorValue) value).setValue(parseColor(element.getAsString()));
        } else if (value instanceof ListValue) {
            ((ListValue) value).setValue(element.getAsString());
        } else if (value instanceof StringValue) {
            ((StringValue) value).setValue(element.getAsString());
        } else if (value instanceof CurveValue) {
            if (element.isJsonObject()) {
                JsonObject curve = element.getAsJsonObject();
                CurveValue.Curve c = ((CurveValue) value).getValue();
                c.initial = getOr(curve, "initial", c.initial);
                c.h1X = getOr(curve, "h1X", c.h1X);
                c.h1Y = getOr(curve, "h1Y", c.h1Y);
                c.h2X = getOr(curve, "h2X", c.h2X);
                c.h2Y = getOr(curve, "h2Y", c.h2Y);
                c.finalStage = getOr(curve, "finalStage", c.finalStage);
                c.maximum = getOr(curve, "maximum", c.maximum);
            }
        } else if (value instanceof MultiBoolValue) {
            if (element.isJsonObject()) {
                JsonObject multi = element.getAsJsonObject();
                for (BoolValue option : ((MultiBoolValue) value).getValues()) {
                    if (multi.has(option.getInternalName())) {
                        option.setValue(multi.get(option.getInternalName()).getAsBoolean());
                    }
                }
            }
        } else if (value instanceof CompositeValue) {
            if (element.isJsonObject()) {
                JsonObject composite = element.getAsJsonObject();
                for (Value<?> child : ((CompositeValue) value).getValues()) {
                    read(composite, child);
                }
            }
        }
    }

    private static float getOr(JsonObject object, String key, float fallback) {
        return object.has(key) ? object.get(key).getAsFloat() : fallback;
    }

    /** Parses "RRGGBB" or "AARRGGBB" hex into a Color (matches the old ColorProperty). */
    private static Color parseColor(String string) {
        String hex = string.replace("#", "").trim();
        if (hex.length() == 6) {
            hex = "FF" + hex;
        }
        int argb = (int) Long.parseLong(hex, 16);
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return new Color(r, g, b, a);
    }
}
