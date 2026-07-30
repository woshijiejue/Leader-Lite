package leader.client.util.render.shader;

import leader.client.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

public class KawaseBlur {

    public static KawaseDownShader kawaseDown = new KawaseDownShader();
    public static KawaseUpShader kawaseUp = new KawaseUpShader();

    public static Framebuffer framebuffer = new Framebuffer(1, 1, false);

    private static int currentIterations;
    private static final List<Framebuffer> framebufferList = new ArrayList<>();

    private static void initFramebuffers(float iterations) {
        for (Framebuffer fb : framebufferList) {
            fb.deleteFramebuffer();
        }
        framebufferList.clear();
        framebufferList.add(framebuffer = ShaderElement.createFrameBuffer(null));
        Minecraft mc = Minecraft.getMinecraft();
        for (int i = 1; i <= iterations; i++) {
            Framebuffer currentBuffer = new Framebuffer((int) (mc.displayWidth / Math.pow(2, i)), (int) (mc.displayHeight / Math.pow(2, i)), false);
            currentBuffer.setFramebufferFilter(GL11.GL_LINEAR);
            GlStateManager.bindTexture(currentBuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GlStateManager.bindTexture(0);
            framebufferList.add(currentBuffer);
        }
    }

    public static void renderBlur(int stencilFrameBufferTexture, int iterations, int offset) {
        Minecraft mc = Minecraft.getMinecraft();
        if (currentIterations != iterations || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            initFramebuffers(iterations);
            currentIterations = iterations;
        }
        renderFBO(framebufferList.get(1), mc.getFramebuffer().framebufferTexture, kawaseDown, offset);
        for (int i = 1; i < iterations; i++) {
            renderFBO(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, kawaseDown, offset);
        }
        for (int i = iterations; i > 1; i--) {
            renderFBO(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, kawaseUp, offset);
        }
        Framebuffer lastBuffer = framebufferList.get(0);
        lastBuffer.framebufferClear();
        lastBuffer.bindFramebuffer(false);
        GL20.glUseProgram(kawaseUp.programId);
        kawaseUp.setOffset(offset, offset);
        kawaseUp.setInTexture(0);
        kawaseUp.setCheck(1);
        kawaseUp.setTextureToCheck(16);
        kawaseUp.setHalfPixel(1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
        kawaseUp.setResolution(lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
        GL13.glActiveTexture(GL13.GL_TEXTURE16);
        RenderUtil.bindTexture(stencilFrameBufferTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(framebufferList.get(1).framebufferTexture);
        drawQuads();
        GL20.glUseProgram(0);
        mc.getFramebuffer().bindFramebuffer(true);
        RenderUtil.bindTexture(framebufferList.get(0).framebufferTexture);
        RenderUtil.setAlphaLimit(0);
        enableBlend();
        drawQuads();
        GlStateManager.bindTexture(0);
    }

    private static void renderFBO(Framebuffer framebuffer, int framebufferTexture, KawaseDownShader shader, float offset) {
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(false);
        GL20.glUseProgram(shader.programId);
        RenderUtil.bindTexture(framebufferTexture);
        shader.setOffset(offset, offset);
        shader.setInTexture(0);
        shader.setHalfPixel(1.0f / framebuffer.framebufferWidth, 1.0f / framebuffer.framebufferHeight);
        shader.setResolution(framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        drawQuads();
        GL20.glUseProgram(0);
    }

    private static void renderFBO(Framebuffer framebuffer, int framebufferTexture, KawaseUpShader shader, float offset) {
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(false);
        GL20.glUseProgram(shader.programId);
        RenderUtil.bindTexture(framebufferTexture);
        shader.setOffset(offset, offset);
        shader.setInTexture(0);
        shader.setCheck(0);
        shader.setHalfPixel(1.0f / framebuffer.framebufferWidth, 1.0f / framebuffer.framebufferHeight);
        shader.setResolution(framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        drawQuads();
        GL20.glUseProgram(0);
    }

    private static void drawQuads() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        float width = (float) sr.getScaledWidth_double();
        float height = (float) sr.getScaledHeight_double();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(0, height);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(width, height);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(width, 0);
        GL11.glEnd();
    }

    private static void enableBlend() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
