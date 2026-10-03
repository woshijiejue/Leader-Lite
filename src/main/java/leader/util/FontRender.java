package leader.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public final class FontRender {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Map<Integer, CustomFontRenderer> renderers = new HashMap<>();
    private static int fontMode = 0;

    private FontRender() {
    }

    public static void setFontMode(int mode) {
        int normalizedMode = Math.max(0, Math.min(12, mode));
        if (normalizedMode == fontMode) {
            return;
        }
        dispose();
        fontMode = normalizedMode;
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow, boolean customFont) {
        drawString(text, x, y, color, shadow, 18.0F, customFont);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow, float size, boolean customFont) {
        if (text == null || text.isEmpty()) {
            return;
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            if (customFont) {
                CustomFontRenderer renderer = getRenderer(size);
                if (renderer != null) {
                    if (shadow) {
                        renderer.drawStringWithShadow(text, x, y, color);
                    } else {
                        renderer.drawString(text, x, y, color);
                    }
                    return;
                }
            }
            drawVanilla(text, x, y, color, shadow, size);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.bindTexture(0);
            if (depth && !GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) {
                GlStateManager.enableDepth();
            } else if (!depth && GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) {
                GlStateManager.disableDepth();
            }
            if (blend && !GL11.glIsEnabled(GL11.GL_BLEND)) {
                GlStateManager.enableBlend();
            } else if (!blend && GL11.glIsEnabled(GL11.GL_BLEND)) {
                GlStateManager.disableBlend();
            }
            if (texture && !GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
                GlStateManager.enableTexture2D();
            } else if (!texture && GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
                GlStateManager.disableTexture2D();
            }
        }
    }

    public static void drawStringWithShadow(String text, float x, float y, int color, boolean customFont) {
        drawStringWithShadow(text, x, y, color, 18.0F, customFont);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color, float size, boolean customFont) {
        drawString(text, x, y, color, true, size, customFont);
    }

    public static void drawCenteredString(String text, float x, float y, int color, boolean customFont) {
        drawCenteredString(text, x, y, color, 18.0F, customFont);
    }

    public static void drawCenteredString(String text, float x, float y, int color, float size, boolean customFont) {
        drawString(text, x - getStringWidth(text, size, customFont) / 2.0F, y, color, false, size, customFont);
    }

    public static int getStringWidth(String text, boolean customFont) {
        return getStringWidth(text, 18.0F, customFont);
    }

    public static int getStringWidth(String text, float size, boolean customFont) {
        if (customFont) {
            CustomFontRenderer renderer = getRenderer(size);
            if (renderer != null) {
                return renderer.getStringWidth(text);
            }
        }
        return Math.round(mc.fontRendererObj.getStringWidth(text) * normalizeSize(size) / 18.0F);
    }

    public static int getFontHeight(boolean customFont) {
        return getFontHeight(18.0F, customFont);
    }

    public static int getFontHeight(float size, boolean customFont) {
        if (customFont) {
            CustomFontRenderer renderer = getRenderer(size);
            if (renderer != null) {
                return Math.max(1, Math.round(renderer.getFontHeight()));
            }
        }
        return Math.max(1, Math.round(mc.fontRendererObj.FONT_HEIGHT * normalizeSize(size) / 18.0F));
    }

    public static float getBaseline(float size, boolean customFont) {
        if (customFont) {
            CustomFontRenderer renderer = getRenderer(size);
            if (renderer != null) {
                return renderer.getBaseline();
            }
        }
        return 7.0F * normalizeSize(size) / 18.0F;
    }

    public static float getCapHeight(float size, boolean customFont) {
        if (customFont) {
            CustomFontRenderer renderer = getRenderer(size);
            if (renderer != null) {
                return renderer.getCapHeight();
            }
        }
        return 7.0F * normalizeSize(size) / 18.0F;
    }

    public static void dispose() {
        for (CustomFontRenderer renderer : renderers.values()) {
            renderer.dispose();
        }
        renderers.clear();
    }

    private static CustomFontRenderer getRenderer(float size) {
        int key = Math.max(1, Math.round(normalizeSize(size) * 100.0F));
        CustomFontRenderer renderer = renderers.get(key);
        if (renderer == null) {
            renderer = new CustomFontRenderer(getFontPath(fontMode), normalizeSize(size), true);
            renderers.put(key, renderer);
        }
        return renderer;
    }

    private static void drawVanilla(String text, float x, float y, int color, boolean shadow, float size) {
        float scale = normalizeSize(size) / 18.0F;
        if (Math.abs(scale - 1.0F) < 0.0001F) {
            if (shadow) {
                mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
            } else {
                mc.fontRendererObj.drawString(text, x, y, color, false);
            }
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        if (shadow) {
            mc.fontRendererObj.drawStringWithShadow(text, 0.0F, 0.0F, color);
        } else {
            mc.fontRendererObj.drawString(text, 0.0F, 0.0F, color, false);
        }
        GlStateManager.popMatrix();
    }

    private static float normalizeSize(float size) {
        if (Float.isNaN(size) || Float.isInfinite(size) || size <= 0.0F) {
            return 18.0F;
        }
        return Math.min(size, 256.0F);
    }

    private static String getFontPath(int mode) {
        switch (mode) {
            case 1:
                return "/leader/font/xylitol_bold.ttf";
            case 2:
                return "/leader/font/harmonyos_sans_sc_regular.ttf";
            case 3:
                return "/leader/font/harmonyos_sans_sc_medium.ttf";
            case 4:
                return "/leader/font/Inter_SemiBold.ttf";
            case 5:
                return "/leader/font/NotoSans-Regular.ttf";
            case 6:
                return "/leader/font/NotoSansSC-Regular.ttf";
            case 7:
                return "/leader/font/Nursultan.ttf";
            case 8:
                return "/leader/font/product_sans_regular.ttf";
            case 9:
                return "/leader/font/SF-Pro-Display-Semibold.otf";
            case 10:
                return "/leader/font/SF-Pro-Rounded-Bold.otf";
            case 11:
                return "/leader/font/SF-Pro-Rounded-Medium.otf";
            case 12:
                return "/leader/font/SF-Pro-Rounded-Regular.otf";
            default:
                return "/leader/font/xylitol_font.ttf";
        }
    }
}
