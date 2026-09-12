package leader.module.modules.render.notification;

import leader.Leader;
import leader.event.EventTarget;
import leader.events.Render2DEvent;
import leader.module.Module;
import leader.module.modules.render.FontManager;
import leader.module.modules.render.HUD;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Notification extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final List<NotificationEntry> entries = new ArrayList<>();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"RIGHT", "LEFT"});
    public final ModeProperty style = new ModeProperty("style", 1, new String[]{"CLASSIC", "MODERN", "8BIT", "AURA", "FROST"});
    public final IntProperty duration = new IntProperty("duration", 1500, 500, 5000);
    public final IntProperty maxAlerts = new IntProperty("max-alerts", 5, 1, 10);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final FloatProperty fontScale = new FloatProperty("font-scale", 1.0F, 0.7F, 1.5F);
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 20, 0, 255);
    public final BooleanProperty blur = new BooleanProperty("blur", false);
    public final IntProperty blurIterations = new IntProperty("blur-iterations", 2, 1, 8, blur::getValue);
    public final IntProperty blurOffset = new IntProperty("blur-offset", 3, 1, 10, blur::getValue);
    public final BooleanProperty pixelIcon = new BooleanProperty("pixel-icon", true, () -> this.style.getValue() == 2);
    public final BooleanProperty pixelBlink = new BooleanProperty("pixel-blink", true, () -> this.style.getValue() == 2);
    public final BooleanProperty scanlines = new BooleanProperty("scanlines", true, () -> this.style.getValue() == 2);
    private Framebuffer stencilBlur;

    public Notification() {
        super("Notification", false);
    }

    public static void addNotification(String text, NoticeMode noticeMode) {
        entries.add(new NotificationEntry(text, noticeMode, System.currentTimeMillis()));
        Notification notification = (Notification) Leader.moduleManager.modules.get(Notification.class);
        if (notification != null) {
            int max = notification.maxAlerts.getValue();
            while (entries.size() > max) {
                entries.remove(0);
            }
        }
    }

    private float getAlpha(long now, long start, long dur) {
        float elapsed = now - start;
        float fadeIn = Math.min(dur * 0.15F, 200.0F);
        float fadeOut = Math.min(dur * 0.20F, 300.0F);
        if (elapsed < fadeIn) {
            return elapsed / fadeIn;
        }
        if (elapsed > dur - fadeOut) {
            return (dur - elapsed) / fadeOut;
        }
        return 1.0F;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        float screenWidth = sr.getScaledWidth();
        float screenHeight = sr.getScaledHeight();
        long now = System.currentTimeMillis();
        long dur = this.duration.getValue();
        entries.removeIf(entry -> now - entry.startTime > dur);
        if (entries.isEmpty()) return;

        if (this.style.getValue() == 1) {
            renderModern(sr, now, dur);
            return;
        }
        if (this.style.getValue() == 2) {
            renderEightBit(sr, now, dur);
            return;
        }
        if (this.style.getValue() == 3) {
            renderAura(sr, now, dur);
            return;
        }
        if (this.style.getValue() == 4) {
            renderFrost(sr, now, dur);
            return;
        }

        float cardWidth = 100.0F;
        float cardHeight = 20.0F;
        float gap = 3.0F;
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float textY = (cardHeight - textHeight) / 2.0F;

        float offX = this.offsetX.getValue() + 4.0F;
        float offY = this.offsetY.getValue() + 4.0F;
        boolean isRight = this.mode.getValue() == 0;
        boolean doBlur = this.blur.getValue();
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float step = cardHeight + gap;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);

        float baseX = isRight ? screenWidth - cardWidth - offX : offX;
        float baseY = screenHeight - offY - cardHeight;

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = getAlpha(now, entry.startTime, dur);
            int idx = max - 1 - i;
            float targetY = (baseY - idx * step) * invScale;
            if (Float.isNaN(entry.animY)) entry.animY = targetY;
            entry.animY += (targetY - entry.animY) * reflow;
            float y = entry.animY;
            float x = baseX * invScale;
            Color themeColor = switch (entry.noticeMode) {
                case Enable ->  new Color(0x00FF00);
                case Disable -> new Color(0xFF4444);
                case Info -> new Color(0xFF6D19);
            };

            if (doBlur) {
                final float bx = x;
                final float by = y;
                ShaderElement.addBlurTask(() -> {
                    RenderUtil.enableRenderState();
                    RenderUtil.drawRect(bx, by, bx + cardWidth, by + cardHeight, -1);
                    RenderUtil.disableRenderState();
                });
            }

            float bgAlpha = 0.4F * alpha;
            float fillWidth = cardWidth * progress;
            float fillAlpha = Math.min(0.3F * alpha, 1.0F);
            float borderAlpha = 0.25F * alpha;

            RenderUtil.enableRenderState();
            RenderUtil.drawRect(x, y, x + cardWidth, y + cardHeight, new Color(0.0F, 0.0F, 0.0F, bgAlpha).getRGB());

            int fillColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (fillAlpha * 255.0F)).getRGB();
            RenderUtil.drawRect(x, y, x + fillWidth, y + cardHeight, fillColor);

            int borderColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (borderAlpha * 255.0F)).getRGB();
            RenderUtil.drawLine(x, y, x + cardWidth, y, 1.0F, borderColor);
            RenderUtil.drawLine(x, y + cardHeight, x + cardWidth, y + cardHeight, 1.0F, borderColor);
            RenderUtil.drawLine(x, y, x, y + cardHeight, 1.0F, borderColor);
            RenderUtil.drawLine(x + cardWidth, y, x + cardWidth, y + cardHeight, 1.0F, borderColor);
            RenderUtil.disableRenderState();

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int nameColor = new Color(1.0F, 1.0F, 1.0F, alpha).getRGB();
            int iconColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (alpha * 255.0F)).getRGB();

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 4.0F, y + textY, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(entry.text, 0.0F, 0.0F, nameColor, false);
            GlStateManager.popMatrix();

            float iconSize = 8.0F;
            float iconX = x + cardWidth - 4.0F - iconSize;
            float iconY = y + (cardHeight - iconSize) / 2.0F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(iconX, iconY, 0.0F);
            GlStateManager.disableTexture2D();
            GL11.glLineWidth(2.0F);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(themeColor.getRed() / 255f, themeColor.getGreen() / 255f, themeColor.getBlue() / 255f, alpha);
            switch (entry.noticeMode) {
                case Enable -> {
                    // 叹号主体
                    GL11.glVertex2f(1.0F, iconSize * 0.55F);
                    GL11.glVertex2f(iconSize * 0.45F, iconSize - 1.0F);
                    // 叹号底部像素点
                    GL11.glVertex2f(iconSize * 0.45F, iconSize - 1.0F);
                    GL11.glVertex2f(iconSize - 1.0F, 1.0F);
                }
                case Disable -> {
                    GL11.glVertex2f(1.0F, 1.0F);
                    GL11.glVertex2f(iconSize - 1.0F, iconSize - 1.0F);
                    GL11.glVertex2f(iconSize - 1.0F, 1.0F);
                    GL11.glVertex2f(1.0F, iconSize - 1.0F);
                }
                case Info -> {
                    float centerX = iconSize * 0.5F;
                    GL11.glVertex2f(centerX, 1.0F);
                    GL11.glVertex2f(centerX, iconSize * 0.62F);

                    GL11.glVertex2f(centerX - 1.0F, iconSize * 0.82F);
                    GL11.glVertex2f(centerX + 1.0F, iconSize * 0.82F);
                }
            }
            GL11.glEnd();
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(2.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }

    private void renderModern(ScaledResolution sr, long now, long dur) {
        float cardWidth = 136.0F;
        float cardHeight = 34.0F;
        float gap = 5.0F;
        float radius = 6.0F;
        float textScale = this.fontScale.getValue();
        float offX = this.offsetX.getValue() + 6.0F;
        float offY = this.offsetY.getValue() + 8.0F;
        boolean isRight = this.mode.getValue() == 0;
        boolean doBlur = this.blur.getValue();
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float baseX = isRight ? sr.getScaledWidth() - cardWidth - offX : offX;
        float baseY = sr.getScaledHeight() - offY - cardHeight;
        float step = cardHeight + gap;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = Math.max(0.0F, Math.min(1.0F, getAlpha(now, entry.startTime, dur)));
            int idx = max - 1 - i;
            float slide = (1.0F - alpha) * 18.0F;
            float x = (baseX + (isRight ? slide : -slide)) * invScale;
            float targetY = (baseY - idx * step) * invScale;
            if (Float.isNaN(entry.animY)) entry.animY = targetY;
            entry.animY += (targetY - entry.animY) * reflow;
            float y = entry.animY;
            Color themeColor = switch (entry.noticeMode) {
                case Enable ->  new Color(96, 224, 150);
                case Disable -> new Color(255, 110, 11);
                case Info -> new Color(255, 243, 122);
            };

            if (doBlur) {
                final float bx = x;
                final float by = y;
                ShaderElement.addBlurTask(() -> RenderUtil.drawRoundedRectWithGl(bx, by, bx + cardWidth, by + cardHeight, radius, -1));
            }
            int rimColor = new Color(255, 255, 255, (int) (30.0F * alpha)).getRGB();
            int glassColor = new Color(13, 15, 21, (int) (178.0F * alpha)).getRGB();
            int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (235.0F * alpha)).getRGB();
            int accentSoft = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (42.0F * alpha)).getRGB();
            int track = new Color(255, 255, 255, (int) (26.0F * alpha)).getRGB();

            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius, rimColor);
            RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 1.0F, x + cardWidth - 1.0F, y + cardHeight - 1.0F, radius - 1.0F, glassColor);
            // Left accent bar.
            RenderUtil.drawRoundedRectWithGl(x + 3.0F, y + 7.5F, x + 4.5F, y + cardHeight - 7.5F, 0.75F, accent);
            RenderUtil.drawRoundedRectWithGl(x + cardWidth - 23.0F, y + 7.0F, x + cardWidth - 9.0F, y + 21.0F, 4.0F, accentSoft);

            float progressY = y + cardHeight - 4.5F;
            RenderUtil.drawRoundedRectWithGl(x + 8.0F, progressY, x + cardWidth - 8.0F, progressY + 1.5F, 0.75F, track);
            RenderUtil.drawRoundedRectWithGl(x + 8.0F, progressY, x + 8.0F + (cardWidth - 16.0F) * (1.0F - progress), progressY + 1.5F, 0.75F, accent);

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int nameColor = new Color(245, 247, 252, (int) (245.0F * alpha)).getRGB();
            int stateColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (245.0F * alpha)).getRGB();
            String stateText = switch (entry.noticeMode) {
                case Enable -> "ON";
                case Disable -> "OFF";
                case Info -> "WARNING";
            };
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 10.0F, y + 5.0F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(entry.text, 0.0F, 0.0F, nameColor, false);
            FontManager.drawString(stateText, 0.0F, FontManager.getFontHeight() + 2.0F, stateColor, false);
            GlStateManager.popMatrix();

            drawStatusIcon(x + cardWidth - 20.0F, y + 10.0F, 8.0F, entry.noticeMode, themeColor, alpha);

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }

    private static final String[] PIXEL_CHECK = {
            "......X",
            ".....XX",
            "....XX.",
            "X..XX..",
            "XX.XX..",
            ".XXX...",
            "..X...."
    };
    private static final String[] PIXEL_CROSS = {
            "X.....X",
            ".X...X.",
            "..X.X..",
            "...X...",
            "..X.X..",
            ".X...X.",
            "X.....X"
    };

    private static final String[] PIXEL_EXCLAMATION = {
            "..XXX..",
            "..XXX..",
            "..XXX..",
            "..XXX..",
            ".......",
            "..XXX..",
            "..XXX.."
    };

    private void renderEightBit(ScaledResolution sr, long now, long dur) {
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float cardWidth = 140.0F;
        boolean showIcon = this.pixelIcon.getValue();
        boolean blink = this.pixelBlink.getValue();
        boolean showScanlines = this.scanlines.getValue();
        float cardHeight = Math.max(26.0F, textHeight + 14.0F);
        float gap = 4.0F;
        float offX = this.offsetX.getValue() + 6.0F;
        float offY = this.offsetY.getValue() + 8.0F;
        boolean isRight = this.mode.getValue() == 0;
        boolean doBlur = this.blur.getValue();
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float baseX = isRight ? sr.getScaledWidth() - cardWidth - offX : offX;
        float baseY = sr.getScaledHeight() - offY - cardHeight;
        float step = cardHeight + gap;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);
        final float notch = 3.0F;

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = Math.max(0.0F, Math.min(1.0F, getAlpha(now, entry.startTime, dur)));
            int idx = max - 1 - i;
            float slide = (1.0F - alpha) * 16.0F;
            // Snap the slide to whole pixels so the retro edges stay crisp.
            slide = Math.round(slide);
            float x = (baseX + (isRight ? slide : -slide)) * invScale;
            float targetY = (baseY - idx * step) * invScale;
            if (Float.isNaN(entry.animY)) entry.animY = targetY;
            entry.animY += (targetY - entry.animY) * reflow;
            float y = entry.animY;
            Color themeColor = switch (entry.noticeMode) {
                case Enable ->  new Color(61, 255, 110);
                case Disable -> new Color(255, 61, 92);
                case Info -> new Color(255, 250, 0);
            };

            int themeR = themeColor.getRed();
            int themeG = themeColor.getGreen();
            int themeB = themeColor.getBlue();

            if (doBlur) {
                final float bx = x;
                final float by = y;
                ShaderElement.addBlurTask(() -> {
                    RenderUtil.enableRenderState();
                    RenderUtil.drawRect(bx, by, bx + cardWidth, by + cardHeight, -1);
                    RenderUtil.disableRenderState();
                });
            }

            RenderUtil.enableRenderState();

            // Hard drop shadow, offset like an old console sprite.
            int shadowColor = new Color(0, 0, 0, (int) (110.0F * alpha)).getRGB();
            RenderUtil.drawRect(x + 3.0F, y + 3.0F, x + cardWidth + 3.0F, y + cardHeight + 3.0F, shadowColor);

            // Body with notched corners: center slab + two side slabs.
            int bodyColor = new Color(12, 12, 18, (int) (235.0F * alpha)).getRGB();
            RenderUtil.drawRect(x + notch, y, x + cardWidth - notch, y + cardHeight, bodyColor);
            RenderUtil.drawRect(x, y + notch, x + notch, y + cardHeight - notch, bodyColor);
            RenderUtil.drawRect(x + cardWidth - notch, y + notch, x + cardWidth, y + cardHeight - notch, bodyColor);

            // Chunky 2px pixel border following the notched silhouette.
            int borderColor = new Color(themeR, themeG, themeB, (int) (255.0F * alpha)).getRGB();
            int darkBorder = new Color(themeR / 3, themeG / 3, themeB / 3, (int) (255.0F * alpha)).getRGB();
            RenderUtil.drawRect(x + notch, y, x + cardWidth - notch, y + 2.0F, borderColor);
            RenderUtil.drawRect(x + notch, y + cardHeight - 2.0F, x + cardWidth - notch, y + cardHeight, darkBorder);
            RenderUtil.drawRect(x, y + notch, x + 2.0F, y + cardHeight - notch, borderColor);
            RenderUtil.drawRect(x + cardWidth - 2.0F, y + notch, x + cardWidth, y + cardHeight - notch, darkBorder);
            // Corner step pixels.
            RenderUtil.drawRect(x + 1.0F, y + 1.0F, x + notch, y + 2.0F, borderColor);
            RenderUtil.drawRect(x + 1.0F, y + 2.0F, x + 2.0F, y + notch, borderColor);
            RenderUtil.drawRect(x + cardWidth - notch, y + 1.0F, x + cardWidth - 1.0F, y + 2.0F, borderColor);
            RenderUtil.drawRect(x + cardWidth - 2.0F, y + 2.0F, x + cardWidth - 1.0F, y + notch, borderColor);
            RenderUtil.drawRect(x + 1.0F, y + cardHeight - 2.0F, x + notch, y + cardHeight - 1.0F, darkBorder);
            RenderUtil.drawRect(x + 1.0F, y + cardHeight - notch, x + 2.0F, y + cardHeight - 2.0F, darkBorder);
            RenderUtil.drawRect(x + cardWidth - notch, y + cardHeight - 2.0F, x + cardWidth - 1.0F, y + cardHeight - 1.0F, darkBorder);
            RenderUtil.drawRect(x + cardWidth - 2.0F, y + cardHeight - notch, x + cardWidth - 1.0F, y + cardHeight - 2.0F, darkBorder);

            // CRT scanlines.
            if (showScanlines) {
                int scanColor = new Color(0, 0, 0, (int) (36.0F * alpha)).getRGB();
                for (float ly = y + 3.0F; ly < y + cardHeight - 2.0F; ly += 3.0F) {
                    RenderUtil.drawRect(x + 2.0F, ly, x + cardWidth - 2.0F, ly + 1.0F, scanColor);
                }
            }

            // Segmented progress bar along the bottom.
            float segW = 6.0F;
            float segGap = 2.0F;
            float segY = y + cardHeight - 6.0F;
            float segX1 = x + 8.0F;
            float segX2 = x + cardWidth - 8.0F;
            int segCount = Math.max(1, (int) ((segX2 - segX1 + segGap) / (segW + segGap)));
            int lit = Math.round(progress * segCount);
            int segLit = new Color(themeR, themeG, themeB, (int) (255.0F * alpha)).getRGB();
            int segOff = new Color(themeR / 4, themeG / 4, themeB / 4, (int) (140.0F * alpha)).getRGB();
            for (int s = 0; s < segCount; s++) {
                float sx = segX1 + s * (segW + segGap);
                RenderUtil.drawRect(sx, segY, Math.min(sx + segW, segX2), segY + 3.0F, s < lit ? segLit : segOff);
            }

            RenderUtil.disableRenderState();

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            float textX = x + 8.0F;
            if (showIcon) {
                boolean visible = !blink || (now / 350L) % 2L == 0L;
                if (visible) {
                    drawPixelIcon(x + 8.0F, y + (cardHeight - 14.0F) / 2.0F - 1.0F, 2.0F, entry.noticeMode, themeColor, alpha);
                }
                textX = x + 26.0F;
            }

            int nameColor = new Color(255, 255, 255, (int) (255.0F * alpha)).getRGB();
            float textY = y + (cardHeight - textHeight) / 2.0F - 1.0F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(textX, textY, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(entry.text.toUpperCase(), 0.0F, 0.0F, nameColor, true);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }

    private void drawPixelIcon(float x, float y, float pixel, NoticeMode mode, Color themeColor, float alpha) {
        String[] pattern = switch (mode) {
            case Enable -> PIXEL_CHECK;
            case Disable -> PIXEL_CROSS;
            case Info -> PIXEL_EXCLAMATION;
        };

        int color = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (255.0F * alpha)).getRGB();
        int shadowColor = new Color(0, 0, 0, (int) (140.0F * alpha)).getRGB();
        RenderUtil.enableRenderState();
        for (int row = 0; row < pattern.length; row++) {
            String line = pattern[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != 'X') continue;
                float px = x + col * pixel;
                float py = y + row * pixel;
                RenderUtil.drawRect(px + 1.0F, py + 1.0F, px + pixel + 1.0F, py + pixel + 1.0F, shadowColor);
                RenderUtil.drawRect(px, py, px + pixel, py + pixel, color);
            }
        }
        RenderUtil.disableRenderState();
    }

    /** Draws a ring segment (TRIANGLE_STRIP arc band). Angles in degrees, 0 = east, 90 = south. */
    private void drawArcRing(float cx, float cy, float radius, float thickness, float startDeg, float sweepDeg, int color) {
        float a = ((color >> 24) & 255) / 255.0F;
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        float inner = radius - thickness;
        int segs = Math.max(8, (int) (Math.abs(sweepDeg) / 6.0F));
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double ang = Math.toRadians(startDeg + sweepDeg * i / (float) segs);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            wr.pos(cx + cos * radius, cy + sin * radius, 0.0D).color(r, g, b, a).endVertex();
            wr.pos(cx + cos * inner, cy + sin * inner, 0.0D).color(r, g, b, a).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * AURA style: minimal-text flat card. A glowing countdown ring on the left
     * replaces the progress bar; the only text is the module name.
     */
    private void renderAura(ScaledResolution sr, long now, long dur) {
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float cardWidth = 120.0F;
        float cardHeight = 30.0F;
        float gap = 4.0F;
        float radius = 8.0F;
        float offX = this.offsetX.getValue() + 6.0F;
        float offY = this.offsetY.getValue() + 8.0F;
        boolean isRight = this.mode.getValue() == 0;
        boolean doBlur = this.blur.getValue();
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float baseX = isRight ? sr.getScaledWidth() - cardWidth - offX : offX;
        float baseY = sr.getScaledHeight() - offY - cardHeight;
        float step = cardHeight + gap;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = Math.max(0.0F, Math.min(1.0F, getAlpha(now, entry.startTime, dur)));
            int idx = max - 1 - i;
            float slide = (1.0F - alpha) * 14.0F;
            float x = (baseX + (isRight ? slide : -slide)) * invScale;
            float targetY = (baseY - idx * step) * invScale;
            if (Float.isNaN(entry.animY)) entry.animY = targetY;
            entry.animY += (targetY - entry.animY) * reflow;
            float y = entry.animY;
            Color themeColor = switch (entry.noticeMode) {
                    case Enable -> new Color(96, 224, 150);
                    case Disable -> new Color(255, 110, 116);
                    case Info -> new Color(255, 245, 70);
            };
            if (doBlur) {
                final float bx = x;
                final float by = y;
                ShaderElement.addBlurTask(() -> RenderUtil.drawRoundedRectWithGl(bx, by, bx + cardWidth, by + cardHeight, radius, -1));
            }

            // Flat, solid card with a soft offset shadow. No glass layers.
            RenderUtil.drawRoundedRectWithGl(x, y + 2.0F, x + cardWidth, y + cardHeight + 2.0F, radius,
                    new Color(0, 0, 0, (int) (55.0F * alpha)).getRGB());
            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius,
                    new Color(20, 22, 27, (int) (225.0F * alpha)).getRGB());

            // Countdown ring on the left (remaining time), glowing theme arc.
            float ringCX = x + 17.0F;
            float ringCY = y + cardHeight / 2.0F;
            float sweep = Math.max((1.0F - progress) * 360.0F, 2.0F);
            int trackCol = new Color(255, 255, 255, (int) (20.0F * alpha)).getRGB();
            int glowCol = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (42.0F * alpha)).getRGB();
            int arcCol = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (255.0F * alpha)).getRGB();
            drawArcRing(ringCX, ringCY, 8.0F, 1.75F, 0.0F, 360.0F, trackCol);
            drawArcRing(ringCX, ringCY, 8.0F, 3.5F, -90.0F, sweep, glowCol);
            drawArcRing(ringCX, ringCY, 8.0F, 1.75F, -90.0F, sweep, arcCol);

            // Status dot in the ring center.
            GlStateManager.disableDepth();
            RenderUtil.fillCircle(ringCX, ringCY, 2.5D, 20, arcCol);
            GlStateManager.enableDepth();

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Module name — the only text. Trimmed to fit beside the ring.
            String name = entry.text;
            float maxNameW = (cardWidth - 44.0F) / textScale;
            if (FontManager.getStringWidth(name) > maxNameW) {
                while (name.length() > 1 && FontManager.getStringWidth(name + "..") > maxNameW) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "..";
            }
            int nameColor = new Color(238, 242, 248, (int) (245.0F * alpha)).getRGB();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 32.0F, y + (cardHeight - textHeight) / 2.0F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(name, 0.0F, 0.0F, nameColor, false);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }

    private Color frostAccent(NoticeMode noticeMode) {
        switch (noticeMode) {
            case Disable: return new Color(0xFF, 0x6B, 0x7E);
            case Info: return new Color(0xFF, 0xC4, 0x6B);
            default: return new Color(0x6F, 0xB2, 0xFF);
        }
    }

    private void renderFrost(ScaledResolution sr, long now, long dur) {
        float textScale = this.fontScale.getValue();
        float offX = this.offsetX.getValue() + 6.0F;
        float offY = this.offsetY.getValue() + 8.0F;
        float localScale = this.scale.getValue();
        float invScale = 1.0F / localScale;
        boolean isRight = this.mode.getValue() == 0;
        boolean doBlur = this.blur.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());

        float cardHeight = 26.0F;
        float radius = 8.0F;
        float barInset = 5.0F;
        float padLeft = 12.0F;
        float padRight = 10.0F;
        float minWidth = 96.0F;
        float maxWidth = 200.0F;
        float textHeight = FontManager.getFontHeight() * textScale;
        float step = cardHeight + 5.0F;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);
        float baseY = sr.getScaledHeight() - offY - cardHeight;

        GlStateManager.pushMatrix();
        GlStateManager.scale(localScale, localScale, 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = Math.max(0.0F, Math.min(1.0F, getAlpha(now, entry.startTime, dur)));
            Color accent = frostAccent(entry.noticeMode);
            int ar = accent.getRed();
            int ag = accent.getGreen();
            int ab = accent.getBlue();

            String name = entry.text;
            float maxNameWidth = (maxWidth - padLeft - padRight) / textScale;
            if (FontManager.getStringWidth(name) > maxNameWidth) {
                while (name.length() > 1 && FontManager.getStringWidth(name + "..") > maxNameWidth) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "..";
            }

            float cardWidth = Math.max(minWidth, Math.min(maxWidth,
                    padLeft + FontManager.getStringWidth(name) * textScale + padRight));

            int idx = max - 1 - i;
            float slide = (1.0F - alpha) * 14.0F;
            float targetX = (isRight ? sr.getScaledWidth() - offX - cardWidth + slide : offX - slide) * invScale;
            float targetY = (baseY - idx * step) * invScale;
            if (Float.isNaN(entry.animX)) {
                entry.animX = targetX;
                entry.animY = targetY;
            }
            entry.animX += (targetX - entry.animX) * reflow;
            entry.animY += (targetY - entry.animY) * reflow;
            float x = entry.animX;
            float y = entry.animY;

            if (doBlur) {
                final float bx = x;
                final float by = y;
                final float bw = cardWidth;
                final float bh = cardHeight;
                final float br = radius;
                ShaderElement.addBlurTask(() -> RenderUtil.drawRoundedRectWithGl(bx, by, bx + bw, by + bh, br, -1));
            }

            RenderUtil.drawZenGlass(x, y, x + cardWidth, y + cardHeight, radius, alpha);
            RenderUtil.drawRoundedRectWithGl(x + 4.0F, y + barInset, x + 7.0F, y + cardHeight - barInset, 1.5F,
                    new Color(ar, ag, ab, (int) (250.0F * alpha)).getRGB());

            float lineY = y + cardHeight - 3.6F;
            float lineLeft = x + padLeft;
            float lineRight = x + cardWidth - padRight;
            RenderUtil.drawRoundedRectWithGl(lineLeft, lineY, lineRight, lineY + 1.6F,
                    Math.min(0.8F, (lineRight - lineLeft) / 2.0F),
                    new Color(255, 255, 255, (int) (28.0F * alpha)).getRGB());
            float remain = 1.0F - progress;
            if (remain > 0.004F) {
                float fillRight = lineLeft + (lineRight - lineLeft) * remain;
                RenderUtil.drawRoundedRectWithGl(lineLeft, lineY, fillRight, lineY + 1.6F,
                        Math.min(0.8F, (fillRight - lineLeft) / 2.0F),
                        new Color(ar, ag, ab, (int) (250.0F * alpha)).getRGB());
            }

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            float contentWidth = cardWidth - padLeft - padRight;
            float textX = x + padLeft + Math.max(0.0F,
                    (contentWidth - FontManager.getStringWidth(name) * textScale) / 2.0F);
            GlStateManager.pushMatrix();
            GlStateManager.translate(textX, y + (cardHeight - textHeight) / 2.0F - 2.2F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(name, 0.0F, 0.0F, new Color(244, 247, 252, (int) (238.0F * alpha)).getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }

    private void drawStatusIcon(float x, float y, float iconSize, NoticeMode noticeMode, Color themeColor, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.0F);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glColor4f(themeColor.getRed() / 255f, themeColor.getGreen() / 255f, themeColor.getBlue() / 255f, alpha);
        GL11.glBegin(GL11.GL_LINES);

        switch (noticeMode) {
            case Enable -> {
                GL11.glVertex2f(1.0F, iconSize * 0.55F);
                GL11.glVertex2f(iconSize * 0.42F, iconSize - 1.0F);

                GL11.glVertex2f(iconSize * 0.42F, iconSize - 1.0F);
                GL11.glVertex2f(iconSize - 1.0F, 1.0F);
            }

            case Disable -> {
                GL11.glVertex2f(1.0F, 1.0F);
                GL11.glVertex2f(iconSize - 1.0F, iconSize - 1.0F);

                GL11.glVertex2f(iconSize - 1.0F, 1.0F);
                GL11.glVertex2f(1.0F, iconSize - 1.0F);
            }

            case Info -> {
                float center = iconSize * 0.5F;
                float radius = (iconSize - 2.0F) * 0.5F;
                int segments = 16;

                for (int i = 0; i < segments; i++) {
                    double angle1 = Math.PI * 2.0D * i / segments;
                    double angle2 = Math.PI * 2.0D * (i + 1) / segments;

                    GL11.glVertex2f(
                            center + (float) Math.cos(angle1) * radius,
                            center + (float) Math.sin(angle1) * radius
                    );

                    GL11.glVertex2f(
                            center + (float) Math.cos(angle2) * radius,
                            center + (float) Math.sin(angle2) * radius
                    );
                }

                GL11.glVertex2f(center, iconSize * 0.68F);
                GL11.glVertex2f(center, iconSize * 0.36F);

                GL11.glVertex2f(center - 1.0F, iconSize * 0.18F);
                GL11.glVertex2f(center + 1.0F, iconSize * 0.18F);
            }
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public void drawBlur() {
        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.blur.getValue()) return;
        if (!this.blur.getValue()) return;
        if (stencilBlur == null) {
            stencilBlur = ShaderElement.createFrameBuffer(null);
        }
        stencilBlur.framebufferClear();
        stencilBlur.bindFramebuffer(false);
        for (Runnable runnable : ShaderElement.getTasks()) {
            runnable.run();
        }
        ShaderElement.getTasks().clear();
        stencilBlur.unbindFramebuffer();
        leader.util.shader.KawaseBlur.renderBlur(stencilBlur.framebufferTexture, blurIterations.getValue(), blurOffset.getValue());
    }

    private static class NotificationEntry {
        final String text;
        final NoticeMode noticeMode;
        final long startTime;
        float animX = Float.NaN;
        float animY = Float.NaN;

        NotificationEntry(String text, NoticeMode mode, long startTime) {
            this.text = text;
            this.noticeMode = mode;
            this.startTime = startTime;
        }
    }

}
