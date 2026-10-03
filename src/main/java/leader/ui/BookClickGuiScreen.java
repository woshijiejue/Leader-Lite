package leader.ui;

import leader.Leader;
import leader.module.Module;
import leader.ui.components.CategoryComponent;
import leader.ui.components.ModuleComponent;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
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

    private static final int LEFT_PANEL_WIDTH = 86;
    private static final int MID_PANEL_WIDTH = 196;
    private static final int RIGHT_PANEL_WIDTH = 164;
    private static final int PANEL_HEIGHT = 250;
    private static final int TITLE_BAR_HEIGHT = 16;
    private static final int ITEM_HEIGHT = 18;
    private static final int PANEL_GAP = 6;
    private static final int WINDOW_PADDING = 8;

    // A small, consistent radius scale keeps the interface feeling soft without
    // making the compact Minecraft layout look inflated.
    private static final float WINDOW_RADIUS = 11.0F;
    private static final float PANEL_RADIUS = 8.0F;
    private static final float ITEM_RADIUS = 6.0F;
    private static final float SCROLLBAR_RADIUS = 2.5F;
    private static final float EDGE_FADE_HEIGHT = 8.0F;

    private int windowX, windowY;
    private boolean draggingWindow;
    private boolean windowPositionInitialized;
    private int dragOffsetX, dragOffsetY;

    private final Minecraft mc = Minecraft.getMinecraft();

    private final ArrayList<CategoryComponent> categoryList;
    private int selectedCategory = 0;
    private int selectedModule = 0;
    private int centerModuleIdx = 0;

    private float animatedCenterIdx = 0;

    private int settingScroll = 0;
    private double settingScrollSmooth = 0;

    private static final Color ACCENT_COLOR = new Color(86, 157, 255);
    private static final int COLOR_ACCENT = ACCENT_COLOR.getRGB();
    private static final int COLOR_WHITE = new Color(235, 235, 245).getRGB();
    private static final int COLOR_GRAY = new Color(145, 145, 160).getRGB();
    private static final int COLOR_BG_OVERLAY = new Color(8, 10, 16, 178).getRGB();
    private static final int COLOR_WINDOW_BG = new Color(30, 33, 41, 248).getRGB();
    private static final int COLOR_TITLE_BG = new Color(38, 42, 52, 250).getRGB();
    private static final int COLOR_PANEL_BG = new Color(35, 39, 48, 246).getRGB();
    private static final int COLOR_SHADOW = new Color(0, 0, 0, 78).getRGB();

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
        if (!windowPositionInitialized) {
            windowX = (this.width - getWindowWidth()) / 2;
            windowY = (this.height - getWindowHeight()) / 2;
            windowPositionInitialized = true;
        }
        windowX = Math.max(5, Math.min(windowX, Math.max(5, this.width - getWindowWidth() - 5)));
        windowY = Math.max(5, Math.min(windowY, Math.max(5, this.height - getWindowHeight() - 5)));
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

        drawRoundedRectSafe(x + 2, y + 3, x + w + 2, y + h + 3, WINDOW_RADIUS, COLOR_SHADOW);
        drawRoundedRectSafe(x, y, x + w, y + h, WINDOW_RADIUS, COLOR_WINDOW_BG);

        drawRoundedTopRectSafe(x, y, x + w, y + TITLE_BAR_HEIGHT + WINDOW_PADDING, WINDOW_RADIUS, COLOR_TITLE_BG);
        drawRoundedRectSafe(x + 8, y + TITLE_BAR_HEIGHT + WINDOW_PADDING - 1,
                x + w - 8, y + TITLE_BAR_HEIGHT + WINDOW_PADDING, 0.75F,
                new Color(255, 255, 255, 22).getRGB());
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();

        String titleRight = "Click GUI";
        float titleY = y + WINDOW_PADDING / 2.0F + 3.0F;
        GuiText.drawShadow("Leader Lite", x + 9, titleY, COLOR_WHITE);
        GuiText.draw(titleRight, x + w - 9 - GuiText.width(titleRight), titleY, COLOR_GRAY);

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

    /** Draws a rounded cap at the top while keeping the title bar flush with the window body. */
    private void drawRoundedTopRectSafe(float x1, float y1, float x2, float y2, float radius, int color) {
        drawRoundedRectSafe(x1, y1, x2, y2, radius, color);
        RenderUtil.enableRenderState();
        Gui.drawRect(Math.round(x1), Math.round(y1 + radius), Math.round(x2), Math.round(y2), color);
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
    }

    private void drawLeftPanel(int x, int y, int mouseX, int mouseY) {
        drawRoundedRectSafe(x, y, x + LEFT_PANEL_WIDTH, y + PANEL_HEIGHT, PANEL_RADIUS, COLOR_PANEL_BG);
        GuiText.draw("Categories", x + 8, y + 6, COLOR_WHITE);

        int itemY = y + 19;
        for (int i = 0; i < categoryList.size(); i++) {
            String name = categoryList.get(i).getName();
            boolean hovered = mouseX >= x && mouseX <= x + LEFT_PANEL_WIDTH && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean selected = (i == selectedCategory);

            if (selected) {
                drawRoundedRectSafe(x + 4, itemY, x + LEFT_PANEL_WIDTH - 4, itemY + ITEM_HEIGHT, ITEM_RADIUS, new Color(86, 157, 255, 48).getRGB());
            } else if (hovered) {
                drawRoundedRectSafe(x + 4, itemY, x + LEFT_PANEL_WIDTH - 4, itemY + ITEM_HEIGHT, ITEM_RADIUS, new Color(255, 255, 255, 12).getRGB());
            }

            int color = selected ? COLOR_WHITE : (hovered ? new Color(200, 200, 215).getRGB() : COLOR_GRAY);
            GuiText.draw(trimText(name, LEFT_PANEL_WIDTH - 16), x + 8, itemY + (float) (ITEM_HEIGHT - GuiText.height()) / 2, color);
            itemY += ITEM_HEIGHT;
        }
    }

    private void drawMiddlePanel(int x, int y, int mouseX, int mouseY) {
        drawRoundedRectSafe(x, y, x + MID_PANEL_WIDTH, y + PANEL_HEIGHT, PANEL_RADIUS, COLOR_PANEL_BG);
        GuiText.draw("Modules", x + 8, y + 6, COLOR_WHITE);

        CategoryComponent cat = categoryList.get(selectedCategory);
        ArrayList<Component> modules = cat.getModules();
        if (modules == null || modules.isEmpty()) {
            GuiText.draw("No modules", x + 8, y + 25, COLOR_GRAY);
            return;
        }

        int listTop = y + 20;
        int listBottom = y + PANEL_HEIGHT - 7;
        int visibleRows = Math.max(1, (listBottom - listTop) / ITEM_HEIGHT);
        int firstIndex = Math.max(0, Math.min(centerModuleIdx - visibleRows / 2, modules.size() - visibleRows));

        ScaledResolution sr = new ScaledResolution(mc);
        double scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) ((sr.getScaledHeight() - listBottom) * scale),
                (int) (MID_PANEL_WIDTH * scale), (int) ((listBottom - listTop) * scale));

        for (int row = 0; row < visibleRows && firstIndex + row < modules.size(); row++) {
            int index = firstIndex + row;
            int rowY = listTop + row * ITEM_HEIGHT;
            ModuleComponent modComp = (ModuleComponent) modules.get(index);
            boolean selected = index == selectedModule;
            boolean enabled = modComp.mod.isEnabled();
            boolean hovered = mouseX >= x + 4 && mouseX <= x + MID_PANEL_WIDTH - 4
                    && mouseY >= rowY && mouseY < rowY + ITEM_HEIGHT;

            if (selected) {
                drawRoundedRectSafe(x + 4, rowY, x + MID_PANEL_WIDTH - 4, rowY + ITEM_HEIGHT, ITEM_RADIUS, new Color(86, 157, 255, 54).getRGB());
            } else if (hovered) {
                drawRoundedRectSafe(x + 4, rowY, x + MID_PANEL_WIDTH - 4, rowY + ITEM_HEIGHT, ITEM_RADIUS, new Color(255, 255, 255, 12).getRGB());
            }

            if (enabled) {
                drawRoundedRectSafe(x + 8, rowY + 6, x + 11, rowY + 12, 2, COLOR_ACCENT);
            }
            int textColor = selected ? COLOR_WHITE : (enabled ? new Color(210, 220, 235).getRGB() : COLOR_GRAY);
            GuiText.draw(trimText(modComp.mod.getName(), MID_PANEL_WIDTH - 30), x + 17,
                    rowY + (float) (ITEM_HEIGHT - GuiText.height()) / 2, textColor);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.enableAlpha();
    }

    private void drawRightPanel(int x, int y, int mouseX, int mouseY) {
        drawRoundedRectSafe(x, y, x + RIGHT_PANEL_WIDTH, y + PANEL_HEIGHT, PANEL_RADIUS, COLOR_PANEL_BG);

        if (categoryList.isEmpty()) return;
        CategoryComponent cat = categoryList.get(selectedCategory);
        if (cat.getModules().isEmpty()) {
            GuiText.drawCentered("No modules", x + RIGHT_PANEL_WIDTH / 2.0F,
                    y + (PANEL_HEIGHT - GuiText.height()) / 2.0F, COLOR_GRAY);
            return;
        }
        if (selectedModule >= cat.getModules().size())
            selectedModule = cat.getModules().size() - 1;

        ModuleComponent modComp = (ModuleComponent) cat.getModules().get(selectedModule);
        String modName = modComp.mod.getName();
        boolean isEnabled = modComp.mod.isEnabled();
        String stateText = isEnabled ? "Enabled" : "Disabled";
        int stateX = x + RIGHT_PANEL_WIDTH - 8 - GuiText.width(stateText);
        int nameWidth = Math.max(0, stateX - (x + 8) - 6);
        GuiText.draw(trimText(modName, nameWidth), x + 8, y + 6, isEnabled ? COLOR_WHITE : COLOR_GRAY);
        GuiText.draw(stateText, stateX, y + 6, isEnabled ? COLOR_ACCENT : COLOR_GRAY);
        drawRoundedRectSafe(x + 8, y + 18, x + RIGHT_PANEL_WIDTH - 8, y + 19, 0.75F,
                new Color(255, 255, 255, 22).getRGB());

        ArrayList<Component> settings = modComp.getSettings();
        if (settings == null || settings.isEmpty()) {
            GuiText.draw("No settings", x + 8, y + 25, COLOR_GRAY);
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
        int origCatWidth = cat.getWidth();
        int origOff = modComp.offsetY;
        boolean origExpand = modComp.panelExpand;

        cat.setX(x + 6);
        cat.setY(contentStartY - (int) settingScrollSmooth);
        cat.setWidth(RIGHT_PANEL_WIDTH - 12);
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

        // Fade the edges of an overflowing settings list instead of ending
        // rows on a visibly rigid clipping line.
        if (totalHeight > contentAreaHeight) {
            double maxScrollD = Math.max(0, totalHeight - contentAreaHeight);
            if (settingScrollSmooth > 0.5) {
                drawEdgeFade(x + 1, contentStartY, x + RIGHT_PANEL_WIDTH - 1, EDGE_FADE_HEIGHT, true);
            }
            if (settingScrollSmooth < maxScrollD - 0.5) {
                drawEdgeFade(x + 1, contentStartY + contentAreaHeight - EDGE_FADE_HEIGHT,
                        x + RIGHT_PANEL_WIDTH - 1, EDGE_FADE_HEIGHT, false);
            }
        }

        cat.setX(origCatX);
        cat.setY(origCatY);
        cat.setWidth(origCatWidth);
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
            drawRoundedRectSafe(barX, barY, barX + 4, barY + barH, SCROLLBAR_RADIUS, new Color(255, 255, 255, 105).getRGB());
        }
    }

    private void drawEdgeFade(float left, float top, float right, float height, boolean fromTop) {
        int bands = 6;
        float bandHeight = height / bands;
        for (int i = 0; i < bands; i++) {
            int distance = fromTop ? bands - 1 - i : i;
            int alpha = 10 + distance * 12;
            float y1 = top + i * bandHeight;
            float y2 = top + (i + 1) * bandHeight + 0.25F;
            drawRoundedRectSafe(left, y1, right, y2, 0.5F,
                    new Color(30, 33, 41, alpha).getRGB());
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

        if (categoryList.isEmpty()) return;

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

            int listTop = contentY + 20;
            int listBottom = contentY + PANEL_HEIGHT - 7;
            int visibleRows = Math.max(1, (listBottom - listTop) / ITEM_HEIGHT);
            int firstIndex = Math.max(0, Math.min(centerModuleIdx - visibleRows / 2, modules.size() - visibleRows));
            for (int row = 0; row < visibleRows && firstIndex + row < modules.size(); row++) {
                int rowY = listTop + row * ITEM_HEIGHT;
                if (mouseY < rowY || mouseY >= rowY + ITEM_HEIGHT) continue;

                int index = firstIndex + row;
                ModuleComponent modComp = (ModuleComponent) modules.get(index);
                if (button == 0) modComp.mod.toggle();
                selectedModule = index;
                centerModuleIdx = index;
                settingScroll = 0;
                settingScrollSmooth = 0;
                return;
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
                int origCatWidth = cat.getWidth();
                int origOff = modComp.offsetY;
                boolean origExpand = modComp.panelExpand;

                int contentStartY = contentY + 20;
                cat.setX(rightX + 6);
                cat.setY(contentStartY - (int) settingScrollSmooth);
                cat.setWidth(RIGHT_PANEL_WIDTH - 12);
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
                cat.setWidth(origCatWidth);
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
        if (categoryList.isEmpty()) return;
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
                int origCatWidth = cat.getWidth();
                int origOff = modComp.offsetY;
                boolean origExpand = modComp.panelExpand;

                int contentStartY = contentY + 20;
                cat.setX(rightX + 6);
                cat.setY(contentStartY - (int) settingScrollSmooth);
                cat.setWidth(RIGHT_PANEL_WIDTH - 12);
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
                cat.setWidth(origCatWidth);
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

    private String trimText(String text, int maxWidth) {
        return GuiText.trim(text, maxWidth);
    }
}
