package leader.util.shader;

import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public class Shadow {

    public static ShadowShader shader = new ShadowShader();
    public static Framebuffer framebuffer = new Framebuffer(1, 1, false);
    private static float prevRadius = -1.0F;

    public static void renderShadow(int maskTexture, int radius, int offset) {
        Minecraft mc = Minecraft.getMinecraft();
        if (radius < 1 || mc.displayWidth < 1 || mc.displayHeight < 1 || !shader.isUsable()) {
            return;
        }
        framebuffer = ShaderElement.createFrameBuffer(framebuffer);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(true);
        GL20.glUseProgram(shader.programId);
        setupUniforms(radius, (float) offset, 0.0F);
        RenderUtil.bindTexture(maskTexture);
        drawQuads();
        GL20.glUseProgram(0);
        framebuffer.unbindFramebuffer();

        mc.getFramebuffer().bindFramebuffer(true);
        GL20.glUseProgram(shader.programId);
        setupUniforms(radius, 0.0F, (float) offset);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int prevUnit1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderUtil.bindTexture(maskTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(framebuffer.framebufferTexture);
        drawQuads();
        GL20.glUseProgram(0);

        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableAlpha();
        GlStateManager.resetColor();
        GlStateManager.bindTexture(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        RenderUtil.bindTexture(prevUnit1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void setupUniforms(int radius, float directionX, float directionY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (radius != prevRadius) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(256);
            for (int i = 0; i <= radius; i++) {
                buffer.put(calculateGaussianValue(i, radius));
            }
            buffer.rewind();
            shader.setWeights(buffer);
            prevRadius = radius;
        }
        shader.setInTexture(0);
        shader.setTextureToCheck(1);
        shader.setRadius((float) radius);
        shader.setTexelSize(1.0F / (float) mc.displayWidth, 1.0F / (float) mc.displayHeight);
        shader.setDirection(directionX, directionY);
    }

    private static float calculateGaussianValue(float x, float sigma) {
        double output = 1.0D / Math.sqrt(2.0D * Math.PI * (double) (sigma * sigma));
        return (float) (output * Math.exp(-(double) (x * x) / (2.0D * (double) (sigma * sigma))));
    }

    private static void drawQuads() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        float width = (float) sr.getScaledWidth_double();
        float height = (float) sr.getScaledHeight_double();
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
}
