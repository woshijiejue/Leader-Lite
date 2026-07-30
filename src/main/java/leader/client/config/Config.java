package leader.client.config;

import com.google.gson.JsonObject;
import leader.client.Leader;
import lombok.Getter;

import java.io.File;

@Getter
public class Config {
    private final File file;
    private final String name;

    public Config(String name) {
        this.name = name;
        this.file = new File(Leader.mainDir, name + ".json");
    }

    public void loadConfig(JsonObject object){

    }

    public JsonObject saveConfig(){
        return null;
    }
}
