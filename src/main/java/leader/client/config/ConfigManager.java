package leader.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leader.client.Leader;
import leader.client.config.impl.*;
import leader.client.util.DebugUtil;
import leader.mixin.accessor.IAccessorMinecraft;
import net.minecraft.client.Minecraft;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {

    private final ModuleConfig defaultSettings = new ModuleConfig("default");
    private final AccountConfig account = new AccountConfig("account");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager() {
        loadConfigs();
    }

    public void loadConfig(Config config) {
        if (config == null) return;

        if (!config.getFile().exists()) {
            saveConfig(config);
            return;
        }

        try (FileReader reader = new FileReader(config.getFile())) {
            JsonObject jsonObject = new JsonParser().parse(reader).getAsJsonObject();
            config.loadConfig(jsonObject);
        } catch (Exception e) {
            ((IAccessorMinecraft) Minecraft.getMinecraft()).getLogger().error("Failed to load config: {}", e.getMessage());
            DebugUtil.sendFormatted(String.format("%sConfig couldn't be loaded (&c&o%s&r)&r", Leader.clientName, config.getName()));
        }
    }

    public void saveConfig(Config config) {
        if (config == null) return;

        if (!config.getFile().getParentFile().exists()) {
            config.getFile().getParentFile().mkdirs();
        }

        JsonObject jsonObject = config.saveConfig();

        try (FileWriter writer = new FileWriter(config.getFile())) {
            writer.write(gson.toJson(jsonObject));
        } catch (IOException e) {
            ((IAccessorMinecraft) Minecraft.getMinecraft()).getLogger().error("Failed to save config: {}", e.getMessage());
            DebugUtil.sendFormatted(String.format("%sConfig couldn't be saved (&c&o%s&r)&r", Leader.clientName, config.getName()));
        }
    }

    public void loadConfigs() {
        loadConfig(defaultSettings);
        loadConfig(account);
        if (Leader.friendComponent != null) loadConfig(Leader.friendComponent);
        if (Leader.targetComponent != null) loadConfig(Leader.targetComponent);
    }

    public void saveConfigs() {
        saveConfig(defaultSettings);
        saveConfig(account);
        if (Leader.friendComponent != null) saveConfig(Leader.friendComponent);
        if (Leader.targetComponent != null) saveConfig(Leader.targetComponent);
    }
}