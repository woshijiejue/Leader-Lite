package leader.ui.components;

import leader.Leader;
import leader.module.Module;
import leader.property.Property;
import leader.property.properties.*;
import leader.ui.AnimationValue;
import leader.ui.Component;
import leader.ui.GuiText;
import leader.ui.dataset.impl.FloatSlider;
import leader.ui.dataset.impl.IntSlider;
import leader.ui.dataset.impl.PercentageSlider;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ModuleComponent implements Component {
    private static final int TITLE_HEIGHT = 18;
    private static final int SETTINGS_TOP_GAP = 6;
    private static final float ROW_STAGGER = 0.10F;
    private static final float ROW_MIN_SCALE = 0.72F;
    private static final float ROW_ANIMATION_EPSILON = 0.999F;
    private final ArrayList<Component> settings;
    private final AnimationValue expandAnimation = new AnimationValue(0.0F, 180L);
    public Module mod;
    public CategoryComponent category;
    public int offsetY;
    public boolean panelExpand;

    public ModuleComponent(Module mod, CategoryComponent category, int offsetY) {
        this.mod = mod;
        this.category = category;
        this.offsetY = offsetY;
        this.settings = new ArrayList<>();
        this.panelExpand = false;
        int y = TITLE_HEIGHT + SETTINGS_TOP_GAP;
        if (!Leader.propertyManager.properties.get(mod.getClass()).isEmpty()) {
            for (Property<?> prop : Leader.propertyManager.properties.get(mod.getClass())) {
                Component component = null;
                if (prop instanceof BooleanProperty) component = new CheckBoxComponent((BooleanProperty) prop, this, y);
                else if (prop instanceof FloatProperty)
                    component = new SliderComponent(new FloatSlider((FloatProperty) prop), this, y);
                else if (prop instanceof IntProperty)
                    component = new SliderComponent(new IntSlider((IntProperty) prop), this, y);
                else if (prop instanceof PercentProperty)
                    component = new SliderComponent(new PercentageSlider((PercentProperty) prop), this, y);
                else if (prop instanceof ModeProperty) component = new ModeComponent((ModeProperty) prop, this, y);
                else if (prop instanceof ColorProperty)
                    component = new ColorSliderComponent((ColorProperty) prop, this, y);
                else if (prop instanceof TextProperty) component = new TextComponent((TextProperty) prop, this, y);
                if (component != null) {
                    settings.add(component);
                    y += component.getHeight();
                }
            }
        }
        settings.add(new BindComponent(this, y));
    }

    @Override
    public void draw(AtomicInteger offset) {
        int x = category.getX();
        int y = category.getY() + offsetY;
        int width = category.getWidth();
        int titleH = TITLE_HEIGHT;
        // Sample the animation once per frame so the row, divider and chevron
        // stay in sync even when the frame rate fluctuates.
        float expandProgress = expandAnimation.get();
        if (mod.isEnabled()) {
            // Soft row highlight plus a glowing accent bar.
            RenderUtil.drawRoundedRectWithGl(x + 5, y + 2.5F, x + width - 5, y + titleH - 2.5F, 6, new Color(255, 255, 255, 9).getRGB());
            RenderUtil.drawRoundedRectWithGl(x + 7, y + 4.5F, x + 12.5F, y + titleH - 4.5F, 2.75F, new Color(110, 170, 255, 60).getRGB());
            RenderUtil.drawRoundedRectWithGl(x + 8, y + 5.5F, x + 11.5F, y + titleH - 5.5F, 1.75F, new Color(120, 175, 255).getRGB());
        }
        if (expandProgress > 0.001F) {
            RenderUtil.drawRoundedRectWithGl(x + 8, y + titleH - 1, x + width - 8, y + titleH, 0.75F,
                    new Color(255, 255, 255, 18).getRGB());
        }
        int textColor = mod.isEnabled() ? new Color(245, 248, 252).getRGB() : new Color(180, 184, 193).getRGB();
        String displayName = trimText(mod.getName(), width - 34);
        // Center the label vertically regardless of the active font height.
        float textY = y + (TITLE_HEIGHT - GuiText.height()) / 2.0F;
        GuiText.drawShadow(displayName, x + 17, textY, textColor);
        if (!settings.isEmpty()) {
            String arrow = expandProgress > 0.5F ? "v" : ">";
            GuiText.drawShadow(arrow, x + width - 16, textY, new Color(175, 185, 201).getRGB());
        }
        if (expandProgress > 0.001F) {
            drawAnimatedSettings(offset, expandProgress);
        }
    }

    public ArrayList<Component> getSettings() {
        return settings;
    }

    @Override
    public void setComponentStartAt(int n) {
        this.offsetY = n;
        int y = n + TITLE_HEIGHT + SETTINGS_TOP_GAP;
        for (Component c : settings) {
            c.setComponentStartAt(y);
            if (c.isVisible()) y += c.getHeight();
        }
    }

    @Override
    public int getHeight() {
        float progress = expandAnimation.get();
        return TITLE_HEIGHT + Math.round(getSettingsHeight() * progress);
    }

    @Override
    public void update(int mx, int my) {
        if (!panelExpand) return;
        for (Component c : settings) if (c.isVisible()) c.update(mx, my);
    }

    @Override
    public void mouseDown(int x, int y, int b) {
        if (isHovered(x, y)) {
            if (b == 0) mod.toggle();
            else if (b == 1) {
                panelExpand = !panelExpand;
                expandAnimation.setTarget(panelExpand ? 1.0F : 0.0F);
            }
            return;
        }
        if (!panelExpand) return;
        for (Component c : settings) if (c.isVisible()) c.mouseDown(x, y, b);
    }

    @Override
    public void mouseReleased(int x, int y, int b) {
        if (!panelExpand) return;
        for (Component c : settings) if (c.isVisible()) c.mouseReleased(x, y, b);
    }

    @Override
    public void keyTyped(char ch, int k) {
        if (!panelExpand) return;
        for (Component c : settings) if (c.isVisible()) c.keyTyped(ch, k);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    public boolean contains(int x, int y) {
        return x > category.getX() + 5 && x < category.getX() + category.getWidth() - 5
                && y > category.getY() + offsetY && y < category.getY() + offsetY + getHeight();
    }

    private boolean isHovered(int x, int y) {
        return x > category.getX() + 5 && x < category.getX() + category.getWidth() - 5 && y > category.getY() + offsetY && y < category.getY() + 18 + offsetY;
    }

    private String trimText(String text, int maxWidth) {
        return GuiText.trim(text, maxWidth);
    }

    private int getSettingsHeight() {
        return SETTINGS_TOP_GAP + settings.stream()
                .filter(Component::isVisible)
                .mapToInt(Component::getHeight)
                .sum();
    }

    private void drawAnimatedSettings(AtomicInteger offset, float progress) {
        float scale = category.getRenderScale();
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        float moduleTop = category.getY() + offsetY + TITLE_HEIGHT;
        float moduleBottom = moduleTop + getSettingsHeight() * progress;
        float categoryTop = category.getY() + category.getTitleHeight();
        float categoryBottom = categoryTop + category.getDisplayHeight();
        float clipTop = Math.max(moduleTop, categoryTop);
        float clipBottom = Math.min(moduleBottom, categoryBottom);
        if (clipBottom <= clipTop) return;

        int framebufferScale = resolution.getScaleFactor();
        int left = (int) Math.floor(category.getX() * scale * framebufferScale);
        int right = (int) Math.ceil((category.getX() + category.getWidth()) * scale * framebufferScale);
        int bottom = (int) Math.floor((resolution.getScaledHeight() - clipBottom * scale) * framebufferScale);
        int top = (int) Math.ceil((resolution.getScaledHeight() - clipTop * scale) * framebufferScale);
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, Math.max(1, right - left), Math.max(1, top - bottom));
        int visibleCount = 0;
        for (Component c : settings) if (c.isVisible()) visibleCount++;
        int rowIndex = 0;
        int rowOffset = 0;
        // Reserve a minimum per-row animation window, then distribute the
        // remaining time across the stagger. This guarantees the final row
        // reaches scale 1.0 exactly when the parent animation completes.
        float rowStagger = visibleCount > 1
                ? Math.min(ROW_STAGGER, 0.82F / (visibleCount - 1))
                : 0.0F;
        float rowDuration = 1.0F - rowStagger * Math.max(0, visibleCount - 1);
        for (Component c : settings) {
            if (c.isVisible()) {
                float rowTop = moduleTop + SETTINGS_TOP_GAP + rowOffset;
                float rowCenter = rowTop + c.getHeight() * 0.5F;
                float centerX = category.getX() + category.getWidth() * 0.5F;
                float rowProgress = clamp01((progress - rowIndex * rowStagger) / rowDuration);
                // A row is absent until its own stagger begins, then scales in.
                // Once settled, bypass the matrix transform completely so no
                // permanent sub-pixel scaling remains on the text or controls.
                if (rowProgress > 0.0F) {
                    if (rowProgress >= ROW_ANIMATION_EPSILON) {
                        c.draw(offset);
                    } else {
                        float eased = rowProgress * rowProgress * (3.0F - 2.0F * rowProgress);
                        float rowScale = ROW_MIN_SCALE + (1.0F - ROW_MIN_SCALE) * eased;
                        GL11.glPushMatrix();
                        GL11.glTranslatef(centerX, rowCenter, 0.0F);
                        GL11.glScalef(rowScale, rowScale, 1.0F);
                        GL11.glTranslatef(-centerX, -rowCenter, 0.0F);
                        c.draw(offset);
                        GL11.glPopMatrix();
                    }
                }
                rowOffset += c.getHeight();
                rowIndex++;
                offset.incrementAndGet();
            }
        }
        GL11.glPopAttrib();
    }

    private float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
