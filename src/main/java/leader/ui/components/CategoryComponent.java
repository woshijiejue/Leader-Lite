package leader.ui.components;

import leader.module.Module;
import leader.ui.AnimationValue;
import leader.ui.Component;
import leader.ui.GuiText;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
public class CategoryComponent {
    private final int MAX_HEIGHT = 300;
    public ArrayList<Component> modulesInCategory = new ArrayList<>();
    public String categoryName;
    private boolean categoryOpened;
    private int width;
    private int y;
    private int x;
    private final int bh;
    public boolean dragging;
    public int xx;
    public int yy;
    public boolean pin = false;
    private double marginY, marginX;
    private int scroll = 0;
    private final AnimationValue scrollAnimation = new AnimationValue(0.0F, 180L);
    private final AnimationValue expandAnimation = new AnimationValue(0.0F, 220L);
    private int height = 0;
    private float displayHeight = 0.0F;
    private float renderScale = 1.0F;
    private boolean layoutOverride;
    private int layoutX;
    private int layoutY;
    private int layoutWidth;
    private final int titleHeight;

    public CategoryComponent(String category, List<Module> modules) {
        this.categoryName = category;
        this.width = 132;
        this.x = 5;
        this.y = 5;
        this.bh = 16;
        this.titleHeight = this.bh + 3;
        this.xx = 0;
        this.categoryOpened = true;
        this.dragging = false;
        int tY = this.bh + 3;
        this.marginX = 80;
        this.marginY = 4.5;
        for (Module mod : modules) {
            ModuleComponent b = new ModuleComponent(mod, this, tY);
            this.modulesInCategory.add(b);
            tY += b.getHeight();
        }
    }

    public ArrayList<Component> getModules() { return this.modulesInCategory; }
    public void setX(int n) { this.x = n; }
    public void setY(int y) { this.y = y; }
    public void setRenderBounds(int x, int y, int width) {
        this.layoutOverride = true;
        this.layoutX = x;
        this.layoutY = y;
        this.layoutWidth = Math.max(100, width);
    }
    public void clearRenderBounds() {
        this.layoutOverride = false;
    }
    public void mousePressed(boolean d) { this.dragging = d; }
    public boolean isPin() { return this.pin; }
    public void setPin(boolean on) { this.pin = on; }
    public boolean isOpened() { return this.categoryOpened; }
    public void setOpened(boolean on) { this.categoryOpened = on; }

    public void render(float uiScale) {
        renderScale = uiScale;
        int oldX = this.x;
        int oldY = this.y;
        int oldWidth = this.width;
        if (this.layoutOverride) {
            this.x = this.layoutX;
            this.y = this.layoutY;
            this.width = this.layoutWidth;
        }
        float animatedScroll = scrollAnimation.get();
        float displayH = displayHeight;
        float totalH = titleHeight + displayH + (displayH > 0.0F ? 4.0F : 0.0F);

        RenderUtil.drawRoundedRectWithGl(x, y, x + width, y + totalH, 8, new Color(255, 255, 255, 30).getRGB());
        RenderUtil.drawRoundedRectWithGl(x + 1, y + 1, x + width - 1, y + totalH - 1, 7, new Color(15, 17, 23, 208).getRGB());
        RenderUtil.drawRoundedRectWithGl(x + 2, y + 2, x + width - 2, y + titleHeight, 6, new Color(255, 255, 255, 14).getRGB());
        RenderUtil.drawRoundedRectWithGl(x + 8, y + titleHeight - 1, x + width - 8, y + titleHeight, 0.75F,
                new Color(255, 255, 255, 16).getRGB());

        GuiText.draw(trimText(categoryName, width - 32), x + 9, y + 6, new Color(232, 235, 242).getRGB(), 12.0F);
        GuiText.draw(categoryOpened ? "−" : "+", x + width - 14, y + 6, new Color(125, 172, 238).getRGB(), 12.0F);

        if (displayH > 0 && !modulesInCategory.isEmpty()) {
            int renderHeight = 0;
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            double framebufferScale = sr.getScaleFactor();
            double scaledX = x * uiScale;
            double scaledY = (y + titleHeight) * uiScale;
            double scaledWidth = width * uiScale;
            double scaledHeight = displayH * uiScale;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                    (int) Math.floor(scaledX * framebufferScale),
                    (int) Math.floor((sr.getScaledHeight() - scaledY - scaledHeight) * framebufferScale),
                    (int) Math.ceil(scaledWidth * framebufferScale),
                    (int) Math.ceil(scaledHeight * framebufferScale));
            for (Component c : modulesInCategory) {
                int ch = c.getHeight();
                if (renderHeight + ch > animatedScroll && renderHeight < animatedScroll + displayH) {
                    c.draw(new AtomicInteger(0));
                }
                renderHeight += ch;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            if (height > displayH) {
                float scrollY = y + titleHeight + animatedScroll * displayH / height;
                float barH = Math.max( (float)displayH * displayH / height, 10);
                Gui.drawRect(x + width - 4, y + titleHeight + 5, x + width - 3,
                        y + titleHeight + Math.round(displayH) - 5, new Color(255, 255, 255, 18).getRGB());
                RenderUtil.drawRoundedRectWithGl(x + width - 5, scrollY, x + width - 2, scrollY + barH, 2.5F, new Color(255, 255, 255, 90).getRGB());
            }
        }
        if (this.layoutOverride) {
            this.x = oldX;
            this.y = oldY;
            this.width = oldWidth;
        }
    }

