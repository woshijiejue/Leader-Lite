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
import leader.util.Icon;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Notification extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final List<NotificationEntry> entries = new ArrayList<>();
    private float lastLayoutX = Float.NaN;
    private float lastLayoutY = Float.NaN;
    private float lastLayoutScale = Float.NaN;
    private float lastLayoutFontScale = Float.NaN;
    private int lastLayoutStyle = -1;
    private int lastLayoutMode = -1;
    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;
    private boolean previewing;
    private float previewX, previewY, previewAlpha;
    private NoticeMode previewMode = NoticeMode.Enable;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"RIGHT", "LEFT"});
    public final ModeProperty style = new ModeProperty("style", 1, new String[]{"CLASSIC", "MODERN", "8BIT", "AURA", "FROST", "LUCID", "SLATE"});
    public final IntProperty duration = new IntProperty("duration", 1500, 500, 5000);
    public final IntProperty maxAlerts = new IntProperty("max-alerts", 5, 1, 10);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final FloatProperty fontScale = new FloatProperty("font-scale", 1.0F, 0.7F, 1.5F);
    public final BooleanProperty pixelIcon = new BooleanProperty("pixel-icon", true, () -> this.style.getValue() == 2);
    public final BooleanProperty pixelBlink = new BooleanProperty("pixel-blink", true, () -> this.style.getValue() == 2);
    public final BooleanProperty scanlines = new BooleanProperty("scanlines", true, () -> this.style.getValue() == 2);

    public Notification() {
        super("Notification", false);
    }

    public static int count() {
        return entries.size();
    }

    public static boolean hasLatest() {
        return !entries.isEmpty();
    }

    public static String latestText() {
        return entries.isEmpty() ? "" : entries.get(entries.size() - 1).text;
    }

    public static String latestDescription() {
        return entries.isEmpty() ? "" : entries.get(entries.size() - 1).description;
    }

    public static NoticeMode latestMode() {
        return entries.isEmpty() ? NoticeMode.Info : entries.get(entries.size() - 1).noticeMode;
    }

    public static long latestStartTime() {
        return entries.isEmpty() ? 0L : entries.get(entries.size() - 1).startTime;
    }

    public static int latestColor() {
        if (entries.isEmpty()) return 0xFFFFFF;
        switch (entries.get(entries.size() - 1).noticeMode) {
            case Enable:
                return 0x00FF00;
            case Disable:
                return 0xFF4444;
            default:
                return 0xFF6D19;
        }
    }

    public static float latestProgress() {
        if (entries.isEmpty()) return 0.0F;
        Notification notification = (Notification) Leader.moduleManager.modules.get(Notification.class);
        if (notification == null) return 0.0F;
        NotificationEntry entry = entries.get(entries.size() - 1);
        float dur = notification.duration.getValue();
        float elapsed = System.currentTimeMillis() - entry.startTime;
        float progress = 1.0F - elapsed / dur;
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    public static void addNotification(String text, NoticeMode noticeMode) {
        addNotification(text, "", noticeMode);
    }

    public static void addNotification(String text, String description, NoticeMode noticeMode) {
        entries.add(new NotificationEntry(text, description, noticeMode, System.currentTimeMillis()));
        Notification notification = (Notification) Leader.moduleManager.modules.get(Notification.class);
        if (notification != null) {
            int max = notification.maxAlerts.getValue();
            while (entries.size() > max) {
                entries.remove(0);
            }
        }
    }

    private float getAlpha(long now, long start, long dur) {
        if (previewing) return previewAlpha;
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
        long now = System.currentTimeMillis();
        long dur = this.duration.getValue();
        entries.removeIf(entry -> now - entry.startTime > dur);
        if (Leader.hudElementManager.isSuppressed("Notification")) return;
        if (mc.currentScreen instanceof leader.ui.GuiHUDDesigner) return;
        ScaledResolution sr = new ScaledResolution(mc);
        resetLayoutIfChanged(sr);
        float screenWidth = sr.getScaledWidth();
        float screenHeight = sr.getScaledHeight();
        if (entries.isEmpty()) return;

        renderEntries(sr, now, dur, entries);
    }

    private void renderEntries(ScaledResolution sr, long now, long dur, List<NotificationEntry> entries) {
        if (this.style.getValue() == 1) {
            renderModern(sr, now, dur, entries);
            return;
        }
        if (this.style.getValue() == 2) {
            renderEightBit(sr, now, dur, entries);
            return;
        }
        if (this.style.getValue() == 3) {
            renderAura(sr, now, dur, entries);
            return;
        }
        if (this.style.getValue() == 4) {
            renderFrost(sr, now, dur, entries);
            return;
        }
        if (this.style.getValue() == 5) {
            renderLucid(sr, now, dur, entries);
            return;
        }
        if (this.style.getValue() == 6) {
            renderSlate(sr, now, dur, entries);
            return;
        }

        float cardWidth = 100.0F;
        float cardHeight = 20.0F;
        float gap = 3.0F;
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float textY = (cardHeight - textHeight) / 2.0F;

        float offX = renderOriginX(4);
        float offY = renderOriginY(4);
        boolean isRight = this.mode.getValue() == 0;
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float step = cardHeight + gap;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);

        float baseX = offX;
        float baseY = offY;

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = getAlpha(now, entry.startTime, dur);
            int idx = max - 1 - i;
            float targetY = cardY(sr, baseY, cardHeight, idx * step);
            if (Float.isNaN(entry.animY)) entry.animY = targetY;
            entry.animY += (targetY - entry.animY) * reflow;
            float y = entry.animY;
            float x = cardX(sr, baseX, cardWidth, 0);
            Color themeColor = switch (entry.noticeMode) {
                case Enable ->  new Color(0x00FF00);
                case Disable -> new Color(0xFF4444);
                case Info -> new Color(0xFF6D19);
            };

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(bx, by, bx + cardWidth, by + cardHeight, -1);
                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();
            });

            float bgAlpha = 0.4F * alpha;
            float fillWidth = cardWidth * progress;
            float fillAlpha = Math.min(0.3F * alpha, 1.0F);
            float borderAlpha = 0.25F * alpha;

            RenderUtil.enableRenderState();
            RenderUtil.drawRect(x, y, x + cardWidth, y + cardHeight, Leader.hudElementManager.background("Notification", 2.0F, 20.0F, bgAlpha));

            int fillColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (fillAlpha * 255.0F)).getRGB();
            RenderUtil.drawRect(x, y, x + fillWidth, y + cardHeight, fillColor);

            int borderColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (borderAlpha * 255.0F)).getRGB();
            float px = 1.0F / (sr.getScaleFactor() * this.scale.getValue());
            RenderUtil.drawRect(x, y, x + cardWidth, y + px, borderColor);
            RenderUtil.drawRect(x, y + cardHeight - px, x + cardWidth, y + cardHeight, borderColor);
            RenderUtil.drawRect(x, y + px, x + px, y + cardHeight - px, borderColor);
            RenderUtil.drawRect(x + cardWidth - px, y + px, x + cardWidth, y + cardHeight - px, borderColor);
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

                    GL11.glVertex2f(1.0F, iconSize * 0.55F);
                    GL11.glVertex2f(iconSize * 0.45F, iconSize - 1.0F);

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

    private void renderModern(ScaledResolution sr, long now, long dur, List<NotificationEntry> entries) {
        float cardWidth = 136.0F;
        float cardHeight = 34.0F;
        float gap = 5.0F;
        float radius = 6.0F;
        float textScale = this.fontScale.getValue();
        float offX = renderOriginX(6);
        float offY = renderOriginY(8);
        boolean isRight = this.mode.getValue() == 0;
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float baseX = offX;
        float baseY = offY;
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
            float x = cardX(sr, baseX, cardWidth, isRight ? slide : -slide);
            float targetY = cardY(sr, baseY, cardHeight, idx * step);
            if (Float.isNaN(entry.animY)) entry.animY = targetY;
            entry.animY += (targetY - entry.animY) * reflow;
            float y = entry.animY;
            Color themeColor = switch (entry.noticeMode) {
                case Enable ->  new Color(96, 224, 150);
                case Disable -> new Color(255, 110, 11);
                case Info -> new Color(255, 243, 122);
            };

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.drawRoundedRectWithGl(bx, by, bx + cardWidth, by + cardHeight, radius, -1);
                GlStateManager.popMatrix();
            });

            int rimColor = new Color(255, 255, 255, (int) (30.0F * alpha)).getRGB();
            int glassColor = Leader.hudElementManager.background("Notification", 2.0F, 20.0F, 178.0F * alpha / 255.0F);
            int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (235.0F * alpha)).getRGB();
            int accentSoft = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (42.0F * alpha)).getRGB();
            int track = new Color(255, 255, 255, (int) (26.0F * alpha)).getRGB();

            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius, rimColor);
            RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 1.0F, x + cardWidth - 1.0F, y + cardHeight - 1.0F, radius - 1.0F, glassColor);

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

    private void renderEightBit(ScaledResolution sr, long now, long dur, List<NotificationEntry> entries) {
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float cardWidth = 140.0F;
        boolean showIcon = this.pixelIcon.getValue();
        boolean blink = this.pixelBlink.getValue();
        boolean showScanlines = this.scanlines.getValue();
        float cardHeight = Math.max(26.0F, textHeight + 14.0F);
        float gap = 4.0F;
        float offX = renderOriginX(6);
        float offY = renderOriginY(8);
        boolean isRight = this.mode.getValue() == 0;
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float baseX = offX;
        float baseY = offY;
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

            slide = Math.round(slide);
            float x = cardX(sr, baseX, cardWidth, isRight ? slide : -slide);
            float targetY = cardY(sr, baseY, cardHeight, idx * step);
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

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(bx, by, bx + cardWidth, by + cardHeight, -1);
                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();
            });

            RenderUtil.enableRenderState();

            int shadowColor = new Color(0, 0, 0, (int) (110.0F * alpha)).getRGB();
            RenderUtil.drawRect(x + 3.0F, y + 3.0F, x + cardWidth + 3.0F, y + cardHeight + 3.0F, shadowColor);

            int bodyColor = Leader.hudElementManager.background("Notification", 2.0F, 20.0F, 235.0F * alpha / 255.0F);
            RenderUtil.drawRect(x + notch, y, x + cardWidth - notch, y + cardHeight, bodyColor);
            RenderUtil.drawRect(x, y + notch, x + notch, y + cardHeight - notch, bodyColor);
            RenderUtil.drawRect(x + cardWidth - notch, y + notch, x + cardWidth, y + cardHeight - notch, bodyColor);

            int borderColor = new Color(themeR, themeG, themeB, (int) (255.0F * alpha)).getRGB();
            int darkBorder = new Color(themeR / 3, themeG / 3, themeB / 3, (int) (255.0F * alpha)).getRGB();
            RenderUtil.drawRect(x + notch, y, x + cardWidth - notch, y + 2.0F, borderColor);
            RenderUtil.drawRect(x + notch, y + cardHeight - 2.0F, x + cardWidth - notch, y + cardHeight, darkBorder);
            RenderUtil.drawRect(x, y + notch, x + 2.0F, y + cardHeight - notch, borderColor);
            RenderUtil.drawRect(x + cardWidth - 2.0F, y + notch, x + cardWidth, y + cardHeight - notch, darkBorder);

            RenderUtil.drawRect(x + 1.0F, y + 1.0F, x + notch, y + 2.0F, borderColor);
            RenderUtil.drawRect(x + 1.0F, y + 2.0F, x + 2.0F, y + notch, borderColor);
            RenderUtil.drawRect(x + cardWidth - notch, y + 1.0F, x + cardWidth - 1.0F, y + 2.0F, borderColor);
            RenderUtil.drawRect(x + cardWidth - 2.0F, y + 2.0F, x + cardWidth - 1.0F, y + notch, borderColor);
            RenderUtil.drawRect(x + 1.0F, y + cardHeight - 2.0F, x + notch, y + cardHeight - 1.0F, darkBorder);
            RenderUtil.drawRect(x + 1.0F, y + cardHeight - notch, x + 2.0F, y + cardHeight - 2.0F, darkBorder);
            RenderUtil.drawRect(x + cardWidth - notch, y + cardHeight - 2.0F, x + cardWidth - 1.0F, y + cardHeight - 1.0F, darkBorder);
            RenderUtil.drawRect(x + cardWidth - 2.0F, y + cardHeight - notch, x + cardWidth - 1.0F, y + cardHeight - 2.0F, darkBorder);

            if (showScanlines) {
                int scanColor = new Color(0, 0, 0, (int) (36.0F * alpha)).getRGB();
                for (float ly = y + 3.0F; ly < y + cardHeight - 2.0F; ly += 3.0F) {
                    RenderUtil.drawRect(x + 2.0F, ly, x + cardWidth - 2.0F, ly + 1.0F, scanColor);
                }
            }

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

    private void renderAura(ScaledResolution sr, long now, long dur, List<NotificationEntry> entries) {
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float cardWidth = 120.0F;
        float cardHeight = 30.0F;
        float gap = 4.0F;
        float radius = 8.0F;
        float offX = renderOriginX(6);
        float offY = renderOriginY(8);
        boolean isRight = this.mode.getValue() == 0;
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float baseX = offX;
        float baseY = offY;
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
            float x = cardX(sr, baseX, cardWidth, isRight ? slide : -slide);
            float targetY = cardY(sr, baseY, cardHeight, idx * step);
            if (Float.isNaN(entry.animY)) entry.animY = targetY;
            entry.animY += (targetY - entry.animY) * reflow;
            float y = entry.animY;
            Color themeColor = switch (entry.noticeMode) {
                    case Enable -> new Color(96, 224, 150);
                    case Disable -> new Color(255, 110, 116);
                    case Info -> new Color(255, 245, 70);
            };
            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.drawRoundedRectWithGl(bx, by, bx + cardWidth, by + cardHeight, radius, -1);
                GlStateManager.popMatrix();
            });

            RenderUtil.drawRoundedRectWithGl(x, y + 2.0F, x + cardWidth, y + cardHeight + 2.0F, radius,
                    new Color(0, 0, 0, (int) (55.0F * alpha)).getRGB());
            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius,
                    Leader.hudElementManager.background("Notification", 2.0F, 20.0F, 225.0F * alpha / 255.0F));

            float ringCX = x + 17.0F;
            float ringCY = y + cardHeight / 2.0F;
            float sweep = Math.max((1.0F - progress) * 360.0F, 2.0F);
            int trackCol = new Color(255, 255, 255, (int) (20.0F * alpha)).getRGB();
            int glowCol = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (42.0F * alpha)).getRGB();
            int arcCol = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (255.0F * alpha)).getRGB();
            drawArcRing(ringCX, ringCY, 8.0F, 1.75F, 0.0F, 360.0F, trackCol);
            drawArcRing(ringCX, ringCY, 8.0F, 3.5F, -90.0F, sweep, glowCol);
            drawArcRing(ringCX, ringCY, 8.0F, 1.75F, -90.0F, sweep, arcCol);

            GlStateManager.disableDepth();
            RenderUtil.fillCircle(ringCX, ringCY, 2.5D, 20, arcCol);
            GlStateManager.enableDepth();

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

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
            case Disable: return new Color(0xEF, 0x44, 0x44);
            case Info: return new Color(0xF5, 0x9E, 0x0B);
            default: return new Color(0x3B, 0x82, 0xF6);
        }
    }

    private void renderFrost(ScaledResolution sr, long now, long dur, List<NotificationEntry> entries) {
        float textScale = this.fontScale.getValue();
        float offX = renderOriginX(6);
        float offY = renderOriginY(8);
        float localScale = this.scale.getValue();
        float invScale = 1.0F / localScale;
        boolean isRight = this.mode.getValue() == 0;
        int max = Math.min(entries.size(), this.maxAlerts.getValue());

        float cardHeight = 26.0F;
        float radius = 8.0F;
        float padLeft = 12.0F;
        float padRight = 8.0F;
        float minWidth = 96.0F;
        float maxWidth = 200.0F;
        float textHeight = FontManager.getFontHeight() * textScale;
        float step = cardHeight + 4.0F;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);
        float baseY = offY;

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

            float nameWidth = FontManager.getStringWidth(name) * textScale;
            float cardWidth = Math.max(minWidth, Math.min(maxWidth, padLeft + nameWidth + padRight));

            int idx = max - 1 - i;
            float slide = (1.0F - alpha) * 14.0F;
            float targetX = cardX(sr, offX, cardWidth, isRight ? slide : -slide);
            float targetY = cardY(sr, baseY, cardHeight, idx * step);
            if (Float.isNaN(entry.animX)) {
                entry.animX = targetX;
                entry.animY = targetY;
            }
            entry.animX += (targetX - entry.animX) * reflow;
            entry.animY += (targetY - entry.animY) * reflow;
            float x = entry.animX;
            float y = entry.animY;

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            final float bw = cardWidth;
            final float bh = cardHeight;
            final float br = radius;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.drawRoundedRectWithGl(bx, by, bx + bw, by + bh, br, -1);
                GlStateManager.popMatrix();
            });

            RenderUtil.drawRoundedRectWithGl(x + 0.5F, y + 1.8F, x + cardWidth + 0.5F, y + cardHeight + 1.8F,
                    radius, new Color(16, 20, 30, (int) (24.0F * alpha)).getRGB());
            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius,
                    new Color(255, 255, 255, (int) (140.0F * alpha)).getRGB());
            Color frostBg = Leader.hudElementManager.backgroundColor("Notification", 2.0F, 20.0F);
            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius,
                    new Color(frostBg.getRed(), frostBg.getGreen(), frostBg.getBlue(),
                            (int) (frostBg.getAlpha() * alpha * 0.5F)).getRGB());

            RenderUtil.drawRoundedRectWithGl(x + 4.0F, y + 5.0F, x + 7.0F, y + cardHeight - 5.0F, 1.5F,
                    new Color(ar, ag, ab, (int) (250.0F * alpha)).getRGB());

            float lineY = y + cardHeight - 3.6F;
            float lineLeft = x + padLeft;
            float lineRight = x + cardWidth - padRight;
            RenderUtil.drawRoundedRectWithGl(lineLeft, lineY, lineRight, lineY + 1.6F,
                    Math.min(0.8F, (lineRight - lineLeft) / 2.0F),
                    new Color(20, 24, 34, (int) (22.0F * alpha)).getRGB());
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

            float textX = x + padLeft + Math.max(0.0F, (cardWidth - padLeft - padRight - nameWidth) / 2.0F);
            GlStateManager.pushMatrix();
            GlStateManager.translate(textX, y + (cardHeight - textHeight) / 2.0F - 1.0F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(name, 0.0F, 0.0F, new Color(20, 24, 34, (int) (232.0F * alpha)).getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }

    private Icon lucidIcon(NoticeMode noticeMode) {
        switch (noticeMode) {
            case Disable: return Icon.CLOSE;
            case Info: return Icon.INFO;
            default: return Icon.CHECK;
        }
    }

    private void renderLucid(ScaledResolution sr, long now, long dur, List<NotificationEntry> entries) {
        float textScale = this.fontScale.getValue();
        float offX = renderOriginX(6);
        float offY = renderOriginY(8);
        float localScale = this.scale.getValue();
        float invScale = 1.0F / localScale;
        boolean isRight = this.mode.getValue() == 0;
        int max = Math.min(entries.size(), this.maxAlerts.getValue());

        float radius = 4.5F;
        float iconSize = 12.0F;
        float iconX = 7.0F;
        float padLeft = iconX + iconSize + 8.0F;
        float padRight = 9.0F;
        float minWidth = 90.0F;
        float maxWidth = 220.0F;
        float textHeight = FontManager.getFontHeight() * textScale;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);

        GlStateManager.pushMatrix();
        GlStateManager.scale(localScale, localScale, 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = Math.max(0.0F, Math.min(1.0F, getAlpha(now, entry.startTime, dur)));

            String title = entry.text;
            String description = entry.description;
            float maxTextWidth = (maxWidth - padLeft - padRight) / textScale;
            if (FontManager.getStringWidth(title) > maxTextWidth) {
                while (title.length() > 1 && FontManager.getStringWidth(title + "..") > maxTextWidth) {
                    title = title.substring(0, title.length() - 1);
                }
                title = title + "..";
            }
            if (!description.isEmpty() && FontManager.getStringWidth(description) > maxTextWidth) {
                while (description.length() > 1 && FontManager.getStringWidth(description + "..") > maxTextWidth) {
                    description = description.substring(0, description.length() - 1);
                }
                description = description + "..";
            }

            float titleWidth = FontManager.getStringWidth("\u00a7l" + title) * textScale;
            float descriptionWidth = FontManager.getStringWidth(description) * textScale;
            float contentWidth = Math.max(titleWidth, descriptionWidth);
            boolean twoLines = !description.isEmpty();
            float cardHeight = twoLines ? 4.5F + textHeight + 2.0F + textHeight + 5.0F : textHeight + 10.0F;
            float cardWidth = Math.max(minWidth, Math.min(maxWidth, padLeft + contentWidth + padRight));
            float step = cardHeight + 4.0F;
            float baseY = offY;

            int idx = max - 1 - i;
            float slide = (1.0F - alpha) * 14.0F;
            float targetX = cardX(sr, offX, cardWidth, isRight ? slide : -slide);
            float stackOffset = 0;
            for (int j = i + 1; j < max; j++) {
                boolean lines = !entries.get(j).description.isEmpty();
                stackOffset += (lines ? 4.5F + textHeight + 2 + textHeight + 5 : textHeight + 10) + 4;
            }
            float targetY = cardY(sr, baseY, cardHeight, stackOffset);
            if (Float.isNaN(entry.animX)) {
                entry.animX = targetX;
                entry.animY = targetY;
            }
            entry.animX += (targetX - entry.animX) * reflow;
            entry.animY += (targetY - entry.animY) * reflow;
            float x = entry.animX;
            float y = entry.animY;

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            final float bw = cardWidth;
            final float bh = cardHeight;
            final float br = radius;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.drawRoundedRectWithGl(bx, by, bx + bw, by + bh, br, -1);
                GlStateManager.popMatrix();
            });

            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius,
                    Leader.hudElementManager.background("Notification", 2.0F, 20.0F, 51.0F * alpha / 255.0F));

            float iconY = y + (cardHeight - iconSize) / 2.0F;
            lucidIcon(entry.noticeMode).draw(x + iconX, iconY, iconSize, 0xFFFFFF, alpha);

            float lineY = y + cardHeight - 3.2F;
            float lineLeft = x + padLeft;
            float lineRight = x + cardWidth - padRight;
            float remain = 1.0F - progress;
            if (remain > 0.004F) {
                float fillRight = lineLeft + (lineRight - lineLeft) * remain;
                RenderUtil.drawRoundedRectWithGl(lineLeft, lineY, fillRight, lineY + 1.4F,
                        Math.min(0.7F, (fillRight - lineLeft) / 2.0F),
                        new Color(255, 255, 255, (int) (70.0F * alpha)).getRGB());
            }

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            float textX = x + padLeft;
            if (twoLines) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(textX, y + 4.5F, 0.0F);
                GlStateManager.scale(textScale, textScale, 1.0F);
                FontManager.drawString("\u00a7l" + title, 0.0F, 0.0F,
                        new Color(255, 255, 255, (int) (252.0F * alpha)).getRGB(), false);
                GlStateManager.popMatrix();

                GlStateManager.pushMatrix();
                GlStateManager.translate(textX, y + 4.5F + textHeight + 2.0F, 0.0F);
                GlStateManager.scale(textScale, textScale, 1.0F);
                FontManager.drawString(description, 0.0F, 0.0F,
                        new Color(255, 255, 255, (int) (185.0F * alpha)).getRGB(), false);
                GlStateManager.popMatrix();
            } else {
                GlStateManager.pushMatrix();
                GlStateManager.translate(textX, y + (cardHeight - textHeight) / 2.0F, 0.0F);
                GlStateManager.scale(textScale, textScale, 1.0F);
                FontManager.drawString("\u00a7l" + title, 0.0F, 0.0F,
                        new Color(255, 255, 255, (int) (252.0F * alpha)).getRGB(), false);
                GlStateManager.popMatrix();
            }

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }

    private void renderSlate(ScaledResolution sr, long now, long dur, List<NotificationEntry> entries) {
        float textScale = this.fontScale.getValue();
        float subScale = Math.max(0.74F, textScale * 0.82F);
        float textHeight = FontManager.getFontHeight() * textScale;
        float subHeight = FontManager.getFontHeight() * subScale;
        float cardHeight = Math.max(30.0F, textHeight + subHeight + 9.0F);
        float radius = 3.5F;
        float iconWell = 27.0F;
        float textXOffset = iconWell + 7.0F;
        float padRight = 9.0F;
        float minWidth = 116.0F;
        float maxWidth = 220.0F;
        float offX = renderOriginX(6);
        float offY = renderOriginY(8);
        float localScale = this.scale.getValue();
        float invScale = 1.0F / localScale;
        boolean isRight = this.mode.getValue() == 0;
        int max = Math.min(entries.size(), this.maxAlerts.getValue());
        float step = cardHeight + 4.0F;
        float baseY = offY;
        float reflow = 1.0F - (float) Math.exp(-0.0165F * 16.0F);

        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        Color hudColor = hud != null ? hud.getColor(now) : new Color(92, 112, 210);

        GlStateManager.pushMatrix();
        GlStateManager.scale(localScale, localScale, 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float remain = 1.0F - progress;
            float alpha = Math.max(0.0F, Math.min(1.0F, getAlpha(now, entry.startTime, dur)));

            String title = entry.description.isEmpty() ? "Module" : entry.text;
            String statePrefix = entry.noticeMode == NoticeMode.Enable ? "Enabled "
                    : entry.noticeMode == NoticeMode.Disable ? "Disabled " : "";
            String detail = entry.description.isEmpty() ? statePrefix + entry.text : entry.description;
            float maxTextWidth = (maxWidth - textXOffset - padRight) / textScale;
            if (FontManager.getStringWidth(title) > maxTextWidth) {
                while (title.length() > 1 && FontManager.getStringWidth(title + "..") > maxTextWidth) {
                    title = title.substring(0, title.length() - 1);
                }
                title += "..";
            }
            float maxDetailWidth = (maxWidth - textXOffset - padRight) / subScale;
            if (FontManager.getStringWidth(detail) > maxDetailWidth) {
                while (detail.length() > 1 && FontManager.getStringWidth(detail + "..") > maxDetailWidth) {
                    detail = detail.substring(0, detail.length() - 1);
                }
                detail += "..";
            }
            float contentWidth = Math.max(FontManager.getStringWidth(title) * textScale,
                    FontManager.getStringWidth(detail) * subScale);
            float cardWidth = Math.max(minWidth, Math.min(maxWidth, textXOffset + contentWidth + padRight));

            int idx = max - 1 - i;
            float slide = (1.0F - alpha) * 14.0F;
            float targetX = cardX(sr, offX, cardWidth, isRight ? slide : -slide);
            float targetY = cardY(sr, baseY, cardHeight, idx * step);
            if (Float.isNaN(entry.animX)) {
                entry.animX = targetX;
                entry.animY = targetY;
            }
            entry.animX += (targetX - entry.animX) * reflow;
            entry.animY += (targetY - entry.animY) * reflow;
            float x = entry.animX;
            float y = entry.animY;

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            final float bw = cardWidth;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.drawRoundedRectWithGl(bx, by, bx + bw, by + cardHeight, radius, -1);
                GlStateManager.popMatrix();
            });

            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight,
                    radius, Leader.hudElementManager.background("Notification", 2.0F, 20.0F, 51.0F * alpha / 255.0F));

            float barWidth = Math.max(1.5F, (cardWidth - 4.0F) * remain);
            RenderUtil.drawRect(x + 2.0F, y + cardHeight - 1.0F,
                    x + 2.0F + barWidth, y + cardHeight,
                    new Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue(), (int) (235.0F * alpha)).getRGB());

            lucidIcon(entry.noticeMode).drawCentered(x + iconWell / 2.0F, y + cardHeight / 2.0F,
                    14.0F, 0xFFFFFF, alpha);

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + textXOffset, y + 4.0F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(title, 0.0F, 0.0F,
                    new Color(255, 255, 255, (int) (252.0F * alpha)).getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + textXOffset, y + 5.0F + textHeight, 0.0F);
            GlStateManager.scale(subScale, subScale, 1.0F);
            FontManager.drawString(detail, 0.0F, 0.0F,
                    new Color(255, 255, 255, (int) (220.0F * alpha)).getRGB(), false);
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

    private static class NotificationEntry {
        final String text;
        final String description;
        final NoticeMode noticeMode;
        final long startTime;
        float animX = Float.NaN;
        float animY = Float.NaN;

        NotificationEntry(String text, String description, NoticeMode mode, long startTime) {
            this.text = text;
            this.description = description == null ? "" : description;
            this.noticeMode = mode;
            this.startTime = startTime;
        }
    }

    public float[] previewSize() {
        float textHeight = FontManager.getFontHeight() * fontScale.getValue();
        float width, height;
        switch (style.getValue()) {
            case 0: width = 100; height = 20; break;
            case 1: width = 136; height = 34; break;
            case 2: width = 140; height = Math.max(26, textHeight + 14); break;
            case 3: width = 120; height = 30; break;
            case 4:
                width = Math.max(96, Math.min(200, 20 + FontManager.getStringWidth("Notification") * fontScale.getValue()));
                height = 26;
                break;
            case 5:
                width = Math.max(90, Math.min(220, 36 + Math.max(FontManager.getStringWidth("\u00a7lNotification"),
                        FontManager.getStringWidth("Enabled")) * fontScale.getValue()));
                height = 11.5F + textHeight * 2;
                break;
            default:
                float subScale = Math.max(0.74F, fontScale.getValue() * 0.82F);
                width = Math.max(116, Math.min(220, 43 + Math.max(FontManager.getStringWidth("Notification") * fontScale.getValue(),
                        FontManager.getStringWidth("Enabled") * subScale)));
                height = Math.max(30, textHeight + FontManager.getFontHeight() * subScale + 9);
        }
        return new float[]{width * scale.getValue(), height * scale.getValue()};
    }

    private float cardX(ScaledResolution sr, float originX, float width, float slide) {
        if (previewing) return originX / scale.getValue();
        // RIGHT uses the preview's right edge as the anchor, including variable-width styles.
        return NotificationLayout.x(sr.getScaledWidth(), originX, previewSize()[0], width,
                scale.getValue(), mode.getValue() == 0, slide);
    }

    private float cardY(ScaledResolution sr, float originY, float height, float stackOffset) {
        if (previewing) return (originY - stackOffset * scale.getValue()) / scale.getValue();
        // The newest card's bottom edge stays fixed; older cards stack upwards in scaled units.
        return NotificationLayout.y(sr.getScaledHeight(), originY, previewSize()[1], height,
                scale.getValue(), stackOffset);
    }

    private float renderOriginX(float padding) {
        return previewing ? previewX : Leader.hudElementManager.x("Notification", 2, 20) + padding;
    }

    private float renderOriginY(float padding) {
        return previewing ? previewY : Leader.hudElementManager.y("Notification", 2, 20) + padding;
    }

    private void resetLayoutIfChanged(ScaledResolution sr) {
        float x = Leader.hudElementManager.x("Notification", 2, 20);
        float y = Leader.hudElementManager.y("Notification", 2, 20);
        float sc = scale.getValue(), fs = fontScale.getValue();
        int st = style.getValue(), md = mode.getValue();
        if (x != lastLayoutX || y != lastLayoutY || sc != lastLayoutScale
                || fs != lastLayoutFontScale || st != lastLayoutStyle || md != lastLayoutMode
                || sr.getScaledWidth() != lastLayoutWidth || sr.getScaledHeight() != lastLayoutHeight) {
            for (NotificationEntry entry : entries) entry.animX = entry.animY = Float.NaN;
            lastLayoutX = x; lastLayoutY = y; lastLayoutScale = sc;
            lastLayoutFontScale = fs; lastLayoutStyle = st; lastLayoutMode = md;
            lastLayoutWidth = sr.getScaledWidth(); lastLayoutHeight = sr.getScaledHeight();
        }
    }

    public void renderPreview(float x, float y, float alpha) {
        long now = System.currentTimeMillis(), dur = duration.getValue();
        List<NotificationEntry> sample = new ArrayList<>();
        String description = previewMode == NoticeMode.Enable ? "Enabled" : previewMode == NoticeMode.Disable ? "Disabled" : "Warning";
        sample.add(new NotificationEntry("Notification", description, previewMode, now - Math.round(dur * 0.35F)));
        previewing = true;
        previewX = x; previewY = y; previewAlpha = alpha;
        try {
            // The same style renderer as the live HUD, but with an isolated sample queue.
            renderEntries(new ScaledResolution(mc), now, dur, sample);
        } finally {
            previewing = false;
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    public NoticeMode getPreviewMode() { return previewMode; }

    public void setPreviewMode(NoticeMode mode) { previewMode = mode; }

}
