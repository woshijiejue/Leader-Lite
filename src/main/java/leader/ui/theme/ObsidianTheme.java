package leader.ui.theme;

import leader.Leader;
import leader.module.modules.render.BetterFPS;
import leader.module.modules.render.GuiModule;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.awt.Color;

/** Xylitol-inspired rendering primitives. Light editor chrome and compact translucent game HUD. */
public final class ObsidianTheme {
    public static final Color TEXT = new Color(246, 246, 246);
    public static final Color MUTED = new Color(194, 194, 194);
    public static final Color ACCENT = new Color(255, 175, 63);
    public static final Color SURFACE = new Color(0, 0, 0);
    public static final Color SUCCESS = new Color(106, 206, 145);
    public static final Color WARNING = new Color(255, 175, 63);
    public static final Color GUI_ACCENT = new Color(3, 168, 245);
    public static final Color GUI_TEXT = new Color(38, 38, 38);
    public static final Color GUI_MUTED = new Color(125, 125, 125);

    private ObsidianTheme() { }

    private static GuiModule settings() {
        return Leader.moduleManager == null ? null : (GuiModule) Leader.moduleManager.getModule(GuiModule.class);
    }

    public static boolean active() {
        GuiModule gui = settings();
        return gui != null && gui.style.getValue() >= 2;
    }

    public static boolean motion() {
        GuiModule gui = settings();
        return gui != null && gui.visualMotion.getValue() && !BetterFPS.skipExtraPostFX();
    }

    public static Color accent(long time, long offset) {
        float p = motion() ? (float) (0.82 + 0.18 * Math.sin(time % 60000L * Math.PI / 3000 - offset * 0.25)) : 1;
        return new Color(Math.round(255 * p), Math.round(175 * p), Math.round(63 * p));
    }

    public static int rgba(Color color, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha))) << 24) | (color.getRGB() & 0xFFFFFF);
    }

    /** Neutral chrome only; colour-picker swatches and gameplay colours bypass this. */
    public static int chrome(int color) {
        if (!active()) return color;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 48) return color;
        int value = (r + g + b) / 3;
        Color mapped = value >= 195 ? GUI_TEXT : value >= 90 ? GUI_MUTED
                : value >= 45 ? new Color(170, 170, 170) : new Color(245 - value / 3, 245 - value / 3, 245 - value / 3);
        return (color & 0xFF000000) | (mapped.getRGB() & 0xFFFFFF);
    }

    public static int ink(int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        // Preserve warning/status colours in Island; only recolour neutral typography.
        if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 55) return color;
        Color mapped = (r + g + b) / 3 > 195 ? TEXT : MUTED;
        return (color & 0xFF000000) | (mapped.getRGB() & 0xFFFFFF);
    }

    public static void surface(float x, float y, float w, float h, float radius, float opacity, int seed) {
        if (opacity <= 0.01F || w <= 2 || h <= 2) return;
        RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, Math.min(4, radius), rgba(SURFACE, 100 * opacity));
    }

    public static void editorSurface(float x, float y, float w, float h, float opacity) {
        if (opacity <= 0.01F || w <= 0 || h <= 0) return;
        RenderUtil.drawRoundedRectWithGl(x, y + 2, x + w, y + h + 2, 6, rgba(Color.BLACK, 12 * opacity));
        RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, 6, rgba(new Color(250, 249, 248), 240 * opacity));
    }

    /** Deferred masks use screen-space scaling, not the caller's later-restored matrix. */
    public static void mask(float x, float y, float w, float h, float scale, float radius) {
        if (w <= 0 || h <= 0) return;
        ShaderElement.addBlurTask(() -> {
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1);
            RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, radius, -1);
            GlStateManager.popMatrix();
        });
    }

    /** Thin top accent from the reference HUD; no corner cuts or decorative frame. */
    public static void edges(float x, float y, float w, float h, float opacity, int seed) {
        if (opacity <= 0.01F || w < 16 || h < 8) return;
        RenderUtil.drawRoundedRectGradientH(x, y, x + w, y + 1, 0,
                rgba(accent(System.currentTimeMillis(), 0), 230 * opacity), rgba(accent(System.currentTimeMillis(), 6), 230 * opacity));
    }

    /** Architectural backdrop is editor-only; no overlay is added during gameplay. */
    public static void backdrop(float w, float h, float opacity) {
        if (!active() || opacity <= 0.01F) return;
        RenderUtil.drawRoundedRectWithGl(0, 0, w, h, 0, rgba(Color.BLACK, 45 * opacity));
    }

    /** Simple initial/avatar fallback; no custom geometric logo. */
    public static void emblem(float cx, float cy, float size, float opacity) {
        if (opacity <= 0.01F) return;
        RenderUtil.drawRoundedRectWithGl(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, 3,
                rgba(new Color(255, 255, 255), 18 * opacity));
    }

    public static void status(float cx, float cy, int state, Color tint, float opacity) {
        if (opacity <= 0.01F) return;
        Batch batch = new Batch();
        try {
            int color = rgba(tint, 240 * opacity);
            if (state == 0) {
                batch.line(cx - 4, cy, cx - 1, cy + 3, 1.4F, color);
                batch.line(cx - 1, cy + 3, cx + 5, cy - 3, 1.4F, color);
            } else if (state == 1) {
                batch.line(cx - 3.5F, cy - 3.5F, cx + 3.5F, cy + 3.5F, 1.4F, color);
                batch.line(cx + 3.5F, cy - 3.5F, cx - 3.5F, cy + 3.5F, 1.4F, color);
            } else {
                batch.rect(cx - 0.7F, cy - 4, cx + 0.7F, cy + 1, color);
                batch.rect(cx - 0.7F, cy + 3, cx + 0.7F, cy + 4.4F, color);
            }
        } finally { batch.close(); }
    }

    /** One batched draw per ornament; restore state through Minecraft's state cache. */
    private static final class Batch {
        private final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final Tessellator tess = Tessellator.getInstance();
        private final WorldRenderer wr = tess.getWorldRenderer();

        Batch() {
            GlStateManager.disableTexture2D(); GlStateManager.disableDepth(); GlStateManager.disableCull();
            GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        }

        void vertex(float x, float y, int color) {
            wr.pos(x, y, 0).color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).endVertex();
        }

        void triangle(float x, float y, float x2, float y2, float x3, float y3, int color) {
            vertex(x, y, color); vertex(x2, y2, color); vertex(x3, y3, color);
        }

        void rect(float x, float y, float x2, float y2, int color) {
            if (x2 <= x || y2 <= y) return;
            triangle(x, y, x2, y, x2, y2, color); triangle(x, y, x2, y2, x, y2, color);
        }

        void line(float x, float y, float x2, float y2, float width, int color) {
            float dx = x2 - x, dy = y2 - y, length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.01F) return;
            float nx = -dy / length * width / 2, ny = dx / length * width / 2;
            triangle(x + nx, y + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, color);
            triangle(x + nx, y + ny, x2 - nx, y2 - ny, x - nx, y - ny, color);
        }

        void close() {
            try { tess.draw(); }
            finally {
                if (texture) GlStateManager.enableTexture2D();
                if (depth) GlStateManager.enableDepth();
                if (cull) GlStateManager.enableCull();
                GlStateManager.tryBlendFuncSeparate(src, dst, srcA, dstA);
                if (!blend) GlStateManager.disableBlend();
                GlStateManager.color(1, 1, 1, 1);
            }
        }
    }
}
