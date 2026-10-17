package leader.util;

import net.minecraft.client.Minecraft;

/** World-scoped skip-tick access through the local mixin bridge. */
public final class WorldTickUtil {
    private WorldTickUtil() { }

    public static int getSkipTicks() {
        Object world = Minecraft.getMinecraft().theWorld;
        return world instanceof ISkipTickWorld ? ((ISkipTickWorld) world).leader$getSkipTicks() : 0;
    }

    public static void setSkipTicks(int ticks) {
        Object world = Minecraft.getMinecraft().theWorld;
        if (world instanceof ISkipTickWorld) ((ISkipTickWorld) world).leader$setSkipTicks(ticks);
    }

    public static void clearSkipTicks() {
        setSkipTicks(0);
    }
}
