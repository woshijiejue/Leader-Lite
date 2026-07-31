package leader.client.component.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import leader.client.Leader;
import leader.client.config.Config;
import leader.client.util.InstanceAccess;
import lombok.Getter;

import java.awt.Color;
import java.util.ArrayList;

@Getter
public class PlayerComponent extends Config implements InstanceAccess {
    public ArrayList<String> players;
    public Color color;

    public PlayerComponent(String name, Color color) {
        super(name);
        this.players = new ArrayList<>();
        this.color = color;
    }

    @Override
    public void loadConfig(JsonObject object) {
        if (object.has("players")) {
            players.clear();
            JsonArray array = object.getAsJsonArray("players");
            for (JsonElement element : array) {
                players.add(element.getAsString());
            }
        }
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        for (String player : players) {
            array.add(new JsonPrimitive(player));
        }
        object.add("players", array);
        return object;
    }

    public void save() {
        if (Leader.configManager != null) {
            Leader.configManager.saveConfig(this);
        }
    }

    public String add(String name) {
        if (isFriend(name)) {
            return null;
        }
        players.add(name);
        save();
        return name;
    }

    public String remove(String name) {
        for (String player : players) {
            if (player.equalsIgnoreCase(name)) {
                players.remove(player);
                save();
                return player;
            }
        }
        return null;
    }

    public void clear() {
        players.clear();
        save();
    }

    public boolean isFriend(String string) {
        return this.players.stream().anyMatch(string2 -> string2.equalsIgnoreCase(string));
    }
}