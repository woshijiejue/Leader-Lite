package leader.client.ui.components;

import leader.client.module.values.ValueFormat;
import leader.client.module.values.impl.BoolValue;
import leader.client.ui.Component;
import leader.client.util.misc.ChatColors;
import leader.client.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ValueCheckBoxComponent implements Component {
    private final BoolValue value;
    private final ModuleComponent module;
    private int offsetY;
    private int x;
    private int y;

    public ValueCheckBoxComponent(BoolValue value, ModuleComponent parentModule, int offsetY) {
        this.value = value;
        this.module = parentModule;
        this.x = parentModule.category.getX() + parentModule.category.getWidth();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        int boxX = module.category.getX() + 4;
        int boxY = module.category.getY() + offsetY + 2;
        int boxSize = 8;

        RenderUtil.drawRoundedRectWithGl(boxX - 1, boxY - 1, module.category.getX() + module.category.getWidth() - 3, boxY + boxSize + 1, 5, new Color(0, 0, 0, 40).getRGB());
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        Minecraft.getMinecraft().fontRendererObj.drawString(
                this.value.getDisplayName().replace("-", " ") + ": " + ChatColors.formatColor(ValueFormat.format(this.value)),
                (float) ((this.module.category.getX() + 4) * 2),
                (float) ((this.module.category.getY() + this.offsetY + 5) * 2),
                -1, false);
        GL11.glPopMatrix();
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return 12;
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.module.category.getY() + this.offsetY;
        this.x = this.module.category.getX();
    }

    public void mouseDown(int x, int y, int button) {
        if (this.isHovered(x, y) && button == 0 && this.module.panelExpand) {
            this.value.setValue(!this.value.getValue());
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {}

    @Override
    public void keyTyped(char chatTyped, int keyCode) {}

    public boolean isHovered(int x, int y) {
        return x > this.x && x < this.x + this.module.category.getWidth() && y > this.y && y < this.y + 11;
    }

    @Override
    public boolean isVisible() {
        return value.canDisplay();
    }
}
