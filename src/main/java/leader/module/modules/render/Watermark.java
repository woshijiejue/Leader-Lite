package leader.module.modules.render;

import leader.Leader;
import leader.event.EventTarget;
import leader.events.Render2DEvent;
import leader.module.Module;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
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

    public final ModeProperty mode = new ModeProperty("mode", 1, new String[]{"CLASSIC", "MODERN", "FROST"});
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

        // ── Phase cycling ──
        long total = PHASE_MS * 3L;
        long pos = now % total;
        int phase = (int) (pos / PHASE_MS);
        long phasePos = pos % PHASE_MS;

        String curText = getText(phase);
        String nextText = getText((phase + 1) % 3);

        // ── Fade animation ──
        float fade = 0.0F;
        if (phasePos > PHASE_MS - FADE_MS) {
            fade = (float) (phasePos - (PHASE_MS - FADE_MS)) / (float) FADE_MS;
        }
        fade = Math.max(0.0F, Math.min(1.0F, fade));
        float anim = easeOutCubic(fade);

        // ── Theme color ──
        HUD hud = getHud();
        Color tc = hud != null ? hud.getColor(now) : new Color(0, 190, 255);

        if (this.mode.getValue() == 0) {
            renderClassic(curText, nextText, anim, tc);
            return;
        }
        if (this.mode.getValue() == 2) {
            renderFrost(curText, nextText, anim, tc, now);
            return;
        }

        float uiScale = this.scale.getValue();
        float textScale = this.fontScale.getValue();

        // ── Measure text ──
        float curW = FontManager.getStringWidth(curText) * textScale;
        float nextW = FontManager.getStringWidth(nextText) * textScale;
        float textH = FontManager.getFontHeight() * textScale;
        float textW = curW + (nextW - curW) * anim;

        // ── Layout ──
        float padX = 12.0F;
        float padY = 7.0F;
        float dot = 4.0F;
        float dotGap = 7.0F;
        float contentW = dot + dotGap + textW;

        float cardW = padX + contentW + padX;
        float cardH = padY + textH + padY;
        float radius = 6.0F;

        // ── Clamp to screen ──
        ScaledResolution sr = new ScaledResolution(mc);
        float maxW = sr.getScaledWidth() / uiScale;
        float maxH = sr.getScaledHeight() / uiScale;

        float x = this.offX.getValue();
        float y = this.offY.getValue();
        if (x + cardW > maxW) x = maxW - cardW - 4.0F;
        if (y + cardH > maxH) y = maxH - cardH - 4.0F;
        if (x < 4.0F) x = 4.0F;
        if (y < 4.0F) y = 4.0F;

        // ── Frosted-glass card ──
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

        // ── Pulsing theme dot ──
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

    private void renderFrost(String curText, String nextText, float anim, Color themeColor, long now) {
        float uiScale = this.scale.getValue();
        float textScale = this.fontScale.getValue();
        float curW = FontManager.getStringWidth(curText) * textScale;
        float nextW = FontManager.getStringWidth(nextText) * textScale;
        float textH = FontManager.getFontHeight() * textScale;
        float textW = curW + (nextW - curW) * anim;

        float padX = 10.0F;
        float padY = 6.0F;
        float dot = 4.0F;
        float dotGap = 7.0F;
        float contentW = dot + dotGap + textW;
        float cardW = padX + contentW + padX;
        float cardH = padY + textH + padY;
        float radius = 8.0F;

        ScaledResolution sr = new ScaledResolution(mc);
        float maxW = sr.getScaledWidth() / uiScale;
        float maxH = sr.getScaledHeight() / uiScale;
        float x = this.offX.getValue();
        float y = this.offY.getValue();
        if (x + cardW > maxW) x = maxW - cardW - 4.0F;
        if (y + cardH > maxH) y = maxH - cardH - 4.0F;
        if (x < 4.0F) x = 4.0F;
        if (y < 4.0F) y = 4.0F;

        float pulse = 0.7F + 0.3F * (float) Math.sin(now * 0.004D);
        int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 250).getRGB();

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);
        RenderUtil.drawZenGlass(x, y, x + cardW, y + cardH, radius, 1.0F);
        RenderUtil.drawRoundedRectWithGl(x + padX - 1.0F, y + (cardH - dot) / 2.0F - 1.0F,
                x + padX + dot + 1.0F, y + (cardH + dot) / 2.0F + 1.0F, 3.0F,
                new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(),
                        (int) (60.0F * pulse)).getRGB());
        RenderUtil.drawRoundedRectWithGl(x + padX, y + (cardH - dot) / 2.0F, x + padX + dot, y + (cardH + dot) / 2.0F,
                2.0F, accent);

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float textX = x + padX + dot + dotGap;
        float baseY = y + (cardH - textH) / 2.0F + 1.0F;
        drawFrostPhaseText(curText, textX, baseY - 5.0F * anim, textScale, (int) ((1.0F - anim) * 232.0F));
        if (anim > 0.005F) {
            drawFrostPhaseText(nextText, textX, baseY + 7.0F - 7.0F * anim, textScale, (int) (anim * 232.0F));
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawFrostPhaseText(String text, float x, float y, float textScale, int alpha) {
        if (alpha <= 0) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(textScale, textScale, 1.0F);
        FontManager.drawString(text, 0.0F, 0.0F, new Color(244, 247, 252, alpha).getRGB(), false);
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

    /** Draw a single text phase with the given alpha, including a subtle shadow. */
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
