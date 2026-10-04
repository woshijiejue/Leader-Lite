package leader.util.shader;

import leader.util.RenderUtil;
import leader.module.modules.render.BetterFPS;
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
    private static long lastRefresh;
    private static Object lastWorld;
    private static Object lastScreen;
    private static int lastOffset;
    private static int currentDivisor = 1;
    private static int sourceWidth, sourceHeight;
    private static final List<Framebuffer> framebufferList = new ArrayList<>();

    private static void initFramebuffers(float iterations) {
        for (Framebuffer fb : framebufferList) {
            if (fb != null) {
                fb.deleteFramebuffer();
            }
        }
        framebufferList.clear();
        Minecraft mc = Minecraft.getMinecraft();
        sourceWidth = mc.displayWidth; sourceHeight = mc.displayHeight;
        currentDivisor = BetterFPS.blurResolutionDivisor();
        framebuffer = new Framebuffer(Math.max(1, mc.displayWidth / currentDivisor),
                Math.max(1, mc.displayHeight / currentDivisor), false);
        framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        framebufferList.add(framebuffer);
        for (int i = 1; i <= iterations; i++) {
            Framebuffer currentBuffer = new Framebuffer(Math.max(1, (int) (framebuffer.framebufferWidth / Math.pow(2, i))),
                    Math.max(1, (int) (framebuffer.framebufferHeight / Math.pow(2, i))), false);
            currentBuffer.setFramebufferFilter(GL11.GL_LINEAR);
            int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager.bindTexture(currentBuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GlStateManager.bindTexture(prevTexture);
            framebufferList.add(currentBuffer);
        }
    }

    public static void renderBlur(int stencilFrameBufferTexture, int iterations, int offset) {
        Minecraft mc = Minecraft.getMinecraft();
        if (iterations < 1 || mc.displayWidth < 1 || mc.displayHeight < 1
                || !kawaseDown.isUsable() || !kawaseUp.isUsable()) {
            return;
        }
        boolean resized = currentIterations != iterations || sourceWidth != mc.displayWidth || sourceHeight != mc.displayHeight
                || currentDivisor != BetterFPS.blurResolutionDivisor();
        if (resized) {
            initFramebuffers(iterations);
            currentIterations = iterations;
        }
        int refreshRate = BetterFPS.blurRefreshRate();
        long now = System.nanoTime();
        boolean refresh = resized || refreshRate == 0 || lastRefresh == 0 || now - lastRefresh >= 1000000000L / refreshRate
                || lastWorld != mc.theWorld || lastScreen != mc.currentScreen || lastOffset != offset;
        if (refresh) {
            // Cache only the blurred scene, not its alpha mask. Current HUD shapes are composited every frame.
            renderFBO(framebufferList.get(1), mc.getFramebuffer().framebufferTexture, kawaseDown, offset);
            for (int i = 1; i < iterations; i++) {
                renderFBO(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, kawaseDown, offset);
            }
            for (int i = iterations; i > 1; i--) {
                renderFBO(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, kawaseUp, offset);
            }
            lastRefresh = now; lastWorld = mc.theWorld; lastScreen = mc.currentScreen; lastOffset = offset;
        }
        Framebuffer lastBuffer = framebufferList.get(0);
        lastBuffer.framebufferClear();
        lastBuffer.bindFramebuffer(true);
        GL20.glUseProgram(kawaseUp.programId);
        kawaseUp.setOffset(offset, offset);
        kawaseUp.setInTexture(0);
        kawaseUp.setCheck(1);
        kawaseUp.setTextureToCheck(1);
        kawaseUp.setHalfPixel(1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
        kawaseUp.setResolution(lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int previousTextureUnit1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
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
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.resetColor();
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        RenderUtil.bindTexture(previousTextureUnit1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void renderFBO(Framebuffer framebuffer, int framebufferTexture, KawaseDownShader shader, float offset) {
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(true);
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
        framebuffer.bindFramebuffer(true);
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
        // 确保纹理已启用
        GlStateManager.enableTexture2D();
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
