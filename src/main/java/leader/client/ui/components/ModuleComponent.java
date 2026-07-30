package leader.client.ui.components;

import leader.client.Leader;
import leader.client.module.Module;
import leader.client.module.modules.render.HUD;
import leader.client.property.Property;
import leader.client.property.properties.*;
import leader.client.ui.Component;
import leader.client.ui.dataset.impl.*;
import leader.client.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ModuleComponent implements Component {
    public Module mod;
    public CategoryComponent category;
    public int offsetY;
    private final ArrayList<Component> settings;
    public boolean panelExpand;

    public ModuleComponent(Module mod, CategoryComponent category, int offsetY) {
        this.mod = mod;
        this.category = category;
        this.offsetY = offsetY;
        this.settings = new ArrayList<>();
        this.panelExpand = false;
        int y = offsetY + 12;
        if (!Leader.propertyManager.properties.get(mod.getClass()).isEmpty()) {
            for (Property<?> prop : Leader.propertyManager.properties.get(mod.getClass())) {
                if (prop instanceof BooleanProperty)      { settings.add(new CheckBoxComponent((BooleanProperty) prop, this, y)); y += 12; }
                else if (prop instanceof FloatProperty)   { settings.add(new SliderComponent(new FloatSlider((FloatProperty) prop), this, y)); y += 16; }
                else if (prop instanceof IntProperty)     { settings.add(new SliderComponent(new IntSlider((IntProperty) prop), this, y)); y += 16; }
                else if (prop instanceof PercentProperty) { settings.add(new SliderComponent(new PercentageSlider((PercentProperty) prop), this, y)); y += 16; }
                else if (prop instanceof ModeProperty)    { settings.add(new ModeComponent((ModeProperty) prop, this, y)); y += 12; }
                else if (prop instanceof ColorProperty)   { settings.add(new ColorSliderComponent((ColorProperty) prop, this, y)); y += 32; }
                else if (prop instanceof TextProperty)    { settings.add(new TextComponent((TextProperty) prop, this, y)); y += 12; }
            }
        }
        settings.add(new BindComponent(this, y));
    }

    @Override
    public void draw(AtomicInteger offset) {
        int x = category.getX();
        int y = category.getY() + offsetY;
        int width = category.getWidth();
        int titleH = 16;
        if (panelExpand) {
            RenderUtil.drawRoundedRectWithGl(x + 1, y, x + width - 1, y + 16, 4, new Color(255,255,255,10).getRGB());
        }
        if (mod.isEnabled()) {
            Color ind = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get());
            RenderUtil.drawRoundedRectWithGl(x + 2, y + 2, x + 4, y + 14, 2, ind.getRGB());
        }
        int textColor = mod.isEnabled() ?
                ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get()).getRGB() :
                new Color(160, 160, 170).getRGB();
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(mod.getName(), x + 7, y + 4, textColor);
        if (!settings.isEmpty()) {
            String arrow = panelExpand ? "▼" : "▶";
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(arrow, x + width - 10, y + 5, 0xAAAAAA);
        }
        if (panelExpand) {
            for (Component c : settings) {
                if (c.isVisible()) {
                    c.draw(offset);
                    offset.incrementAndGet();
                }
            }
        }
    }
    public ArrayList<Component> getSettings() {
        return settings;
    }
    @Override public void setComponentStartAt(int n) { this.offsetY = n; int y = n + 16; for (Component c : settings) { c.setComponentStartAt(y); if (c.isVisible()) y += c.getHeight(); } }
    @Override public int getHeight() { return panelExpand ? 16 + settings.stream().filter(Component::isVisible).mapToInt(Component::getHeight).sum() : 16; }
    @Override public void update(int mx, int my) { if (!panelExpand) return; for (Component c : settings) if (c.isVisible()) c.update(mx, my); }
    @Override public void mouseDown(int x, int y, int b) { if (isHovered(x,y) && b==0) mod.toggle(); if (isHovered(x,y) && b==1) panelExpand = !panelExpand; if (!panelExpand) return; for (Component c : settings) if (c.isVisible()) c.mouseDown(x,y,b); }
    @Override public void mouseReleased(int x, int y, int b) { if (!panelExpand) return; for (Component c : settings) if (c.isVisible()) c.mouseReleased(x,y,b); }
    @Override public void keyTyped(char ch, int k) { if (!panelExpand) return; for (Component c : settings) if (c.isVisible()) c.keyTyped(ch, k); }
    @Override public boolean isVisible() { return true; }
    private boolean isHovered(int x, int y) { return x > category.getX() && x < category.getX()+category.getWidth() && y > category.getY()+offsetY && y < category.getY()+16+offsetY; }
}