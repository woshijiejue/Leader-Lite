package leader.util;

import leader.module.modules.render.FontManager;
import leader.module.modules.render.BetterFPS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/** Small drawing helpers for isolated editor samples; never updates live module state. */
public final class HUDPreviewUtil {
    private HUDPreviewUtil() { }

    public static int color(Color c, float opacity) {
        return (Math.max(0, Math.min(255, Math.round(c.getAlpha() * opacity))) << 24) | (c.getRGB() & 0xFFFFFF);
    }

    public static String fit(String text, float width, float size) {
        if (width <= 0) return "";
        if (FontManager.getStringWidth(text, size) <= width) return text;
        while (!text.isEmpty() && FontManager.getStringWidth(text + "..", size) > width) {
            text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        }
        return text.isEmpty() ? "" : text + "..";
    }

    public static void text(String s, float x, float baseline, float size, int color, boolean shadow) {
        if ((color >>> 24) < 4) return;
        FontManager.drawString(s, x, baseline - FontManager.getBaseline(size), color, shadow, size);
    }

    public static void head(float x, float y, float size, float radius, float opacity) {
        Minecraft mc = Minecraft.getMinecraft();
        ResourceLocation skin = mc.thePlayer != null ? mc.thePlayer.getLocationSkin()
                : new ResourceLocation("textures/entity/steve.png");
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        GlStateManager.color(1, 1, 1, opacity);
        mc.getTextureManager().bindTexture(skin);
        roundedTexture(x, y, size, radius, 8);
        roundedTexture(x, y, size, radius, 40);
        if (cull) GlStateManager.enableCull();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void roundedTexture(float x, float y, float size, float radius, float u) {
        radius = Math.max(0, Math.min(size / 2, radius));
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x + size / 2, y + size / 2, 0).tex((u + 4) / 64, 12.0F / 64).endVertex();
        for (int corner = 0; corner <= 4; corner++) {
            int c = corner % 4;
            float cx = x + (c == 0 || c == 3 ? radius : size - radius);
            float cy = y + (c < 2 ? radius : size - radius);
            for (int step = 0; step <= (corner == 4 ? 0 : 10); step++) {
                double angle = Math.toRadians(180 + c * 90 + step * 9);
                float px = cx + (float) Math.cos(angle) * radius;
                float py = cy + (float) Math.sin(angle) * radius;
                wr.pos(px, py, 0).tex((u + (px - x) / size * 8) / 64,
                        (8 + (py - y) / size * 8) / 64).endVertex();
            }
        }
        tessellator.draw();
    }

    public static void bar(float x, float y, float width, float height, float ratio, Color tint, float opacity) {
        RenderUtil.drawRoundedRectWithGl(x, y, x + width, y + height, height / 2,
                color(new Color(255, 255, 255, 22), opacity));
        float fill = width * Math.max(0, Math.min(1, ratio));
        if (fill > 0) RenderUtil.drawRoundedRectGradientH(x, y, x + fill, y + height,
                Math.min(height / 2, fill / 2), color(tint.darker(), opacity), color(tint, opacity));
    }

    public static void ring(float x, float y, float radius, float ratio, Color tint, float opacity) {
        if (BetterFPS.optimizedHUD()) {
            RenderUtil.drawArcRing(x, y, radius, 1.5F, -90, 360,
                    color(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 28), opacity));
            if (ratio > 0) RenderUtil.drawArcRing(x, y, radius, 1.5F, -90, 360 * Math.min(1, ratio), color(tint, opacity));
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48 - Math.PI / 2;
            double b = Math.PI * 2 * (i + 1) / 48 - Math.PI / 2;
            Color c = i / 48.0F < ratio ? tint : new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 28);
            RenderUtil.drawLine(x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius,
                    x + (float) Math.cos(b) * radius, y + (float) Math.sin(b) * radius, 1.5F, color(c, opacity));
        }
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }
}
