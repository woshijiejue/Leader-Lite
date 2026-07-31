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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Potion extends Module {

    
    private final Map<Integer, Integer> potionMaxDurations = new HashMap<>();
    private List<PotionEffect> currentEffects = new ArrayList<>();

    public final ListValue mode = new ListValue("mode", new String[]{"RIGHT", "LEFT"}, "RIGHT", this);
    public final ListValue displayMode = new ListValue("display-mode", new String[]{"Bar", "Circle", "Modern"}, "Bar", this);
    public final SliderValue offsetX = new SliderValue("offset-x", 2, 0, 255, Representation.INT, this);
    public final SliderValue offsetY = new SliderValue("offset-y", 2, 0, 255, Representation.INT, this);
    public final SliderValue scale = new SliderValue("scale", 1.0, 0.5, 1.5, Representation.FLOAT, this);
    public final SliderValue fontScale = new SliderValue("font-scale", 1.0, 0.7, 1.5, Representation.FLOAT, this);
    public final BoolValue blur = new BoolValue("blur", false, this);
    public final SliderValue blurIterations = new SliderValue("blur-iterations", 2, 1, 8, () -> this.blur.getValue(), Representation.INT, this);
    public final SliderValue blurOffset = new SliderValue("blur-offset", 3, 1, 10, () -> this.blur.getValue(), Representation.INT, this);
    private Framebuffer stencilBlur;

    public Potion() {
        super("Potion", false);
    }

    private String getPotionName(PotionEffect effect) {
        net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.potionTypes[effect.getPotionID()];
        return I18n.format(potion.getName()) + " " + intToRoman(effect.getAmplifier() + 1);
    }

    private String fitText(String text, float maxWidth, float scale) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) {
            return "";
        }
        if (FontManager.getStringWidth(text) * scale <= maxWidth) {
            return text;
        }
        String dots = "...";
        float dotsWidth = FontManager.getStringWidth(dots) * scale;
        if (dotsWidth >= maxWidth) {
            return dots;
        }
        String result = text;
        while (result.length() > 0 && (FontManager.getStringWidth(result) * scale + dotsWidth) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + dots;
    }

    private static String intToRoman(int num) {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (values[i] <= num) {
                num -= values[i];
                sb.append(symbols[i]);
            }
        }
        return sb.toString();
    }

    private void updateMaxDurations() {
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : potionMaxDurations.entrySet()) {
            if (mc.thePlayer.getActivePotionEffect(net.minecraft.potion.Potion.potionTypes[entry.getKey()]) == null) {
                toRemove.add(entry.getKey());
            }
        }
        for (int id : toRemove) potionMaxDurations.remove(id);
        for (PotionEffect effect : currentEffects) {
            int id = effect.getPotionID();
            if (!potionMaxDurations.containsKey(id) || potionMaxDurations.get(id) < effect.getDuration()) {
                potionMaxDurations.put(id, effect.getDuration());
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer.getActivePotionEffects().isEmpty()) return;

        currentEffects = mc.thePlayer.getActivePotionEffects().stream()
                .sorted(Comparator.comparingInt(e -> -(
                        e.getDuration() + (potionMaxDurations.getOrDefault(e.getPotionID(), 0) / 2)
                )))
                .collect(Collectors.toList());
        updateMaxDurations();
        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);

        int index = 0;
        boolean doBlur = this.blur.getValue();
        float invScale = 1.0F / this.scale.getValue();
        boolean isRight = this.mode.is("RIGHT");

        if (this.displayMode.is("Modern")) {
            renderModern(index, doBlur, invScale, isRight);
        } else if (this.displayMode.is("Circle")) {
            renderCircle(index, doBlur, invScale, isRight);
        } else {
            renderBar(index, doBlur, invScale, isRight);
        }

        GlStateManager.popMatrix();
    }

    private void renderBar(int index, boolean doBlur, float invScale, boolean isRight) {
        float screenWidth = new ScaledResolution(mc).getScaledWidth();
        float cardWidth = 130.0F;
        float cardHeight = 28.0F;
        float gap = 2.0F;
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float textY1 = 3.0F;
        float textY2 = textY1 + textHeight + 1.0F;
        float iconSize = cardHeight - 4.0F;
        float iconOffset = iconSize + 4.0F;
        float offX = this.offsetX.getValue() + 4.0F;
        float offY = this.offsetY.getValue() + 4.0F;
        float baseX = isRight ? (screenWidth - cardWidth - offX) * invScale : offX * invScale;
        float baseY = offY * invScale;
        float step = (cardHeight + gap) * invScale;
        for (PotionEffect effect : currentEffects) {
            net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.potionTypes[effect.getPotionID()];
            int id = effect.getPotionID();
            int maxDur = potionMaxDurations.getOrDefault(id, Math.max(effect.getDuration(), 1));
            float ratio = Math.min((float) effect.getDuration() / (float) maxDur, 1.0F);
            int potionColor = potion.getLiquidColor();
            Color themeColor = new Color((potionColor & 0x00FFFFFF) | 0xFF000000, true);
            String name = getPotionName(effect);
            String durationStr = net.minecraft.potion.Potion.getDurationString(effect);

            float x = baseX;
            float y = baseY + index * step;

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
            RenderUtil.drawRect(x, y, x + cardWidth, y + cardHeight, new Color(0, 0, 0, 0.45F).getRGB());

            float fillWidth = cardWidth * ratio;
            int fillColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 60).getRGB();
            RenderUtil.drawRect(x, y, x + fillWidth, y + cardHeight, fillColor);

            int borderColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 50).getRGB();
            RenderUtil.drawLine(x, y, x + cardWidth, y, 1.0F, borderColor);
            RenderUtil.drawLine(x + cardWidth, y, x + cardWidth, y + cardHeight, 1.0F, borderColor);
            RenderUtil.drawLine(x + cardWidth, y + cardHeight, x, y + cardHeight, 1.0F, borderColor);
            RenderUtil.drawLine(x, y + cardHeight, x, y, 1.0F, borderColor);
            RenderUtil.disableRenderState();

            if (potion.hasStatusIcon()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
                int iconIndex = potion.getStatusIconIndex();
                float u = iconIndex % 8 * 18;
                float v = 198 + iconIndex / 8 * 18;
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                Gui.drawScaledCustomSizeModalRect((int) (x + 2.0F), (int) (y + 2.0F), u, v, 18, 18, (int) iconSize, (int) iconSize, 256.0F, 256.0F);
                GlStateManager.disableBlend();
            }

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + iconOffset, y + textY1, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(name, 0.0F, 0.0F, -1, false);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + iconOffset, y + textY2, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(durationStr, 0.0F, 0.0F, themeColor.getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();

            index++;
        }
    }

    private void renderCircle(int index, boolean doBlur, float invScale, boolean isRight) {
        float cardWidth = 110.0F;
        float cardHeight = 30.0F;
        float gap = 3.0F;
        float radius = 3.5F;
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float textY1 = 4.0F;
        float textY2 = textY1 + textHeight + 1.0F;
        float iconSize = 18.0F;
        float iconOffset = iconSize + 4.0F;
        float offX = this.offsetX.getValue() + 4.0F;
        float offY = this.offsetY.getValue() + 4.0F;
        float baseX = isRight ? (new ScaledResolution(mc).getScaledWidth() - cardWidth - offX) * invScale : offX * invScale;
        float baseY = offY * invScale;
        float step = (cardHeight + gap) * invScale;

        for (PotionEffect effect : currentEffects) {
            net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.potionTypes[effect.getPotionID()];
            int id = effect.getPotionID();
            int maxDur = potionMaxDurations.getOrDefault(id, Math.max(effect.getDuration(), 1));
            float ratio = Math.min((float) effect.getDuration() / (float) maxDur, 1.0F);
            int potionColor = potion.getLiquidColor();
            Color themeColor = new Color((potionColor & 0x00FFFFFF) | 0xFF000000, true);
            String name = getPotionName(effect);
            String durationStr = net.minecraft.potion.Potion.getDurationString(effect);

            float x = baseX;
            float y = baseY + index * step;

            if (doBlur) {
                final float bx = x;
                final float by = y;
                ShaderElement.addBlurTask(() -> {
                    RenderUtil.enableRenderState();
                    RenderUtil.drawRoundedRect(bx, by, bx + cardWidth, by + cardHeight, radius, -1);
                    RenderUtil.disableRenderState();
                });
            }

            RenderUtil.enableRenderState();
            RenderUtil.drawRoundedRect(x, y, x + cardWidth, y + cardHeight, radius, new Color(0, 0, 0, 0.45F).getRGB());
            RenderUtil.disableRenderState();

            float cx = x + iconSize / 2.0F + 2.0F;
            float cy = y + cardHeight / 2.0F;
            float ringRadius = Math.min(iconSize / 2.0F + 1.5F, cardHeight / 2.0F - 1.0F);
            float ringThickness = 1.5F;

            drawProgressRing(cx, cy, ringRadius, ringThickness, ratio, themeColor);

            RenderUtil.disableRenderState();

            if (potion.hasStatusIcon()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
                int iconIndex = potion.getStatusIconIndex();
                float u = iconIndex % 8 * 18;
                float v = 198 + iconIndex / 8 * 18;
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                Gui.drawScaledCustomSizeModalRect((int) (x + 2.0F), (int) (y + (cardHeight - iconSize) / 2.0F), u, v, 18, 18, (int) iconSize, (int) iconSize, 256.0F, 256.0F);
                GlStateManager.disableBlend();
            }

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + iconOffset, y + textY1, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(name, 0.0F, 0.0F, -1, false);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + iconOffset, y + textY2, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(durationStr, 0.0F, 0.0F, themeColor.getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();

            index++;
        }
    }

    private void renderModern(int index, boolean doBlur, float invScale, boolean isRight) {
        float screenWidth = new ScaledResolution(mc).getScaledWidth();
        float cardWidth = 144.0F;
        float cardHeight = 36.0F;
        float gap = 4.0F;
        float radius = 7.0F;
        float textScale = this.fontScale.getValue();
        float subScale = Math.max(0.72F, textScale * 0.76F);
        float iconBox = 25.0F;
        float iconSize = 17.0F;
        float offX = this.offsetX.getValue() + 6.0F;
        float offY = this.offsetY.getValue() + 6.0F;
        float baseX = isRight ? (screenWidth - cardWidth - offX) * invScale : offX * invScale;
        float baseY = offY * invScale;
        float step = (cardHeight + gap) * invScale;

        for (PotionEffect effect : currentEffects) {
            net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.potionTypes[effect.getPotionID()];
            int id = effect.getPotionID();
            int maxDur = potionMaxDurations.getOrDefault(id, Math.max(effect.getDuration(), 1));
            float ratio = Math.min((float) effect.getDuration() / (float) maxDur, 1.0F);
            int potionColor = potion.getLiquidColor();
            Color themeColor = new Color((potionColor & 0x00FFFFFF) | 0xFF000000, true);
            String durationStr = net.minecraft.potion.Potion.getDurationString(effect);
            float textX = 38.0F;
            float nameMaxWidth = cardWidth - textX - 18.0F;
            String name = fitText(getPotionName(effect), nameMaxWidth, textScale);
            String subText = fitText(durationStr, nameMaxWidth, subScale);

            float x = baseX;
            float y = baseY + index * step;

            if (doBlur) {
                final float bx = x;
                final float by = y;
                ShaderElement.addBlurTask(() -> RenderUtil.drawRoundedRectWithGl(bx, by, bx + cardWidth, by + cardHeight, radius, -1));
            }

            int bgColor = new Color(12, 14, 20, 184).getRGB();
            int iconBg = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 48).getRGB();
            int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 220).getRGB();
            int track = new Color(255, 255, 255, 30).getRGB();
            int nameColor = new Color(245, 247, 252, 245).getRGB();
            int subColor = new Color(188, 196, 210, 220).getRGB();

            RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 2.0F, x + cardWidth + 1.0F, y + cardHeight + 2.0F, radius, new Color(0, 0, 0, 35).getRGB());
            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius, bgColor);
            RenderUtil.drawRoundedRectWithGl(x + 6.0F, y + 5.5F, x + 6.0F + iconBox, y + 5.5F + iconBox, 6.0F, iconBg);
            RenderUtil.drawRoundedRectWithGl(x + cardWidth - 7.0F, y + 6.0F, x + cardWidth - 4.0F, y + cardHeight - 6.0F, 1.5F, track);
            RenderUtil.drawRoundedRectWithGl(x + cardWidth - 7.0F, y + cardHeight - 6.0F - (cardHeight - 12.0F) * ratio, x + cardWidth - 4.0F, y + cardHeight - 6.0F, 1.5F, accent);

            if (potion.hasStatusIcon()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
                int iconIndex = potion.getStatusIconIndex();
                float u = iconIndex % 8 * 18;
                float v = 198 + iconIndex / 8 * 18;
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                Gui.drawScaledCustomSizeModalRect((int) (x + 10.0F), (int) (y + 9.5F), u, v, 18, 18, (int) iconSize, (int) iconSize, 256.0F, 256.0F);
                GlStateManager.disableBlend();
            }

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + textX, y + 6.0F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(name, 0.0F, 0.0F, nameColor, false);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + textX, y + 20.0F, 0.0F);
            GlStateManager.scale(subScale, subScale, 1.0F);
            FontManager.drawString(subText, 0.0F, 0.0F, subColor, false);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            index++;
        }
    }

    private void drawProgressRing(float cx, float cy, float r, float thickness, float ratio, Color color) {
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();

        float rC = color.getRed() / 255f;
        float gC = color.getGreen() / 255f;
        float bC = color.getBlue() / 255f;
        float innerR = r - thickness;
        int segs = 48;

        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.25F);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segs; i++) {
            double angle = Math.PI * 2 * i / segs - Math.PI / 2;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            GL11.glVertex2f(cx + cos * r, cy + sin * r);
            GL11.glVertex2f(cx + cos * innerR, cy + sin * innerR);
        }
        GL11.glEnd();

        int pieDeg = (int) (ratio * 360);
        if (pieDeg < 1) pieDeg = 1;
        GL11.glColor4f(rC, gC, bC, 0.7F);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segs; i++) {
            double angle = Math.toRadians(i * pieDeg / (double) segs - 90.0);
            GL11.glVertex2f(cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r);
        }
        GL11.glEnd();

        GlStateManager.popAttrib();
    }

    private void drawRing(float cx, float cy, float r, float thickness, int startDeg, int endDeg, int segments) {
        float innerR = r - thickness;
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.toRadians(startDeg + (endDeg - startDeg) * i / (float) segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            GL11.glVertex2f(cx + cos * r, cy + sin * r);
            GL11.glVertex2f(cx + cos * innerR, cy + sin * innerR);
        }
        GL11.glEnd();
    }

    public void drawBlur() {
        if (!this.blur.getValue()) return;
        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        if (hud != null && hud.blur.getValue()) return;
        Notification notification = (Notification) Leader.moduleManager.modules.get(Notification.class);
        if (notification != null && notification.blur.getValue()) return;
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
}
