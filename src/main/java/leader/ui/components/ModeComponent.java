package leader.ui.components;

import leader.enums.ChatColors;
import leader.property.properties.ModeProperty;
import leader.ui.Component;
import leader.ui.GuiText;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ModeComponent implements Component {
    private final ModeProperty property;
    private final ModuleComponent parentModule;
    private int x;
    private int y;
    private int offsetY;

    public ModeComponent(ModeProperty desc, ModuleComponent parentModule, int offsetY) {
        this.property = desc;
        this.parentModule = parentModule;
        this.x = parentModule.category.getX() + parentModule.category.getWidth();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        int x = parentModule.category.getX() + 8;
        int y = parentModule.category.getY() + offsetY;
        int w = parentModule.category.getWidth() - 16;
        int textY = y + (getHeight() - GuiText.height()) / 2;
        RenderUtil.drawRoundedRectWithGl(x, y + 1, x + w, y + getHeight() - 1, 6, new Color(255, 255, 255, 14).getRGB());

        String mode = this.property.getModeString().replace("_", " ");
        GuiText.drawShadow(GuiText.trimLabelValue(this.property.getName() + ": ", mode, w - 4), x + 2, textY, new Color(215, 218, 225).getRGB());
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.category.getX();
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return Math.max(14, GuiText.height() + 5);
    }

    public void mouseDown(int x, int y, int button) {
        if (isHovered(x, y)) {
            if (button == 0) {
                this.property.nextMode();
            } else if (button == 1) {
                this.property.previousMode();
            }
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {}

    @Override
    public void keyTyped(char chatTyped, int keyCode) {}

    private boolean isHovered(int x, int y) {
        return x > this.x + 6 && x < this.x + this.parentModule.category.getWidth() - 6 && y > this.y && y < this.y + 11;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}
