package leader.module.modules.render;

import leader.Leader;
import leader.event.EventTarget;
import leader.events.Render2DEvent;
import leader.module.Module;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.Icon;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class Watermark extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String CLIENT_NAME = "Leader Lite";
    private static final long PHASE_MS = 2200L;
    private static final long FADE_MS = 350L;

    private long lastFrameTime = System.currentTimeMillis();
    private int displayFps = 0;
    private int frameCount = 0;

    public final ModeProperty mode = new ModeProperty("mode", 1, new String[]{"CLASSIC", "MODERN", "ICON", "LUCID"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final FloatProperty fontScale = new FloatProperty("font-scale", 1.0F, 0.7F, 1.5F);
    public final IntProperty offX = new IntProperty("offset-x", 4, 0, 500);
    public final IntProperty offY = new IntProperty("offset-y", 4, 0, 500);

    public Watermark() {
        super("Watermark", false);
    }

    private String getText(int phase) {
        switch (phase) {
            case 0:
                return CLIENT_NAME;
            case 1:
                return displayFps + " FPS";
            default:
                return mc.thePlayer != null ? mc.thePlayer.getName() : CLIENT_NAME;
        }
    }

    private float easeOutCubic(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private HUD getHud() {
        try {
            return (HUD) Leader.moduleManager.modules.get(HUD.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;

        long now = System.currentTimeMillis();
        frameCount++;
        if (now - lastFrameTime >= 1000L) {
            displayFps = frameCount;
            frameCount = 0;
            lastFrameTime = now;
        }

        long total = PHASE_MS * 3L;
        long pos = now % total;
        int phase = (int) (pos / PHASE_MS);
        long phasePos = pos % PHASE_MS;

        String curText = getText(phase);
        String nextText = getText((phase + 1) % 3);

        float fade = 0.0F;
        if (phasePos > PHASE_MS - FADE_MS) {
            fade = (float) (phasePos - (PHASE_MS - FADE_MS)) / (float) FADE_MS;
        }
        fade = Math.max(0.0F, Math.min(1.0F, fade));
        float anim = easeOutCubic(fade);

        HUD hud = getHud();
        Color tc = hud != null ? hud.getColor(now) : new Color(0, 190, 255);

        if (this.mode.getValue() == 0) {
            renderClassic(curText, nextText, anim, tc);
            return;
        }
        if (this.mode.getValue() == 2) {
            renderIcon(tc);
            return;
        }
        if (this.mode.getValue() == 3) {
            renderLucid(tc);
            return;
        }

        float uiScale = this.scale.getValue();
        float textScale = this.fontScale.getValue();

        float curW = FontManager.getStringWidth(curText) * textScale;
        float nextW = FontManager.getStringWidth(nextText) * textScale;
        float textH = FontManager.getFontHeight() * textScale;
        float textW = curW + (nextW - curW) * anim;

        float padX = 12.0F;
        float padY = 7.0F;
        float dot = 4.0F;
        float dotGap = 7.0F;
        float contentW = dot + dotGap + textW;

        float cardW = padX + contentW + padX;
        float cardH = padY + textH + padY;
        float radius = 6.0F;

        ScaledResolution sr = new ScaledResolution(mc);
        float maxW = sr.getScaledWidth() / uiScale;
        float maxH = sr.getScaledHeight() / uiScale;

        float x = this.offX.getValue();
        float y = this.offY.getValue();
        if (x + cardW > maxW) x = maxW - cardW - 4.0F;
        if (y + cardH > maxH) y = maxH - cardH - 4.0F;
        if (x < 4.0F) x = 4.0F;
        if (y < 4.0F) y = 4.0F;

        int rimCol = new Color(255, 255, 255, 36).getRGB();
        int glassCol = new Color(13, 15, 21, 172).getRGB();
        int tintCol = new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 14).getRGB();
        int shineCol = new Color(255, 255, 255, 20).getRGB();

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);
        RenderUtil.drawRoundedRectWithGl(x, y, x + cardW, y + cardH, radius, rimCol);
        RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 1.0F, x + cardW - 1.0F, y + cardH - 1.0F, radius - 1.0F, glassCol);
        RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 1.0F, x + cardW - 1.0F, y + cardH - 1.0F, radius - 1.0F, tintCol);
        RenderUtil.drawRoundedRectWithGl(x + 2.0F, y + 2.0F, x + cardW - 2.0F, y + cardH * 0.45F, radius - 2.0F, shineCol);

        float pulse = 0.7F + 0.3F * (float) Math.sin(now * 0.004D);
        float dotY = y + (cardH - dot) / 2.0F;
        int dotGlow = new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), (int) (70.0F * pulse)).getRGB();
        int dotCore = new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), (int) (235.0F * pulse)).getRGB();
        RenderUtil.drawRoundedRectWithGl(x + padX - 1.5F, dotY - 1.5F, x + padX + dot + 1.5F, dotY + dot + 1.5F, 3.0F, dotGlow);
        RenderUtil.drawRoundedRectWithGl(x + padX, dotY, x + padX + dot, dotY + dot, 2.0F, dotCore);

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float textX = x + padX + dot + dotGap;
        float baseY = y + (cardH - textH) / 2.0F + 1.0F;
        float curOffY = -5.0F * anim;
        int curAlpha = (int) ((1.0F - anim) * 245.0F);
        drawPhaseText(curText, textX, baseY + curOffY, textScale, curAlpha);
        if (anim > 0.005F) {
            float nextOffY = 8.0F - 8.0F * anim;
            int nextAlpha = (int) (anim * 245.0F);
            drawPhaseText(nextText, textX, baseY + nextOffY, textScale, nextAlpha);
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void renderIcon(Color themeColor) {
        float uiScale = this.scale.getValue();
        float textScale = this.fontScale.getValue();
        String name = CLIENT_NAME;
        String fps = this.displayFps + " fps";
        String player = mc.thePlayer != null ? mc.thePlayer.getName() : "-";

        float padX = 7.0F;
        float padY = 5.0F;
        float barW = 2.0F;
        float barGap = 6.0F;
        float iconW = 9.0F;
        float iconH = iconW * 0.86F + 3.0F;
        float iconGap = 6.0F;
        float sepGap = 6.0F;
        float textH = FontManager.getFontHeight() * textScale;

        float nameW = FontManager.getStringWidth(name) * textScale;
        float fpsW = FontManager.getStringWidth(fps) * textScale;
        float playerW = FontManager.getStringWidth(player) * textScale;
        float sepW = FontManager.getStringWidth("|") * textScale;

        float contentW = barW + barGap + iconW + iconGap + nameW + sepGap + sepW + sepGap
                + fpsW + sepGap + sepW + sepGap + playerW;
        float cardW = padX + contentW + padX;
        float cardH = Math.max(padY * 2.0F + textH, padY * 2.0F + iconH);

        ScaledResolution sr = new ScaledResolution(mc);
        float maxW = sr.getScaledWidth() / uiScale;
        float x = this.offX.getValue();
        float y = this.offY.getValue();
        if (x + cardW > maxW) x = maxW - cardW - 4.0F;
        if (x < 4.0F) x = 4.0F;

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);

        RenderUtil.drawRoundedRectWithGl(x, y, x + cardW, y + cardH, 4.0F, new Color(0, 0, 0, 96).getRGB());
        RenderUtil.drawRoundedRectWithGl(x + padX - 3.0F, y + padY - 1.0F, x + padX - 1.0F,
                y + cardH - padY + 1.0F, 1.0F,
                new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 250).getRGB());

        float cursor = x + padX + barW + barGap;
        this.drawLogo(cursor, y + (cardH - iconH) / 2.0F, iconW, themeColor);
        cursor += iconW + iconGap;

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float textY = y + (cardH - textH) / 2.0F + 1.0F;
        int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 250).getRGB();
        int white = new Color(238, 242, 248, 246).getRGB();
        int dim = new Color(148, 156, 170, 215).getRGB();

        this.drawIconText(name, cursor, textY, textScale, accent);
        cursor += nameW + sepGap;
        this.drawIconText("|", cursor, textY, textScale, dim);
        cursor += sepW + sepGap;
        this.drawIconText(fps, cursor, textY, textScale, white);
        cursor += fpsW + sepGap;
        this.drawIconText("|", cursor, textY, textScale, dim);
        cursor += sepW + sepGap;
        this.drawIconText(player, cursor, textY, textScale, white);

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawIconText(String text, float x, float y, float scale, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        FontManager.drawString(text, 0.0F, 0.0F, color, false);
        GlStateManager.popMatrix();
    }

    private void drawLogo(float x, float y, float size, Color themeColor) {
        float w = size;
        float h = size * 0.86F;
        float r = themeColor.getRed() / 255.0F;
        float g = themeColor.getGreen() / 255.0F;
        float b = themeColor.getBlue() / 255.0F;

        GlStateManager.disableTexture2D();
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);

        GL11.glColor4f(r, g, b, 0.95F);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x, y + h * 0.12F);
        GL11.glVertex2f(x, y + h * 0.48F);
        GL11.glVertex2f(x + w * 0.36F, y + h * 0.48F);

        GL11.glVertex2f(x + w * 0.5F, y + h * 0.02F);
        GL11.glVertex2f(x + w * 0.18F, y + h * 0.48F);
        GL11.glVertex2f(x + w * 0.82F, y + h * 0.48F);

        GL11.glVertex2f(x + w, y + h * 0.12F);
        GL11.glVertex2f(x + w * 0.64F, y + h * 0.48F);
        GL11.glVertex2f(x + w, y + h * 0.48F);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + h * 0.42F);
        GL11.glVertex2f(x + w, y + h * 0.42F);
        GL11.glVertex2f(x + w, y + h * 0.78F);
        GL11.glVertex2f(x, y + h * 0.78F);

        GL11.glColor4f(Math.min(1.0F, r * 0.5F + 0.5F), Math.min(1.0F, g * 0.5F + 0.5F),
                Math.min(1.0F, b * 0.5F + 0.5F), 1.0F);
        GL11.glVertex2f(x, y + h * 0.82F);
        GL11.glVertex2f(x + w, y + h * 0.82F);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();

        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.enableTexture2D();
        GlStateManager.resetColor();
    }

    private void renderLucid(Color themeColor) {
        float uiScale = this.scale.getValue();
        float textScale = this.fontScale.getValue();
        String name = CLIENT_NAME;
        String fps = this.displayFps + " FPS";
        String player = mc.thePlayer != null ? mc.thePlayer.getName() : "-";

        float chipSize = 16.0F;
        float chipX = 4.0F;
        float gap = 7.0F;
        float padRight = 8.0F;
        float padY = 3.5F;
        float textH = FontManager.getFontHeight() * textScale;
        float nameW = FontManager.getStringWidth(name) * textScale;
        float fpsW = FontManager.getStringWidth(fps) * textScale;
        float playerW = FontManager.getStringWidth(player) * textScale;
        float sepW = FontManager.getStringWidth("|") * textScale;
        float sepGap = 6.0F;

        float contentW = chipSize + gap + nameW + sepGap + sepW + sepGap + fpsW + sepGap + sepW + sepGap + playerW;
        float cardW = chipX + contentW + padRight;
        float cardH = Math.max(chipSize + padY * 2.0F, padY * 2.0F + textH);
        float radius = 7.0F;

        ScaledResolution sr = new ScaledResolution(mc);
        float maxW = sr.getScaledWidth() / uiScale;
        float maxH = sr.getScaledHeight() / uiScale;
        float x = this.offX.getValue();
        float y = this.offY.getValue();
        if (x + cardW > maxW) x = maxW - cardW - 4.0F;
        if (y + cardH > maxH) y = maxH - cardH - 4.0F;
        if (x < 4.0F) x = 4.0F;
        if (y < 4.0F) y = 4.0F;

        int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 255).getRGB();
        int chipBg = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 48).getRGB();

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);
        RenderUtil.drawRoundedRectWithGl(x + 0.5F, y + 1.8F, x + cardW + 0.5F, y + cardH + 1.8F, radius,
                new Color(0, 0, 0, 42).getRGB());
        RenderUtil.drawRoundedRectWithGl(x, y, x + cardW, y + cardH, radius, new Color(44, 48, 58, 190).getRGB());

        float chipY = y + (cardH - chipSize) / 2.0F;
        RenderUtil.drawRoundedRectWithGl(x + chipX, chipY, x + chipX + chipSize, chipY + chipSize, 5.0F, chipBg);
        Icon.CROWN.drawCentered(x + chipX + chipSize / 2.0F, chipY + chipSize / 2.0F, 10.0F, accent, 1.0F);

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float cursor = x + chipX + chipSize + gap;
        float textY = y + (cardH - textH) / 2.0F + 1.0F;
        int white = new Color(240, 244, 250, 246).getRGB();
        int dim = new Color(148, 156, 170, 200).getRGB();

        drawLucidText(name, cursor, textY, textScale, accent);
        cursor += nameW + sepGap;
        drawLucidText("|", cursor, textY, textScale, dim);
        cursor += sepW + sepGap;
        drawLucidText(fps, cursor, textY, textScale, white);
        cursor += fpsW + sepGap;
        drawLucidText("|", cursor, textY, textScale, dim);
        cursor += sepW + sepGap;
        drawLucidText(player, cursor, textY, textScale, white);

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawLucidText(String text, float x, float y, float textScale, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(textScale, textScale, 1.0F);
        FontManager.drawString(text, 0.0F, 0.0F, color, false);
        GlStateManager.popMatrix();
    }

    private void renderClassic(String curText, String nextText, float anim, Color themeColor) {
        float uiScale = this.scale.getValue();
        float textScale = this.fontScale.getValue();
        float curW = FontManager.getStringWidth(curText) * textScale;
        float nextW = FontManager.getStringWidth(nextText) * textScale;
        float contentW = curW + (nextW - curW) * anim;
        float textH = FontManager.getFontHeight() * textScale;
        float x = this.offX.getValue();
        float y = this.offY.getValue();
        int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 245).getRGB();

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderUtil.drawLine(x, y + textH + 3.0F, x + contentW, y + textH + 3.0F, 1.5F, accent);
        drawPhaseText(curText, x, y, textScale, (int) ((1.0F - anim) * 245.0F));
        if (anim > 0.005F) {
            drawPhaseText(nextText, x, y + 7.0F - 7.0F * anim, textScale, (int) (anim * 245.0F));
        }
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawPhaseText(String text, float x, float y, float textScale, int alpha) {
        if (alpha <= 0) return;

        int textColor = new Color(245, 245, 250, alpha).getRGB();
        int shadowColor = new Color(0, 0, 0, Math.min(alpha, 80)).getRGB();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(textScale, textScale, 1.0F);
        FontManager.drawString(text, 0.8F, 0.8F, shadowColor, false);
        FontManager.drawString(text, 0.0F, 0.0F, textColor, false);
        GlStateManager.popMatrix();
    }
}
