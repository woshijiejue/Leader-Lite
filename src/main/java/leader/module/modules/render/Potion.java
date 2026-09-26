package leader.module.modules.render;

import leader.event.EventTarget;
import leader.events.Render2DEvent;
import leader.module.Module;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.ColorUtil;
import leader.util.Icon;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
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

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Integer, Integer> potionMaxDurations = new HashMap<>();
    private List<PotionEffect> currentEffects = new ArrayList<>();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"RIGHT", "LEFT"});
    public final ModeProperty displayMode = new ModeProperty("display-mode", 0, new String[]{"Bar", "Circle", "Modern", "Frost", "Lucid", "Slate"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final FloatProperty fontScale = new FloatProperty("font-scale", 1.0F, 0.7F, 1.5F);

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
        float invScale = 1.0F / this.scale.getValue();
        boolean isRight = this.mode.getValue() == 0;

        if (this.displayMode.getValue() == 5) {
            renderSlate(index, invScale, isRight);
        } else if (this.displayMode.getValue() == 4) {
            renderLucid(index, invScale, isRight);
        } else if (this.displayMode.getValue() == 3) {
            renderFrost(index, invScale, isRight);
        } else if (this.displayMode.getValue() == 2) {
            renderModern(index, invScale, isRight);
        } else if (this.displayMode.getValue() == 1) {
            renderCircle(index, invScale, isRight);
        } else {
            renderBar(index, invScale, isRight);
        }

        GlStateManager.popMatrix();
    }

    private void renderBar(int index, float invScale, boolean isRight) {
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
            RenderUtil.drawRect(x, y, x + cardWidth, y + cardHeight, new Color(0, 0, 0, 0.45F).getRGB());

            float fillWidth = cardWidth * ratio;
            int fillColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 60).getRGB();
            RenderUtil.drawRect(x, y, x + fillWidth, y + cardHeight, fillColor);

            int borderColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 70).getRGB();
            float line = 1.0F / new ScaledResolution(mc).getScaleFactor() / this.scale.getValue();
            RenderUtil.drawRect(x, y, x + cardWidth, y + line, borderColor);
            RenderUtil.drawRect(x, y + cardHeight - line, x + cardWidth, y + cardHeight, borderColor);
            RenderUtil.drawRect(x, y + line, x + line, y + cardHeight - line, borderColor);
            RenderUtil.drawRect(x + cardWidth - line, y + line, x + cardWidth, y + cardHeight - line, borderColor);
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

    private void renderCircle(int index, float invScale, boolean isRight) {
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

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawRoundedRect(bx, by, bx + cardWidth, by + cardHeight, radius, -1);
                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();
            });

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

    private void renderModern(int index, float invScale, boolean isRight) {
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

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.drawRoundedRectWithGl(bx, by, bx + cardWidth, by + cardHeight, radius, -1);
                GlStateManager.popMatrix();
            });

            int rimColor = new Color(255, 255, 255, 34).getRGB();
            int glassColor = new Color(13, 15, 21, 178).getRGB();
            int tintColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 14).getRGB();
            int shineColor = new Color(255, 255, 255, 18).getRGB();
            int iconBg = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 42).getRGB();
            int iconRim = new Color(255, 255, 255, 26).getRGB();
            int accentGlow = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 55).getRGB();
            int accent = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 225).getRGB();
            int track = new Color(255, 255, 255, 26).getRGB();
            int nameColor = new Color(240, 244, 250, 245).getRGB();
            int subColor = new Color(188, 196, 210, 220).getRGB();

            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius, rimColor);
            RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 1.0F, x + cardWidth - 1.0F, y + cardHeight - 1.0F, radius - 1.0F, glassColor);
            RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 1.0F, x + cardWidth - 1.0F, y + cardHeight - 1.0F, radius - 1.0F, tintColor);
            RenderUtil.drawRoundedRectWithGl(x + 2.0F, y + 2.0F, x + cardWidth - 2.0F, y + cardHeight * 0.45F, radius - 2.0F, shineColor);

            RenderUtil.drawRoundedRectWithGl(x + 6.0F, y + 5.5F, x + 6.0F + iconBox, y + 5.5F + iconBox, 6.0F, iconRim);
            RenderUtil.drawRoundedRectWithGl(x + 6.5F, y + 6.0F, x + 5.5F + iconBox, y + 5.0F + iconBox, 5.5F, iconBg);

            float fillH = (cardHeight - 12.0F) * ratio;
            RenderUtil.drawRoundedRectWithGl(x + cardWidth - 7.0F, y + 6.0F, x + cardWidth - 4.0F, y + cardHeight - 6.0F, 1.5F, track);
            RenderUtil.drawRoundedRectWithGl(x + cardWidth - 8.0F, y + cardHeight - 7.0F - fillH, x + cardWidth - 3.0F, y + cardHeight - 5.0F, 2.5F, accentGlow);
            RenderUtil.drawRoundedRectWithGl(x + cardWidth - 7.0F, y + cardHeight - 6.0F - fillH, x + cardWidth - 4.0F, y + cardHeight - 6.0F, 1.5F, accent);

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

    private void renderFrost(int index, float invScale, boolean isRight) {
        float screenWidth = new ScaledResolution(mc).getScaledWidth();
        float cardWidth = 124.0F;
        float cardHeight = 26.0F;
        float gap = 3.0F;
        float radius = 8.0F;
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float iconSize = 18.0F;
        float textX = 30.0F;
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
            Color fill = ColorUtil.darker(themeColor, 0.82F);
            String name = fitText(getPotionName(effect), cardWidth - textX - 8.0F
                    - FontManager.getStringWidth(net.minecraft.potion.Potion.getDurationString(effect)) * textScale - 6.0F,
                    textScale);
            String durationStr = net.minecraft.potion.Potion.getDurationString(effect);

            float x = baseX;
            float y = baseY + index * step;

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

            RenderUtil.drawGlass(x, y, x + cardWidth, y + cardHeight, radius, 1.0F);

            float lineY = y + cardHeight - 3.6F;
            float lineLeft = x + textX;
            float lineRight = x + cardWidth - 8.0F;
            RenderUtil.drawRoundedRectWithGl(lineLeft, lineY, lineRight, lineY + 1.6F,
                    Math.min(0.8F, (lineRight - lineLeft) / 2.0F), new Color(20, 24, 34, 22).getRGB());
            float fillW = Math.max(2.0F, (lineRight - lineLeft) * ratio);
            RenderUtil.drawRoundedRectWithGl(lineLeft, lineY, lineLeft + fillW, lineY + 1.6F,
                    Math.min(0.8F, fillW / 2.0F),
                    new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 250).getRGB());

            if (potion.hasStatusIcon()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
                int iconIndex = potion.getStatusIconIndex();
                float u = iconIndex % 8 * 18;
                float v = 198 + iconIndex / 8 * 18;
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                Gui.drawScaledCustomSizeModalRect((int) (x + 5.0F), (int) (y + (cardHeight - iconSize) / 2.0F),
                        u, v, 18, 18, (int) iconSize, (int) iconSize, 256.0F, 256.0F);
                GlStateManager.disableBlend();
            }

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + textX, y + (cardHeight - textHeight) / 2.0F - 2.2F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(name, 0.0F, 0.0F, new Color(20, 24, 34, 232).getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + cardWidth - 8.0F
                    - FontManager.getStringWidth(durationStr) * textScale, y + (cardHeight - textHeight) / 2.0F - 2.2F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(durationStr, 0.0F, 0.0F,
                    new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 240).getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            index++;
        }
    }

    private void renderLucid(int index, float invScale, boolean isRight) {
        float screenWidth = new ScaledResolution(mc).getScaledWidth();
        float cardWidth = 124.0F;
        float cardHeight = 26.0F;
        float gap = 3.0F;
        float radius = 7.0F;
        float chipSize = 18.0F;
        float chipX = 4.0F;
        float textScale = this.fontScale.getValue();
        float textHeight = FontManager.getFontHeight() * textScale;
        float textX = chipX + chipSize + 7.0F;
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
            Color liquid = new Color((potion.getLiquidColor() & 0x00FFFFFF) | 0xFF000000, true);
            Color fill = ColorUtil.darker(liquid, 0.82F);
            String durationStr = net.minecraft.potion.Potion.getDurationString(effect);
            String name = fitText(getPotionName(effect), cardWidth - textX - 8.0F
                    - FontManager.getStringWidth(durationStr) * textScale - 6.0F, textScale);

            float x = baseX;
            float y = baseY + index * step;

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
                    new Color(12, 14, 19, 51).getRGB());

            float chipY = y + (cardHeight - chipSize) / 2.0F;
            RenderUtil.drawRoundedRectWithGl(x + chipX, chipY, x + chipX + chipSize, chipY + chipSize, 5.5F,
                    new Color(liquid.getRed(), liquid.getGreen(), liquid.getBlue(), 45).getRGB());
            Icon.potion(id).drawCentered(x + chipX + chipSize / 2.0F, chipY + chipSize / 2.0F, 11.0F,
                    liquid.getRGB(), 1.0F);

            float lineY = y + cardHeight - 3.6F;
            float lineLeft = x + textX;
            float lineRight = x + cardWidth - 8.0F;
            RenderUtil.drawRoundedRectWithGl(lineLeft, lineY, lineRight, lineY + 1.6F,
                    Math.min(0.8F, (lineRight - lineLeft) / 2.0F), new Color(255, 255, 255, 26).getRGB());
            float fillW = Math.max(2.0F, (lineRight - lineLeft) * ratio);
            RenderUtil.drawRoundedRectWithGl(lineLeft, lineY, lineLeft + fillW, lineY + 1.6F,
                    Math.min(0.8F, fillW / 2.0F),
                    new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 250).getRGB());

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + textX, y + (cardHeight - textHeight) / 2.0F - 2.2F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(name, 0.0F, 0.0F, new Color(240, 244, 250, 246).getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + cardWidth - 8.0F
                    - FontManager.getStringWidth(durationStr) * textScale, y + (cardHeight - textHeight) / 2.0F - 2.2F, 0.0F);
            GlStateManager.scale(textScale, textScale, 1.0F);
            FontManager.drawString(durationStr, 0.0F, 0.0F,
                    new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 240).getRGB(), false);
            GlStateManager.popMatrix();

            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            index++;
        }
    }

    private void renderSlate(int index, float invScale, boolean isRight) {
        float screenWidth = new ScaledResolution(mc).getScaledWidth();
        float nameSize = 16.0F * this.fontScale.getValue();
        float timeSize = 13.0F * this.fontScale.getValue();
        float pad = 5.0F;
        float well = 18.0F;
        float radius = 5.0F;
        float gap = 3.0F;
        float capH = FontManager.getCapHeight(nameSize);
        float cardHeight = pad * 2.0F + Math.max(well, capH + 8.0F);
        float offX = this.offsetX.getValue() + 6.0F;
        float offY = this.offsetY.getValue() + 6.0F;
        float step = (cardHeight + gap) * invScale;

        float cardWidth = 110.0F;
        for (PotionEffect effect : currentEffects) {
            float w = pad + well + 7.0F + FontManager.getStringWidth(getPotionName(effect), nameSize) + 10.0F
                    + FontManager.getStringWidth(net.minecraft.potion.Potion.getDurationString(effect), timeSize) + pad + 2.0F;
            cardWidth = Math.max(cardWidth, Math.min(190.0F, w));
        }
        float baseX = isRight ? (screenWidth - cardWidth - offX) * invScale : offX * invScale;
        float baseY = offY * invScale;

        for (PotionEffect effect : currentEffects) {
            int id = effect.getPotionID();
            net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.potionTypes[id];
            int maxDur = potionMaxDurations.getOrDefault(id, Math.max(effect.getDuration(), 1));
            float ratio = Math.min((float) effect.getDuration() / (float) maxDur, 1.0F);
            Color liquid = new Color((potion.getLiquidColor() & 0x00FFFFFF) | 0xFF000000, true);
            Color tint = ColorUtil.interpolate(0.35F, liquid, Color.WHITE);
            String duration = net.minecraft.potion.Potion.getDurationString(effect);
            float timeW = FontManager.getStringWidth(duration, timeSize);
            float textX = pad + well + 7.0F;
            String name = fitSized(getPotionName(effect), cardWidth - textX - timeW - 10.0F - pad, nameSize);

            float x = baseX;
            float y = baseY + index * step;
            float centerY = y + (cardHeight - 3.0F) / 2.0F;
            float baseline = centerY + capH / 2.0F;

            final float sc = this.scale.getValue();
            final float bx = x;
            final float by = y;
            final float bw = cardWidth;
            final float bh = cardHeight;
            final int mask = liquid.getRGB();
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.drawRoundedRectWithGl(bx, by, bx + bw, by + bh, radius, mask);
                GlStateManager.popMatrix();
            });

            RenderUtil.drawRoundedRectWithGl(x, y, x + cardWidth, y + cardHeight, radius, new Color(14, 15, 20, 120).getRGB());
            RenderUtil.drawRoundedRectWithGl(x + 0.5F, y + 0.5F, x + cardWidth - 0.5F, y + cardHeight * 0.5F, radius - 0.5F,
                    new Color(255, 255, 255, 8).getRGB());

            float wellX = x + pad;
            float wellY = centerY - well / 2.0F;
            RenderUtil.drawRoundedRectWithGl(wellX, wellY, wellX + well, wellY + well, 4.5F,
                    new Color(liquid.getRed(), liquid.getGreen(), liquid.getBlue(), 42).getRGB());
            Icon.potion(id).drawCentered(wellX + well / 2.0F, centerY, 11.0F, tint.getRGB(), 1.0F);

            float lineL = x + textX;
            float lineR = x + cardWidth - pad;
            float lineY = y + cardHeight - pad - 1.0F;
            RenderUtil.drawRoundedRectWithGl(lineL, lineY, lineR, lineY + 1.5F, 0.75F, new Color(255, 255, 255, 24).getRGB());
            float fillW = Math.max(1.5F, (lineR - lineL) * ratio);
            RenderUtil.drawRoundedRectWithGl(lineL, lineY, lineL + fillW, lineY + 1.5F, 0.75F,
                    new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 235).getRGB());

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            FontManager.drawString(name, x + textX, baseline - FontManager.getBaseline(nameSize),
                    new Color(244, 246, 250).getRGB(), false, nameSize);
            FontManager.drawString(duration, x + cardWidth - pad - timeW, baseline - FontManager.getBaseline(timeSize),
                    new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 230).getRGB(), false, timeSize);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            index++;
        }
    }

    private String fitSized(String text, float maxWidth, float size) {
        if (FontManager.getStringWidth(text, size) <= maxWidth) return text;
        String result = text;
        float dots = FontManager.getStringWidth("...", size);
        while (result.length() > 0 && FontManager.getStringWidth(result, size) + dots > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
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

}
