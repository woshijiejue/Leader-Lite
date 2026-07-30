package leader.client.ui.components;

import leader.client.module.Module;
import leader.client.ui.Component;
import leader.client.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
    private double animScroll = 0;
    private double animExpandHeight = 0;
    private int height = 0;

    public CategoryComponent(String category, List<Module> modules) {
        this.categoryName = category;
        this.width = 92;
        this.x = 5;
        this.y = 5;
        this.bh = 13;
        this.xx = 0;
        this.categoryOpened = false;
        this.dragging = false;
        int tY = this.bh + 3;
        this.marginX = 80;
        this.marginY = 4.5;
        for (Module mod : modules) {
            ModuleComponent b = new ModuleComponent(mod, this, tY);
            this.modulesInCategory.add(b);
            tY += 16;
        }
    }

    public ArrayList<Component> getModules() { return this.modulesInCategory; }
    public void setX(int n) { this.x = n; }
    public void setY(int y) { this.y = y; }
    public void mousePressed(boolean d) { this.dragging = d; }
    public boolean isPin() { return this.pin; }
    public void setPin(boolean on) { this.pin = on; }
    public boolean isOpened() { return this.categoryOpened; }
    public void setOpened(boolean on) { this.categoryOpened = on; }

    public void render(FontRenderer renderer) {
        this.width = 92;
        update();
        height = 0;
        for (Component c : modulesInCategory) height += c.getHeight();
        int maxScroll = Math.max(0, height - MAX_HEIGHT);
        if (scroll > maxScroll) scroll = maxScroll;
        animScroll += (scroll - animScroll) * 0.2;
        int targetH = categoryOpened ? Math.min(height, MAX_HEIGHT) : 0;
        animExpandHeight += (targetH - animExpandHeight) * 0.2;
        int displayH = (int) Math.round(animExpandHeight);
        if (displayH < 1 && !categoryOpened) displayH = 0;
        int titleH = bh + 3;
        int totalH = titleH + displayH + (displayH > 0 ? 4 : 0);

        RenderUtil.drawRoundedRectWithGl(x + 3, y + 3, x + width + 3, y + totalH + 3, 7, new Color(0,0,0,100).getRGB());
        RenderUtil.drawRoundedRectWithGl(x, y, x + width, y + totalH, 7, new Color(24,24,36,235).getRGB());
        RenderUtil.drawRoundedRectWithGl(x, y, x + width, y + titleH, 7, new Color(40,40,56,240).getRGB());
        Gui.drawRect(x, y + titleH - 7, x + width, y + titleH, new Color(40,40,56,240).getRGB());

        if (displayH > 0)
            Gui.drawRect(x + 3, y + titleH, x + width - 3, y + titleH + 1, new Color(255,255,255,20).getRGB());

        renderer.drawString(categoryName, x + 5, y + 4, new Color(220,220,240).getRGB(), false);
        renderer.drawString(categoryOpened ? "−" : "+", (float) (x + marginX), (int)(y + marginY), Color.WHITE.getRGB(), false);

        if (displayH > 0 && !modulesInCategory.isEmpty()) {
            int renderHeight = 0;
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            double scale = sr.getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int)(x*scale), (int)((sr.getScaledHeight()-(y+titleH+displayH))*scale), (int)(width*scale), (int)(displayH*scale));
            for (Component c : modulesInCategory) {
                int ch = c.getHeight();
                if (renderHeight + ch > animScroll && renderHeight < animScroll + displayH) {
                    int dy = (int)(renderHeight - animScroll);
                    c.setComponentStartAt(titleH + dy);
                    c.draw(new AtomicInteger(0));
                }
                renderHeight += ch;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            if (height > displayH) {
                float scrollY = y + titleH + (float)(animScroll * displayH / height);
                float barH = Math.max( (float)displayH * displayH / height, 10);
                Gui.drawRect(x + width - 3, y + titleH, x + width - 1, y + titleH + displayH, new Color(0,0,0,80).getRGB());
                RenderUtil.drawRoundedRectWithGl(x + width - 3, scrollY, x + width - 1, scrollY + barH, 1, new Color(255,255,255,120).getRGB());
            }
        }
    }

    public void update() {
        int offset = this.bh + 3;
        for (Component component : this.modulesInCategory) {
            component.setComponentStartAt(offset);
            offset += component.getHeight();
        }
    }

    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getWidth() { return this.width; }

    public void handleDrag(int x, int y) {
        if (this.dragging) {
            this.setX(x - this.xx);
            this.setY(y - this.yy);
        }
    }

    public boolean isHovered(int x, int y) {
        return x >= this.x + 92 - 13 && x <= this.x + this.width && (float) y >= (float) this.y + 2.0F && y <= this.y + this.bh + 1;
    }

    public boolean mousePressed(int x, int y) {
        return x >= this.x + 77 && x <= this.x + this.width - 6 && (float) y >= (float) this.y + 2.0F && y <= this.y + this.bh + 1;
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
        int areaTop = this.y + this.bh;
        int areaBottom = this.y + this.bh + MAX_HEIGHT;
        if (mouseX >= this.x && mouseX <= this.x + width && mouseY >= areaTop && mouseY <= areaBottom) {
            scroll -= scrollAmount * 12;
            scroll = Math.max(0, Math.min(scroll, height - MAX_HEIGHT));
        }
    }
}