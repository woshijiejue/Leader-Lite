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
import leader.util.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
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

    public final ModeProperty mode = new ModeProperty("mode", 1, new String[]{"CLASSIC", "MODERN", "LUCID"});
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
            renderLucid(tc, now);
            return;
        }

        renderModern(curText, nextText, anim, tc, now, phasePos);
    }

    private void renderLucid(Color themeColor, long now) {
        final float uiScale = this.scale.getValue();
        float titleSize = 18.0F * this.fontScale.getValue();
        float infoSize = 15.0F * this.fontScale.getValue();

        String title = CLIENT_NAME;
        String[] items = new String[]{
                this.displayFps + " fps",
                mc.thePlayer != null ? mc.thePlayer.getName() : "-",
                this.getPing() + " ms"
        };

        float capH = Math.max(FontManager.getCapHeight(titleSize), FontManager.getCapHeight(infoSize));
        float padX = 8.0F;
        float blockH = capH + 12.0F;
        float blockGap = 3.0F;
        float icon = 11.0F;
        float iconGap = 5.0F;
        final float radius = 4.0F;

        float titleW = FontManager.getStringWidth(title, titleSize);
        float[] blockW = new float[items.length + 1];
        blockW[0] = padX + icon + iconGap + titleW + padX;
        for (int i = 0; i < items.length; i++) {
            blockW[i + 1] = padX + FontManager.getStringWidth(items[i], infoSize) + padX;
        }
        float cardW = -blockGap;
        for (float w : blockW) cardW += w + blockGap;
        float cardH = blockH;

        ScaledResolution sr = new ScaledResolution(mc);
        float maxW = sr.getScaledWidth() / uiScale;
        float maxH = sr.getScaledHeight() / uiScale;
        float x = this.offX.getValue();
        float y = this.offY.getValue();
        if (x + cardW > maxW) x = maxW - cardW - 4.0F;
        if (y + cardH > maxH) y = maxH - cardH - 4.0F;
        if (x < 4.0F) x = 4.0F;
        if (y < 4.0F) y = 4.0F;

        final float[] blockX = new float[blockW.length];
        float cursorX = x;
        for (int i = 0; i < blockW.length; i++) {
            blockX[i] = cursorX;
            cursorX += blockW[i] + blockGap;
        }

        int ar = themeColor.getRed();
        int ag = themeColor.getGreen();
        int ab = themeColor.getBlue();
        Color hi = new Color(Math.min(255, ar + 60), Math.min(255, ag + 60), Math.min(255, ab + 60));

        final float by = y;
        final float bh = cardH;
        final int maskColor = new Color(ar, ag, ab, 255).getRGB();
        ShaderElement.addBlurTask(() -> {
            GlStateManager.pushMatrix();
            GlStateManager.scale(uiScale, uiScale, 1.0F);
            for (int i = 0; i < blockX.length; i++) {
                RenderUtil.drawRoundedRectWithGl(blockX[i], by, blockX[i] + blockW[i], by + bh, radius, maskColor);
            }
            GlStateManager.popMatrix();
        });

        float centerY = y + cardH / 2.0F;
        float baseline = centerY + capH / 2.0F;

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);

        float tx = blockX[0];
        RenderUtil.drawRoundedRectGradientH(tx, y, tx + blockW[0], y + cardH, radius,
                new Color(ar, ag, ab, 235).getRGB(), new Color(hi.getRed(), hi.getGreen(), hi.getBlue(), 235).getRGB());
        float shine = (now % 3200L) / 3200.0F;
        float shineX = tx - 14.0F + (blockW[0] + 28.0F) * shine;
        float s1 = Math.max(tx + 1.0F, shineX - 8.0F);
        float s2 = Math.min(tx + blockW[0] - 1.0F, shineX + 8.0F);
        if (s2 - s1 > 1.0F) {
            RenderUtil.drawRoundedRectGradientH(s1, y + 1.0F, (s1 + s2) / 2.0F, y + cardH - 1.0F, 0.0F,
                    new Color(255, 255, 255, 0).getRGB(), new Color(255, 255, 255, 46).getRGB());
            RenderUtil.drawRoundedRectGradientH((s1 + s2) / 2.0F, y + 1.0F, s2, y + cardH - 1.0F, 0.0F,
                    new Color(255, 255, 255, 46).getRGB(), new Color(255, 255, 255, 0).getRGB());
        }
        int ink = new Color(14, 16, 22, 255).getRGB();
        Icon.CROWN.drawCentered(tx + padX + icon / 2.0F, centerY, icon, ink, 1.0F);
        this.drawLucidText(title, tx + padX + icon + iconGap, baseline, ink, titleSize);

        int textColor = new Color(240, 243, 248, 245).getRGB();
        for (int i = 0; i < items.length; i++) {
            float bx = blockX[i + 1];
            float bw = blockW[i + 1];
            RenderUtil.drawRoundedRectWithGl(bx + padX, y + cardH - 2.0F, bx + bw - padX, y + cardH - 1.0F, 0.5F,
                    new Color(ar, ag, ab, 170).getRGB());
            this.drawLucidText(items[i], bx + padX, baseline - 0.5F, textColor, infoSize);
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void renderModern(String curText, String nextText, float anim, Color tc, long now, long phasePos) {
        float uiScale = this.scale.getValue();
        float titleSize = 18.0F * this.fontScale.getValue();
        float infoSize = 14.0F * this.fontScale.getValue();
        String brand = "Leader";
        String tag = "LITE";
        float tagSize = infoSize * 0.8F;

        float titleCap = FontManager.getCapHeight(titleSize);
        float infoCap = FontManager.getCapHeight(infoSize);
        float brandW = FontManager.getStringWidth(brand, titleSize);
        float tagW = FontManager.getStringWidth(tag, tagSize);
        float curW = FontManager.getStringWidth(curText, infoSize);
        float nextW = FontManager.getStringWidth(nextText, infoSize);
        float infoW = curW + (nextW - curW) * anim;

        float padX = 10.0F;
        float accentW = 2.0F;
        float tagPad = 3.5F;
        float tagBoxW = tagW + tagPad * 2.0F;
        float sepGap = 9.0F;
        float cardH = Math.max(titleCap, infoCap) + 16.0F;
        float cardW = padX + accentW + 7.0F + brandW + 4.0F + tagBoxW + sepGap + 1.0F + sepGap + infoW + padX;
        final float radius = 5.0F;

        ScaledResolution sr = new ScaledResolution(mc);
        float maxW = sr.getScaledWidth() / uiScale;
        float maxH = sr.getScaledHeight() / uiScale;
        float x = this.offX.getValue();
        float y = this.offY.getValue();
        if (x + cardW > maxW) x = maxW - cardW - 4.0F;
        if (y + cardH > maxH) y = maxH - cardH - 4.0F;
        if (x < 4.0F) x = 4.0F;
        if (y < 4.0F) y = 4.0F;

        final float bx = x;
        final float by = y;
        final float bw = cardW;
        final float bh = cardH;
        final int maskColor = new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 255).getRGB();
        ShaderElement.addBlurTask(() -> {
            GlStateManager.pushMatrix();
            GlStateManager.scale(uiScale, uiScale, 1.0F);
            RenderUtil.drawRoundedRectWithGl(bx, by, bx + bw, by + bh, radius, maskColor);
            GlStateManager.popMatrix();
        });

        int ar = tc.getRed();
        int ag = tc.getGreen();
        int ab = tc.getBlue();
        Color hi = new Color(Math.min(255, ar + 70), Math.min(255, ag + 70), Math.min(255, ab + 70));
        float centerY = y + cardH / 2.0F;

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);

        float cursor = x + padX;
        RenderUtil.drawRoundedRectGradient(cursor, centerY - titleCap / 2.0F - 2.0F, cursor + accentW,
                centerY + titleCap / 2.0F + 2.0F, 1.0F,
                new Color(hi.getRed(), hi.getGreen(), hi.getBlue(), 255).getRGB(), new Color(ar, ag, ab, 255).getRGB());
        cursor += accentW + 7.0F;

        this.drawLucidText(brand, cursor, centerY + titleCap / 2.0F, new Color(248, 249, 252, 255).getRGB(), titleSize);
        cursor += brandW + 4.0F;

        float tagCap = FontManager.getCapHeight(tagSize);
        float tagY1 = centerY - tagCap / 2.0F - 3.0F;
        float tagY2 = centerY + tagCap / 2.0F + 3.0F;
        RenderUtil.drawRoundedRectWithGl(cursor, tagY1, cursor + tagBoxW, tagY2, 2.5F, new Color(ar, ag, ab, 230).getRGB());
        this.drawLucidText(tag, cursor + tagPad, centerY + tagCap / 2.0F, new Color(14, 16, 22, 255).getRGB(), tagSize);
        cursor += tagBoxW + sepGap;

        RenderUtil.drawRoundedRectWithGl(cursor, centerY - infoCap / 2.0F - 2.0F, cursor + 1.0F,
                centerY + infoCap / 2.0F + 2.0F, 0.5F, new Color(255, 255, 255, 60).getRGB());
        cursor += 1.0F + sepGap;

        float progress = Math.min(1.0F, phasePos / (float) PHASE_MS);
        float lineX1 = cursor;
        float lineX2 = cursor + infoW;
        RenderUtil.drawRoundedRectWithGl(lineX1, y + cardH - 3.0F, lineX2, y + cardH - 2.0F, 0.5F,
                new Color(255, 255, 255, 26).getRGB());
        RenderUtil.drawRoundedRectWithGl(lineX1, y + cardH - 3.0F, lineX1 + infoW * progress, y + cardH - 2.0F, 0.5F,
                new Color(ar, ag, ab, 220).getRGB());

        float clipTop = y + 2.0F;
        float clipBottom = y + cardH - 4.0F;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int factor = sr.getScaleFactor();
        GL11.glScissor((int) Math.floor(cursor * uiScale * factor),
                (int) Math.floor((sr.getScaledHeight() - clipBottom * uiScale) * factor),
                (int) Math.ceil((infoW + 2.0F) * uiScale * factor),
                (int) Math.ceil((clipBottom - clipTop) * uiScale * factor));
        float infoBase = centerY + infoCap / 2.0F;
        int curAlpha = (int) ((1.0F - anim) * 235.0F);
        if (curAlpha > 3) {
            this.drawLucidText(curText, cursor, infoBase - 7.0F * anim, new Color(205, 211, 224, curAlpha).getRGB(), infoSize);
        }
        if (anim > 0.005F) {
            int nextAlpha = (int) (anim * 235.0F);
            if (nextAlpha > 3) {
                this.drawLucidText(nextText, cursor, infoBase + 7.0F - 7.0F * anim, new Color(205, 211, 224, nextAlpha).getRGB(), infoSize);
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawLucidText(String text, float x, float baseline, int color, float size) {
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        FontManager.drawString(text, x, baseline - FontManager.getBaseline(size), color, false, size);
    }

    private int getPing() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) return 0;
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info != null ? info.getResponseTime() : 0;
    }

    private void renderClassic(String curText, String nextText, float anim, Color themeColor) {
        float uiScale = this.scale.getValue();
        float textScale = this.fontScale.getValue();
        float curW = FontManager.getStringWidth(curText) * textScale;
        float nextW = FontManager.getStringWidth(nextText) * textScale;
        float x = this.offX.getValue();
        float y = this.offY.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
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
