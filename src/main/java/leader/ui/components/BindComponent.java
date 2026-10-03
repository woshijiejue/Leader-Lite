package leader.ui.components;

import leader.Leader;
import leader.module.modules.render.GuiModule;
import leader.module.modules.render.HUD;
import leader.ui.Component;
import leader.ui.GuiText;
import leader.ui.dataset.BindStage;
import leader.util.KeyBindUtil;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BindComponent implements Component {
    private boolean isBinding;
    private final ModuleComponent parentModule;
    private int offsetY;
    private int x;
    private int y;

    public BindComponent(ModuleComponent b, int offsetY) {
        this.parentModule = b;
        this.x = b.category.getX() + b.category.getWidth();
        this.y = b.category.getY() + b.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        int x = parentModule.category.getX() + 8;
        int y = parentModule.category.getY() + offsetY;
        int w = parentModule.category.getWidth() - 16;
        int h = getHeight() - 2;
        int color = isBinding ? new Color(70, 132, 220, 210).getRGB() : new Color(255, 255, 255, 10).getRGB();

        RenderUtil.drawRoundedRectWithGl(x, y + 1, x + w, y + h, 6, color);
        String displayText = this.isBinding ? "Press a key" : GuiText.trimLabelValue("Bind · ",
                KeyBindUtil.getKeyName(this.parentModule.mod.getKey()), Math.max(0, w - 4));
        GuiText.drawShadow(displayText, x + 2, y + (getHeight() - GuiText.height()) / 2,
                isBinding ? 0xFFFFFFFF : new Color(196, 208, 228).getRGB());
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.category.getX();
    }

    public void mouseDown(int x, int y, int button) {
        if (this.isHovered(x, y) && button == 0 && this.parentModule.panelExpand) {
            this.isBinding = !this.isBinding;
        } else if (this.isBinding && this.parentModule.panelExpand) {
            int keyIndex = button - 100;
            if (button == 0) {
                this.isBinding = false;
                return;
            }
            this.parentModule.mod.setKey(keyIndex);
            this.isBinding = false;
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {}

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
        if (this.isBinding) {
            if (keyCode == 1) {
                this.isBinding = false;
                return;
            }
            if (keyCode == 11) {
                if (this.parentModule.mod instanceof GuiModule) {
                    this.parentModule.mod.setKey(54);
                } else {
                    this.parentModule.mod.setKey(0);
                }
            } else {
                this.parentModule.mod.setKey(keyCode);
            }
            this.isBinding = false;
        }
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    public boolean isHovered(int x, int y) {
        return x > this.x + 8 && x < this.x + this.parentModule.category.getWidth() - 8 && y > this.y && y < this.y + getHeight();
    }

    public int getHeight() {
        return Math.max(16, GuiText.height() + 7);
    }

    @Override
    public boolean isVisible() {
        return true;
    }
}
