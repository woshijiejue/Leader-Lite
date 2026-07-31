package leader.client.ui.components;

import leader.client.module.values.impl.ListValue;
import leader.client.ui.Component;
import leader.client.util.misc.ChatColors;
import leader.client.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ValueModeComponent implements Component {
    private final ListValue value;
    private final ModuleComponent parentModule;
    private int x;
    private int y;
    private int offsetY;

    public ValueModeComponent(ListValue value, ModuleComponent parentModule, int offsetY) {
        this.value = value;
        this.parentModule = parentModule;
        this.x = parentModule.category.getX() + parentModule.category.getWidth();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        int x = parentModule.category.getX() + 4;
        int y = parentModule.category.getY() + offsetY;
        int w = parentModule.category.getWidth() - 8;

        RenderUtil.drawRoundedRectWithGl(x, y + 1, x + w, y + getHeight() - 1, 5, new Color(0, 0, 0, 50).getRGB());

        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String mode = this.value.getValue() == null ? "" : this.value.getValue();
        mode = mode.replace("_", " ");
        int bruhWidth = (int) (Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.value.getDisplayName() + ": ") * 0.5);
        Minecraft.getMinecraft().fontRendererObj.drawString(
                this.value.getDisplayName() + ": ",
                (float) ((this.parentModule.category.getX() + 4) * 2),
                (float) ((this.parentModule.category.getY() + this.offsetY + 4) * 2), 0xffffffff, true);
        String label = mode.isEmpty() ? "" : mode.substring(0, 1).toUpperCase() + mode.substring(1).toLowerCase();
        Minecraft.getMinecraft().fontRendererObj.drawString(
                ChatColors.formatColor("&9" + label),
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
                this.value.nextMode();
            } else if (button == 1) {
                this.value.previousMode();
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
        return value.canDisplay();
    }
}
