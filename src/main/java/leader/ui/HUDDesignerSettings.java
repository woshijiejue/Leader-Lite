package leader.ui;

import leader.Leader;
import leader.module.modules.render.FontManager;
import leader.module.modules.render.Island;
import leader.module.modules.render.notification.NoticeMode;
import leader.module.modules.render.notification.Notification;
import leader.property.Property;
import leader.property.properties.*;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Scrollable inspector. Input and drawing share the same clipped control rectangles. */
final class HUDDesignerSettings {
    private final GuiHUDDesigner designer;
    private final HUDDesignerElement element;
    private final List<Control> controls = new ArrayList<>();
    private int tab;
    private float scroll;
    private float contentHeight;
    private float x, y, w, h, top, bottom;
    private float opacity;
    private Control dragging;
    private boolean buildingBody;
    private ColorProperty colorProperty;
    private Color accent = new Color(110, 170, 255);
    private final boolean panelLeft;

    private static final class Control {
        float x, y, w, h;
        Runnable click;
        Property<?> property;
        int channel = -1;
        boolean numeric;
        boolean body;
        boolean contains(int mx, int my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    }

    HUDDesignerSettings(GuiHUDDesigner designer, HUDDesignerElement element) {
        this.designer = designer;
        this.element = element;
        float[] bounds = element.bounds(designer.islandState());
        this.panelLeft = bounds[0] + bounds[2] / 2.0F >= new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth() / 2.0F;
    }

