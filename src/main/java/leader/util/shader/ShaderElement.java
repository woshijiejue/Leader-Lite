package leader.util.shader;

import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

public class ShaderElement {
    private static final ArrayList<Runnable> tasks = new ArrayList<>();
    private static final ArrayList<Runnable> bloomTasks = new ArrayList<>();

    public static ArrayList<Runnable> getTasks() {
        return tasks;
    }

    public static void addBlurTask(Runnable context) {
        if (leader.Leader.moduleManager == null) return;
        leader.module.modules.render.Shaders shaders = (leader.module.modules.render.Shaders)
                leader.Leader.moduleManager.modules.get(leader.module.modules.render.Shaders.class);
        // Avoid retaining hundreds of masks that will just be thrown away next frame.
        if (shaders == null || !shaders.isEnabled() || !(shaders.blur.getValue() || shaders.shadow.getValue() || shaders.bloom.getValue())) return;
        tasks.add(context);
    }

    public static ArrayList<Runnable> getBloomTasks() {
        return bloomTasks;
    }

    public static void addBloomTask(Runnable context) {
        bloomTasks.add(context);
    }

    public static void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer) {
        if (framebuffer == null || framebuffer.framebufferWidth != Minecraft.getMinecraft().displayWidth || framebuffer.framebufferHeight != Minecraft.getMinecraft().displayHeight) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            return new Framebuffer(Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight, true);
        }
        return framebuffer;
    }

    public static void blurArea(double x, double y, double v, double v1) {
        addBlurTask(() -> RenderUtil.drawRect((float) x, (float) y, (float) v, (float) v1, -1));
    }
}
