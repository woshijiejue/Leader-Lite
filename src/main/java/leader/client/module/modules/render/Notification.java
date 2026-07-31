package leader.client.module.modules.render;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.events.Render2DEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.util.render.RenderUtil;
import leader.client.util.render.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Notification extends Module {

    
    private static final List<NotificationEntry> entries = new ArrayList<>();

    public final ListValue mode = new ListValue("mode", new String[]{"RIGHT", "LEFT"}, "RIGHT", this);
    public final ListValue style = new ListValue("style", new String[]{"CLASSIC", "MODERN"}, "MODERN", this);
    public final SliderValue duration = new SliderValue("duration", 1500, 500, 5000, Representation.INT, this);
    public final SliderValue maxAlerts = new SliderValue("max-alerts", 5, 1, 10, Representation.INT, this);
    public final SliderValue scale = new SliderValue("scale", 1.0, 0.5, 1.5, Representation.FLOAT, this);
    public final SliderValue fontScale = new SliderValue("font-scale", 1.0, 0.7, 1.5, Representation.FLOAT, this);
    public final SliderValue offsetX = new SliderValue("offset-x", 2, 0, 255, Representation.INT, this);
    public final SliderValue offsetY = new SliderValue("offset-y", 20, 0, 255, Representation.INT, this);
    public final BoolValue blur = new BoolValue("blur", false, this);
    public final SliderValue blurIterations = new SliderValue("blur-iterations", 2, 1, 8, () -> this.blur.getValue(), Representation.INT, this);
    public final SliderValue blurOffset = new SliderValue("blur-offset", 3, 1, 10, () -> this.blur.getValue(), Representation.INT, this);
    private Framebuffer stencilBlur;

    public Notification() {
        super("Notification", false);
    }

    public static void addNotification(String moduleName, boolean enabled) {
        entries.add(new NotificationEntry(moduleName, enabled, System.currentTimeMillis()));
        Notification notification = (Notification) Leader.moduleManager.modules.get(Notification.class);
        if (notification != null) {
            int max = notification.maxAlerts.getValue().intValue();
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
        long dur = this.duration.getValue().longValue();
        entries.removeIf(entry -> now - entry.startTime > dur);
        if (entries.isEmpty()) return;

        if (this.style.is("MODERN")) {
            renderModern(sr, now, dur);
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
        boolean isRight = this.mode.is("RIGHT");
        boolean doBlur = this.blur.getValue();
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue().intValue());
        float step = cardHeight + gap;

        float baseX = isRight ? screenWidth - cardWidth - offX : offX;
        float baseY = screenHeight - offY - cardHeight;

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = getAlpha(now, entry.startTime, dur);
            int idx = max - 1 - i;
            float y = (baseY - idx * step) * invScale;
            float x = baseX * invScale;
            Color themeColor = entry.enabled ? new Color(0x00FF00) : new Color(0xFF4444);

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
            FontManager.drawString(entry.moduleName, 0.0F, 0.0F, nameColor, false);
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
            if (entry.enabled) {
                GL11.glColor4f(themeColor.getRed() / 255f, themeColor.getGreen() / 255f, themeColor.getBlue() / 255f, alpha);
                GL11.glVertex2f(1.0F, iconSize * 0.55F);
                GL11.glVertex2f(iconSize * 0.45F, iconSize - 1.0F);
                GL11.glVertex2f(iconSize * 0.45F, iconSize - 1.0F);
                GL11.glVertex2f(iconSize - 1.0F, 1.0F);
            } else {
                GL11.glColor4f(themeColor.getRed() / 255f, themeColor.getGreen() / 255f, themeColor.getBlue() / 255f, alpha);
                GL11.glVertex2f(1.0F, 1.0F);
                GL11.glVertex2f(iconSize - 1.0F, iconSize - 1.0F);
                GL11.glVertex2f(iconSize - 1.0F, 1.0F);
                GL11.glVertex2f(1.0F, iconSize - 1.0F);
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
        boolean isRight = this.mode.is("RIGHT");
        boolean doBlur = this.blur.getValue();
        float invScale = 1.0F / this.scale.getValue();
        int max = Math.min(entries.size(), this.maxAlerts.getValue().intValue());
        float baseX = isRight ? sr.getScaledWidth() - cardWidth - offX : offX;
        float baseY = sr.getScaledHeight() - offY - cardHeight;
        float step = cardHeight + gap;

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);

        for (int i = 0; i < max; i++) {
            NotificationEntry entry = entries.get(i);
            float progress = Math.min((float) (now - entry.startTime) / (float) dur, 1.0F);
            float alpha = Math.max(0.0F, Math.min(1.0F, getAlpha(now, entry.startTime, dur)));
            int idx = max - 1 - i;
            float slide = (1.0F - alpha) * 18.0F;
            float x = (baseX + (isRight ? slide : -slide)) * invScale;
            float y = (baseY - idx * step) * invScale;
            Color themeColor = entry.enabled ? new Color(72, 220, 130) : new Color(255, 90, 95);

            if (doBlur) {
                final float bx = x;
                final float by = y;
                ShaderElement.addBlurTask(() -> RenderUtil.drawRoundedRectWithGl(bx, by, bx + cardWidth, by + cardHeight, radius, -1));
            }

            int bgColor = new Color(12, 14, 20, (int) (198.0F * alpha)).getRGB();
            int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (235.0F * alpha)).getRGB();
            int accentSoft = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (55.0F * alpha)).getRGB();
            int track = new Color(255, 255, 255, (int) (34.0F * alpha)).getRGB();

            RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 2.0F, x + cardWidth + 1.0F, y + cardHeight + 2.0F, radius, new Color(0, 0, 0, (int) (42.0F * alpha)).getRGB());
            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius, bgColor);
            RenderUtil.drawRoundedRectWithGl(x, y, x + 2.0F, y + cardHeight, 1.0F, accent);
            RenderUtil.drawRoundedRectWithGl(x + cardWidth - 23.0F, y + 7.0F, x + cardWidth - 9.0F, y + 21.0F, 4.0F, accentSoft);

            float progressY = y + cardHeight - 4.0F;
            RenderUtil.drawRect(x + 8.0F, progressY, x + cardWidth - 8.0F, progressY + 2.0F, track);
            RenderUtil.drawRect(x + 8.0F, progressY, x + 8.0F + (cardWidth - 16.0F) * (1.0F - progress), progressY + 2.0F, accent);

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int nameColor = new Color(245, 247, 252, (int) (245.0F * alpha)).getRGB();
            int stateColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (245.0F * alpha)).getRGB();
            String stateText = entry.enabled ? "Enabled" : "Disabled";
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 10.0F, y + 5.0F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(entry.moduleName, 0.0F, 0.0F, nameColor, false);
            FontManager.drawString(stateText, 0.0F, FontManager.getFontHeight() + 2.0F, stateColor, false);
            GlStateManager.popMatrix();

            drawStatusIcon(x + cardWidth - 20.0F, y + 10.0F, 8.0F, entry.enabled, themeColor, alpha);

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }

        GlStateManager.popMatrix();
    }

    private void drawStatusIcon(float x, float y, float iconSize, boolean enabled, Color themeColor, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.0F);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glColor4f(themeColor.getRed() / 255f, themeColor.getGreen() / 255f, themeColor.getBlue() / 255f, alpha);
        GL11.glBegin(GL11.GL_LINES);
        if (enabled) {
            GL11.glVertex2f(1.0F, iconSize * 0.55F);
            GL11.glVertex2f(iconSize * 0.42F, iconSize - 1.0F);
            GL11.glVertex2f(iconSize * 0.42F, iconSize - 1.0F);
            GL11.glVertex2f(iconSize - 1.0F, 1.0F);
        } else {
            GL11.glVertex2f(1.0F, 1.0F);
            GL11.glVertex2f(iconSize - 1.0F, iconSize - 1.0F);
            GL11.glVertex2f(iconSize - 1.0F, 1.0F);
            GL11.glVertex2f(1.0F, iconSize - 1.0F);
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
        leader.client.util.render.shader.KawaseBlur.renderBlur(stencilBlur.framebufferTexture, blurIterations.getValue().intValue(), blurOffset.getValue().intValue());
    }

    private static class NotificationEntry {
        final String moduleName;
        final boolean enabled;
        final long startTime;

        NotificationEntry(String moduleName, boolean enabled, long startTime) {
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.startTime = startTime;
        }
    }
}
