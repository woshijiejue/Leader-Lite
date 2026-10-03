package leader.ui;

import leader.Leader;
import leader.config.Config;
import leader.module.Module;
import leader.module.modules.player.Scaffold;
import leader.module.modules.render.*;
import leader.module.modules.render.notification.Notification;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GuiHUDDesigner extends GuiScreen {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private Color accent = new Color(110, 170, 255);
    private final GuiScreen parent;
    private final List<HUDDesignerElement> elements = new ArrayList<>();
    private final List<Fusion> fusions = new ArrayList<>();
    private final List<float[]> selectorBoxes = new ArrayList<>();
    private HUDDesignerElement dragging;
    private HUDDesignerElement selected;
    private HUDDesignerSettings settings;
    private String pulledModule;
    private float offsetX, offsetY, startX, startY;
    private float pulledX, pulledY;
    private boolean moved;
    private long lastFrame;
    private int previewState;
    private float fusionPulse;
    private boolean snap = true;

    private static final class Fusion {
        HUDDesignerElement element;
        float x, y, targetX, targetY, progress;
        float[] bounds;
    }

    public GuiHUDDesigner() {
        this(null);
    }

    public GuiHUDDesigner(GuiScreen parent) {
        this.parent = parent;
        for (Module module : Leader.moduleManager.modules.values()) {
            if (module.isEnabled() && (module instanceof HUD || module instanceof TargetHUD
                    || module instanceof Notification || module instanceof Watermark || module instanceof GifDisplay
                    || module instanceof Indicators || module instanceof Potion || module instanceof BedTracker || module instanceof Island)) {
                elements.add(new HUDDesignerElement(module.getName(), module));
            }
        }
        // The display setting is required; the movement module itself may stay disabled.
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        if (scaffold != null && scaffold.blockCounter.getValue()) {
            elements.add(new HUDDesignerElement("ScaffoldCounter", scaffold));
        }
        // Existing fusion links remain accessible even while Island is disabled.
        Island island = (Island) Leader.moduleManager.modules.get(Island.class);
        if (island != null && find("Island") == null && !Leader.hudElementManager.mergedModules("Island").isEmpty()) {
            elements.add(new HUDDesignerElement("Island", island));
        }
    }

    private HUDDesignerElement find(String name) {
        for (HUDDesignerElement element : elements) if (element.name.equals(name)) return element;
        return null;
    }

    private boolean counterEditable() {
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        return scaffold != null && scaffold.blockCounter.getValue();
    }

    private void syncCounter() {
        HUDDesignerElement counter = find("ScaffoldCounter");
        if (counterEditable()) {
            if (counter == null) elements.add(new HUDDesignerElement("ScaffoldCounter",
                    Leader.moduleManager.modules.get(Scaffold.class)));
        } else if (counter != null) {
            if (selected == counter) closeSettings();
            if (dragging == counter) {
                if (pulledModule != null) counter.setOrigin(pulledX, pulledY);
                dragging = null;
                pulledModule = null;
            }
            elements.remove(counter);
            fusions.removeIf(f -> f.element == counter);
        }
    }

    private boolean visible(HUDDesignerElement element) {
        return (element == dragging && (pulledModule == null || moved)) || element.name.equals("Island")
                || !"Island".equals(Leader.hudElementManager.mergedInto(element.name));
    }

    private float rowHeight() { return Math.max(20, FontManager.getFontHeight(12) + 9); }

    private float rowY(HUDDesignerElement island, int index) {
        return island.origin()[1] + island.size(previewState)[1] + 8 + index * rowHeight();
    }

    private float[] box(HUDDesignerElement element) {
        float[] b = element.bounds(element.name.equals("Island") ? previewState : 0);
        if (element.name.equals("Island")) {
            int rows = Leader.hudElementManager.mergedModules("Island").size();
            if (rows > 0) {
                b[2] = Math.max(b[2], 208);
                b[3] += 8 + rows * rowHeight() + 4;
            }
        }
        return b;
    }

    private boolean inside(int mx, int my, float[] b) {
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    private HUDDesignerElement hit(int mx, int my) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            HUDDesignerElement element = elements.get(i);
            if (visible(element) && inside(mx, my, box(element))) return element;
        }
        return null;
    }

    private int rgba(Color color, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha))) << 24) | (color.getRGB() & 0xFFFFFF);
    }

    private void text(String text, float x, float y, Color color, float size) {
        FontManager.drawString(text, x, y, color.getRGB(), false, size);
    }

    private void border(float[] b, Color color, float alpha) {
        int c = rgba(color, alpha);
        RenderUtil.drawRoundedRect(b[0], b[1], b[0] + b[2], b[1] + 0.8F, 0.4F, c);
        RenderUtil.drawRoundedRect(b[0], b[1] + b[3] - 0.8F, b[0] + b[2], b[1] + b[3], 0.4F, c);
        RenderUtil.drawRoundedRect(b[0], b[1], b[0] + 0.8F, b[1] + b[3], 0.4F, c);
        RenderUtil.drawRoundedRect(b[0] + b[2] - 0.8F, b[1], b[0] + b[2], b[1] + b[3], 0.4F, c);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        syncCounter();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016F : Math.max(0, Math.min(0.05F, (now - lastFrame) / 1000.0F));
        lastFrame = now;
        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        accent = hud != null ? hud.getColor(now) : new Color(110, 170, 255);
        RenderUtil.drawRoundedRectGradient(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), 0,
                new Color(10, 11, 16, 80).getRGB(), new Color(4, 5, 8, 120).getRGB());
        text("HUD Designer", 8, 10, new Color(245, 247, 252), 16);
        text("Drag to move   Click for settings   Drop onto Island to fuse", 8, 25,
                new Color(128, 137, 155), 10);
        text(snap ? "G: snap on / Shift: free drag" : "G: snap off", 8, sr.getScaledHeight() - 12,
                new Color(127, 143, 166), 9);
        updateDrag(mouseX, mouseY);
        HUDDesignerElement hovered = settings == null ? hit(mouseX, mouseY) : null;
        for (HUDDesignerElement element : elements) {
            if (visible(element) && element != dragging) drawElement(element, element == hovered);
        }
        if (dragging != null) {
            if (pulledModule == null || moved) {
                drawElement(dragging, true);
                drawDropHint(mouseX, mouseY);
            }
        }
        drawFusions(dt);
        drawSelector(mouseX, mouseY);
        fusionPulse = Math.max(0, fusionPulse - dt * 2.6F);
        HUDDesignerElement island = find("Island");
        if (island != null && fusionPulse > 0) {
            float[] b = island.bounds(previewState);
            float pad = (1 - fusionPulse) * 7;
            border(new float[]{b[0] - pad, b[1] - pad, b[2] + 2 * pad, b[3] + 2 * pad}, accent, fusionPulse * 170);
        }
        if (settings != null) settings.draw(mouseX, mouseY, dt);
        GlStateManager.color(1, 1, 1, 1);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSelector(int mx, int my) {
        selectorBoxes.clear();
        float x = 8, y = 40;
        for (HUDDesignerElement element : elements) {
            float w = FontManager.getStringWidth(element.name, 10) + 12;
            if (x + w > width - 8) { x = 8; y += 19; }
            float[] b = new float[]{x, y, w, 17};
            selectorBoxes.add(b);
            boolean fused = "Island".equals(Leader.hudElementManager.mergedInto(element.name));
            boolean hover = settings == null && inside(mx, my, b);
            RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + 17, 3,
                    rgba(new Color(18, 20, 28), hover ? 195 : 115));
            if (element == selected) RenderUtil.drawRect(x + 3, y + 16, x + w - 3, y + 17, rgba(accent, 180));
            text(element.name, x + 6, y + 5, element == selected ? accent
                    : fused ? new Color(145, 206, 181) : new Color(177, 192, 214), 10);
            x += w + 5;
        }
    }

    private void drawElement(HUDDesignerElement element, boolean hovered) {
        float[] p = element.origin();
        GlStateManager.pushMatrix();
        element.preview(p[0], p[1], element == dragging ? 0.82F : 1, element.name.equals("Island") ? previewState : 0);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
        float[] b = box(element);
        boolean active = element == selected || element == dragging;
        if (active || hovered) border(b, active ? accent : new Color(122, 145, 179), active ? 145 : 90);
        if (hovered || active) {
            String label = element.name + " / " + element.styleName();
            float labelHeight = FontManager.getFontHeight(10);
            float ly = b[1] >= labelHeight + 6 ? b[1] - labelHeight - 4 : b[1] + b[3] + 5;
            ly = Math.min(height - labelHeight - 2, ly);
            RenderUtil.drawRoundedRectWithGl(b[0], ly - 2, b[0] + FontManager.getStringWidth(label, 10) + 10,
                    ly + FontManager.getFontHeight(10) + 2, 3, new Color(17, 20, 28, 140).getRGB());
            text(label, b[0] + 5, ly, active ? accent : new Color(185, 201, 226), 10);
        }
        if (!element.name.equals("Island")) return;
        List<String> merged = Leader.hudElementManager.mergedModules("Island");
        if (merged.isEmpty()) return;
        float y = rowY(element, 0);
        RenderUtil.drawRoundedRectWithGl(b[0], y - 3, b[0] + b[2], b[1] + b[3], 3,
                new Color(18, 20, 28, 160).getRGB());
        for (int i = 0; i < merged.size(); i++) {
            float ry = rowY(element, i);
            String name = merged.get(i);
            RenderUtil.fillCircle(b[0] + 10, ry + rowHeight() / 2, 2, 16, new Color(140, 206, 178).getRGB());
            text(name, b[0] + 18, ry + 5, new Color(198, 221, 218), 12);
            text("drag out / edit", b[0] + b[2] - FontManager.getStringWidth("drag out / edit", 9) - 8,
                    ry + 6, new Color(119, 138, 157), 9);
        }
    }

    private void drawFusions(float dt) {
        Iterator<Fusion> it = fusions.iterator();
        while (it.hasNext()) {
            Fusion f = it.next();
            f.progress = Math.min(1, f.progress + dt / 0.32F);
            float t = f.progress * f.progress * (3 - 2 * f.progress);
            float sc = 1 - 0.86F * t;
            float cx = f.x + f.bounds[0] + f.bounds[2] / 2;
            float cy = f.y + f.bounds[1] + f.bounds[3] / 2;
            float fx = cx + (f.targetX - cx) * t, fy = cy + (f.targetY - cy) * t;
            GlStateManager.pushMatrix();
            GlStateManager.translate(fx, fy, 0);
            GlStateManager.scale(sc, sc, 1);
            GlStateManager.translate(-cx, -cy, 0);
            // Vanilla's font renderer treats near-zero alpha as opaque; stop text before that threshold.
            if (1 - t > 0.05F) f.element.preview(f.x, f.y, 1 - t, 0);
            border(new float[]{f.x + f.bounds[0], f.y + f.bounds[1], f.bounds[2], f.bounds[3]},
                    accent, 200 * (1 - t));
            GlStateManager.popMatrix();
            if (f.progress >= 1) it.remove();
        }
    }

    private boolean overIsland(int mx, int my) {
        HUDDesignerElement island = find("Island");
        return island != null && island.module.isEnabled() && inside(mx, my, box(island));
    }

    private void drawDropHint(int mx, int my) {
        String label;
        if (pulledModule != null) label = overIsland(mx, my) ? "Release to keep fusion" : "Release to detach";
        else if (!Leader.hudElementManager.isMergeable(dragging.name)) label = "Standalone element";
        else if (find("Island") == null || !find("Island").module.isEnabled()) label = "Enable Island to fuse";
        else label = overIsland(mx, my) ? "Release to fuse into Island" : "Drop onto Island to fuse";
        float w = FontManager.getStringWidth(label, 11) + 18;
        float x = Math.min(width - w - 4, mx + 12), y = Math.min(height - 23, my + 16);
        RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + 22, 3, new Color(18, 20, 28, 190).getRGB());
        text(label, x + 9, y + 6, accent, 11);
    }

    private void beginDrag(HUDDesignerElement element, int mx, int my) {
        dragging = element;
        float[] p = element.origin();
        offsetX = mx - p[0]; offsetY = my - p[1];
        startX = mx; startY = my;
        moved = false;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (settings != null) {
            if (settings.contains(mx, my)) settings.click(mx, my, button);
            else closeSettings();
            return;
        }
        if (button != 0) return;
        for (int i = 0; i < selectorBoxes.size() && i < elements.size(); i++) {
            if (inside(mx, my, selectorBoxes.get(i))) { openSettings(elements.get(i)); return; }
        }
        HUDDesignerElement element = hit(mx, my);
        if (element == null) return;
        if (element.name.equals("Island")) {
            List<String> merged = Leader.hudElementManager.mergedModules("Island");
            float[] b = box(element);
            for (int i = 0; i < merged.size(); i++) {
                if (!inside(mx, my, new float[]{b[0], rowY(element, i), b[2], rowHeight()})) continue;
                String name = merged.get(i);
                if (name.equals("ScaffoldCounter") && !counterEditable()) return;
                HUDDesignerElement child = find(name);
                if (child == null) {
                    Module module = name.equals("ScaffoldCounter") ? Leader.moduleManager.modules.get(Scaffold.class)
                            : Leader.moduleManager.modules.values().stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
                    if (module == null) { unmerge(name); return; }
                    child = new HUDDesignerElement(name, module);
                    elements.add(child);
                }
                beginDrag(child, mx, my);
                pulledModule = name;
                float[] p = child.origin();
                pulledX = p[0]; pulledY = p[1];
                offsetX = 12; offsetY = 10;
                return;
            }
        }
        beginDrag(element, mx, my);
        // Keep draw order and hit order consistent while overlapping elements are edited.
        elements.remove(element);
        elements.add(element);
    }

    private void updateDrag(int mx, int my) {
        if (settings != null) { settings.drag(mx); return; }
        if (dragging == null) return;
        if (Math.abs(mx - startX) > 2 || Math.abs(my - startY) > 2) moved = true;
        if (!moved) return;
        float x = mx - offsetX, y = my - offsetY;
        float[] b = box(dragging);
        float[] p = dragging.origin();
        float dx = b[0] - p[0], dy = b[1] - p[1];
        float left = x + dx, top = y + dy;
        if (snap && !isShiftKeyDown()) {
            float centerX = width / 2.0F, centerY = height / 2.0F;
            if (Math.abs(left + b[2] / 2 - centerX) < 5) {
                left = centerX - b[2] / 2;
                RenderUtil.drawRect(centerX, 45, centerX + 0.5F, height, rgba(accent, 80));
            }
            if (Math.abs(top + b[3] / 2 - centerY) < 5) {
                top = centerY - b[3] / 2;
                RenderUtil.drawRect(0, centerY, width, centerY + 0.5F, rgba(accent, 80));
            }
            if (Math.abs(left) < 5) left = 0;
            if (Math.abs(width - left - b[2]) < 5) left = width - b[2];
        }
        left = Math.max(0, Math.min(Math.max(0, width - b[2]), left));
        top = Math.max(0, Math.min(Math.max(0, height - b[3]), top));
        dragging.setOrigin(left - dx, top - dy);
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        if (button != 0) return;
        if (settings != null) settings.release();
        if (dragging == null) return;
        // A fast drag can start and finish between GUI ticks.
        updateDrag(mx, my);
        HUDDesignerElement element = dragging;
        if (pulledModule != null) {
            if (moved && !overIsland(mx, my)) unmerge(pulledModule);
            else {
                element.setOrigin(pulledX, pulledY);
                if (!moved) openSettings(element);
            }
        } else if (moved) {
            if (Leader.hudElementManager.isMergeable(element.name) && overIsland(mx, my)) fuse(element);
        } else openSettings(element);
        dragging = null;
        pulledModule = null;
        Leader.hudElementManager.save();
    }

    private void fuse(HUDDesignerElement element) {
        HUDDesignerElement island = find("Island");
        if (island == null || !island.module.isEnabled()) return;
        float[] p = element.origin(), b = element.bounds(0), target = island.bounds(previewState);
        Fusion f = new Fusion();
        f.element = element; f.x = p[0]; f.y = p[1];
        f.bounds = new float[]{b[0] - p[0], b[1] - p[1], b[2], b[3]};
        f.targetX = target[0] + target[2] / 2; f.targetY = target[1] + target[3] / 2;
        fusions.add(f);
        Leader.hudElementManager.merge(element.name, "Island");
        fusionPulse = 1;
    }

    void unmerge(String name) {
        Leader.hudElementManager.unmerge(name);
        HUDDesignerElement element = find(name);
        if (element != null) keepOnScreen(element);
        Leader.hudElementManager.save();
    }

    private void openSettings(HUDDesignerElement element) {
        if (element.name.equals("ScaffoldCounter") && !counterEditable()) return;
        selected = element;
        settings = new HUDDesignerSettings(this, element);
    }

    void closeSettings() {
        if (settings != null) settings.release();
        settings = null;
        selected = null;
        Leader.hudElementManager.save();
    }

    int islandState() { return previewState; }
    Color accent() { return accent; }
    void setIslandState(int state) { previewState = state; }

    void alignElement(HUDDesignerElement element, int axis) {
        float[] b = element.bounds(element.name.equals("Island") ? previewState : 0), p = element.origin();
        if (axis == 0) p[0] += (width - b[2]) / 2 - b[0];
        else p[1] += (height - b[3]) / 2 - b[1];
        element.setOrigin(p[0], p[1]);
    }

    void nudge(HUDDesignerElement element, float x, float y) {
        float[] p = element.origin();
        element.setOrigin(p[0] + x, p[1] + y);
        keepOnScreen(element);
    }

    void bottomRight(HUDDesignerElement element) {
        float[] b = element.bounds(element.name.equals("Island") ? previewState : 0);
        float[] p = element.origin();
        element.setOrigin(p[0] + width - b[0] - b[2], p[1] + height - b[1] - b[3]);
    }

    void resetPosition(HUDDesignerElement element) {
        float[] p = element.defaults();
        Leader.hudElementManager.set(element.name, p[0], p[1]);
        keepOnScreen(element);
    }

    void keepOnScreen(HUDDesignerElement element) {
        float[] b = element.bounds(element.name.equals("Island") ? previewState : 0), p = element.origin();
        float x = Math.max(0, Math.min(Math.max(0, width - b[2]), b[0]));
        float y = Math.max(0, Math.min(Math.max(0, height - b[3]), b[1]));
        element.setOrigin(p[0] + x - b[0], p[1] + y - b[1]);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (settings != null && wheel != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (settings.contains(mx, my)) settings.wheel(wheel);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && settings != null) { closeSettings(); return; }
        if (keyCode == Keyboard.KEY_G && settings == null) { snap = !snap; return; }
        if (keyCode == Keyboard.KEY_ESCAPE && parent != null) { mc.displayGuiScreen(parent); return; }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        if (settings != null) settings.release();
        if (pulledModule != null && dragging != null) dragging.setOrigin(pulledX, pulledY);
        dragging = null;
        Leader.hudElementManager.save();
        new Config(Config.lastConfig == null ? "default" : Config.lastConfig, false).save();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
