package leader.client.module.modules.render;

import leader.client.Leader;
import leader.client.component.impl.network.blink.BlinkType;
import leader.client.util.misc.ChatColors;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.Render2DEvent;
import leader.client.events.TickEvent;
import leader.mixin.accessor.IAccessorGuiChat;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ColorValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.util.render.ColorUtil;
import leader.client.util.render.RenderUtil;
import leader.client.util.render.shader.KawaseBlur;
import leader.client.util.render.shader.ShaderElement;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HUD extends Module {

    private List<Module> activeModules = new ArrayList<>();
    public final ListValue colorMode = new ListValue(
            "color", new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}, "CUSTOM1", this
    );
    public final SliderValue colorSpeed = new SliderValue("color-speed", 1.0, 0.5, 1.5, Representation.FLOAT, this);
    public final SliderValue colorSaturation = new SliderValue("color-saturation", 50, 0, 100, Representation.INT, this);
    public final SliderValue colorBrightness = new SliderValue("color-brightness", 100, 0, 100, Representation.INT, this);
    public final ColorValue custom1 = new ColorValue("custom-color-1", Color.WHITE, () -> this.colorMode.is("CUSTOM1") || this.colorMode.is("CUSTOM12") || this.colorMode.is("CUSTOM123"), this);
    public final ColorValue custom2 = new ColorValue("custom-color-2", Color.WHITE, () -> this.colorMode.is("CUSTOM12") || this.colorMode.is("CUSTOM123"), this);
    public final ColorValue custom3 = new ColorValue("custom-color-3", Color.WHITE, () -> this.colorMode.is("CUSTOM123"), this);
    public final ListValue posX = new ListValue("position-x", new String[]{"LEFT", "RIGHT"}, "LEFT", this);
    public final ListValue posY = new ListValue("position-y", new String[]{"TOP", "BOTTOM"}, "TOP", this);
    public final SliderValue offsetX = new SliderValue("offset-x", 2, 0, 255, Representation.INT, this);
    public final SliderValue offsetY = new SliderValue("offset-y", 2, 0, 255, Representation.INT, this);
    public final SliderValue scale = new SliderValue("scale", 1.0, 0.5, 1.5, Representation.FLOAT, this);
    public final SliderValue background = new SliderValue("background", 25, 0, 100, Representation.INT, this);
    public final BoolValue showBar = new BoolValue("bar", true, this);
    public final BoolValue shadow = new BoolValue("shadow", true, this);
    public final BoolValue suffixes = new BoolValue("suffixes", true, this);
    public final BoolValue lowerCase = new BoolValue("lower-case", false, this);
    public final BoolValue chatOutline = new BoolValue("chat-outline", true, this);
    public final BoolValue blinkTimer = new BoolValue("blink-timer", true, this);
    public final BoolValue toggleSound = new BoolValue("toggle-sounds", true, this);
    public final BoolValue toggleAlerts = new BoolValue("toggle-alerts", false, this);
    public final BoolValue bgColor = new BoolValue("bg-color", false, this);
    public final BoolValue glow = new BoolValue("glow", false, this);
    public final BoolValue blur = new BoolValue("blur", false, this);
    public final SliderValue blurIterations = new SliderValue("blur-iterations", 2, 1, 8, Representation.INT, this);
    public final SliderValue blurOffset = new SliderValue("blur-offset", 3, 1, 10, Representation.INT, this);
    public final SliderValue barless = new SliderValue("barless", 0, 0, 8, () -> this.showBar.getValue(), Representation.INT, this);
    public final ListValue barMode = new ListValue("bar-mode", new String[]{"RIGHT", "LEFT", "TOP", "BOTTOM"}, "RIGHT", () -> this.showBar.getValue(), this);
    private Framebuffer blurStencil;

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = FontManager.getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + FontManager.getStringWidth(str);
            }
        }
        return width;
    }

    private static int setAlpha(int color, float alpha) {
        int a = (int) (alpha * 255.0F);
        return (color & 0xFFFFFF) | (a << 24);
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public HUD() {
        super("HUD", true, true);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        if (this.colorMode.is("RAINBOW")) {
            color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
        } else if (this.colorMode.is("CHROMA")) {
            color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
        } else if (this.colorMode.is("ASTOLFO")) {
            float cycle = this.getColorCycle(time, offset);
            if (cycle % 1.0F < 0.5F) {
                cycle = 1.0F - cycle % 1.0F;
            }
            color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
        } else if (this.colorMode.is("CUSTOM1")) {
            color = this.custom1.getValue();
        } else if (this.colorMode.is("CUSTOM12")) {
            double cycle1 = this.getColorCycle(time, offset);
            color = ColorUtil.interpolate(
                    (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                    this.custom1.getValue(),
                    this.custom2.getValue()
            );
        } else if (this.colorMode.is("CUSTOM123")) {
            double cycle2 = this.getColorCycle(time, offset);
            float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
            if (floor <= 0.5F) {
                color = ColorUtil.interpolate(floor * 2.0F, this.custom1.getValue(), this.custom2.getValue());
            } else {
                color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, this.custom2.getValue(), this.custom3.getValue());
            }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            this.activeModules = Leader.moduleManager.modules.values().stream().filter(module -> module.isEnabled() && !module.isHidden()).sorted(Comparator.comparingInt(this::getModuleWidth).reversed()).collect(Collectors.<Module>toList());
        }
    }

    private void drawGlowOutline(float x1, float y1, float x2, float y2, int color, int passes, float step,
                                 boolean top, boolean bottom, boolean left, boolean right) {
        for (int i = passes; i >= 1; i--) {
            float expand = i * step;
            float intensity = (float) (passes - i + 1) / (float) passes;
            int glowColor = setAlpha(color, 0.045F * intensity * intensity);

            if (top) {
                RenderUtil.drawRect(x1 - expand, y1 - expand, x2 + expand, y1, glowColor);
            }
            if (bottom) {
                RenderUtil.drawRect(x1 - expand, y2, x2 + expand, y2 + expand, glowColor);
            }
            if (left) {
                RenderUtil.drawRect(x1 - expand, y1, x1, y2, glowColor);
            }
            if (right) {
                RenderUtil.drawRect(x2, y1, x2 + expand, y2, glowColor);
            }
        }
    }

    public void drawBlur() {
        blurStencil = ShaderElement.createFrameBuffer(blurStencil);
        blurStencil.framebufferClear();
        blurStencil.bindFramebuffer(false);
        for (Runnable runnable : ShaderElement.getTasks()) {
            runnable.run();
        }
        ShaderElement.getTasks().clear();
        blurStencil.unbindFramebuffer();
        KawaseBlur.renderBlur(blurStencil.framebufferTexture, blurIterations.getValue().intValue(), blurOffset.getValue().intValue());
    }

    public void clearBlurTasks() {
        ShaderElement.getTasks().clear();
    }

    private void drawGlowText(String text, float x, float y, int color, int passes, float spread) {
        if (!FontManager.customFont.getValue()) return;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        for (int i = passes; i >= 1; i--) {
            float offset = i * spread;
            float intensity = (float) (passes - i + 1) / (float) passes;
            int glowColor = setAlpha(color, 0.10F * intensity * intensity);
            float diagonal = offset * 0.65F;
            FontManager.drawString(text, x + offset, y, glowColor, false);
            FontManager.drawString(text, x - offset, y, glowColor, false);
            FontManager.drawString(text, x, y + offset, glowColor, false);
            FontManager.drawString(text, x, y - offset, glowColor, false);
            FontManager.drawString(text, x + diagonal, y + diagonal, glowColor, false);
            FontManager.drawString(text, x - diagonal, y + diagonal, glowColor, false);
            FontManager.drawString(text, x + diagonal, y - diagonal, glowColor, false);
            FontManager.drawString(text, x - diagonal, y - diagonal, glowColor, false);
        }
        GlStateManager.disableBlend();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Leader.commandManager != null && Leader.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            float height = (float) FontManager.getFontHeight() - 1.0F;
            float x = (float) this.offsetX.getValue().intValue()
                    + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F)) * this.scale.getValue();
            float y = (float) this.offsetY.getValue().intValue() + 1.0F * this.scale.getValue();
            if (this.posX.is("RIGHT")) {
                x = (float) new ScaledResolution(mc).getScaledWidth() - x;
            }
            if (this.posY.is("BOTTOM")) {
                y = (float) new ScaledResolution(mc).getScaledHeight() - y - height * this.scale.getValue();
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
            long l = System.currentTimeMillis();
            long offset = 0L;
            float listMinX = Float.MAX_VALUE, listMinY = Float.MAX_VALUE;
            float listMaxX = Float.MIN_VALUE, listMaxY = Float.MIN_VALUE;
            int count = this.activeModules.size();
            float[] rowX1 = new float[count];
            float[] rowX2 = new float[count];
            float[] rowY1 = new float[count];
            float[] rowY2 = new float[count];
            for (Module module : this.activeModules) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix) - (this.shadow.getValue() ? 0 : 1));
                Color themeColor = this.getColor(l, offset);
                int color = themeColor.getRGB();
                float sx = x / this.scale.getValue();
                float sy = y / this.scale.getValue();
                float bgX1 = sx - 1.0F - (this.posX.is("LEFT") ? 0.0F : totalWidth);
                float bgY1 = sy - (this.posY.is("TOP") ? (offset == 0L ? 1.0F : 0.0F) : (this.shadow.getValue() ? 1.0F : 0.0F));
                float bgX2 = sx + 1.0F + (this.posX.is("LEFT") ? totalWidth : 0.0F);
                float bgY2 = sy + height + (this.posY.is("TOP") ? (this.shadow.getValue() ? 1.0F : 0.0F) : (offset == 0L ? 1.0F : 0.0F));
                float textX = sx - (this.posX.is("RIGHT") ? totalWidth : 0.0F);
                float textY = sy;
                listMinX = Math.min(listMinX, bgX1);
                listMinY = Math.min(listMinY, bgY1);
                listMaxX = Math.max(listMaxX, bgX2);
                listMaxY = Math.max(listMaxY, bgY2);
                rowX1[(int)offset] = bgX1;
                rowX2[(int)offset] = bgX2;
                rowY1[(int)offset] = bgY1;
                rowY2[(int)offset] = bgY2;
                boolean hasBg = this.background.getValue() > 0;
                boolean useThemeBg = this.bgColor.getValue();
                int bgAlphaColor;
                if (useThemeBg) {
                    bgAlphaColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), (int) (this.background.getValue().floatValue() / 100.0F * 255.0F)).getRGB();
                } else {
                    bgAlphaColor = new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F).getRGB();
                }
                int glowColor = useThemeBg ? color : themeColor.getRGB();

                if (hasBg && this.blur.getValue()) {
                    final float blurX1 = bgX1;
                    final float blurY1 = bgY1;
                    final float blurX2 = bgX2;
                    final float blurY2 = bgY2;
                    ShaderElement.addBlurTask(() -> {
                        RenderUtil.enableRenderState();
                        RenderUtil.drawRect(blurX1, blurY1, blurX2, blurY2, -1);
                        RenderUtil.disableRenderState();
                    });
                }

                if (hasBg && this.glow.getValue()) {
                    boolean firstRow = offset == 0L;
                    boolean lastRow = offset == this.activeModules.size() - 1;
                    boolean outerLeft = this.posX.is("RIGHT");
                    RenderUtil.enableRenderState();
                    drawGlowOutline(
                            bgX1, bgY1, bgX2, bgY2, glowColor, 6, 0.5F,
                            firstRow, lastRow, outerLeft, !outerLeft
                    );
                    RenderUtil.disableRenderState();
                }

                RenderUtil.enableRenderState();
                if (hasBg) {
                    RenderUtil.drawRect(bgX1, bgY1, bgX2, bgY2, bgAlphaColor);
                }
                if (this.showBar.getValue()) {
                    int barlessVal = this.barless.getValue().intValue();
                    float barY1 = bgY1 + barlessVal;
                    float barY2 = bgY2 - barlessVal;
                    if (this.barMode.is("RIGHT")) {
                        boolean alignLeft = this.posX.is("LEFT");
                        if (alignLeft) {
                            RenderUtil.drawRect(sx - 2.0F, barY1, sx - 1.0F, barY2, color);
                        } else {
                            RenderUtil.drawRect(sx + 1.0F, barY1, sx + 2.0F, barY2, color);
                        }
                    } else if (this.barMode.is("LEFT")) {
                        boolean alignLeft = this.posX.is("LEFT");
                        if (alignLeft) {
                            RenderUtil.drawRect(bgX2, barY1, bgX2 + 1.0F, barY2, color);
                        } else {
                            RenderUtil.drawRect(bgX1 - 1.0F, barY1, bgX1, barY2, color);
                        }
                    } else if (this.barMode.is("TOP")) {
                        float bw = 1.0F;
                        if (offset == 0L) {
                            RenderUtil.drawRect(bgX1, bgY1 - bw, bgX2, bgY1, color);
                        }
                    } else if (this.barMode.is("BOTTOM")) {
                        float bw = 1.0F;
                        if (offset == this.activeModules.size() - 1) {
                            RenderUtil.drawRect(bgX1, bgY2, bgX2, bgY2 + bw, color);
                        }
                    }
                }
                RenderUtil.disableRenderState();

                GlStateManager.disableDepth();

                if (this.glow.getValue()) {
                    drawGlowText(moduleName, textX, textY, glowColor, 4, 0.65F);
                }
                if (this.shadow.getValue()) {
                    FontManager.drawStringWithShadow(moduleName, textX, textY, color);
                } else {
                    FontManager.drawString(
                                    moduleName,
                                    textX,
                                    textY + (this.posY.is("BOTTOM") ? 1.0F : 0.0F),
                                    color,
                                    false
                            );
                }
                if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                    float suffixX = (float) FontManager.getStringWidth(moduleName) + 3.0F;
                    for (String string : moduleSuffix) {
                        if (this.glow.getValue()) {
                            drawGlowText(string, textX + suffixX, textY, ChatColors.GRAY.toAwtColor(), 2, 0.35F);
                        }
                        if (this.shadow.getValue()) {
                            FontManager.drawStringWithShadow(
                                            string,
                                            textX + suffixX,
                                            textY,
                                            ChatColors.GRAY.toAwtColor()
                                    );
                        } else {
                            FontManager.drawString(
                                            string,
                                            textX + suffixX,
                                            textY + (this.posY.is("BOTTOM") ? 1.0F : 0.0F),
                                            ChatColors.GRAY.toAwtColor(),
                                            false
                                    );
                        }
                        suffixX += (float) FontManager.getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
                    }
                }
                y += (height + (this.shadow.getValue() ? 1.0F : 0.0F)) * this.scale.getValue() * (this.posY.is("TOP") ? 1.0F : -1.0F);
                offset++;
            }

            if (this.blinkTimer.getValue()) {
                BlinkType blinkingModule = Leader.blinkComponent.getBlinkingModule();
                if (blinkingModule != BlinkType.NONE && blinkingModule != BlinkType.AUTO_BLOCK) {
                    long movementPacketSize = Leader.blinkComponent.countMovement();
                    if (movementPacketSize > 0L) {
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        FontManager.drawString(
                                        String.valueOf(movementPacketSize),
                                        (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.scale.getValue()
                                                - (float) FontManager.getStringWidth(String.valueOf(movementPacketSize)) / 2.0F,
                                        (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                                        this.getColor(l, offset).getRGB() & 16777215 | -1090519040,
                                        this.shadow.getValue()
                                );
                        GlStateManager.disableBlend();
                    }
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }
}
