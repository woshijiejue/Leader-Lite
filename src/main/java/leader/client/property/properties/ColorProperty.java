package leader.client.property.properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leader.client.property.Property;

import java.util.function.BooleanSupplier;

public class ColorProperty extends Property<Integer> {
    public ColorProperty(String name, Integer color) {
        this(name, color, null);
    }

    public ColorProperty(String string, Integer color, BooleanSupplier check) {
        super(string, color, null, check);
    }

    @Override
    public String getValuePrompt() {
        return "HEX";
    }

    @Override
    public String formatValue() {
        int val = this.getValue();
        String hex = String.format("%06X", val & 0x00FFFFFF);
        return String.format("&c%s&a%s&9%s", hex.substring(0, 2), hex.substring(2, 4), hex.substring(4, 6));
    }

    @Override
    public boolean parseString(String string) {
        String hex = string.replace("#", "").trim();
        if (hex.length() == 6) {
            hex = "FF" + hex;
        }
        try {
            return this.setValue((int) Long.parseLong(hex, 16));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        JsonElement element = jsonObject.get(this.getName());
        if (element == null || !element.isJsonPrimitive()) return false;
        String str = element.getAsString();
        return this.parseString(str);
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), String.format("%08X", this.getValue()));
    }
}
