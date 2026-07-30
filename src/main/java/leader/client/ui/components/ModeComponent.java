package leader.client.ui.components;

import leader.client.enums.ChatColors;
import leader.client.property.properties.ModeProperty;
import leader.client.ui.Component;
import leader.client.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
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
        int x = parentModule.category.getX() + 4;
        int y = parentModule.category.getY() + offsetY;
        int w = parentModule.category.getWidth() - 8;

        // 背景
        RenderUtil.drawRoundedRectWithGl(x, y + 1, x + w, y + getHeight() - 1, 5, new Color(0, 0, 0, 50).getRGB());

        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String mode = this.property.getModeString();
        mode = mode.replace("_", " ");
        int bruhWidth = (int) (Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.property.getName() + ": ") * 0.5);
        // 完全使用原版坐标和颜色
        Minecraft.getMinecraft().fontRendererObj.drawString(
                this.property.getName() + ": ",
                (float) ((this.parentModule.category.getX() + 4) * 2),
                (float) ((this.parentModule.category.getY() + this.offsetY + 4) * 2), 0xffffffff, true);
        Minecraft.getMinecraft().fontRendererObj.drawString(
                ChatColors.formatColor("&9" + mode.substring(0, 1).toUpperCase() + mode.substring(1).toLowerCase()),
                (float) ((this.parentModule.category.getX() + 4 + bruhWidth) * 2),
                (float) ((this.parentModule.category.getY() + this.offsetY + 4) * 2), -1, true);
        GL11.glPopMatrix();
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
        return 12;
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
        return x > this.x && x < this.x + this.parentModule.category.getWidth() && y > this.y && y < this.y + 11;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}