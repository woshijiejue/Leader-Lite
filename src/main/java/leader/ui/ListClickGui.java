package leader.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leader.Leader;
import leader.module.Module;
import leader.module.modules.render.FontManager;
import leader.module.modules.render.GuiModule;
import leader.module.modules.render.HUD;
import leader.property.Property;
import leader.property.properties.BooleanProperty;
import leader.property.properties.ColorProperty;
import leader.property.properties.ModeProperty;
import leader.property.properties.TextProperty;
import leader.ui.callback.GuiInput;
import leader.util.Icon;
import leader.util.KeyBindUtil;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static leader.ui.GuiProperties.*;

public class ListClickGui extends GuiScreen {
    private static final float PANEL_W = 122.0F;
    private static final float PANEL_GAP = 8.0F;
    private static final float HEADER_H = 22.0F;
    private static final float ROW_H = 17.0F;
    private static final float MAX_CONTENT = 300.0F;
    private static final float RADIUS = 3.0F;
    private static final float TOP = 40.0F;
    private static final String[] CATEGORY_NAMES = {"Combat", "Movement", "Render", "Player", "Misc", "Legit"};

    private final File configFile = new File("./config/Leader/", "clickgui-list.txt");
    private final List<Panel> panels = new ArrayList<>();
    private final Map<String, Float> anims = new HashMap<>();
    private final Map<Module, Float> settingHeights = new HashMap<>();
    private final Set<Module> expanded = new HashSet<>();
    private final Set<ColorProperty> expandedColors = new HashSet<>();
    private final Map<ColorProperty, float[]> colorStates = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final ArrayDeque<float[]> scissors = new ArrayDeque<>();

    private Panel currentPanel;
    private Panel draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;
    private Property<?> draggingSlider;
    private float sliderTrackX;
    private float sliderTrackW;
    private ColorProperty draggingColor;
    private int draggingColorBar;
    private float colorTrackX;
    private float colorTrackW;
    private Module bindingModule;

    private long openedAt;
    private long lastFrame;
    private float dt = 0.016F;
    private float alpha = 1.0F;
    private float scale = 1.0F;
    private Color accent = new Color(110, 170, 255);
    private float clipTop = -100000.0F;
    private float clipBottom = 100000.0F;

    private interface ClickAction {
        void click(int button, int mouseX, int mouseY);
    }

    private static final class Hit {
        final float x1, y1, x2, y2;
        final ClickAction action;
        final Panel panel;

