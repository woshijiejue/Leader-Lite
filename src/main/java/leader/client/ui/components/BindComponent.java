package leader.client.ui.components;

import leader.client.Leader;
import leader.client.module.modules.render.GuiModule;
import leader.client.module.modules.render.HUD;
import leader.client.ui.Component;
import leader.client.ui.dataset.BindStage;
import leader.client.util.misc.KeyBindUtil;
import leader.client.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
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
        int x = parentModule.category.getX() + 4;
        int y = parentModule.category.getY() + offsetY;
        int w = parentModule.category.getWidth() - 8;
        int h = getHeight();

        // 背景（玻璃质感，绑定状态呼吸红光）
        if (isBinding) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5);
            int red = (int) (180 + 75 * pulse);
            RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, 5, new Color(red, 40, 40, 180).getRGB());
        } else {
            RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, 5, new Color(255, 255, 255, 15).getRGB());
        }

        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String displayText = this.isBinding ? BindStage.binding : BindStage.bind + ": " + KeyBindUtil.getKeyName(this.parentModule.mod.getKey());
        // 文字坐标相对于背景左边缘偏移 2 像素（原版为 (category.getX()+4)*2，即左边缘 x+4，这里保持相同）
        this.renderText(displayText, ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get()).getRGB());
        GL11.glPopMatrix();
    }

    private void renderText(String s, int color) {
        // 原版坐标：(category.getX()+4)*2, (category.getY()+offsetY+3)*2
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(s,
                (float) ((this.parentModule.category.getX() + 4) * 2),
                (float) ((this.parentModule.category.getY() + this.offsetY + 3) * 2), color);
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
        return x > this.x && x < this.x + this.parentModule.category.getWidth() && y > this.y - 1 && y < this.y + 12;
    }

    public int getHeight() {
        return 12;
    }

    @Override
    public boolean isVisible() {
        return true;
    }
}