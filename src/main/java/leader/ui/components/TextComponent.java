package leader.ui.components;
import leader.enums.ChatColors;
import leader.property.properties.TextProperty;
import leader.ui.Component;
import leader.ui.GuiText;
import leader.ui.callback.GuiInput;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
public class TextComponent implements Component {
    private final TextProperty property;
    private final ModuleComponent module;
    private int offsetY;
    private int x;
    private int y;
    public TextComponent(TextProperty property, ModuleComponent parentModule, int offsetY) {
        this.property = property;
        this.module = parentModule;
        this.x = parentModule.category.getX() + parentModule.category.getWidth();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }
    public void draw(AtomicInteger offset) {
        int x = module.category.getX() + 8;
        int y = module.category.getY() + offsetY;
        int w = module.category.getWidth() - 16;
        int textY = y + (getHeight() - GuiText.height()) / 2;
        RenderUtil.drawRoundedRectWithGl(x, y + 1, x + w, y + getHeight() - 1, 6, new Color(255, 255, 255, 14).getRGB());
        GuiText.drawShadow(GuiText.trimLabelValue(this.property.getName().replace("-", " ") + ": ",
                        ChatColors.formatColor(this.property.formatValue()), w - 4),
                x + 2, textY, new Color(215, 218, 225).getRGB());
    }
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }
    @Override
    public int getHeight() {
        return Math.max(14, GuiText.height() + 5);
    }
    public void update(int mousePosX, int mousePosY) {
        this.y = this.module.category.getY() + this.offsetY;
        this.x = this.module.category.getX();
    }
    public void mouseDown(int x, int y, int button) {
        if (this.isHovered(x, y) && button == 0 && this.module.panelExpand) {
            GuiInput.prompt(property.getName().replace("-", " "), property.getValue(), property::setValue, Minecraft.getMinecraft().currentScreen);
        }
    }
    @Override
    public void mouseReleased(int x, int y, int button) {}
    @Override
    public void keyTyped(char chatTyped, int keyCode) {}
    public boolean isHovered(int x, int y) {
        return x > this.x + 6 && x < this.x + this.module.category.getWidth() - 6 && y > this.y && y < this.y + 11;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}
