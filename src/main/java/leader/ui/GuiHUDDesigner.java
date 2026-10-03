package leader.ui;

import leader.util.shader.ShaderElement;

import leader.Leader;
import leader.module.Module;
import leader.module.modules.render.BedTracker;
import leader.module.modules.render.FontManager;
import leader.module.modules.render.GifDisplay;
import leader.module.modules.render.HUD;
import leader.module.modules.render.Indicators;
import leader.module.modules.render.Island;
import leader.module.modules.render.Potion;
import leader.module.modules.render.TargetHUD;
import leader.module.modules.render.Watermark;
import leader.module.modules.render.notification.Notification;
import leader.module.modules.player.Scaffold;
import leader.property.properties.ModeProperty;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiHUDDesigner extends GuiScreen {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final String ISLAND = "Island";
    private static final float PANEL_W = 244.0F;
    private static final float PANEL_H = 258.0F;
    private static final float SLIDER_LEFT = 16.0F;
    private static final float SLIDER_RIGHT = 16.0F;
    private static final float SLIDER_TRACK_H = 6.0F;
    private static final float ISLAND_HEAD = 26.0F;
    private static final int SWATCH_COUNT = 8;

    private static final Color ACCENT = new Color(255, 214, 128);
    private static final Color MUTED = new Color(146, 152, 168);
    private static final Color VALUE = new Color(196, 202, 216);

    private String draggingModule = null;
    private float dragOffsetX = 0.0F;
    private float dragOffsetY = 0.0F;
    private float dragStartMouseX = 0.0F;
    private float dragStartMouseY = 0.0F;
    private boolean dragMoved = false;

    private String settingsModule = null;
    private int activeSlider = -1;
    private float panelSlideProgress = 0.0F;
    private float hoverGlow = 0.0F;
    private String hoverElement = null;
    private float openTime = 0.0F;

    private final List<String> hudModules = new ArrayList<>();

    public GuiHUDDesigner() {
        collectHUDModules();
    }

    private void collectHUDModules() {
        hudModules.clear();

        for (Module module : Leader.moduleManager.modules.values()) {
            if (!module.isEnabled()) continue;

            if (module instanceof HUD) {
                add("HUD");
            } else if (module instanceof TargetHUD) {
                add("TargetHUD");
            } else if (module instanceof Notification) {
                add("Notification");
            } else if (module instanceof Watermark) {
                add("Watermark");
            } else if (module instanceof GifDisplay) {
                add("GifDisplay");
            } else if (module instanceof Indicators) {
                add("Indicators");
            } else if (module instanceof Potion) {
                add("Potion");
            } else if (module instanceof BedTracker) {
                add("BedTracker");
            } else if (module instanceof Island) {
                add("Island");
            }
        }

        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled() && scaffold.blockCounter.getValue()) {
            add("ScaffoldCounter");
        }
    }

    private void add(String name) {
        if (!hudModules.contains(name)) {
            hudModules.add(name);
        }
    }

    private boolean isVisible(String name) {
        return name.equals(ISLAND) || !Leader.hudElementManager.isSuppressed(name);
    }

    private boolean hasAlign() {
        return settingsModule != null && settingsModule.equals("HUD");
    }

    private float islandRowH() {
        return FontManager.getFontHeight() + 4.0F;
    }

    private float[] box(String name) {
        float x = Leader.hudElementManager.x(name, 40.0F, 40.0F);
        float y = Leader.hudElementManager.y(name, 40.0F, 40.0F);
        float w = Math.max(148.0F, FontManager.getStringWidth(name) + 104.0F);
        float h = 30.0F;

        if (name.equals(ISLAND)) {
            List<String> merged = Leader.hudElementManager.mergedModules(ISLAND);
            w = Math.max(158.0F, FontManager.getStringWidth("Island") + 126.0F);
            h = ISLAND_HEAD + Math.max(1, merged.size()) * islandRowH() + 4.0F;
        }

        return new float[]{x, y, w, h};
    }

    private float islandRowY(float cardY, int index) {
        return cardY + ISLAND_HEAD + index * islandRowH();
    }

    private String hitTest(int mouseX, int mouseY) {
        String found = null;
        for (String name : hudModules) {
            if (!isVisible(name)) continue;

            float[] b = box(name);
            if (mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                found = name;
            }
        }
        return found;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScaledResolution sr = new ScaledResolution(mc);
        int centerX = sr.getScaledWidth() / 2;
        openTime = Math.min(1.0F, openTime + 0.05F);

        ShaderElement.blurArea(0, 0, sr.getScaledWidth(), sr.getScaledHeight());
        RenderUtil.drawRect(0.0F, 0.0F, (float) sr.getScaledWidth(), (float) sr.getScaledHeight(), new Color(5, 7, 12, 215).getRGB());

        String currentHover = hitTest(mouseX, mouseY);
        if (currentHover != null && currentHover.equals(hoverElement)) {
            hoverGlow = Math.min(1.0F, hoverGlow + 0.08F);
        } else {
            hoverGlow = Math.max(0.0F, hoverGlow - 0.12F);
            hoverElement = currentHover;
        }

        float ease = openTime * openTime * (3.0F - 2.0F * openTime);
        float titleY = 12.0F - (1.0F - ease) * 8.0F;
        String title = "HUD Designer";
        FontManager.drawString(title, centerX - FontManager.getStringWidth(title) / 2.0F, titleY,
                new Color(255, 255, 255, (int) (ease * 255)).getRGB(), true);
        RenderUtil.drawRoundedRect(centerX - 60.0F, titleY + 13.0F, centerX + 60.0F, titleY + 14.0F, 0.5F,
                new Color(255, 214, 128, (int) (ease * 90)).getRGB());

        String hint = "Drag to move  |  Click to edit background  |  Drop onto Island to merge";
        FontManager.drawString(hint, centerX - FontManager.getStringWidth(hint) / 2.0F, 28.0F,
                new Color(140, 146, 162, (int) (ease * 255)).getRGB(), false);

        for (String name : hudModules) {
            if (!isVisible(name)) continue;
            drawElement(name, mouseX, mouseY);
        }

        if (draggingModule != null) {
            drawMergeHint(mouseX, mouseY);
        }

        if (settingsModule != null) {
            drawSettings(mouseX, mouseY);
        }
    }

    private void drawElement(String name, int mouseX, int mouseY) {
        float[] b = box(name);
        float x = b[0];
        float y = b[1];
        float w = b[2];
        float h = b[3];

        boolean island = name.equals(ISLAND);
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        boolean active = name.equals(draggingModule);

        Color base = new Color(Leader.hudElementManager.background(name, 40.0F, 40.0F), true);
        int boost = active ? 70 : hovered ? 40 : 0;
        int fill = new Color(
                Math.min(255, base.getRed() + boost),
                Math.min(255, base.getGreen() + boost),
                Math.min(255, base.getBlue() + boost),
                Math.max(90, base.getAlpha())).getRGB();

        float shadowDepth = active ? 3.5F : hovered ? 2.0F : 0.0F;
        if (shadowDepth > 0.0F) {
            RenderUtil.drawRoundedRectWithGl(x, y + shadowDepth, x + w, y + h + shadowDepth, 6.0F,
                    new Color(0, 0, 0, (int) (shadowDepth * 15)).getRGB());
        }
        ShaderElement.blurArea(x, y, x + w, y + h);
        RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, 6.0F, fill);

        boolean selecting = name.equals(settingsModule);
        float pulse = hovered ? hoverGlow : 0.0F;
        int baseAlpha = selecting ? 230 : island ? 190 : active ? 210 : hovered ? 190 : 130;
        int ringAlpha = (int) Math.min(255, baseAlpha + pulse * 60);
        Color ringBase = selecting ? new Color(255, 220, 130)
                : island ? new Color(120, 210, 255)
                : active ? new Color(255, 220, 130)
                : hovered ? new Color(150, 190, 255)
                : new Color(120, 130, 155);
        int ring = new Color(ringBase.getRed(), ringBase.getGreen(), ringBase.getBlue(), ringAlpha).getRGB();
        RenderUtil.drawRoundedRect(x, y, x + w, y + 1.0F, 0.5F, ring);
        RenderUtil.drawRoundedRect(x, y + h - 1.0F, x + w, y + h, 0.5F, ring);
        RenderUtil.drawRoundedRect(x, y, x + 1.0F, y + h, 0.5F, ring);
        RenderUtil.drawRoundedRect(x + w - 1.0F, y, x + w, y + h, 0.5F, ring);

        int bgSwatch = Leader.hudElementManager.background(name, 40.0F, 40.0F);
        float swatchRight = x + w - 11.0F;
        FontManager.drawString(name, x + 11.0F, y + 9.0F, Color.WHITE.getRGB(), true);

        RenderUtil.drawRoundedRectWithGl(swatchRight - 22.0F, y + 7.0F, swatchRight, y + 25.0F, 3.0F, bgSwatch);
        RenderUtil.drawRoundedRect(swatchRight - 22.0F, y + 7.0F, swatchRight, y + 7.8F, 0.4F, new Color(255, 255, 255, 75).getRGB());

        String tag = island ? "merges" : Leader.hudElementManager.isMergeable(name) ? "mergeable" : "locked";
        int tagColor = island ? new Color(120, 210, 255).getRGB()
                : Leader.hudElementManager.isMergeable(name) ? new Color(140, 150, 170).getRGB()
                : new Color(228, 148, 148).getRGB();
        FontManager.drawString(tag, swatchRight - 28.0F - FontManager.getStringWidth(tag),
                y + 9.0F, tagColor, false);

        if (island) {
            RenderUtil.drawRoundedRect(x + 11.0F, y + ISLAND_HEAD - 5.0F, x + w - 11.0F, y + ISLAND_HEAD - 4.4F, 0.3F,
                    new Color(255, 255, 255, 22).getRGB());

            List<String> merged = Leader.hudElementManager.mergedModules(ISLAND);
            if (merged.isEmpty()) {
                FontManager.drawString("nothing merged", x + 12.0F, y + ISLAND_HEAD + 2.0F,
                        new Color(120, 126, 142).getRGB(), false);
            } else {
                for (int i = 0; i < merged.size(); i++) {
                    String mergedName = merged.get(i);
                    String label = "+ " + mergedName;
                    float rowTop = islandRowY(y, i);
                    float rowTextY = rowTop + 2.0F;
                    boolean mergeHover = mouseX >= x + 12.0F && mouseX <= x + 12.0F + FontManager.getStringWidth(label)
                            && mouseY >= rowTop && mouseY <= rowTop + islandRowH();

                    if (mergeHover) {
                        RenderUtil.drawRoundedRectWithGl(x + 10.0F, rowTop, x + 12.0F + FontManager.getStringWidth(label) + 4.0F,
                                rowTop + islandRowH() - 1.0F, 3.0F, new Color(255, 96, 96, 45).getRGB());
                    }
                    RenderUtil.fillCircle(x + 15.0F, rowTop + islandRowH() / 2.0F - 0.5F, 1.8F, 16,
                            mergeHover ? new Color(255, 100, 100).getRGB() : new Color(120, 255, 150).getRGB());
                    FontManager.drawString(label, x + 22.0F, rowTextY,
                            mergeHover ? new Color(255, 120, 120).getRGB() : new Color(196, 236, 208).getRGB(), false);
                    FontManager.drawString("drag out to unmerge", x + w - 11.0F - FontManager.getStringWidth("drag out to unmerge"),
                            rowTextY, new Color(110, 116, 132).getRGB(), false);
                }
            }
        }
    }

    private void drawMergeHint(int mouseX, int mouseY) {
        float[] island = box(ISLAND);
        boolean over = mouseX >= island[0] && mouseX <= island[0] + island[2]
                && mouseY >= island[1] && mouseY <= island[1] + island[3];
        boolean mergeable = Leader.hudElementManager.isMergeable(draggingModule);

        String label;
        int tone;
        if (!mergeable) {
            label = draggingModule + " cannot merge into Island";
            tone = new Color(228, 148, 148).getRGB();
        } else if (over) {
            label = Leader.hudElementManager.isSuppressed(draggingModule) ? "Release to undo merge" : "Release to merge into Island";
            tone = Leader.hudElementManager.isSuppressed(draggingModule)
                    ? new Color(255, 180, 120).getRGB() : new Color(140, 255, 170).getRGB();
        } else {
            label = "Drop onto Island to merge";
            tone = new Color(190, 196, 212).getRGB();
        }

        float w = FontManager.getStringWidth(label) + 18.0F;
        float h = FontManager.getFontHeight() + 9.0F;
        float x = mouseX + 12.0F;
        float y = mouseY + 14.0F;

        RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, 4.0F, new Color(0, 0, 0, 110).getRGB());
        RenderUtil.drawRoundedRectWithGl(x + 1.0F, y + 1.0F, x + w - 1.0F, y + h - 1.0F, 3.5F, new Color(18, 20, 26, 232).getRGB());
        RenderUtil.drawRoundedRect(x + 1.0F, y + 1.0F, x + w - 1.0F, y + 1.8F, 0.4F, tone);
        FontManager.drawString(label, x + 9.0F, y + 5.0F, tone, false);
    }

    private float panelX() {
        return new ScaledResolution(mc).getScaledWidth() / 2.0F - PANEL_W / 2.0F;
    }

    private float panelY() {
        return new ScaledResolution(mc).getScaledHeight() / 2.0F - PANEL_H / 2.0F;
    }

    private float panelOffsetY() {
        float p = panelSlideProgress;
        float ease = p * p * (3.0F - 2.0F * p);
        return (1.0F - ease) * 20.0F;
    }

    private float flowTop() {
        return panelY() + 44.0F;
    }

    private float alignLabelY() {
        return flowTop();
    }

    private float alignButtonY() {
        return alignLabelY() + 12.0F;
    }

    private float presetsLabelY() {
        return alignLabelY() + (hasAlign() ? 36.0F : 0.0F);
    }

    private float presetsY() {
        return presetsLabelY() + 13.0F;
    }

    private float sliderY(int index) {
        return presetsLabelY() + 40.0F + index * 26.0F;
    }

    private float sliderTrackY(int index) {
        return sliderY(index) + 13.0F;
    }

    private float swatchX(int index) {
        return panelX() + SLIDER_LEFT + index * 26.6F;
    }

    private int[] sliderColors(int rgb) {
        return new int[]{rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, rgb >>> 24};
    }

    private int packColor(int[] rgba) {
        return (rgba[3] & 255) << 24 | (rgba[0] & 255) << 16 | (rgba[1] & 255) << 8 | (rgba[2] & 255);
    }

    private int[] presets() {
        return new int[]{
                new Color(0, 0, 0, 100).getRGB(),
                new Color(0, 0, 0, 180).getRGB(),
                new Color(46, 48, 54, 150).getRGB(),
                new Color(20, 24, 34, 190).getRGB(),
                new Color(120, 170, 255, 130).getRGB(),
                new Color(40, 20, 20, 150).getRGB(),
                new Color(20, 40, 20, 150).getRGB(),
                new Color(255, 255, 255, 60).getRGB()
        };
    }

    private void sectionLabel(String text, float x, float y, float alpha) {
        FontManager.drawString(text, x, y, new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), (int) alpha).getRGB(), false);
        float width = FontManager.getStringWidth(text);
        RenderUtil.drawRoundedRect(x + width + 8.0F, y + 4.0F, x + PANEL_W - SLIDER_RIGHT - 2.0F, y + 4.6F, 0.3F,
                new Color(255, 255, 255, (int) (alpha * 0.16F)).getRGB());
    }

    private void drawSettings(int mouseX, int mouseY) {
        float px = panelX();
        float py = panelY();

        if (settingsModule != null) {
            panelSlideProgress = Math.min(1.0F, panelSlideProgress + 0.15F);
        } else {
            panelSlideProgress = Math.max(0.0F, panelSlideProgress - 0.20F);
        }
        float slideEase = panelSlideProgress * panelSlideProgress * (3.0F - 2.0F * panelSlideProgress);
        float oy = panelOffsetY();

        ShaderElement.addBlurTask(() -> RenderUtil.drawRoundedRectWithGl(px, py + oy, px + PANEL_W, py + PANEL_H + oy, 12.0F,
                new Color(18, 20, 28, (int) (slideEase * 255)).getRGB()));

        for (int i = 6; i >= 1; i--) {
            float s = i * 2.0F;
            RenderUtil.drawRoundedRectWithGl(px - s, py - s + 3.0F + oy, px + PANEL_W + s, py + PANEL_H + s + 3.0F + oy,
                    12.0F + s, new Color(0, 0, 0, (int) (slideEase * 7)).getRGB());
        }

        RenderUtil.drawRoundedRectWithGl(px - 0.5F, py - 0.5F + oy, px + PANEL_W + 0.5F, py + PANEL_H + 0.5F + oy,
                12.5F, new Color(255, 255, 255, (int) (slideEase * 22)).getRGB());

        RenderUtil.drawRoundedRectGradient(px, py + oy, px + PANEL_W, py + PANEL_H + oy, 12.0F,
                new Color(18, 19, 26, (int) (slideEase * 214)).getRGB(),
                new Color(11, 12, 16, (int) (slideEase * 222)).getRGB());

        float hlMid = px + PANEL_W / 2.0F;
        RenderUtil.drawRoundedRectGradientH(px + 28.0F, py + 0.5F + oy, hlMid, py + 1.0F + oy, 0.0F,
                new Color(255, 255, 255, 0).getRGB(),
                new Color(255, 255, 255, (int) (slideEase * 30)).getRGB());
        RenderUtil.drawRoundedRectGradientH(hlMid, py + 0.5F + oy, px + PANEL_W - 28.0F, py + 1.0F + oy, 0.0F,
                new Color(255, 255, 255, (int) (slideEase * 30)).getRGB(),
                new Color(255, 255, 255, 0).getRGB());

        float headAlpha = slideEase * 255.0F;
        FontManager.drawString(settingsModule, px + SLIDER_LEFT, py + 14.0F + oy,
                new Color(255, 255, 255, (int) headAlpha).getRGB(), true);
        String kind = settingsModule.equals(ISLAND) ? "merged container" : Leader.hudElementManager.isMergeable(settingsModule) ? "merges into Island" : "standalone element";
        FontManager.drawString(kind, px + SLIDER_LEFT, py + 26.0F + oy,
                new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), (int) (slideEase * 210)).getRGB(), false);

        int current = Leader.hudElementManager.background(settingsModule, 40.0F, 40.0F);
        int prevR = (current >> 16) & 255;
        int prevG = (current >> 8) & 255;
        int prevB = current & 255;
        int prevA = (int) (slideEase * ((current >>> 24) & 255));
        float previewX = px + PANEL_W - 78.0F;
        RenderUtil.drawRoundedRectWithGl(previewX, py + 10.0F + oy, px + PANEL_W - SLIDER_RIGHT, py + 36.0F + oy,
                5.0F, new Color(prevR, prevG, prevB, prevA).getRGB());
        RenderUtil.drawRoundedRect(previewX, py + 10.0F + oy, px + PANEL_W - SLIDER_RIGHT, py + 10.9F + oy,
                0.5F, new Color(255, 255, 255, (int) (slideEase * 80)).getRGB());
        RenderUtil.drawRoundedRect(previewX, py + 35.2F + oy, px + PANEL_W - SLIDER_RIGHT, py + 36.0F + oy,
                0.4F, new Color(0, 0, 0, (int) (slideEase * 90)).getRGB());

        sectionLabel("Background", px + SLIDER_LEFT, flowTop() - 12.0F + oy, slideEase * 235.0F);

        if (hasAlign()) {
            HUD hudModule = (HUD) Leader.moduleManager.getModule(HUD.class);
            if (hudModule != null) {
                ModeProperty alignProp = hudModule.align;
                String[] modes = {"LEFT", "RIGHT"};
                int currentAlign = alignProp.getValue();
                sectionLabel("Align", px + SLIDER_LEFT, alignLabelY() + oy, slideEase * 235.0F);
                float btnY = alignButtonY() + oy;
                float btnX = px + SLIDER_LEFT;
                for (int i = 0; i < modes.length; i++) {
                    boolean isActive = i == currentAlign;
                    boolean btnHover = mouseX >= btnX && mouseX <= btnX + 54.0F && mouseY >= btnY && mouseY <= btnY + 17.0F;
                    int btnFill = isActive ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), (int) (slideEase * 205)).getRGB()
                            : btnHover ? new Color(62, 68, 84, (int) (slideEase * 225)).getRGB()
                            : new Color(38, 42, 52, (int) (slideEase * 205)).getRGB();
                    RenderUtil.drawRoundedRectWithGl(btnX, btnY, btnX + 54.0F, btnY + 17.0F, 4.0F, btnFill);
                    if (!isActive) {
                        RenderUtil.drawRoundedRect(btnX, btnY, btnX + 54.0F, btnY + 0.8F, 0.4F,
                                new Color(255, 255, 255, (int) (slideEase * 30)).getRGB());
                    }
                    int textColor = isActive ? new Color(28, 30, 36, (int) (slideEase * 255)).getRGB()
                            : new Color(180, 186, 202, (int) (slideEase * 255)).getRGB();
                    FontManager.drawString(modes[i], btnX + 27.0F - FontManager.getStringWidth(modes[i]) / 2.0F, btnY + 4.5F, textColor, false);
                    btnX += 60.0F;
                }
            }
        }

        sectionLabel("Presets", px + SLIDER_LEFT, presetsLabelY() + oy, slideEase * 235.0F);
        int[] presets = presets();
        for (int i = 0; i < presets.length && i < SWATCH_COUNT; i++) {
            float sx = swatchX(i);
            float sy = presetsY() + oy;
            boolean hovered = mouseX >= sx && mouseX <= sx + 22.0F && mouseY >= sy && mouseY <= sy + 18.0F;
            int swatchColor = new Color((presets[i] >> 16) & 255, (presets[i] >> 8) & 255, presets[i] & 255,
                    (int) (slideEase * ((presets[i] >>> 24) & 255))).getRGB();
            if (hovered) {
                RenderUtil.drawRoundedRectWithGl(sx - 2.0F, sy - 2.0F, sx + 24.0F, sy + 20.0F, 4.0F,
                        new Color(255, 255, 255, (int) (slideEase * 42)).getRGB());
            }
            RenderUtil.drawRoundedRectWithGl(sx, sy, sx + 22.0F, sy + 18.0F, 3.0F, swatchColor);
            RenderUtil.drawRoundedRect(sx, sy, sx + 22.0F, sy + 0.8F, 0.4F,
                    new Color(255, 255, 255, (int) (slideEase * (hovered ? 200 : 55))).getRGB());
        }

        int[] rgba = sliderColors(current);
        String[] labels = {"R", "G", "B", "A"};
        int[] tints = {
                new Color(232, 96, 96).getRGB(),
                new Color(120, 214, 120).getRGB(),
                new Color(110, 160, 255).getRGB(),
                new Color(210, 214, 226).getRGB()
        };

        for (int i = 0; i < 4; i++) {
            float sy = sliderY(i) + oy;
            float trackY = sliderTrackY(i) + oy;
            float left = px + SLIDER_LEFT;
            float right = px + PANEL_W - SLIDER_RIGHT;
            float value = rgba[i] / 255.0F;
            float knobX = left + (right - left) * value;
            int tint = tints[i];
            FontManager.drawString(labels[i], left, sy,
                    new Color((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, (int) (slideEase * 255)).getRGB(), true);
            String valueText = String.valueOf(rgba[i]);
            FontManager.drawString(valueText, right - FontManager.getStringWidth(valueText), sy,
                    new Color(VALUE.getRed(), VALUE.getGreen(), VALUE.getBlue(), (int) (slideEase * 255)).getRGB(), false);

            RenderUtil.drawRoundedRectWithGl(left, trackY, right, trackY + SLIDER_TRACK_H, SLIDER_TRACK_H / 2.0F,
                    new Color(255, 255, 255, (int) (slideEase * 24)).getRGB());
            RenderUtil.drawRoundedRectWithGl(left, trackY, Math.max(left + SLIDER_TRACK_H, knobX), trackY + SLIDER_TRACK_H,
                    SLIDER_TRACK_H / 2.0F, new Color((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, (int) (slideEase * 255)).getRGB());
            RenderUtil.fillCircle(knobX, trackY + SLIDER_TRACK_H / 2.0F, 6.5F, 28,
                    new Color(10, 11, 15, (int) (slideEase * 235)).getRGB());
            RenderUtil.fillCircle(knobX, trackY + SLIDER_TRACK_H / 2.0F, 4.4F, 28,
                    new Color(255, 255, 255, (int) (slideEase * 255)).getRGB());
        }

        float footerY = py + PANEL_H - 18.0F + oy;
        RenderUtil.drawRoundedRect(px + SLIDER_LEFT, footerY - 8.0F, px + PANEL_W - SLIDER_RIGHT, footerY - 7.4F, 0.3F,
                new Color(255, 255, 255, (int) (slideEase * 16)).getRGB());
        String hex = String.format("#%02X%02X%02X%02X", rgba[0], rgba[1], rgba[2], rgba[3]);
        FontManager.drawString(hex, px + SLIDER_LEFT, footerY,
                new Color(168, 174, 190, (int) (slideEase * 255)).getRGB(), false);
        String close = "Click outside to close";
        FontManager.drawString(close, px + PANEL_W - SLIDER_RIGHT - FontManager.getStringWidth(close), footerY,
                new Color(126, 132, 148, (int) (slideEase * 255)).getRGB(), false);
    }

    private boolean inPanel(int mouseX, int mouseY) {
        float px = panelX();
        float py = panelY();
        return mouseX >= px && mouseX <= px + PANEL_W && mouseY >= py && mouseY <= py + PANEL_H;
    }

    private void applySlider(int mouseX) {
        if (settingsModule == null || activeSlider < 0) return;

        float left = panelX() + SLIDER_LEFT;
        float right = panelX() + PANEL_W - SLIDER_RIGHT;
        float value = (mouseX - left) / (right - left);
        value = Math.max(0.0F, Math.min(1.0F, value));

        int current = Leader.hudElementManager.background(settingsModule, 40.0F, 40.0F);
        int[] rgba = sliderColors(current);
        rgba[activeSlider] = Math.round(value * 255.0F);
        Leader.hudElementManager.setBackground(settingsModule, packColor(rgba));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) return;

        if (settingsModule != null) {
            if (inPanel(mouseX, mouseY)) {
                float px = panelX();
                float py = panelY();
                float oy = panelOffsetY();

                if (hasAlign()) {
                    HUD hudModule = (HUD) Leader.moduleManager.getModule(HUD.class);
                    if (hudModule != null) {
                        ModeProperty alignProp = hudModule.align;
                        float btnY = alignButtonY() + oy;
                        float btnX = px + SLIDER_LEFT;
                        for (int i = 0; i < 2; i++) {
                            if (mouseX >= btnX && mouseX <= btnX + 54.0F && mouseY >= btnY && mouseY <= btnY + 17.0F) {
                                alignProp.setValue(i);
                                return;
                            }
                            btnX += 60.0F;
                        }
                    }
                }

                int[] presets = presets();
                for (int i = 0; i < presets.length && i < SWATCH_COUNT; i++) {
                    float sx = swatchX(i);
                    float sy = presetsY() + oy;
                    if (mouseX >= sx && mouseX <= sx + 22.0F && mouseY >= sy && mouseY <= sy + 18.0F) {
                        Leader.hudElementManager.setBackground(settingsModule, presets[i]);
                        return;
                    }
                }

                for (int i = 0; i < 4; i++) {
                    float trackY = sliderTrackY(i) + oy;
                    if (mouseY >= trackY - 8.0F && mouseY <= trackY + SLIDER_TRACK_H + 8.0F) {
                        activeSlider = i;
                        applySlider(mouseX);
                        return;
                    }
                }
                return;
            }

            settingsModule = null;
            activeSlider = -1;
        }

        String hit = hitTest(mouseX, mouseY);
        if (hit != null && hit.equals(ISLAND)) {
            float[] island = box(ISLAND);
            List<String> merged = Leader.hudElementManager.mergedModules(ISLAND);
            for (int i = 0; i < merged.size(); i++) {
                String mergedName = merged.get(i);
                String label = "+ " + mergedName;
                float rowTop = islandRowY(island[1], i);
                if (mouseX >= island[0] + 12.0F && mouseX <= island[0] + 12.0F + FontManager.getStringWidth(label)
                        && mouseY >= rowTop && mouseY <= rowTop + islandRowH()) {
                    Leader.hudElementManager.unmerge(mergedName);
                    return;
                }
            }
        }
        if (hit != null) {
            Leader.hudElementManager.getOrCreate(hit, 40.0F, 40.0F);
            draggingModule = hit;
            dragOffsetX = mouseX - Leader.hudElementManager.x(hit, 40.0F, 40.0F);
            dragOffsetY = mouseY - Leader.hudElementManager.y(hit, 40.0F, 40.0F);
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            dragMoved = false;
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (activeSlider >= 0) {
            activeSlider = -1;
        }

        if (draggingModule != null) {
            String released = draggingModule;
            draggingModule = null;

            if (dragMoved) {
                if (Leader.hudElementManager.isMergeable(released)) {
                    float[] island = box(ISLAND);
                    boolean overIsland = mouseX >= island[0] && mouseX <= island[0] + island[2]
                            && mouseY >= island[1] && mouseY <= island[1] + island[3];

                    if (overIsland && !released.equals("HUD")) {
                        if (Leader.hudElementManager.isSuppressed(released)) {
                            Leader.hudElementManager.unmerge(released);
                        } else {
                            Leader.hudElementManager.merge(released, ISLAND);
                        }
                    } else if (Leader.hudElementManager.isSuppressed(released)) {
                        Leader.hudElementManager.unmerge(released);
                    }
                }
            } else {
                settingsModule = released;
                activeSlider = -1;
            }
        }

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && settingsModule != null) {
            settingsModule = null;
            activeSlider = -1;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        int mouseX = Mouse.getX() * this.width / mc.displayWidth;
        int mouseY = this.height - Mouse.getY() * this.height / mc.displayHeight - 1;

        if (activeSlider >= 0) {
            applySlider(mouseX);
            return;
        }

        if (draggingModule != null) {
            if (Math.abs(mouseX - dragStartMouseX) > 2.0F || Math.abs(mouseY - dragStartMouseY) > 2.0F) {
                dragMoved = true;
            }

            Leader.hudElementManager.set(draggingModule, mouseX - dragOffsetX, mouseY - dragOffsetY);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
