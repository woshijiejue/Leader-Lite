package leader.client.ui;

import leader.client.Leader;
import leader.client.module.Module;
import leader.client.ui.components.CategoryComponent;
import leader.client.ui.components.ModuleComponent;
import leader.client.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BookClickGuiScreen extends GuiScreen {

    private static final int LEFT_PANEL_WIDTH = 70;
    private static final int MID_PANEL_WIDTH = 180;
    private static final int RIGHT_PANEL_WIDTH = 150;
    private static final int PANEL_HEIGHT = 240;
    private static final int TITLE_BAR_HEIGHT = 15;
    private static final int ITEM_HEIGHT = 18;
    private static final int PANEL_GAP = 10;
    private static final int WINDOW_PADDING = 8;

    private int windowX, windowY;
    private boolean draggingWindow;
    private int dragOffsetX, dragOffsetY;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final FontRenderer fr = mc.fontRendererObj;

    private final ArrayList<CategoryComponent> categoryList;
    private int selectedCategory = 0;
    private int selectedModule = 0;
    private int centerModuleIdx = 0;

    private float animatedCenterIdx = 0;

    private int settingScroll = 0;
    private double settingScrollSmooth = 0;

    private static final Color ACCENT_COLOR = new Color(75, 160, 255);
    private static final int COLOR_ACCENT = ACCENT_COLOR.getRGB();
    private static final int COLOR_WHITE = new Color(235, 235, 245).getRGB();
    private static final int COLOR_GRAY = new Color(145, 145, 160).getRGB();
    private static final int COLOR_BG_OVERLAY = new Color(8, 8, 22, 160).getRGB();
    private static final int COLOR_WINDOW_BG = new Color(20, 20, 32, 245).getRGB();
    private static final int COLOR_TITLE_BG = new Color(38, 38, 56, 245).getRGB();
    private static final int COLOR_PANEL_BG = new Color(26, 26, 40, 235).getRGB();
    private static final int COLOR_SHADOW = new Color(0, 0, 0, 85).getRGB();

    public BookClickGuiScreen() {
        Map<String, List<Module>> categoryMap = new LinkedHashMap<>();
        categoryMap.put("Combat", new ArrayList<>());
        categoryMap.put("Movement", new ArrayList<>());
        categoryMap.put("Render", new ArrayList<>());
        categoryMap.put("Player", new ArrayList<>());
        categoryMap.put("Misc", new ArrayList<>());
        categoryMap.put("Legit", new ArrayList<>());

        for (Module module : Leader.moduleManager.modules.values()) {
            String pkg = module.getClass().getPackage().getName().toLowerCase();
            if (pkg.contains("combat")) {
                categoryMap.get("Combat").add(module);
            } else if (pkg.contains("movement")) {
                categoryMap.get("Movement").add(module);
            } else if (pkg.contains("render")) {
                categoryMap.get("Render").add(module);
            } else if (pkg.contains("player")) {
                categoryMap.get("Player").add(module);
            } else if (pkg.contains("misc")) {
                categoryMap.get("Misc").add(module);
            } else if (pkg.contains("legit")) {
                categoryMap.get("Legit").add(module);
            }
        }

        categoryMap.values().forEach(list -> list.sort(Comparator.comparing(m -> m.getName().toLowerCase())));

        this.categoryList = new ArrayList<>();
        for (Map.Entry<String, List<Module>> entry : categoryMap.entrySet()) {
            categoryList.add(new CategoryComponent(entry.getKey(), entry.getValue()));
        }
    }

    @Override
    public void initGui() {
        windowX = (this.width - getWindowWidth()) / 2;
        windowY = (this.height - getWindowHeight()) / 2;
        if (windowX < 0) windowX = 5;
        if (windowY < 0) windowY = 5;
        animatedCenterIdx = centerModuleIdx;
    }

    private int getWindowWidth() {
        return LEFT_PANEL_WIDTH + MID_PANEL_WIDTH + RIGHT_PANEL_WIDTH + PANEL_GAP * 2 + WINDOW_PADDING * 2;
    }

    private int getWindowHeight() {
        return TITLE_BAR_HEIGHT + PANEL_HEIGHT + WINDOW_PADDING * 2;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (Math.abs(animatedCenterIdx - centerModuleIdx) > 0.01f) {
            animatedCenterIdx += (centerModuleIdx - animatedCenterIdx) * 0.3f;
        } else {
            animatedCenterIdx = centerModuleIdx;
        }

        drawBackground();
        drawWindow(mouseX, mouseY);

        if (draggingWindow) {
            windowX = mouseX - dragOffsetX;
            windowY = mouseY - dragOffsetY;
            windowX = Math.max(0, Math.min(windowX, this.width - getWindowWidth()));
            windowY = Math.max(0, Math.min(windowY, this.height - getWindowHeight()));
        }
    }

    private void drawBackground() {
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(0, 0, this.width, this.height, COLOR_BG_OVERLAY);
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.disableCull();
    }

    private void drawWindow(int mouseX, int mouseY) {
        int x = windowX;
        int y = windowY;
        int w = getWindowWidth();
        int h = getWindowHeight();

        drawRoundedRectSafe(x + 4, y + 4, x + w + 4, y + h + 4, 7, new Color(0, 0, 0, 50).getRGB());
        drawRoundedRectSafe(x + 2, y + 2, x + w + 2, y + h + 2, 7, COLOR_SHADOW);
        drawRoundedRectSafe(x, y, x + w, y + h, 7, COLOR_WINDOW_BG);

        drawRoundedRectSafe(x, y, x + w, y + TITLE_BAR_HEIGHT + WINDOW_PADDING, 7, COLOR_TITLE_BG);
        RenderUtil.enableRenderState();
        Gui.drawRect(x, y + TITLE_BAR_HEIGHT + WINDOW_PADDING - 7, x + w, y + TITLE_BAR_HEIGHT + WINDOW_PADDING, COLOR_TITLE_BG);
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();

        drawCenteredString(fr, "\u00bb Leader Lite", x + w / 2, y + WINDOW_PADDING / 2 + 2, COLOR_WHITE);

        int contentX = x + WINDOW_PADDING;
        int contentY = y + WINDOW_PADDING + TITLE_BAR_HEIGHT;
        drawLeftPanel(contentX, contentY, mouseX, mouseY);
        drawMiddlePanel(contentX + LEFT_PANEL_WIDTH + PANEL_GAP, contentY, mouseX, mouseY);
        drawRightPanel(contentX + LEFT_PANEL_WIDTH + MID_PANEL_WIDTH + PANEL_GAP * 2, contentY, mouseX, mouseY);
    }

    private void drawRoundedRectSafe(float x1, float y1, float x2, float y2, float radius, int color) {
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        RenderUtil.drawRoundedRect(x1, y1, x2, y2, radius, color);
        GlStateManager.enableAlpha();
        GlStateManager.disableDepth();
    }

    private void drawLeftPanel(int x, int y, int mouseX, int mouseY) {
        drawRoundedRectSafe(x, y, x + LEFT_PANEL_WIDTH, y + PANEL_HEIGHT, 5, COLOR_PANEL_BG);
        drawCenteredString(fr, "Categories", x + LEFT_PANEL_WIDTH / 2, y + 5, COLOR_WHITE);

        int itemY = y + 18;
        for (int i = 0; i < categoryList.size(); i++) {
            String name = categoryList.get(i).getName();
            boolean hovered = mouseX >= x && mouseX <= x + LEFT_PANEL_WIDTH && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean selected = (i == selectedCategory);

            if (selected) {
                drawRoundedRectSafe(x + 2, itemY, x + LEFT_PANEL_WIDTH - 2, itemY + ITEM_HEIGHT, 4, new Color(75, 160, 255, 80).getRGB());
                drawRoundedRectSafe(x + 3, itemY + 2, x + 5, itemY + ITEM_HEIGHT - 2, 2, COLOR_ACCENT);
            } else if (hovered) {
                drawRoundedRectSafe(x + 2, itemY, x + LEFT_PANEL_WIDTH - 2, itemY + ITEM_HEIGHT, 4, new Color(255, 255, 255, 25).getRGB());
            }

            int color = selected ? COLOR_WHITE : (hovered ? new Color(200, 200, 215).getRGB() : COLOR_GRAY);
            fr.drawStringWithShadow(name, x + 8, itemY + (float) (ITEM_HEIGHT - fr.FONT_HEIGHT) / 2, color);
            itemY += ITEM_HEIGHT;
        }
    }

    private void drawMiddlePanel(int x, int y, int mouseX, int mouseY) {
        drawRoundedRectSafe(x, y, x + MID_PANEL_WIDTH, y + PANEL_HEIGHT, 5, COLOR_PANEL_BG);
        drawCenteredString(fr, "Modules", x + MID_PANEL_WIDTH / 2, y + 5, COLOR_WHITE);

        CategoryComponent cat = categoryList.get(selectedCategory);
        ArrayList<Component> modules = cat.getModules();
        if (modules == null || modules.isEmpty()) return;

        int centerX = x + MID_PANEL_WIDTH / 2;
        int baseY = y + PANEL_HEIGHT / 2 - ITEM_HEIGHT / 2;

        float radiusX = 80f;
        float radiusY = 30f;
        float angleFactor = 1.1f;

        ScaledResolution sr = new ScaledResolution(mc);
        double scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) ((sr.getScaledHeight() - (y + PANEL_HEIGHT)) * scale),
                (int) (MID_PANEL_WIDTH * scale), (int) (PANEL_HEIGHT * scale));

        List<ModuleComponent> drawList = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            float diff = i - animatedCenterIdx;
            if (Math.abs(diff) > 2.2f) continue;
            drawList.add((ModuleComponent) modules.get(i));
        }
        drawList.sort((a, b) -> {
            float dA = modules.indexOf(a) - animatedCenterIdx;
            float dB = modules.indexOf(b) - animatedCenterIdx;
            return Float.compare(Math.abs(dB), Math.abs(dA));
        });

        for (ModuleComponent modComp : drawList) {
            int i = modules.indexOf(modComp);
            float diff = i - animatedCenterIdx;

            float angle = diff * angleFactor;
            float drawX = centerX + (float) (Math.sin(angle) * radiusX);
            float drawY = baseY + (float) ((1 - Math.cos(angle)) * radiusY);

            String name = modComp.mod.getName();
            boolean enabled = modComp.mod.isEnabled();
            boolean isSelected = (i == selectedModule);

            float alphaFactor = 1.0f - Math.abs(diff) * 0.6f;
            if (alphaFactor < 0.4f) alphaFactor = 0.4f;
            if (Math.abs(diff) < 0.2f) alphaFactor = 1.0f;

            int alphaByte = (int) (alphaFactor * 255);

            int textWidth = fr.getStringWidth(name);
            int bgWidth = Math.max(textWidth + 20, 90);
            bgWidth = Math.min(bgWidth, MID_PANEL_WIDTH - 8);
            int bgLeft = (int) (drawX - bgWidth / 2f);
            int bgRight = bgLeft + bgWidth;
            int bgTop = (int) drawY;
            int bgBottom = bgTop + ITEM_HEIGHT;

            drawRoundedRectSafe(bgLeft + 1, bgTop + 1, bgRight + 1, bgBottom + 1, 5, new Color(0, 0, 0, (int)(alphaByte * 0.35)).getRGB());

            int bgColor;
            if (isSelected) {
                bgColor = new Color(75, 160, 255, alphaByte).getRGB();
            } else if (enabled) {
                bgColor = new Color(220, 220, 235, alphaByte).getRGB();
            } else {
                bgColor = new Color(100, 100, 115, alphaByte).getRGB();
            }
            drawRoundedRectSafe(bgLeft, bgTop, bgRight, bgBottom, 5, bgColor);

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            int textColor;
            if (isSelected) {
                textColor = COLOR_WHITE;
            } else if (enabled) {
                textColor = new Color(240, 240, 250).getRGB();
            } else {
                textColor = new Color(160, 160, 170).getRGB();
            }
            int finalTextColor = applyAlpha(textColor, alphaByte);
            fr.drawString(name, drawX - textWidth / 2f, drawY + (ITEM_HEIGHT - fr.FONT_HEIGHT) / 2f, finalTextColor, false);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.enableAlpha();
    }

    private void drawRightPanel(int x, int y, int mouseX, int mouseY) {
        drawRoundedRectSafe(x, y, x + RIGHT_PANEL_WIDTH, y + PANEL_HEIGHT, 5, COLOR_PANEL_BG);

        if (categoryList.isEmpty()) return;
        CategoryComponent cat = categoryList.get(selectedCategory);
        if (cat.getModules().isEmpty()) {
            drawCenteredString(fr, "No modules", x + RIGHT_PANEL_WIDTH / 2, y + PANEL_HEIGHT / 2, COLOR_GRAY);
            return;
        }
        if (selectedModule >= cat.getModules().size())
            selectedModule = cat.getModules().size() - 1;

        ModuleComponent modComp = (ModuleComponent) cat.getModules().get(selectedModule);
        String modName = modComp.mod.getName();
        boolean isEnabled = modComp.mod.isEnabled();
        drawCenteredString(fr, modName, x + RIGHT_PANEL_WIDTH / 2, y + 8, isEnabled ? COLOR_WHITE : COLOR_GRAY);
        Gui.drawRect(x + 6, y + 19, x + RIGHT_PANEL_WIDTH - 6, y + 20, new Color(255, 255, 255, 30).getRGB());

        ArrayList<Component> settings = modComp.getSettings();
        if (settings == null || settings.isEmpty()) {
            fr.drawStringWithShadow("No settings", x + 5, y + 25, COLOR_GRAY);
            return;
        }

        int contentStartY = y + 20;
        int contentAreaHeight = PANEL_HEIGHT - 20 - 8;

        int totalHeight = 0;
        for (Component comp : settings) if (comp.isVisible()) totalHeight += comp.getHeight();
        int maxScroll = Math.max(0, totalHeight - contentAreaHeight);
        if (settingScroll > maxScroll) settingScroll = maxScroll;
        else if (settingScroll < 0) settingScroll = 0;
        settingScrollSmooth += (settingScroll - settingScrollSmooth) * 0.2;

        int origCatX = cat.getX();
        int origCatY = cat.getY();
        int origOff = modComp.offsetY;
        boolean origExpand = modComp.panelExpand;

        cat.setX(x + 4);
        cat.setY(contentStartY - (int) settingScrollSmooth);
        modComp.offsetY = 0;
        modComp.panelExpand = true;

        int yOff = 0;
        for (Component comp : settings) {
            if (comp.isVisible()) {
                comp.setComponentStartAt(yOff);
                yOff += comp.getHeight();
            }
        }

        for (Component comp : settings) {
            if (comp.isVisible()) comp.update(mouseX, mouseY);
        }

        ScaledResolution sr = new ScaledResolution(mc);
        double scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) ((sr.getScaledHeight() - (contentStartY + contentAreaHeight)) * scale),
                (int) (RIGHT_PANEL_WIDTH * scale), (int) (contentAreaHeight * scale));

        AtomicInteger colorOff = new AtomicInteger(0);
        for (Component comp : settings) {
            if (comp.isVisible()) {
                comp.draw(colorOff);
                colorOff.incrementAndGet();
            }
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        cat.setX(origCatX);
        cat.setY(origCatY);
        modComp.offsetY = origOff;
        modComp.panelExpand = origExpand;
        yOff = 0;
        for (Component comp : settings) {
            if (comp.isVisible()) comp.setComponentStartAt(yOff);
            yOff += comp.getHeight();
        }

        if (totalHeight > contentAreaHeight) {
            float barX = x + RIGHT_PANEL_WIDTH - 6;
            float barH = (float) contentAreaHeight * contentAreaHeight / totalHeight;
            float barY = contentStartY + (float) (settingScrollSmooth * contentAreaHeight / totalHeight);
            drawRoundedRectSafe(barX, barY, barX + 3, barY + barH, 1, new Color(75, 160, 255, 180).getRGB());
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (mouseX >= windowX && mouseX <= windowX + getWindowWidth() &&
                mouseY >= windowY && mouseY <= windowY + TITLE_BAR_HEIGHT + WINDOW_PADDING) {
            dragOffsetX = mouseX - windowX;
            dragOffsetY = mouseY - windowY;
            draggingWindow = true;
            return;
        }

        int contentX = windowX + WINDOW_PADDING;
        int contentY = windowY + WINDOW_PADDING + TITLE_BAR_HEIGHT;
        int leftX = contentX;
        int midX = leftX + LEFT_PANEL_WIDTH + PANEL_GAP;
        int rightX = midX + MID_PANEL_WIDTH + PANEL_GAP;

        if (mouseX >= leftX && mouseX <= leftX + LEFT_PANEL_WIDTH && mouseY >= contentY && mouseY <= contentY + PANEL_HEIGHT) {
            int itemY = contentY + 18;
            for (int i = 0; i < categoryList.size(); i++) {
                if (mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                    selectedCategory = i;
                    selectedModule = 0;
                    centerModuleIdx = 0;
                    settingScroll = 0;
                    settingScrollSmooth = 0;
                    return;
                }
                itemY += ITEM_HEIGHT;
            }
        }

        if (mouseX >= midX && mouseX <= midX + MID_PANEL_WIDTH && mouseY >= contentY && mouseY <= contentY + PANEL_HEIGHT) {
            CategoryComponent cat = categoryList.get(selectedCategory);
            ArrayList<Component> modules = cat.getModules();
            if (modules.isEmpty()) return;

            int centerX = midX + MID_PANEL_WIDTH / 2;
            int baseY = contentY + PANEL_HEIGHT / 2 - ITEM_HEIGHT / 2;
            float radiusX = 80f;
            float radiusY = 30f;
            float angleFactor = 1.1f;

            for (int i = 0; i < modules.size(); i++) {
                float diff = i - animatedCenterIdx;
                if (Math.abs(diff) > 2.2f) continue;

                float angle = diff * angleFactor;
                float drawX = centerX + (float) (Math.sin(angle) * radiusX);
                float drawY = baseY + (float) ((1 - Math.cos(angle)) * radiusY);

                ModuleComponent modComp = (ModuleComponent) modules.get(i);
                String name = modComp.mod.getName();
                int textWidth = fr.getStringWidth(name);
                int bgWidth = Math.max(textWidth + 20, 90);
                bgWidth = Math.min(bgWidth, MID_PANEL_WIDTH - 8);
                float left = drawX - bgWidth / 2f;
                float right = left + bgWidth;

                if (mouseX >= left && mouseX <= right && mouseY >= drawY && mouseY <= drawY + ITEM_HEIGHT) {
                    if (button == 0) {
                        modComp.mod.toggle();
                    }
                    selectedModule = i;
                    centerModuleIdx = i;
                    settingScroll = 0;
                    settingScrollSmooth = 0;
                    return;
                }
            }
            return;
        }

        if (mouseX >= rightX && mouseX <= rightX + RIGHT_PANEL_WIDTH && mouseY >= contentY && mouseY <= contentY + PANEL_HEIGHT) {
            CategoryComponent cat = categoryList.get(selectedCategory);
            if (cat.getModules().size() > selectedModule) {
                ModuleComponent modComp = (ModuleComponent) cat.getModules().get(selectedModule);
                ArrayList<Component> settings = modComp.getSettings();
                if (settings == null || settings.isEmpty()) return;

                int origCatX = cat.getX();
                int origCatY = cat.getY();
                int origOff = modComp.offsetY;
                boolean origExpand = modComp.panelExpand;

                int contentStartY = contentY + 20;
                cat.setX(rightX + 4);
                cat.setY(contentStartY - (int) settingScrollSmooth);
                modComp.offsetY = 0;
                modComp.panelExpand = true;

                int yOff = 0;
                for (Component comp : settings) {
                    if (comp.isVisible()) {
                        comp.setComponentStartAt(yOff);
                        yOff += comp.getHeight();
                    }
                }
                for (Component comp : settings) {
                    if (comp.isVisible()) comp.update(mouseX, mouseY);
                }
                for (Component comp : settings) {
                    if (comp.isVisible()) comp.mouseDown(mouseX, mouseY, button);
                }

                cat.setX(origCatX);
                cat.setY(origCatY);
                modComp.offsetY = origOff;
                modComp.panelExpand = origExpand;
                yOff = 0;
                for (Component comp : settings) {
                    if (comp.isVisible()) comp.setComponentStartAt(yOff);
                    yOff += comp.getHeight();
                }
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        draggingWindow = false;
        int contentX = windowX + WINDOW_PADDING;
        int contentY = windowY + WINDOW_PADDING + TITLE_BAR_HEIGHT;
        int rightX = contentX + LEFT_PANEL_WIDTH + MID_PANEL_WIDTH + PANEL_GAP * 2;

        if (mouseX >= rightX && mouseX <= rightX + RIGHT_PANEL_WIDTH && mouseY >= contentY && mouseY <= contentY + PANEL_HEIGHT) {
            CategoryComponent cat = categoryList.get(selectedCategory);
            if (cat.getModules().size() > selectedModule) {
                ModuleComponent modComp = (ModuleComponent) cat.getModules().get(selectedModule);
                ArrayList<Component> settings = modComp.getSettings();
                if (settings == null || settings.isEmpty()) return;

                int origCatX = cat.getX();
                int origCatY = cat.getY();
                int origOff = modComp.offsetY;
                boolean origExpand = modComp.panelExpand;

                int contentStartY = contentY + 20;
                cat.setX(rightX + 4);
                cat.setY(contentStartY - (int) settingScrollSmooth);
                modComp.offsetY = 0;
                modComp.panelExpand = true;

                int yOff = 0;
                for (Component comp : settings) {
                    if (comp.isVisible()) comp.setComponentStartAt(yOff);
                    yOff += comp.getHeight();
                }
                for (Component comp : settings) {
                    if (comp.isVisible()) comp.update(mouseX, mouseY);
                }
                for (Component comp : settings) {
                    if (comp.isVisible()) comp.mouseReleased(mouseX, mouseY, button);
                }

                cat.setX(origCatX);
                cat.setY(origCatY);
                modComp.offsetY = origOff;
                modComp.panelExpand = origExpand;
                yOff = 0;
                for (Component comp : settings) {
                    if (comp.isVisible()) comp.setComponentStartAt(yOff);
                    yOff += comp.getHeight();
                }
            }
        }
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        int contentX = windowX + WINDOW_PADDING;
        int contentY = windowY + WINDOW_PADDING + TITLE_BAR_HEIGHT;
        int midX = contentX + LEFT_PANEL_WIDTH + PANEL_GAP;
        int rightX = midX + MID_PANEL_WIDTH + PANEL_GAP;

        if (mouseX >= midX && mouseX <= midX + MID_PANEL_WIDTH && mouseY >= contentY && mouseY <= contentY + PANEL_HEIGHT) {
            CategoryComponent cat = categoryList.get(selectedCategory);
            int maxIdx = cat.getModules().size() - 1;
            if (maxIdx < 0) return;
            int dir = (wheel > 0) ? -1 : 1;
            int newCenter = centerModuleIdx + dir;
            newCenter = Math.max(0, Math.min(newCenter, maxIdx));
            if (newCenter != centerModuleIdx) {
                centerModuleIdx = newCenter;
                selectedModule = newCenter;
                settingScroll = 0;
                settingScrollSmooth = 0;
            }
            return;
        }

        if (mouseX >= rightX && mouseX <= rightX + RIGHT_PANEL_WIDTH && mouseY >= contentY && mouseY <= contentY + PANEL_HEIGHT) {
            CategoryComponent cat = categoryList.get(selectedCategory);
            if (cat.getModules().size() > selectedModule) {
                ModuleComponent modComp = (ModuleComponent) cat.getModules().get(selectedModule);
                ArrayList<Component> settings = modComp.getSettings();
                if (settings != null) {
                    int totalH = 0;
                    for (Component comp : settings) if (comp.isVisible()) totalH += comp.getHeight();
                    int areaH = PANEL_HEIGHT - 20 - 8;
                    int maxScroll = Math.max(0, totalH - areaH);
                    int dir = (wheel > 0) ? -1 : 1;
                    settingScroll += dir * 12;
                    settingScroll = Math.max(0, Math.min(settingScroll, maxScroll));
                }
            }
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            mc.displayGuiScreen(null);
            return;
        }
        CategoryComponent cat = categoryList.get(selectedCategory);
        if (cat.getModules().size() > selectedModule) {
            ModuleComponent modComp = (ModuleComponent) cat.getModules().get(selectedModule);
            ArrayList<Component> settings = modComp.getSettings();
            if (settings != null) {
                for (Component comp : settings) {
                    if (comp.isVisible()) comp.keyTyped(typedChar, keyCode);
                }
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private int applyAlpha(int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (Math.max(0, Math.min(alpha, 255)) << 24) | (r << 16) | (g << 8) | b;
    }

    public void drawCenteredString(FontRenderer fr, String text, int x, int y, int color) {
        fr.drawStringWithShadow(text, x - fr.getStringWidth(text) / 2f, y, color);
    }
}