        Hit(float x1, float y1, float x2, float y2, ClickAction action, Panel panel) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.action = action;
            this.panel = panel;
        }
    }

    private static final class Panel {
        final String name;
        final List<Module> modules;
        float x;
        float y;
        boolean open = true;
        float scroll;
        float scrollTarget;
        float contentH;
        float viewH;

        Panel(String name, List<Module> modules) {
            this.name = name;
            this.modules = modules;
            this.contentH = modules.size() * ROW_H + 6.0F;
        }
    }

    public ListClickGui() {
        Map<String, List<Module>> map = new LinkedHashMap<>();
        for (String name : CATEGORY_NAMES) map.put(name, new ArrayList<>());
        for (Module module : Leader.moduleManager.modules.values()) {
            String pkg = module.getClass().getPackage().getName().toLowerCase(Locale.ROOT);
            for (String name : CATEGORY_NAMES) {
                if (pkg.contains(name.toLowerCase(Locale.ROOT))) {
                    map.get(name).add(module);
                    break;
                }
            }
        }
        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT));
        float px = 10.0F;
        for (Map.Entry<String, List<Module>> entry : map.entrySet()) {
            entry.getValue().sort(comparator);
            Panel panel = new Panel(entry.getKey(), entry.getValue());
            panel.x = px;
            panel.y = TOP;
            px += PANEL_W + PANEL_GAP;
            panels.add(panel);
        }
        loadPositions();
    }

    private float getScale() {
        float content = panels.size() * (PANEL_W + PANEL_GAP) - PANEL_GAP + 20.0F;
        return Math.max(0.5F, Math.min(1.0F, this.width / content));
    }

    @Override
    public void initGui() {
        scale = getScale();
        for (Panel panel : panels) clampPanel(panel);
        openedAt = System.currentTimeMillis();
        lastFrame = 0L;
    }

    private void clampPanel(Panel panel) {
        float maxX = this.width / scale - PANEL_W;
        float maxY = this.height / scale - HEADER_H;
        panel.x = Math.max(0.0F, Math.min(panel.x, Math.max(0.0F, maxX)));
        panel.y = Math.max(0.0F, Math.min(panel.y, Math.max(0.0F, maxY)));
    }

    @Override
    public void drawScreen(int rawX, int rawY, float partialTicks) {
        long now = System.currentTimeMillis();
        dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        float open = clamp01((now - openedAt) / 220.0F);
        alpha = 1.0F - (1.0F - open) * (1.0F - open) * (1.0F - open);

        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        accent = hud != null ? hud.getColor(now) : new Color(110, 170, 255);

        scale = getScale();
        int mouseX = (int) (rawX / scale);
        int mouseY = (int) (rawY / scale);
        if (draggingPanel != null) {
            draggingPanel.x = mouseX - dragOffsetX;
            draggingPanel.y = mouseY - dragOffsetY;
            clampPanel(draggingPanel);
        }
        updateDrags(mouseX);

        hits.clear();
        resetClip();

        RenderUtil.drawRoundedRectGradient(0.0F, 0.0F, this.width, this.height, 0.0F, col(10, 11, 16, 110), col(4, 5, 8, 170));
        text("Leader", 8.0F, 12.0F, col(245, 247, 252, 255), 16.0F);
        text("Lite", 8.0F + width("Leader", 16.0F) + 3.0F, 12.0F, col(accent, 255), 16.0F);
        text("Left click to toggle   Right click for settings   Drag a header to move",
                8.0F, 25.0F, col(110, 118, 135, 255), 10.0F);

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        float slide = (1.0F - alpha) * 10.0F;
        for (Panel panel : new ArrayList<>(panels)) {
            drawPanel(panel, slide, mouseX, mouseY);
        }
        GlStateManager.popMatrix();
        currentPanel = null;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawPanel(final Panel panel, float slide, int mouseX, int mouseY) {
        currentPanel = panel;
        final float x = panel.x;
        final float y = panel.y + slide;
        float openAnim = anim("po" + panel.name, panel.open ? 1.0F : 0.0F, 14.0F);
        float maxScroll = Math.max(0.0F, panel.contentH - MAX_CONTENT);
        panel.scrollTarget = Math.max(0.0F, Math.min(panel.scrollTarget, maxScroll));
        panel.scroll += (panel.scrollTarget - panel.scroll) * (1.0F - (float) Math.exp(-dt * 18.0F));
        float viewH = Math.min(panel.contentH, MAX_CONTENT) * openAnim;
        panel.viewH = viewH;
        final float totalH = HEADER_H + viewH;
        final float sc = scale;

        ShaderElement.addBlurTask(() -> {
            GlStateManager.pushMatrix();
            GlStateManager.scale(sc, sc, 1.0F);
            RenderUtil.drawRoundedRectWithGl(x, y, x + PANEL_W, y + totalH, RADIUS, new Color(18, 20, 28, 255).getRGB());
            GlStateManager.popMatrix();
        });

        for (int i = 4; i >= 1; i--) {
            float s = i * 1.5F;
            RenderUtil.drawRoundedRectWithGl(x - s, y - s + 2.0F, x + PANEL_W + s, y + totalH + s + 2.0F, RADIUS + s, col(0, 0, 0, 9));
        }
        RenderUtil.drawRoundedRectWithGl(x - 0.5F, y - 0.5F, x + PANEL_W + 0.5F, y + totalH + 0.5F, RADIUS + 0.5F, col(255, 255, 255, 20));
        RenderUtil.drawRoundedRectGradient(x, y, x + PANEL_W, y + totalH, RADIUS, col(18, 19, 26, 226), col(11, 12, 16, 234));

        addHit(x, y, x + PANEL_W, y + totalH, (button, mX, mY) -> {
        });

        float cy = y + HEADER_H / 2.0F;
        categoryIcon(panel.name).drawCentered(x + 12.0F, cy, 11.0F, accent.getRGB(), alpha);
        text(panel.name, x + 22.0F, cy, col(245, 247, 252, 255), 15.0F);
        int enabled = 0;
        for (Module module : panel.modules) if (module.isEnabled()) enabled++;
        String count = enabled + "/" + panel.modules.size();
        text(count, x + PANEL_W - 40.0F - width(count, 10.0F), cy, col(100, 108, 124, 255), 10.0F);

        float btnX = x + PANEL_W - 36.0F;
        float btnY = y + HEADER_H / 2.0F;
        Icon.FLASK.drawCentered(btnX, btnY, 9.0F, col(150, 157, 173, 255), alpha);

        chevron(x + PANEL_W - 9.0F, cy, openAnim, col(150, 157, 173, 255));
        RenderUtil.drawRoundedRectGradientH(x + 0.5F, y + HEADER_H - 1.0F, x + PANEL_W - 0.5F, y + HEADER_H, 0.0F,
                col(accent, (int) (230 * Math.max(0.35F, openAnim))), col(accent, 0));

        addHit(x, y, x + PANEL_W, y + HEADER_H, (button, mX, mY) -> {
            if (button == 0) {
                if (mX >= panel.x + PANEL_W - 16.0F) {
                    panel.open = !panel.open;
                } else if (mX >= panel.x + PANEL_W - 44.0F && mX < panel.x + PANEL_W - 28.0F) {
                    mc.displayGuiScreen(new GuiHUDDesigner());
                } else {
                    draggingPanel = panel;
                    dragOffsetX = mX - panel.x;
                    dragOffsetY = mY - panel.y;
                }
            } else if (button == 1) {
                panel.open = !panel.open;
            }
        });

        if (viewH > 0.5F) {
            float top = y + HEADER_H;
            pushScissor(x, top, PANEL_W, viewH);
            setClip(top, top + viewH);
            float oy = top + 2.0F - panel.scroll;
            float start = oy;
            for (Module module : panel.modules) {
                oy += drawModule(module, x, oy, mouseX, mouseY);
            }
            panel.contentH = oy - start + 4.0F;
            resetClip();
            popScissor();

            if (maxScroll > 0.0F) {
                float thumbH = Math.max(16.0F, viewH * MAX_CONTENT / panel.contentH);
                float thumbY = top + (viewH - thumbH) * (panel.scroll / maxScroll);
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(x + PANEL_W - 2.5F, thumbY, x + PANEL_W - 1.0F, thumbY + thumbH, col(255, 255, 255, 70));
                RenderUtil.disableRenderState();
                if (panel.scroll > 1.0F) {
                    RenderUtil.drawRoundedRectGradient(x + 0.5F, top, x + PANEL_W - 3.0F, top + 8.0F, 0.0F, col(15, 16, 22, 200), col(15, 16, 22, 0));
                }
                if (panel.scroll < maxScroll - 1.0F) {
                    RenderUtil.drawRoundedRectGradient(x + 0.5F, top + viewH - 8.0F, x + PANEL_W - 3.0F, top + viewH, 0.0F, col(11, 12, 16, 0), col(11, 12, 16, 220));
                }
            }
        }
    }

    private float drawModule(final Module module, float x, float oy, int mouseX, int mouseY) {
        String key = module.getName();
        boolean rowVisible = oy + ROW_H > clipTop && oy < clipBottom;
        boolean hovered = rowVisible && inside(mouseX, mouseY, x, Math.max(oy, clipTop), x + PANEL_W, Math.min(oy + ROW_H, clipBottom));
        float hover = anim("mh" + key, hovered ? 1.0F : 0.0F, 16.0F);
        float on = anim("me" + key, module.isEnabled() ? 1.0F : 0.0F, 14.0F);
        float exp = anim("mx" + key, expanded.contains(module) ? 1.0F : 0.0F, 14.0F);

        if (rowVisible) {
            float pr = Math.min(6.0F, (ROW_H - 3.0F) / 2.0F);
            if (on > 0.01F) {
                RenderUtil.drawRoundedRectWithGl(x + 3.5F, oy + 1.0F, x + PANEL_W - 3.5F, oy + ROW_H - 1.0F, pr + 0.5F, col(accent, (int) (60 * on)));
                RenderUtil.drawRoundedRectWithGl(x + 4.0F, oy + 1.5F, x + PANEL_W - 4.0F, oy + ROW_H - 1.5F, pr, col(24, 26, 34, (int) (235 * on)));
                RenderUtil.drawRoundedRectWithGl(x + 4.0F, oy + 1.5F, x + PANEL_W - 4.0F, oy + ROW_H - 1.5F, pr, col(accent, (int) (24 * on)));
            }
            if (hover > 0.01F) {
                RenderUtil.drawRoundedRectWithGl(x + 4.0F, oy + 1.5F, x + PANEL_W - 4.0F, oy + ROW_H - 1.5F, pr,
                        col(255, 255, 255, (int) (8 * hover)));
            }
            float cy = oy + ROW_H / 2.0F;
            if (on > 0.01F) {
                RenderUtil.enableRenderState();
                RenderUtil.fillCircle(x + 10.0F, cy, 1.7F * on, 16, col(accent, (int) (255 * on)));
                RenderUtil.disableRenderState();
            }
            text(GuiText.trim(module.getName(), (int) (PANEL_W - 30.0F), 14.0F), x + 9.0F + 6.0F * on, cy,
                    mixCol(new Color(140, 147, 163), new Color(246, 248, 252), Math.max(on, hover * 0.5F), 255), 14.0F);
            chevron(x + PANEL_W - 9.0F, cy, exp, col(255, 255, 255, (int) (50 + 110 * Math.max(hover, exp))));
            addHit(x, oy, x + PANEL_W, oy + ROW_H, (button, mX, mY) -> {
                if (button == 0) {
                    module.toggle();
                } else if (button == 1) {
                    if (!expanded.remove(module)) expanded.add(module);
                }
            });
        }

        float h = ROW_H;
        if (exp > 0.01F) {
            float setTop = oy + ROW_H;
            float full = settingHeights.getOrDefault(module, 0.0F);
            float shown = full * exp;
            float saveTop = clipTop;
            float saveBottom = clipBottom;
            pushScissor(x, setTop, PANEL_W, shown);
            setClip(Math.max(clipTop, setTop), Math.min(clipBottom, setTop + shown));
            if (full > 0.0F) {
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(x, setTop, x + PANEL_W, setTop + full, col(0, 0, 0, 50));
                RenderUtil.drawRect(x + 5.0F, setTop + 3.0F, x + 5.5F, setTop + full - 3.0F, col(accent, 90));
                RenderUtil.disableRenderState();
            }
            float rx = x + 10.0F;
            float rw = PANEL_W - 18.0F;
            float sy = setTop + 3.0F;
            List<Property<?>> props = Leader.propertyManager.properties.get(module.getClass());
            if (props != null) {
                for (Property<?> property : props) {
                    if (!property.isVisible()) continue;
                    sy += drawProperty(property, rx, sy, rw, mouseX, mouseY);
                }
            }
            sy += drawBind(module, rx, sy, rw, mouseX, mouseY);
            sy += 3.0F;
            settingHeights.put(module, sy - setTop);
            popScissor();
            setClip(saveTop, saveBottom);
            h += shown;
        }
        return h;
    }

    private float drawProperty(Property<?> property, float rx, float ry, float rw, int mouseX, int mouseY) {
        if (property instanceof BooleanProperty) return drawBoolean((BooleanProperty) property, rx, ry, rw, mouseX, mouseY);
        if (property instanceof ModeProperty) return drawMode((ModeProperty) property, rx, ry, rw, mouseX, mouseY);
        if (isSlider(property)) return drawSlider(property, rx, ry, rw, mouseX, mouseY);
        if (property instanceof ColorProperty) return drawColor((ColorProperty) property, rx, ry, rw, mouseX, mouseY);
        if (property instanceof TextProperty) return drawTextProperty((TextProperty) property, rx, ry, rw, mouseX, mouseY);
        return 0.0F;
    }

    private boolean rowVisible(float ry, float h) {
        return ry + h > clipTop && ry < clipBottom;
    }

    private void settingHover(String key, float rx, float ry, float rw, float h, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, rx - 4.0F, Math.max(ry, clipTop), rx + rw + 4.0F, Math.min(ry + h, clipBottom));
        float hover = anim(key, hovered ? 1.0F : 0.0F, 16.0F);
        if (hover > 0.01F) {
            RenderUtil.drawRoundedRectGradientH(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, 0.0F,
                    col(255, 255, 255, (int) (9 * hover)), col(255, 255, 255, 0));
        }
    }

    private float drawBoolean(final BooleanProperty property, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 14.0F;
        String id = "b" + System.identityHashCode(property);
        float on = anim(id, property.getValue() ? 1.0F : 0.0F, 16.0F);
        if (!rowVisible(ry, h)) return h;
        settingHover(id + "h", rx, ry, rw, h, mouseX, mouseY);
        float cy = ry + h / 2.0F;
        text(GuiText.trim(label(property), (int) (rw - 14.0F), 12.0F), rx, cy,
                mixCol(new Color(160, 166, 181), new Color(236, 239, 245), on, 255), 12.0F);
        float bx = rx + rw - 7.0F;
        RenderUtil.enableRenderState();
        frame(bx, cy - 3.5F, bx + 7.0F, cy + 3.5F, 0.75F, mixCol(new Color(86, 92, 108), accent, on, 255));
        if (on > 0.01F) {
            float in = 1.5F + 2.0F * (1.0F - on);
            RenderUtil.drawRect(bx + in, cy - 3.5F + in, bx + 7.0F - in, cy + 3.5F - in, col(accent, (int) (255 * on)));
        }
        RenderUtil.disableRenderState();
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) property.setValue(!property.getValue());
        });
        return h;
    }

    private float drawMode(final ModeProperty property, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 14.0F;
        if (!rowVisible(ry, h)) return h;
        String id = "m" + System.identityHashCode(property);
        settingHover(id + "h", rx, ry, rw, h, mouseX, mouseY);
        float cy = ry + h / 2.0F;
        String value = GuiText.trim(property.getModeString().replace("_", " "), (int) (rw * 0.6F), 12.0F);
        float vw = width(value, 12.0F);
        text(GuiText.trim(label(property), (int) (rw - vw - 6.0F), 12.0F), rx, cy, col(160, 166, 181, 255), 12.0F);
        text(value, rx + rw - vw, cy, col(accent, 255), 12.0F);
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) property.nextMode();
            else if (button == 1) property.previousMode();
        });
        return h;
    }

    private float drawSlider(final Property<?> property, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 22.0F;
        String id = "s" + System.identityHashCode(property);
        float ratio = anim(id, sliderRatio(property), draggingSlider == property ? 40.0F : 18.0F);
        if (!rowVisible(ry, h)) return h;
        settingHover(id + "h", rx, ry, rw, h, mouseX, mouseY);
        float ly = ry + 7.0F;
        String value = sliderText(property);
        float vw = width(value, 11.0F);
        text(GuiText.trim(label(property), (int) (rw - vw - 6.0F), 12.0F), rx, ly, col(160, 166, 181, 255), 12.0F);
        text(value, rx + rw - vw, ly, col(236, 239, 245, 255), 11.0F);
        float ty = ry + 16.0F;
        float fx = rx + rw * clamp01(ratio);
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(rx, ty - 1.0F, rx + rw, ty + 1.0F, col(255, 255, 255, 24));
        RenderUtil.disableRenderState();
        if (fx - rx > 0.5F) {
            RenderUtil.drawRoundedRectGradientH(rx, ty - 1.0F, fx, ty + 1.0F, 0.0F, col(accent, 140), col(accent, 255));
        }
        boolean active = draggingSlider == property || inside(mouseX, mouseY, rx - 4.0F, Math.max(ry, clipTop), rx + rw + 4.0F, Math.min(ry + h, clipBottom));
        float knob = anim(id + "k", active ? 1.0F : 0.0F, 16.0F);
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(fx - 1.0F, ty - 3.0F - knob, fx + 1.0F, ty + 3.0F + knob, col(245, 247, 252, 255));
        RenderUtil.disableRenderState();
        final float tx = rx;
        final float tw = rw;
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) {
                draggingSlider = property;
                sliderTrackX = tx;
                sliderTrackW = tw;
                setSliderRatio(property, clamp01((mX - tx) / tw));
            } else if (button == 1) {
                GuiInput.prompt(label(property), rawValue(property), s -> setSliderText(property, s), this);
            }
        });
        return h;
    }

    private float drawColor(final ColorProperty property, float rx, float ry, float rw, int mouseX, int mouseY) {
        String id = "c" + System.identityHashCode(property);
        float open = anim(id, expandedColors.contains(property) ? 1.0F : 0.0F, 16.0F);
        float h = 14.0F + 30.0F * open;
        if (!rowVisible(ry, h)) return h;
        settingHover(id + "h", rx, ry, rw, 14.0F, mouseX, mouseY);
        float cy = ry + 7.0F;
        text(GuiText.trim(label(property), (int) (rw - 18.0F), 12.0F), rx, cy, col(160, 166, 181, 255), 12.0F);
        int rgb = property.getValue();
        float sx = rx + rw - 12.0F;
        RenderUtil.enableRenderState();
        frame(sx, cy - 3.5F, rx + rw, cy + 3.5F, 0.75F, col(255, 255, 255, 70));
        RenderUtil.drawRect(sx + 1.0F, cy - 2.5F, rx + rw - 1.0F, cy + 2.5F, col(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255));
        RenderUtil.disableRenderState();
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + 14.0F, (button, mX, mY) -> {
            if (button == 0 || button == 1) {
                if (!expandedColors.remove(property)) expandedColors.add(property);
            }
        });

        if (open > 0.05F) {
            float[] hsb = colorStates.computeIfAbsent(property, p -> new float[3]);
            if (draggingColor != property) {
                Color c = new Color(rgb);
                Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsb);
            }
            float saveTop = clipTop;
            float saveBottom = clipBottom;
            pushScissor(rx - 4.0F, ry, rw + 8.0F, h);
            setClip(Math.max(clipTop, ry), Math.min(clipBottom, ry + h));
            float barH = 5.0F;
            float[] barY = {ry + 16.0F, ry + 25.0F, ry + 34.0F};
            for (int i = 0; i < 6; i++) {
                float x1 = rx + rw * i / 6.0F;
                float x2 = rx + rw * (i + 1) / 6.0F;
                hGradient(x1, barY[0], x2, barY[0] + barH, Color.HSBtoRGB(i / 6.0F, 1.0F, 1.0F), Color.HSBtoRGB((i + 1) / 6.0F, 1.0F, 1.0F));
            }
            hGradient(rx, barY[1], rx + rw, barY[1] + barH, 0xFFFFFFFF, Color.HSBtoRGB(hsb[0], 1.0F, 1.0F));
            hGradient(rx, barY[2], rx + rw, barY[2] + barH, 0xFF000000, Color.HSBtoRGB(hsb[0], hsb[1], 1.0F));
            for (int i = 0; i < 3; i++) {
                final int bar = i;
                float px = rx + rw * hsb[i];
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(px - 1.5F, barY[i] - 1.5F, px + 1.5F, barY[i] + barH + 1.5F, col(0, 0, 0, 160));
                RenderUtil.drawRect(px - 0.75F, barY[i] - 1.0F, px + 0.75F, barY[i] + barH + 1.0F, col(250, 250, 252, 255));
                RenderUtil.disableRenderState();
                final float tx = rx;
                final float tw = rw;
                addHit(rx - 2.0F, barY[i] - 2.0F, rx + rw + 2.0F, barY[i] + barH + 2.0F, (button, mX, mY) -> {
                    if (button == 0) {
                        draggingColor = property;
                        draggingColorBar = bar;
                        colorTrackX = tx;
                        colorTrackW = tw;
                        updateColorDrag(mX);
                    }
                });
            }
            popScissor();
            setClip(saveTop, saveBottom);
        }
        return h;
    }

    private float drawTextProperty(final TextProperty property, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 14.0F;
        if (!rowVisible(ry, h)) return h;
        String id = "t" + System.identityHashCode(property);
        settingHover(id + "h", rx, ry, rw, h, mouseX, mouseY);
        float cy = ry + h / 2.0F;
        String value = property.getValue() == null ? "" : property.getValue();
        String shown = GuiText.trim(value, (int) (rw * 0.55F), 11.0F);
        float vw = width(shown, 11.0F);
        text(GuiText.trim(label(property), (int) (rw - vw - 6.0F), 12.0F), rx, cy, col(160, 166, 181, 255), 12.0F);
        text(shown, rx + rw - vw, cy, col(205, 210, 222, 255), 11.0F);
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) GuiInput.prompt(label(property), property.getValue(), property::setValue, this);
        });
        return h;
    }

    private float drawBind(final Module module, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 14.0F;
        if (!rowVisible(ry, h)) return h;
        String id = "k" + System.identityHashCode(module);
        settingHover(id, rx, ry, rw, h, mouseX, mouseY);
        float cy = ry + h / 2.0F;
        boolean binding = bindingModule == module;
        String value = binding ? "..." : KeyBindUtil.getKeyName(module.getKey());
        if (value == null || value.isEmpty()) value = "NONE";
        float vw = width(value, 11.0F);
        text("Bind", rx, cy, col(160, 166, 181, 255), 12.0F);
        text(value, rx + rw - vw, cy, binding ? col(accent, 255) : col(205, 210, 222, 255), 11.0F);
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) bindingModule = module;
            else if (button == 1) module.setKey(module instanceof GuiModule ? Keyboard.KEY_RSHIFT : KeyBindUtil.NONE);
        });
        return h;
    }

    private void chevron(float cx, float cy, float progress, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0.0F);
        GlStateManager.rotate(90.0F * clamp01(progress), 0.0F, 0.0F, 1.0F);
        RenderUtil.enableRenderState();
        RenderUtil.drawLine(-1.25F, -2.75F, 1.5F, 0.0F, 1.2F, color);
        RenderUtil.drawLine(1.5F, 0.0F, -1.25F, 2.75F, 1.2F, color);
        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }

    private void frame(float x1, float y1, float x2, float y2, float t, int color) {
        RenderUtil.drawRect(x1, y1, x2, y1 + t, color);
        RenderUtil.drawRect(x1, y2 - t, x2, y2, color);
        RenderUtil.drawRect(x1, y1 + t, x1 + t, y2 - t, color);
        RenderUtil.drawRect(x2 - t, y1 + t, x2, y2 - t, color);
    }

    private void hGradient(float x1, float y1, float x2, float y2, int c1, int c2) {
        int a = (int) (255 * alpha);
        RenderUtil.enableRenderState();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y1, 0.0D).color(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, a).endVertex();
        wr.pos(x1, y2, 0.0D).color(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, a).endVertex();
        wr.pos(x2, y2, 0.0D).color(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, a).endVertex();
        wr.pos(x2, y1, 0.0D).color(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, a).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        RenderUtil.disableRenderState();
    }

    private void updateDrags(int mouseX) {
        if (draggingSlider != null && sliderTrackW > 0.0F) {
            setSliderRatio(draggingSlider, clamp01((mouseX - sliderTrackX) / sliderTrackW));
        }
        if (draggingColor != null) {
            updateColorDrag(mouseX);
        }
    }

    private void updateColorDrag(int mouseX) {
        if (draggingColor == null || colorTrackW <= 0.0F) return;
        float[] hsb = colorStates.computeIfAbsent(draggingColor, p -> new float[3]);
        hsb[draggingColorBar] = clamp01((mouseX - colorTrackX) / colorTrackW);
        draggingColor.setValue(new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2])).getRGB());
    }

    private Icon categoryIcon(String name) {
        switch (name) {
            case "Combat":
                return Icon.STRENGTH;
            case "Movement":
                return Icon.SPEED;
            case "Render":
                return Icon.VISION;
            case "Player":
                return Icon.RESIST;
            case "Misc":
                return Icon.INFO;
            default:
                return Icon.CHECK;
        }
    }

    private void text(String s, float x, float centerY, int color, float size) {
        float cap = FontManager.getCapHeight(size);
        FontManager.drawString(s, x, centerY + cap / 2.0F - FontManager.getBaseline(size), color, false, size);
    }

    private float width(String s, float size) {
        return FontManager.getStringWidth(s, size);
    }

    private float anim(String key, float target, float speed) {
        Float current = anims.get(key);
        float value = current == null ? target : current;
        value += (target - value) * (1.0F - (float) Math.exp(-dt * speed));
        if (Math.abs(target - value) < 0.001F) value = target;
        anims.put(key, value);
        return value;
    }

    private int col(int r, int g, int b, int a) {
        int scaled = Math.round(a * alpha);
        if (a > 0) scaled = Math.max(4, scaled);
        return new Color(r, g, b, Math.max(0, Math.min(255, scaled))).getRGB();
    }

    private int col(Color c, int a) {
        return col(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private int mixCol(Color a, Color b, float t, int alphaValue) {
        t = clamp01(t);
        return col((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t), alphaValue);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static boolean inside(int mx, int my, float x1, float y1, float x2, float y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private void addHit(float x1, float y1, float x2, float y2, ClickAction action) {
        float top = Math.max(y1, clipTop);
        float bottom = Math.min(y2, clipBottom);
        if (bottom <= top) return;
        hits.add(new Hit(x1, top, x2, bottom, action, currentPanel));
    }

    private void setClip(float top, float bottom) {
        clipTop = top;
        clipBottom = bottom;
    }

    private void resetClip() {
        clipTop = -100000.0F;
        clipBottom = 100000.0F;
    }

    private void pushScissor(float x, float y, float w, float h) {
        float x1 = x;
        float y1 = y;
        float x2 = x + Math.max(0.0F, w);
        float y2 = y + Math.max(0.0F, h);
        float[] parent = scissors.peek();
        if (parent != null) {
            x1 = Math.max(x1, parent[0]);
            y1 = Math.max(y1, parent[1]);
            x2 = Math.min(x2, parent[2]);
            y2 = Math.min(y2, parent[3]);
        }
        float[] rect = {x1, y1, Math.max(x1, x2), Math.max(y1, y2)};
        scissors.push(rect);
        applyScissor(rect);
    }

    private void popScissor() {
        scissors.poll();
        float[] parent = scissors.peek();
        if (parent == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            applyScissor(parent);
        }
    }

    private void applyScissor(float[] rect) {
        float s = scale * new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) Math.floor(rect[0] * s), (int) Math.floor(mc.displayHeight - rect[3] * s),
                (int) Math.ceil((rect[2] - rect[0]) * s), (int) Math.ceil((rect[3] - rect[1]) * s));
    }

    @Override
    protected void mouseClicked(int rawX, int rawY, int button) throws IOException {
        int mouseX = (int) (rawX / scale);
        int mouseY = (int) (rawY / scale);
        if (bindingModule != null) {
            if (button != 0) bindingModule.setKey(button - 100);
            bindingModule = null;
            return;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (inside(mouseX, mouseY, hit.x1, hit.y1, hit.x2, hit.y2)) {
                if (hit.panel != null && panels.remove(hit.panel)) panels.add(hit.panel);
                hit.action.click(button, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingPanel = null;
        draggingSlider = null;
        draggingColor = null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = (int) (Mouse.getEventX() * this.width / this.mc.displayWidth / scale);
        int mouseY = (int) ((this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1) / scale);
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            float top = panel.y + HEADER_H;
            if (inside(mouseX, mouseY, panel.x, panel.y, panel.x + PANEL_W, top + panel.viewH)) {
                if (inside(mouseX, mouseY, panel.x, top, panel.x + PANEL_W, top + panel.viewH)) {
                    panel.scrollTarget += wheel > 0 ? -24.0F : 24.0F;
                }
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                bindingModule = null;
                return;
            }
            int key = KeyBindUtil.fromTyped(typedChar, keyCode);
            if (key == KeyBindUtil.NONE) return;
            bindingModule.setKey(key);
            bindingModule = null;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        draggingPanel = null;
        draggingSlider = null;
        draggingColor = null;
        bindingModule = null;
        savePositions();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        for (Panel panel : panels) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", panel.x);
            pos.addProperty("y", panel.y);
            pos.addProperty("open", panel.open);
            json.add(panel.name, pos);
        }
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            for (Panel panel : panels) {
                if (!json.has(panel.name)) continue;
                JsonObject pos = json.getAsJsonObject(panel.name);
                if (pos.has("x")) panel.x = pos.get("x").getAsFloat();
                if (pos.has("y")) panel.y = pos.get("y").getAsFloat();
                if (pos.has("open")) panel.open = pos.get("open").getAsBoolean();
            }
        } catch (Exception ignored) {
        }
    }
}
