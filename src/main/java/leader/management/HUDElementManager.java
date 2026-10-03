package leader.management;

import com.google.gson.*;
import leader.Leader;
import leader.module.modules.render.Island;
import leader.mixin.IAccessorMinecraft;
import net.minecraft.client.Minecraft;

import java.awt.Color;
import java.io.*;
import java.util.*;

public class HUDElementManager {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final File hudFile = new File("./config/Leader/", "hud_elements.json");
    private static final int DEFAULT_BACKGROUND = new Color(46, 48, 54, 150).getRGB();

    private final Map<String, HUDElement> elements = new LinkedHashMap<>();

    public static class HUDElement {
        public float x;
        public float y;
        public int backgroundColor;
        public String mergedInto;

        public HUDElement(float x, float y, int backgroundColor) {
            this.x = x;
            this.y = y;
            this.backgroundColor = backgroundColor;
            this.mergedInto = null;
        }
    }

    public HUDElementManager() {
        load();
    }

    public HUDElement get(String name) {
        return elements.get(name);
    }

    public HUDElement getOrCreate(String name, float defaultX, float defaultY) {
        HUDElement element = elements.get(name);
        if (element == null) {
            int background = name.equals("Island") ? new Color(20, 24, 34, 220).getRGB() : DEFAULT_BACKGROUND;
            element = new HUDElement(defaultX, defaultY, background);
            elements.put(name, element);
        }
        return element;
    }

    public float x(String name, float defaultX, float defaultY) {
        return getOrCreate(name, defaultX, defaultY).x;
    }

    public float y(String name, float defaultX, float defaultY) {
        return getOrCreate(name, defaultX, defaultY).y;
    }

    public int background(String name, float defaultX, float defaultY) {
        return getOrCreate(name, defaultX, defaultY).backgroundColor;
    }

    public int background(String name, float defaultX, float defaultY, float alphaScale) {
        int color = background(name, defaultX, defaultY);
        int alpha = Math.max(0, Math.min(255, Math.round((color >>> 24) * alphaScale)));
        return (color & 0xFFFFFF) | (alpha << 24);
    }

    public Color backgroundColor(String name, float defaultX, float defaultY) {
        return new Color(background(name, defaultX, defaultY), true);
    }

    public void set(String name, float x, float y) {
        HUDElement element = getOrCreate(name, x, y);
        element.x = x;
        element.y = y;
    }

    public void setBackground(String name, int color) {
        getOrCreate(name, 0.0F, 0.0F).backgroundColor = color;
    }

    public void merge(String name, String target) {
        getOrCreate(name, 0.0F, 0.0F).mergedInto = target;
    }

    public void unmerge(String name) {
        HUDElement element = elements.get(name);
        if (element != null) {
            element.mergedInto = null;
        }
    }

    public String mergedInto(String name) {
        HUDElement element = elements.get(name);
        return element != null ? element.mergedInto : null;
    }

    public boolean isSuppressed(String name) {
        String target = mergedInto(name);
        return "Island".equals(target) && !target.equals(name) && isIslandActive();
    }

    public boolean isIslandActive() {
        if (Leader.moduleManager == null) return false;
        Island island = (Island) Leader.moduleManager.modules.get(Island.class);
        return island != null && island.isEnabled();
    }

    public List<String> mergedModules(String target) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, HUDElement> entry : elements.entrySet()) {
            if (target.equals(entry.getValue().mergedInto)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public boolean isMergeable(String name) {
        return !name.equals("Island") && !name.equals("Potion") && !name.equals("Watermark") && !name.equals("HUD");
    }

    public Set<String> names() {
        return elements.keySet();
    }

    public void load() {
        if (!hudFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(hudFile))) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return;
            }

            JsonObject root = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonObject()) {
                    continue;
                }
                JsonObject obj = value.getAsJsonObject();
                float x = obj.has("x") ? obj.get("x").getAsFloat() : 0.0F;
                float y = obj.has("y") ? obj.get("y").getAsFloat() : 0.0F;
                int background = obj.has("backgroundColor") ? obj.get("backgroundColor").getAsInt() : DEFAULT_BACKGROUND;
                String mergedInto = obj.has("mergedInto") && !obj.get("mergedInto").isJsonNull() ? obj.get("mergedInto").getAsString() : null;

                HUDElement element = new HUDElement(x, y, background);
                element.mergedInto = mergedInto;
                elements.put(entry.getKey(), element);
            }
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error("Failed to load HUD elements: " + e.getMessage());
        }
    }

    public void save() {
        try {
            hudFile.getParentFile().mkdirs();

            JsonObject root = new JsonObject();
            for (Map.Entry<String, HUDElement> entry : elements.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("x", entry.getValue().x);
                obj.addProperty("y", entry.getValue().y);
                obj.addProperty("backgroundColor", entry.getValue().backgroundColor);
                if (entry.getValue().mergedInto != null) {
                    obj.addProperty("mergedInto", entry.getValue().mergedInto);
                } else {
                    obj.add("mergedInto", JsonNull.INSTANCE);
                }
                root.add(entry.getKey(), obj);
            }

            try (FileWriter writer = new FileWriter(hudFile)) {
                gson.toJson(root, writer);
            }
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error("Failed to save HUD elements: " + e.getMessage());
        }
    }
}
