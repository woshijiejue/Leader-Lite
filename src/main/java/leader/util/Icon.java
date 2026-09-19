package leader.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public enum Icon {
    CHECK("check-circle"),
    CLOSE("close-circle"),
    INFO("information"),
    CROWN("crown"),
    FLASK("flask"),
    SPEED("speedometer"),
    SLOW("snail"),
    HASTE("hammer"),
    FATIGUE("pickaxe"),
    STRENGTH("sword"),
    WEAKNESS("sword-cross"),
    HEAL("heart"),
    REGEN("heart-pulse"),
    HEALTH_BOOST("heart-plus"),
    HARM("heart-remove"),
    JUMP("arrow-up-bold"),
    RESIST("shield"),
    ABSORB("shield-plus"),
    FIRE("fire"),
    WATER("bubbles"),
    VISION("eye"),
    INVIS("eye-off"),
    BLIND("eye-remove"),
    HUNGER("food"),
    POISON("skull"),
    WITHER("skull-crossbones"),
    LUCK("clover");

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final String file;
    private int textureId;
    private boolean loaded;
    private boolean failed;

    Icon(String file) {
        this.file = file;
        this.textureId = 0;
        this.loaded = false;
        this.failed = false;
    }

    private boolean ensureLoaded() {
        if (this.loaded || this.failed) {
            return this.loaded;
        }
        try {
            IResource resource = mc.getResourceManager()
                    .getResource(new ResourceLocation("minecraft", "leader/icon/" + this.file + ".png"));
            BufferedImage image = ImageIO.read(resource.getInputStream());
            if (image == null) {
                this.failed = true;
                return false;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            int texId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            ByteBuffer buffer = ByteBuffer.allocateDirect(4 * pixels.length).order(ByteOrder.nativeOrder());
            for (int pixel : pixels) {
                buffer.put((byte) (pixel >> 16 & 0xFF));
                buffer.put((byte) (pixel >> 8 & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) (pixel >> 24 & 0xFF));
            }
            buffer.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            this.textureId = texId;
            this.loaded = true;
        } catch (Exception e) {
            this.failed = true;
        }
        return this.loaded;
    }

    public void draw(float x, float y, float size, int rgb, float alpha) {
        this.drawScaled(x, y, size, size, rgb, alpha);
    }

    public void drawCentered(float centerX, float centerY, float size, int rgb, float alpha) {
        this.draw(centerX - size / 2.0F, centerY - size / 2.0F, size, rgb, alpha);
    }

    public void drawScaled(float x, float y, float width, float height, int rgb, float alpha) {
        if (alpha <= 0.004F || !this.ensureLoaded()) {
            return;
        }
        float a = alpha;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableTexture2D();
        GlStateManager.color((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, a);
        GlStateManager.bindTexture(this.textureId);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + height, 0.0D).tex(0.0D, 1.0D).endVertex();
        wr.pos(x + width, y + height, 0.0D).tex(1.0D, 1.0D).endVertex();
        wr.pos(x + width, y, 0.0D).tex(1.0D, 0.0D).endVertex();
        wr.pos(x, y, 0.0D).tex(0.0D, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static Icon potion(int potionId) {
        switch (potionId) {
            case 1: return SPEED;
            case 2: return SLOW;
            case 3: return HASTE;
            case 4: return FATIGUE;
            case 5: return STRENGTH;
            case 6: return HEAL;
            case 7: return HARM;
            case 8: return JUMP;
            case 9: return FLASK;
            case 10: return REGEN;
            case 11: return RESIST;
            case 12: return FIRE;
            case 13: return WATER;
            case 14: return INVIS;
            case 15: return BLIND;
            case 16: return VISION;
            case 17: return HUNGER;
            case 18: return WEAKNESS;
            case 19: return POISON;
            case 20: return WITHER;
            case 21: return HEALTH_BOOST;
            case 22: return ABSORB;
            case 23: return HUNGER;
            default: return FLASK;
        }
    }
}
