package leader.client.ui.alt.elixir.account;

//import org.union4dev.base.util.ClientUtils;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.ResourceLocation;
import leader.client.ui.alt.elixir.compat.Session;
import leader.client.ui.alt.elixir.exception.LoginException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public abstract class MinecraftAccount {
    private final String type;
    @Setter
    @Getter
    private ResourceLocation headResource;
    @Getter
    private BufferedImage headImage;
    private static final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    public MinecraftAccount(String type) {
        this.type = type;
    }

    protected void loadHeadResource(final String name) {
        if (getHeadResource() == null) {
            threadPool.execute(() -> {
                try {
                    headImage = ImageIO.read(new URL(String.format("https://minotar.net/avatar/%s", name)));
                } catch (IOException e) {
                    // lientUtils.LOGGER.error(e);
                }
            });
        }
    }

    public final String getType() {
        return this.type;
    }

    public abstract String getName();

    public abstract void setName(final String name);

    public abstract Session getSession();

    public abstract void update() throws LoginException, IOException;

    public abstract void toRawYML(final Map<String, String> data);

    public abstract void fromRawYML(final Map<String, String> data);
}