    public void update() {
        layoutModules();
    }

    public void layoutModules() {
        height = 0;
        for (Component component : modulesInCategory) height += component.getHeight();

        int maxScroll = Math.max(0, height - MAX_HEIGHT);
        if (scroll > maxScroll) scroll = maxScroll;
        scrollAnimation.setTarget(scroll);
        float animatedScroll = scrollAnimation.get();
        int targetHeight = categoryOpened ? Math.min(height, MAX_HEIGHT) : 0;
        expandAnimation.setTarget(targetHeight);
        displayHeight = expandAnimation.get();
        if (displayHeight < 0.01F && !categoryOpened) displayHeight = 0.0F;

        int contentOffset = 0;
        for (Component component : modulesInCategory) {
            component.setComponentStartAt(titleHeight + (int) (contentOffset - animatedScroll));
            contentOffset += component.getHeight();
        }
    }

    public boolean isInsideContent(int mouseX, int mouseY) {
        return categoryOpened && displayHeight > 0.0F
                && mouseX >= x && mouseX <= x + width
                && mouseY >= y + titleHeight && mouseY <= y + titleHeight + displayHeight;
    }

    public int getX() { return this.layoutOverride ? this.layoutX : this.x; }
    public int getY() { return this.layoutOverride ? this.layoutY : this.y; }
    public int getWidth() { return this.layoutOverride ? this.layoutWidth : this.width; }
    public void setWidth(int width) { this.width = Math.max(100, width); }

    public int getVisualHeight() {
        return Math.round(titleHeight + displayHeight + (displayHeight > 0.0F ? 4.0F : 0.0F));
    }

    public int getTitleHeight() { return titleHeight; }
    public float getDisplayHeight() { return displayHeight; }
    public float getRenderScale() { return renderScale; }

    private String trimText(String text, int maxWidth) {
        return GuiText.trim(text, maxWidth, 12.0F);
    }

    public void handleDrag(int x, int y) {
        if (this.dragging) {
            this.setX(x - this.xx);
            this.setY(y - this.yy);
        }
    }

    public boolean isHovered(int x, int y) {
        return x >= this.x + this.width - 13 && x <= this.x + this.width && y >= this.y + 2 && y <= this.y + this.bh + 1;
    }

    public boolean mousePressed(int x, int y) {
        return x >= this.x + this.width - 15 && x <= this.x + this.width - 6 && y >= this.y + 2 && y <= this.y + this.bh + 1;
    }

    public boolean insideArea(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.bh;
    }

    public String getName() { return categoryName; }

    public void setLocation(int parseInt, int parseInt1) {
        this.x = parseInt;
        this.y = parseInt1;
    }

    public void onScroll(int mouseX, int mouseY, int scrollAmount) {
        if (!categoryOpened || height <= MAX_HEIGHT) return;
        int areaTop = this.y + this.titleHeight;
        int areaBottom = this.y + this.titleHeight + Math.round(this.displayHeight);
        if (mouseX >= this.x && mouseX <= this.x + width && mouseY >= areaTop && mouseY <= areaBottom) {
            scroll -= scrollAmount * 12;
            scroll = Math.max(0, Math.min(scroll, height - MAX_HEIGHT));
        }
    }
}