    private int color(Color c, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha * opacity))) << 24) | (c.getRGB() & 0xFFFFFF);
    }

    private void text(String s, float tx, float ty, Color c, float size) {
        FontManager.drawString(s, tx, ty, color(c, 255), false, size);
    }

    private String fit(String s, float maxW, float size) {
        if (FontManager.getStringWidth(s, size) <= maxW) return s;
        while (!s.isEmpty() && FontManager.getStringWidth(s + "..", size) > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    void draw(int mx, int my, float dt) {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        opacity = Math.min(1, opacity + dt * 7);
        accent = designer.accent();
        w = Math.min(224, sr.getScaledWidth() - 16);
        h = Math.min(288, sr.getScaledHeight() - 16);
        x = panelLeft ? 8 : sr.getScaledWidth() - w - 8;
        y = (sr.getScaledHeight() - h) / 2;
        top = y + 56;
        bottom = y + h - 20;
        controls.clear();
        buildingBody = false;
        ShaderElement.blurArea(x, y, x + w, y + h);
        RenderUtil.drawRoundedRectWithGl(x, y + 1.5F, x + w, y + h + 1.5F, 3, color(Color.BLACK, 18));
        RenderUtil.drawRoundedRectWithGl(x - 0.5F, y - 0.5F, x + w + 0.5F, y + h + 0.5F,
                3.5F, color(Color.WHITE, 20));
        RenderUtil.drawRoundedRectGradient(x, y, x + w, y + h, 3,
                color(new Color(18, 19, 26), 205), color(new Color(11, 12, 16), 216));
        RenderUtil.drawRoundedRectGradientH(x + 0.5F, y + 25, x + w - 0.5F, y + 26, 0,
                color(accent, 150), color(accent, 0));
        text(element.name, x + 10, y + 9, new Color(245, 247, 252), 15);
        button("x", x + w - 24, y + 5, 18, 17, false, mx, my, designer::closeSettings);
        String[] tabs = {"Appearance", "Colors", "Layout"};
        float tw = (w - 32) / 3;
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            button(tabs[i], x + 16 + i * tw, y + 32, tw - 4, 18, tab == i, mx, my, () -> {
                tab = index;
                scroll = 0;
                dragging = null;
            });
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (bottom - top))));
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int factor = sr.getScaleFactor();
        GL11.glScissor((int) (x * factor), (int) ((sr.getScaledHeight() - bottom) * factor),
                (int) (w * factor), Math.max(0, (int) ((bottom - top) * factor)));
        float cursor = top + 4 - scroll;
        buildingBody = true;
        if (tab == 0) cursor = appearance(cursor, mx, my);
        else if (tab == 1) cursor = colors(cursor, mx, my);
        else cursor = layout(cursor, mx, my);
        GL11.glPopAttrib();
        contentHeight = cursor - (top - scroll) + 8;
        float visible = bottom - top;
        if (contentHeight > visible) {
            float thumb = Math.max(20, visible * visible / contentHeight);
            float thumbY = top + (visible - thumb) * scroll / Math.max(1, contentHeight - visible);
            RenderUtil.drawRect(x + w - 3, thumbY, x + w - 2, thumbY + thumb, color(Color.WHITE, 60));
        }
        RenderUtil.drawRect(x + 10, bottom + 4, x + w - 10, bottom + 4.5F, color(Color.WHITE, 12));
        text("Live", x + 10, bottom + 9, new Color(145, 161, 183), 9);
        String footer = "Scroll / Esc to close";
        text(footer, x + w - 10 - FontManager.getStringWidth(footer, 9), bottom + 9,
                new Color(123, 136, 155), 9);
    }

    private float appearance(float cursor, int mx, int my) {
        if (element.module instanceof Notification) {
            Notification notification = (Notification) element.module;
            text("Sample notification", x + 16, cursor, new Color(160, 166, 181), 11);
            cursor += 13;
            NoticeMode[] modes = {NoticeMode.Enable, NoticeMode.Disable, NoticeMode.Info};
            String[] labels = {"Enabled", "Disabled", "Notice"};
            float width = (w - 32) / 3;
            for (int i = 0; i < modes.length; i++) {
                final NoticeMode mode = modes[i];
                button(labels[i], x + 16 + i * width, cursor, width - 3, 17, notification.getPreviewMode() == mode,
                        mx, my, () -> notification.setPreviewMode(mode));
            }
            cursor += 25;
        }
        if (element.module instanceof Island) {
            text("Preview state", x + 16, cursor, new Color(160, 166, 181), 11);
            cursor += 13;
            String[] states = {"Idle", "Blocks", "Target", "Health", "Alert"};
            float bw = (w - 36) / 5;
            for (int i = 0; i < states.length; i++) {
                final int state = i;
                button(states[i], x + 16 + i * bw, cursor, bw - 3, 17, designer.islandState() == i,
                        mx, my, () -> designer.setIslandState(state));
            }
            cursor += 25;
            if (designer.islandState() == 4) {
                Island island = (Island) element.module;
                NoticeMode[] modes = {NoticeMode.Enable, NoticeMode.Disable, NoticeMode.Info};
                String[] labels = {"Enabled", "Disabled", "Notice"};
                float width = (w - 32) / 3;
                for (int i = 0; i < modes.length; i++) {
                    final NoticeMode mode = modes[i];
                    button(labels[i], x + 16 + i * width, cursor, width - 3, 17,
                            island.getPreviewNoticeMode() == mode, mx, my, () -> island.setPreviewNoticeMode(mode));
                }
                cursor += 25;
            }
        }
        List<Property<?>> properties = element.settings();
        for (Property<?> p : properties) {
            float left = x + 16, right = x + w - 16;
            String label = p.getName().replace('-', ' ');
            if (p instanceof BooleanProperty) {
                BooleanProperty bool = (BooleanProperty) p;
                Control ctrl = control(left - 4, cursor, w - 24, 17);
                ctrl.click = () -> bool.setValue(!bool.getValue());
                rowHover(ctrl, mx, my);
                text(fit(label, w - 57, 12), left, cursor + 4,
                        bool.getValue() ? new Color(236, 239, 245) : new Color(160, 166, 181), 12);
                float bx = right - 7, by = cursor + 5;
                int c = color(bool.getValue() ? accent : new Color(86, 92, 108), 230);
                RenderUtil.drawRect(bx, by, bx + 7, by + 0.7F, c);
                RenderUtil.drawRect(bx, by + 6.3F, bx + 7, by + 7, c);
                RenderUtil.drawRect(bx, by, bx + 0.7F, by + 7, c);
                RenderUtil.drawRect(bx + 6.3F, by, bx + 7, by + 7, c);
                if (bool.getValue()) RenderUtil.drawRect(bx + 1.5F, by + 1.5F, bx + 5.5F, by + 5.5F, color(accent, 230));
                cursor += 19;
            } else if (p instanceof ModeProperty) {
                ModeProperty mode = (ModeProperty) p;
                String value = fit(mode.getModeString(), (w - 32) * 0.6F, 12);
                float vw = FontManager.getStringWidth(value, 12);
                text(fit(label, w - 40 - vw, 12), left, cursor + 4, new Color(160, 166, 181), 12);
                text(value, right - vw, cursor + 4, accent, 12);
                Control ctrl = control(left - 4, cursor, w - 24, 17);
                ctrl.click = () -> changeMode(mode, true);
                ctrl.property = mode;
                rowHover(ctrl, mx, my);
                cursor += 19;
            } else if (p instanceof ColorProperty) {
                ColorProperty cp = (ColorProperty) p;
                text(fit(label, w - 116, 12), left, cursor + 4, new Color(160, 166, 181), 12);
                button(String.format(Locale.ROOT, "#%06X", cp.getValue() & 0xFFFFFF), right - 64, cursor, 64, 17,
                        false, mx, my, () -> { colorProperty = cp; tab = 1; scroll = 0; });
                RenderUtil.drawRoundedRectWithGl(right - 77, cursor + 5, right - 69, cursor + 12, 1,
                        color(new Color(cp.getValue(), true), 255));
                cursor += 21;
            } else {
                float min = minimum(p), max = maximum(p);
                float value = ((Number) p.getValue()).floatValue();
                text(fit(label, w - 91, 12), left, cursor, new Color(160, 166, 181), 12);
                String v = p instanceof FloatProperty ? String.format(Locale.ROOT, "%.2f", value)
                        : Math.round(value) + (p instanceof PercentProperty ? "%" : "");
                text(v, right - FontManager.getStringWidth(v, 12), cursor, new Color(236, 239, 245), 12);
                Control control = slider(left, cursor + 15, w - 32, (value - min) / Math.max(0.001F, max - min), accent);
                control.numeric = true;
                control.property = p;
                cursor += 26;
            }
        }
        return cursor;
    }

    private float colors(float cursor, int mx, int my) {
        float left = x + 16;
        if (colorProperty == null && !element.supportsBackground()) {
            text("No background layer", left, cursor, new Color(219, 227, 240), 13);
            text("This style renders text, items or an image.", left, cursor + 22, new Color(152, 166, 188), 11);
            text("Use Appearance for its visual settings.", left, cursor + 39, new Color(152, 166, 188), 11);
            return cursor + 66;
        }
        String title = colorProperty == null ? "Background RGBA" : colorProperty.getName();
        text(fit(title, w - 105, 13), left, cursor, new Color(219, 227, 240), 13);
        if (colorProperty != null) button("Background", x + w - 92, cursor - 4, 76, 20, false, mx, my,
                () -> { colorProperty = null; scroll = 0; });
        cursor += 25;
        int current = currentColor();
        for (int yy = 0; yy < 3; yy++) {
            for (int xx = 0; xx < 24; xx++) {
                float cell = (w - 32) / 24;
                RenderUtil.drawRect(left + xx * cell, cursor + yy * 8, left + (xx + 1) * cell, cursor + (yy + 1) * 8,
                        color(new Color((xx + yy) % 2 == 0 ? 57 : 40, (xx + yy) % 2 == 0 ? 61 : 44,
                                (xx + yy) % 2 == 0 ? 70 : 53), 255));
            }
        }
        Color c = new Color(current, true);
        RenderUtil.drawRect(left, cursor, x + w - 16, cursor + 24, color(c, c.getAlpha()));
        cursor += 36;
        int[] presets = {0x96181D29, 0xB4000000, 0x9630323C, 0xA0142538, 0x8281A9EC, 0x96602C40, 0x96234035, 0x3CFFFFFF};
        float bw = (w - 36) / 8;
        for (int i = 0; i < presets.length; i++) {
            final int preset = presets[i];
            Control ctrl = control(left + i * bw, cursor, bw - 3, 23);
            ctrl.click = () -> setColor(colorProperty == null ? preset : (preset | 0xFF000000));
            Color swatch = new Color(preset, true);
            RenderUtil.drawRoundedRectWithGl(ctrl.x, cursor, ctrl.x + ctrl.w, cursor + 23, 4,
                    color(swatch, swatch.getAlpha()));
        }
        cursor += 38;
        String[] labels = {"Red", "Green", "Blue", "Opacity"};
        Color[] tints = {new Color(235, 132, 145), new Color(144, 208, 164), accent, new Color(208, 213, 229)};
        int[] values = {current >> 16 & 255, current >> 8 & 255, current & 255, current >>> 24};
        for (int i = 0; i < 4; i++) {
            text(labels[i], left, cursor, tints[i], 12);
            String v = Integer.toString(values[i]);
            text(v, x + w - 16 - FontManager.getStringWidth(v, 12), cursor, new Color(200, 209, 223), 12);
            Control ctrl = slider(left, cursor + 15, w - 32, values[i] / 255.0F, tints[i]);
            ctrl.channel = i;
            cursor += 28;
        }
        text(String.format(Locale.ROOT, "#%08X  (ARGB)", current), left, cursor, new Color(152, 166, 188), 11);
        return cursor + 20;
    }

    private void changeMode(ModeProperty property, boolean next) {
        float[] before = element.bounds(designer.islandState());
        if (next) property.nextMode();
        else property.previousMode();
        float[] after = element.bounds(designer.islandState());
        float[] origin = element.origin();
        // Alignment/style changes must not teleport an editable HUD off screen.
        element.setOrigin(origin[0] + before[0] - after[0], origin[1] + before[1] - after[1]);
        designer.keepOnScreen(element);
    }

    private float layout(float cursor, int mx, int my) {
        float[] origin = element.origin(), bounds = element.bounds(designer.islandState());
        text(String.format(Locale.ROOT, "Position   %.0f / %.0f", origin[0], origin[1]), x + 16, cursor, accent, 12);
        cursor += 20;
        text(String.format(Locale.ROOT, "Preview   %.0f x %.0f", bounds[2], bounds[3]), x + 16, cursor,
                new Color(151, 164, 186), 12);
        cursor += 29;
        float bw = (w - 36) / 2;
        button("Center X", x + 16, cursor, bw, 24, false, mx, my, () -> designer.alignElement(element, 0));
        button("Center Y", x + 20 + bw, cursor, bw, 24, false, mx, my, () -> designer.alignElement(element, 1));
        cursor += 32;
        button("Reset position", x + 16, cursor, bw, 24, false, mx, my, () -> designer.resetPosition(element));
        button("Keep on screen", x + 20 + bw, cursor, bw, 24, false, mx, my, () -> designer.keepOnScreen(element));
        cursor += 38;
        if (element.name.equals("Notification")) {
            button("Bottom right", x + 16, cursor, w - 32, 19, false, mx, my, () -> designer.bottomRight(element));
            cursor += 26;
        }
        text("NUDGE / 1 PX", x + 16, cursor, new Color(151, 164, 186), 10);
        cursor += 18;
        String[] arrows = {"<", ">", "Up", "Down"};
        int[][] delta = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        float nw = (w - 44) / 4;
        for (int i = 0; i < arrows.length; i++) {
            final int index = i;
            button(arrows[i], x + 16 + i * (nw + 4), cursor, nw, 24, false, mx, my,
                    () -> designer.nudge(element, delta[index][0], delta[index][1]));
        }
        cursor += 38;
        if (!element.name.equals("ScaffoldCounter")) {
            button(element.module.isEnabled() ? "Module enabled" : "Module disabled", x + 16, cursor, w - 32, 26,
                    element.module.isEnabled(), mx, my, () -> element.module.setEnabled(!element.module.isEnabled()));
            cursor += 36;
        }
        if (element.name.equals("Island")) {
            for (String name : Leader.hudElementManager.mergedModules("Island")) {
                button("Unmerge " + name, x + 16, cursor, w - 32, 24, false, mx, my, () -> designer.unmerge(name));
                cursor += 31;
            }
        } else if ("Island".equals(Leader.hudElementManager.mergedInto(element.name))) {
            button("Unmerge from Island", x + 16, cursor, w - 32, 26, false, mx, my, () -> designer.unmerge(element.name));
            cursor += 36;
        }
        return cursor;
    }

    private Control control(float cx, float cy, float cw, float ch) {
        Control ctrl = new Control();
        ctrl.x = cx; ctrl.y = cy; ctrl.w = cw; ctrl.h = ch;
        ctrl.body = buildingBody;
        controls.add(ctrl);
        return ctrl;
    }

    private void button(String label, float bx, float by, float bw, float bh, boolean selected, int mx, int my, Runnable action) {
        Control ctrl = control(bx, by, bw, bh);
        ctrl.click = action;
        boolean hovered = ctrl.contains(mx, my) && (!ctrl.body || my >= top && my <= bottom);
        if (selected || hovered) RenderUtil.drawRoundedRectWithGl(bx, by, bx + bw, by + bh, 2,
                color(selected ? accent : Color.WHITE, selected ? 18 : 7));
        if (selected) RenderUtil.drawRect(bx + 2, by + bh - 1, bx + bw - 2, by + bh - 0.3F, color(accent, 160));
        text(label, bx + (bw - FontManager.getStringWidth(label, 11)) / 2, by + (bh - FontManager.getFontHeight(11)) / 2,
                selected ? accent : new Color(160, 166, 181), 11);
    }

    private void rowHover(Control ctrl, int mx, int my) {
        if (ctrl.contains(mx, my) && my >= top && my <= bottom) {
            RenderUtil.drawRoundedRectGradientH(ctrl.x, ctrl.y, ctrl.x + ctrl.w, ctrl.y + ctrl.h,
                    0, color(Color.WHITE, 8), color(Color.WHITE, 0));
        }
    }

    private Control slider(float sx, float sy, float sw, float value, Color tint) {
        value = Math.max(0, Math.min(1, value));
        RenderUtil.drawRect(sx, sy - 0.8F, sx + sw, sy + 0.8F, color(Color.WHITE, 24));
        if (value > 0) RenderUtil.drawRoundedRectGradientH(sx, sy - 0.8F, sx + sw * value, sy + 0.8F, 0,
                color(tint, 140), color(tint, 235));
        RenderUtil.drawRect(sx + sw * value - 0.8F, sy - 3, sx + sw * value + 0.8F, sy + 3,
                color(new Color(239, 244, 255), 255));
        return control(sx, sy - 5, sw, 10);
    }

    private float minimum(Property<?> p) {
        if (p instanceof FloatProperty) return ((FloatProperty) p).getMinimum();
        if (p instanceof IntProperty) return ((IntProperty) p).getMinimum();
        return ((PercentProperty) p).getMinimum();
    }

    private float maximum(Property<?> p) {
        if (p instanceof FloatProperty) return ((FloatProperty) p).getMaximum();
        if (p instanceof IntProperty) return ((IntProperty) p).getMaximum();
        return ((PercentProperty) p).getMaximum();
    }

    private int currentColor() {
        return colorProperty != null ? colorProperty.getValue() : Leader.hudElementManager.background(element.name, 40, 40);
    }

    private void setColor(int c) {
        if (colorProperty != null) colorProperty.setValue(c);
        else Leader.hudElementManager.setBackground(element.name, c);
    }

    boolean contains(int mx, int my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

    void click(int mx, int my, int button) {
        if (button != 0 && button != 1) return;
        for (Control ctrl : controls) {
            if (ctrl.body && (my < top || my > bottom)) continue;
            if (!ctrl.contains(mx, my)) continue;
            if (button == 1) {
                if (ctrl.property instanceof ModeProperty) changeMode((ModeProperty) ctrl.property, false);
                return;
            }
            if (ctrl.numeric || ctrl.channel >= 0) {
                dragging = ctrl;
                drag(mx);
            } else if (ctrl.click != null) ctrl.click.run();
            return;
        }
    }

    void drag(int mx) {
        if (dragging == null) return;
        float ratio = Math.max(0, Math.min(1, (mx - dragging.x) / dragging.w));
        if (dragging.numeric) {
            Property<?> p = dragging.property;
            if (!p.isVisible()) { dragging = null; return; }
            float value = minimum(p) + ratio * (maximum(p) - minimum(p));
            if (p instanceof FloatProperty) p.setValue(Math.round(value * 100) / 100.0F);
            else p.setValue(Math.round(value));
        } else {
            int[] shifts = {16, 8, 0, 24};
            int shift = shifts[dragging.channel];
            setColor((currentColor() & ~(255 << shift)) | (Math.round(ratio * 255) << shift));
        }
    }

    void release() { dragging = null; }

    void wheel(int delta) {
        if (dragging != null) return;
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (bottom - top)), scroll - Math.signum(delta) * 30));
    }
}
