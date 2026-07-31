package leader.client.config.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leader.client.Leader;
import leader.client.config.Config;
import leader.client.module.Module;
import leader.client.module.values.Value;
import leader.client.module.values.ValueSerializer;
import leader.mixin.accessor.IAccessorMinecraft;
import net.minecraft.client.Minecraft;

import java.util.List;

public class ModuleConfig extends Config {

    public ModuleConfig(String name) {
        super(name);
    }

    @Override
    public void loadConfig(JsonObject object) {
        for (Module module : Leader.moduleManager.modules.values()) {
            if (object.has(module.getName())) {
                JsonElement moduleElement = object.get(module.getName());
                
                if (moduleElement != null && moduleElement.isJsonObject()) {
                    JsonObject moduleObject = moduleElement.getAsJsonObject();

                    // Load standard module toggles
                    if (moduleObject.has("toggled")) {
                        module.setEnabled(moduleObject.get("toggled").getAsBoolean());
                    }
                    if (moduleObject.has("key")) {
                        module.setKey(moduleObject.get("key").getAsInt());
                    }
                    if (moduleObject.has("hidden")) {
                        module.setHidden(moduleObject.get("hidden").getAsBoolean());
                    }

                    // Load Sayori-style Values
                    List<Value<?>> valueList = module.getValues();
                    if (valueList != null) {
                        for (Value<?> value : valueList) {
                            try {
                                ValueSerializer.read(moduleObject, value);
                            } catch (Exception e) {
                                ((IAccessorMinecraft) Minecraft.getMinecraft()).getLogger().warn(
                                        String.format("Failed to load value %s for module %s", value.getInternalName(), module.getName()));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject object = new JsonObject();
        
        for (Module module : Leader.moduleManager.modules.values()) {
            JsonObject moduleObject = new JsonObject();
            
            // Save standard module toggles
            moduleObject.addProperty("toggled", module.isEnabled());
            moduleObject.addProperty("key", module.getKey());
            moduleObject.addProperty("hidden", module.isHidden());

            // Save Sayori-style Values
            List<Value<?>> valueList = module.getValues();
            if (valueList != null) {
                for (Value<?> value : valueList) {
                    try {
                        ValueSerializer.write(moduleObject, value);
                    } catch (Exception e) {
                        ((IAccessorMinecraft) Minecraft.getMinecraft()).getLogger().warn(
                                String.format("Failed to save value %s for module %s", value.getInternalName(), module.getName()));
                    }
                }
            }

            object.add(module.getName(), moduleObject);
        }
        
        return object;
    }
}