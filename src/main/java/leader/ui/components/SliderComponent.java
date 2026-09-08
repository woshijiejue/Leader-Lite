package leader.ui.components;

import leader.Leader;
import leader.module.modules.render.HUD;
import leader.ui.Component;
import leader.ui.GuiText;
import leader.ui.callback.GuiInput;
import leader.ui.dataset.Slider;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;

public class SliderComponent implements Component {
    private final Slider slider;
    private final ModuleComponent parentModule;
    private int offsetY;
    private int x;
    private int y;
    private boolean dragging = false;
    private double sliderWidth;
    private long increment = 0;
    private long decrement = 0;

    public SliderComponent(Slider slider, ModuleComponent parentModule, int offsetY) {
        this.slider = slider;
        this.parentModule = parentModule;
        this.x = parentModule.category.getX() + parentModule.category.getWidth();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        int trackX = this.parentModule.category.getX() + 10;
        int trackY = this.parentModule.category.getY() + this.offsetY + getHeight() - 5;
        int trackW = this.parentModule.category.getWidth() - 20;
        int trackH = 2;
        int accent = new Color(92, 169, 255).getRGB();
        RenderUtil.drawRoundedRectWithGl(trackX, trackY, trackX + trackW, trackY + trackH, 1, new Color(255, 255, 255, 45).getRGB());
        int sliderEnd = trackX + (int) Math.round(this.sliderWidth);
        if (sliderEnd > trackX) {
            RenderUtil.drawRoundedRectWithGl(trackX, trackY, sliderEnd, trackY + trackH, 1, accent);
        }
        int thumbX = Math.max(trackX, Math.min(trackX + trackW, sliderEnd));
        RenderUtil.drawRoundedRectWithGl(thumbX - 2, trackY - 2, thumbX + 2, trackY + trackH + 2, 3, new Color(238, 241, 247).getRGB());
        GuiText.drawShadow(GuiText.trimLabelValue(this.slider.getName() + ": ", this.slider.getValueColorString(), trackW - 2),
                trackX, this.parentModule.category.getY() + this.offsetY + 2, new Color(205, 216, 235).getRGB());
    }
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return Math.max(18, GuiText.height() + 9);
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.category.getX();
        int trackX = this.parentModule.category.getX() + 10;
        int trackWidth = this.parentModule.category.getWidth() - 20;
        double d = Math.min(trackWidth, Math.max(0, mousePosX - trackX));
        this.sliderWidth = (double) trackWidth *
                (this.slider.getInput() - this.slider.getMin()) /
                (this.slider.getMax() - this.slider.getMin());
        if (this.dragging) {
            if (d == 0.0D) {
                this.slider.setValue(this.slider.getMin());
            } else {
                double rawValue = d / (double) trackWidth
                        * (this.slider.getMax() - this.slider.getMin())
                        + this.slider.getMin();
                double increment = this.slider.getIncrement();
                if (increment > 0) {
                    rawValue = Math.round(rawValue / increment) * increment;
                }
                double n = roundToPrecision(rawValue, 2);
                n = Math.max(this.slider.getMin(), Math.min(this.slider.getMax(), n));
                this.slider.setValue(n);
            }
        }
        if (this.increment != 0 && this.increment < System.currentTimeMillis()) {
            this.increment = System.currentTimeMillis() + 50;
            this.slider.stepping(true);
        }
        if (this.decrement != 0 && this.decrement < System.currentTimeMillis()) {
            this.decrement = System.currentTimeMillis() + 50;
            this.slider.stepping(false);
        }
    }

    private static double roundToPrecision(double v, int precision) {
        if (precision < 0) return 0.0D;
        BigDecimal bd = new BigDecimal(v);
        bd = bd.setScale(precision, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void mouseDown(int x, int y, int button) {
        if (this.isTextHovered(x, y) && button == 0 && this.parentModule.panelExpand) {
            GuiInput.prompt(slider.getName().replace("-", " "), slider.getValueString(), slider::setValueString, Minecraft.getMinecraft().currentScreen);
            return;
        }
        if (this.isLeftHalfHovered(x, y) && this.parentModule.panelExpand) {
            if (button == 0) {
                this.dragging = true;
            } else if(button == 1 && this.decrement == 0) {
                this.decrement = System.currentTimeMillis() + 500;
                this.slider.stepping(false);
            }
        }
        if (this.isRightHalfHovered(x, y) && this.parentModule.panelExpand) {
            if (button == 0) {
                this.dragging = true;
            } else if(button == 1 && this.increment == 0) {
                this.increment = System.currentTimeMillis() + 500;
                this.slider.stepping(true);
            }
        }
    }

    public void mouseReleased(int x, int y, int button) {
        this.dragging = false;
        this.increment = 0;
        this.decrement = 0;
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {}

    public boolean isTextHovered(int x, int y) {
        return x > this.x + 7 && x < this.x + this.parentModule.category.getWidth() - 7 && y > this.y && y < this.y + 8;
    }

    public boolean isLeftHalfHovered(int x, int y) {
        return x > this.x + 7 && x < this.x + this.parentModule.category.getWidth() / 2 + 1 && y > this.y + 8 && y < this.y + 17;
    }

    public boolean isRightHalfHovered(int x, int y) {
        return x > this.x + this.parentModule.category.getWidth() / 2 && x < this.x + this.parentModule.category.getWidth() - 7 && y > this.y + 8 && y < this.y + 17;
    }

    @Override
    public boolean isVisible() {
        return slider.isVisible();
    }
}
