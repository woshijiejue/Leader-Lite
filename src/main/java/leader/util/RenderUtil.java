package leader.util;

import leader.enums.ChatColors;
import leader.module.modules.render.FontManager;
import leader.mixin.IAccessorEntityRenderer;
import leader.mixin.IAccessorMinecraft;
import leader.mixin.IAccessorRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;
import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class RenderUtil {
    private static Minecraft mc;
    private static Frustum cameraFrustum;
    private static IntBuffer viewportBuffer;
    private static FloatBuffer modelViewBuffer;
    private static FloatBuffer projectionBuffer;
    private static FloatBuffer vectorBuffer;
    private static Map<Integer, EnchantmentData> enchantmentMap;

    static {
        RenderUtil.mc = Minecraft.getMinecraft();
        RenderUtil.cameraFrustum = new Frustum();
        RenderUtil.viewportBuffer = GLAllocation.createDirectIntBuffer(16);
        RenderUtil.modelViewBuffer = GLAllocation.createDirectFloatBuffer(16);
        RenderUtil.projectionBuffer = GLAllocation.createDirectFloatBuffer(16);
        RenderUtil.vectorBuffer = GLAllocation.createDirectFloatBuffer(4);
        RenderUtil.enchantmentMap = new EnchantmentMap();
    }



    private static final float[] AA_BOUNDARY = new float[4];

    public static void drawRoundedRect(float x, float y, float x2, float y2, float radius, int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        radius = Math.max(0.0F, Math.min(radius, Math.min(x2 - x, y2 - y) / 2.0F));
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x + radius, y, 0).color(r, g, b, a).endVertex();
        wr.pos(x2 - radius, y, 0).color(r, g, b, a).endVertex();
        wr.pos(x2 - radius, y2, 0).color(r, g, b, a).endVertex();
        wr.pos(x + radius, y2, 0).color(r, g, b, a).endVertex();
        wr.pos(x, y + radius, 0).color(r, g, b, a).endVertex();
        wr.pos(x + radius, y + radius, 0).color(r, g, b, a).endVertex();
        wr.pos(x + radius, y2 - radius, 0).color(r, g, b, a).endVertex();
        wr.pos(x, y2 - radius, 0).color(r, g, b, a).endVertex();
        wr.pos(x2 - radius, y + radius, 0).color(r, g, b, a).endVertex();
        wr.pos(x2, y + radius, 0).color(r, g, b, a).endVertex();
        wr.pos(x2, y2 - radius, 0).color(r, g, b, a).endVertex();
        wr.pos(x2 - radius, y2 - radius, 0).color(r, g, b, a).endVertex();
        tessellator.draw();
        if (radius >= 0.1F) {
            drawArc(x + radius, y + radius, radius, 180, 270, color);
            drawArc(x2 - radius, y + radius, radius, 270, 360, color);
            drawArc(x + radius, y2 - radius, radius, 90, 180, color);
            drawArc(x2 - radius, y2 - radius, radius, 0, 90, color);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void drawArc(float cx, float cy, float r, int startAngle, int endAngle, int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float rc = (color >> 16 & 255) / 255.0F;
        float gc = (color >> 8 & 255) / 255.0F;
        float bc = (color & 255) / 255.0F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(cx, cy, 0).color(rc, gc, bc, a).endVertex();
        int steps = Math.max(12, Math.min(28, (int) (r * 3.0F)));
        int increment = Math.max(1, (endAngle - startAngle) / steps);
        for (int i = startAngle; i <= endAngle + increment; i += increment) {
            double rad = Math.toRadians(Math.min(i, endAngle));
            float x = (float) (cx + Math.cos(rad) * r);
            float y = (float) (cy + Math.sin(rad) * r);
            wr.pos(x, y, 0).color(rc, gc, bc, a).endVertex();
        }
        tessellator.draw();
    }

    public static void drawRoundedRectGradient(float x, float y, float x2, float y2, float radius, int topColor, int bottomColor) {
        drawRoundedRectStyled(x, y, x2, y2, radius, 1, topColor, bottomColor);
    }

    private static void drawRoundedRectStyled(float x, float y, float x2, float y2, float radius,
                                              int style, int c1, int c2) {
        float minX = Math.min(x, x2);
        float maxX = Math.max(x, x2);
        float minY = Math.min(y, y2);
        float maxY = Math.max(y, y2);
        float w = maxX - minX;
        float h = maxY - minY;
        if (w <= 0.0001F || h <= 0.0001F) {
            return;
        }
        float r = Math.max(0.0F, Math.min(radius, Math.min(w, h) / 2.0F));

        float feather = Math.min(w, h) <= 2.0F ? 0.15F : 0.5F;
        feather = Math.min(feather, Math.min(w, h) / 2.5F);

        float ix1 = minX + feather;
        float iy1 = minY + feather;
        float ix2 = maxX - feather;
        float iy2 = maxY - feather;
        float ir = Math.max(0.0F, r - feather);
        int steps = r < 0.05F ? 0 : Math.max(6, Math.min(48, (int) Math.ceil(r * 1.5F) + 3));
        int total = steps <= 0 ? 4 : 4 * (steps + 1);

        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cull) {
            GlStateManager.disableCull();
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();

        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        colorVertex(wr, (ix1 + ix2) / 2.0F, (iy1 + iy2) / 2.0F, style, c1, c2, minX, minY, maxX, maxY, 1.0F);
        for (int i = 0; i <= total; i++) {
            roundedBoundaryPoint(i == total ? 0 : i, ix1, iy1, ix2, iy2, ir, steps, AA_BOUNDARY);
            colorVertex(wr, AA_BOUNDARY[0], AA_BOUNDARY[1], style, c1, c2, minX, minY, maxX, maxY, 1.0F);
        }
        tessellator.draw();

        if (feather > 0.001F) {
            wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= total; i++) {
                roundedBoundaryPoint(i == total ? 0 : i, minX, minY, maxX, maxY, r, steps, AA_BOUNDARY);
                float bx = AA_BOUNDARY[0];
                float by = AA_BOUNDARY[1];
                float nx = AA_BOUNDARY[2] * feather;
                float ny = AA_BOUNDARY[3] * feather;
                colorVertex(wr, bx - nx, by - ny, style, c1, c2, minX, minY, maxX, maxY, 1.0F);
                colorVertex(wr, bx + nx, by + ny, style, c1, c2, minX, minY, maxX, maxY, 0.0F);
            }
            tessellator.draw();
        }

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        if (cull) {
            GlStateManager.enableCull();
        }
    }

    private static void roundedBoundaryPoint(int idx, float x1, float y1, float x2, float y2,
                                            float r, int steps, float[] out) {
        if (steps <= 0) {
            switch (idx) {
                case 0:
                    out[0] = x1; out[1] = y1; out[2] = -0.70710677F; out[3] = -0.70710677F;
                    break;
                case 1:
                    out[0] = x2; out[1] = y1; out[2] = 0.70710677F; out[3] = -0.70710677F;
                    break;
                case 2:
                    out[0] = x2; out[1] = y2; out[2] = 0.70710677F; out[3] = 0.70710677F;
                    break;
                default:
                    out[0] = x1; out[1] = y2; out[2] = -0.70710677F; out[3] = 0.70710677F;
                    break;
            }
            return;
        }
        int corner = idx / (steps + 1);
        int j = idx - corner * (steps + 1);
        double angle = Math.toRadians(180.0 + corner * 90.0 + 90.0 * j / (double) steps);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float cx = corner == 1 || corner == 2 ? x2 - r : x1 + r;
        float cy = corner == 0 || corner == 1 ? y1 + r : y2 - r;
        out[0] = cx + cos * r;
        out[1] = cy + sin * r;
        out[2] = cos;
        out[3] = sin;
    }

    private static void colorVertex(WorldRenderer wr, float x, float y, int style, int c1, int c2,
                                    float gx1, float gy1, float gx2, float gy2, float mul) {
        float t = 0.0F;
        if (style == 1) {
            t = clamp01((y - gy1) / Math.max(0.0001F, gy2 - gy1));
        } else if (style == 2) {
            t = clamp01((x - gx1) / Math.max(0.0001F, gx2 - gx1));
        }
        float a = ((c1 >>> 24 & 255) + t * ((c2 >>> 24 & 255) - (c1 >>> 24 & 255))) / 255.0F * mul;
        float r = ((c1 >> 16 & 255) + t * ((c2 >> 16 & 255) - (c1 >> 16 & 255))) / 255.0F;
        float g = ((c1 >> 8 & 255) + t * ((c2 >> 8 & 255) - (c1 >> 8 & 255))) / 255.0F;
        float b = ((c1 & 255) + t * ((c2 & 255) - (c1 & 255))) / 255.0F;
        wr.pos(x, y, 0).color(r, g, b, a).endVertex();
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    public static void drawZenGlass(float x1, float y1, float x2, float y2, float radius, float alpha) {
        drawRoundedRectWithGl(x1, y1, x2, y2, radius, new Color(12, 14, 20, (int) (44.0F * alpha)).getRGB());
        drawRoundedRectWithGl(x1, y1, x2, y2, radius, new Color(255, 255, 255, (int) (12.0F * alpha)).getRGB());
    }

    public static void drawArcRing(float cx, float cy, float radius, float thickness, float startDeg, float sweepDeg, int color) {
        if (sweepDeg <= 0.0F || thickness <= 0.0F) return;
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        float inner = Math.max(0.0F, radius - thickness / 2.0F);
        float outer = radius + thickness / 2.0F;
        int segments = Math.max(3, (int) (Math.abs(sweepDeg) / 6.0F) + 1);
        enableRenderState();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(startDeg + sweepDeg * i / (double) segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            wr.pos(cx + cos * outer, cy + sin * outer, 0).color(r, g, b, a).endVertex();
            wr.pos(cx + cos * inner, cy + sin * inner, 0).color(r, g, b, a).endVertex();
        }
        tessellator.draw();
        disableRenderState();
    }

    public static void drawRoundedRectGradientH(float x, float y, float x2, float y2, float radius, int leftColor, int rightColor) {
        drawRoundedRectStyled(x, y, x2, y2, radius, 2, leftColor, rightColor);
    }

    public static void drawGlass(float x1, float y1, float x2, float y2, float radius, float alpha) {
        drawRoundedRectWithGl(x1 + 0.5F, y1 + 1.8F, x2 + 0.5F, y2 + 1.8F, radius,
                new Color(8, 10, 16, (int) (42.0F * alpha)).getRGB());
        drawRoundedRectGradient(x1, y1, x2, y2, radius,
                new Color(255, 255, 255, (int) (204.0F * alpha)).getRGB(),
                new Color(226, 232, 244, (int) (116.0F * alpha)).getRGB());
        drawRoundedRectWithGl(x1 + radius * 0.7F, y1 + 0.7F, x2 - radius * 0.7F, y1 + 1.5F, 0.4F,
                new Color(255, 255, 255, (int) (120.0F * alpha)).getRGB());
    }

    public static int interpolateColor(int c1, int c2, float fraction) {
        int a1 = (c1 >> 24 & 255), a2 = (c2 >> 24 & 255);
        int r1 = (c1 >> 16 & 255), r2 = (c2 >> 16 & 255);
        int g1 = (c1 >> 8 & 255),  g2 = (c2 >> 8 & 255);
        int b1 = (c1 & 255),       b2 = (c2 & 255);
        return ((int)(a1 + (a2 - a1) * fraction) << 24) |
                ((int)(r1 + (r2 - r1) * fraction) << 16) |
                ((int)(g1 + (g2 - g1) * fraction) << 8)  |
                (int)(b1 + (b2 - b1) * fraction);
    }
    private static ChatColors getColorForLevel(int currentLevel, int maxLevel) {
        if (currentLevel > maxLevel) {
            return ChatColors.LIGHT_PURPLE;
        }
        if (currentLevel == maxLevel) {
            return ChatColors.RED;
        }
        switch (currentLevel) {
            case 1: {
                return ChatColors.AQUA;
            }
            case 2: {
                return ChatColors.GREEN;
            }
            case 3: {
                return ChatColors.YELLOW;
            }
            case 4: {
                return ChatColors.GOLD;
            }
        }
        return ChatColors.GRAY;
    }
    public static void drawOutlinedString(String text, float x, float y) {
        String string2 = text.replaceAll("(?i)§[\\da-f]", "");
        FontManager.drawString(string2, x + 1.0f, y, 0, false);
        FontManager.drawString(string2, x - 1.0f, y, 0, false);
        FontManager.drawString(string2, x, y + 1.0f, 0, false);
        FontManager.drawString(string2, x, y - 1.0f, 0, false);
        FontManager.drawString(text, x, y, -1, false);
    }

    public static void renderEnchantmentText(ItemStack itemStack, float x, float y, float scale) {
        NBTTagList nBTTagList;
        nBTTagList = itemStack.getItem() == Items.enchanted_book ? Items.enchanted_book.getEnchantments(itemStack) : itemStack.getEnchantmentTagList();
        if (nBTTagList != null) {
            for (int i = 0; i < nBTTagList.tagCount(); ++i) {
                EnchantmentData enchantmentData = enchantmentMap.get(nBTTagList.getCompoundTagAt(i).getInteger("id"));
                if (enchantmentData == null) {
                    continue;
                }
                short s = nBTTagList.getCompoundTagAt(i).getShort("lvl");
                ChatColors chatColors = RenderUtil.getColorForLevel(s, enchantmentData.maxLevel);
                RenderUtil.drawOutlinedString(ChatColors.formatColor(String.format("&r%s%s%d&r", enchantmentData.shortName, chatColors, (int) s)), x * (1.0f / scale), (y + (float) i * 4.0f) * (1.0f / scale));
            }
        }
    }

    public static void renderItemInGUI(ItemStack itemStack, int x, int y) {
        renderItemInGUI(itemStack, x, y, true);
    }

    public static void renderItemInGUI(ItemStack itemStack, int x, int y, boolean showEnchantments) {
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.clear(256);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        RenderUtil.mc.getRenderItem().zLevel = -150.0f;
        mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, x, y);
        mc.getRenderItem().renderItemOverlays(RenderUtil.mc.fontRendererObj, itemStack, x, y);
        RenderUtil.mc.getRenderItem().zLevel = 0.0f;
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        if (showEnchantments) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5f, 0.5f, 0.5f);
            GlStateManager.disableDepth();
            RenderUtil.renderEnchantmentText(itemStack, x, y, 0.5f);
            GlStateManager.enableDepth();
            GlStateManager.scale(2.0f, 2.0f, 2.0f);
            GlStateManager.popMatrix();
        }
    }

    public static void renderPotionEffect(PotionEffect potionEffect, int x, int y) {
        int n3 = Potion.potionTypes[potionEffect.getPotionID()].getStatusIconIndex();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.clear(256);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
        Gui.drawModalRectWithCustomSizedTexture(x, y, n3 % 8 * 18, 198 + n3 / 8 * 18, 18, 18, 256.0f, 256.0f);
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public static void drawRect(float x1, float y1, float x2, float y2, int color) {
        if (color == 0) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        RenderUtil.setColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }

    public static void drawRect3D(float x1, float y1, float x2, float y2, int color) {
        if (color == 0) {
            return;
        }
        RenderUtil.setColor(color);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_POLYGON);
        for (int i = 0; i < 2; ++i) {
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x1, y2);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x2, y1);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }

    public static void drawOutlineRect(float x1, float y1, float x2, float y2, float lineWidth, int backgroundColor, int lineColor) {
        RenderUtil.drawRect(0.0f, 0.0f, x2, 27.0f, backgroundColor);
        if (lineColor == 0) {
            return;
        }
        RenderUtil.setColor(lineColor);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawLine(float x1, float y1, float x2, float y2, float lineWidth, int color) {
        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawLine3D(Vec3 start, double endX, double endY, double endZ, float red, float green, float blue, float alpha, float lineWidth) {
        GlStateManager.pushMatrix();
        GlStateManager.color(red, green, blue, alpha);
        boolean bl = RenderUtil.mc.gameSettings.viewBobbing;
        RenderUtil.mc.gameSettings.viewBobbing = false;
        ((IAccessorEntityRenderer) RenderUtil.mc.entityRenderer).callSetupCameraTransform(((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks, 2);
        RenderUtil.mc.gameSettings.viewBobbing = bl;
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(start.xCoord, start.yCoord, start.zCoord);
        GL11.glVertex3d(endX - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), endY - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), endZ - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    public static void drawArrow(float centerX, float centerY, float angle, float length, float lineWidth, int color) {
        float f6 = angle + (float) Math.toRadians(45.0);
        float f7 = angle - (float) Math.toRadians(45.0);
        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f6), centerY + length * (float) Math.sin(f6));
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f7), centerY + length * (float) Math.sin(f7));
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawTriangle(float centerX, float centerY, float angle, float length, int color) {
        float f5 = angle + (float) Math.toRadians(26.25);
        float f6 = angle - (float) Math.toRadians(26.25);
        RenderUtil.setColor(color);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(9);
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f5), centerY + length * (float) Math.sin(f5));
        GL11.glVertex2f(centerX + length * (float) Math.cos(f6), centerY + length * (float) Math.sin(f6));
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }

    public static void drawFilledTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        if (color == 0) return;
        setColor(color);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }

    public static void drawTriangleOutline(float x1, float y1, float x2, float y2, float x3, float y3, float lineWidth, int color) {
        if (color == 0) return;
        setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }
    public static void drawGradientTriangle(float tipX, float tipY, float leftX, float leftY, float rightX, float rightY, int tipColor, int baseColor) {
        float ta = (tipColor >> 24 & 0xFF) / 255.0F;
        float tr = (tipColor >> 16 & 0xFF) / 255.0F;
        float tg = (tipColor >> 8 & 0xFF) / 255.0F;
        float tb = (tipColor & 0xFF) / 255.0F;
        float ba = (baseColor >> 24 & 0xFF) / 255.0F;
        float br = (baseColor >> 16 & 0xFF) / 255.0F;
        float bg = (baseColor >> 8 & 0xFF) / 255.0F;
        float bb = (baseColor & 0xFF) / 255.0F;
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glColor4f(tr, tg, tb, ta);
        GL11.glVertex2f(tipX, tipY);
        GL11.glColor4f(br, bg, bb, ba);
        GL11.glVertex2f(leftX, leftY);
        GL11.glColor4f(br, bg, bb, ba);
        GL11.glVertex2f(rightX, rightY);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }
    public static void drawTriangleProgressBorder(
            float x0, float y0, float x1, float y1, float x2, float y2,
            float progress, float lineWidth, int filledColor, int emptyColor) {

        float e0 = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        float e1 = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        float e2 = (float) Math.sqrt((x0 - x2) * (x0 - x2) + (y0 - y2) * (y0 - y2));
        float total = e0 + e1 + e2;
        float filled = total * Math.min(Math.max(progress, 0.0F), 1.0F);

        float[] px = {x0, x1, x2};
        float[] py = {y0, y1, y2};
        float[] lens = {e0, e1, e2};

        float remaining = filled;
        float cx = x0, cy = y0;

        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (int i = 0; i < 3; i++) {
            float nx = px[(i + 1) % 3];
            float ny = py[(i + 1) % 3];

            if (remaining <= 0.0F) {
                setColor(emptyColor);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(cx, cy);
                GL11.glVertex2f(nx, ny);
                GL11.glEnd();
            } else if (remaining >= lens[i]) {
                setColor(filledColor);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(cx, cy);
                GL11.glVertex2f(nx, ny);
                GL11.glEnd();
                remaining -= lens[i];
            } else {
                float t = remaining / lens[i];
                float ix = cx + (nx - cx) * t;
                float iy = cy + (ny - cy) * t;
                setColor(filledColor);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(cx, cy);
                GL11.glVertex2f(ix, iy);
                GL11.glEnd();
                setColor(emptyColor);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(ix, iy);
                GL11.glVertex2f(nx, ny);
                GL11.glEnd();
                remaining = 0.0F;
            }
            cx = nx;
            cy = ny;
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);
        GlStateManager.resetColor();
    }

    public static void drawFramebuffer(Framebuffer framebuffer) {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(framebuffer.framebufferTexture);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(0.0, 1.0);
        GL11.glVertex2d(0.0, 0.0);
        GL11.glTexCoord2d(0.0, 0.0);
        GL11.glVertex2d(0.0, scaledResolution.getScaledHeight());
        GL11.glTexCoord2d(1.0, 0.0);
        GL11.glVertex2d(scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight());
        GL11.glTexCoord2d(1.0, 1.0);
        GL11.glVertex2d(scaledResolution.getScaledWidth(), 0.0);
        GL11.glEnd();
        GlStateManager.bindTexture(0);
    }

    public static void fillCircle(double x, double y, double radius, int segments, int color) {
        if (radius <= 0.0D) {
            return;
        }
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        float feather = (float) Math.min(0.5F, radius / 2.5D);
        float inner = (float) radius - feather;
        int steps = Math.max(segments, Math.min(96, 8 + (int) Math.ceil(radius * 3.0D)));

        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cull) {
            GlStateManager.disableCull();
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        RenderUtil.setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(x, y);
        for (int i = 0; i <= steps; i++) {
            double angle = i * (Math.PI * 2.0 / steps);
            GL11.glVertex2d(x + Math.cos(angle) * inner, y + Math.sin(angle) * inner);
        }
        GL11.glEnd();

        if (feather > 0.02F) {
            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= steps; i++) {
                double angle = i * (Math.PI * 2.0 / steps);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);

                GlStateManager.color(r, g, b, a);
                GL11.glVertex2d(x + cos * inner, y + sin * inner);
                GlStateManager.color(r, g, b, 0.0F);
                GL11.glVertex2d(x + cos * radius, y + sin * radius);
            }
            GL11.glEnd();
        }

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
        if (cull) {
            GlStateManager.enableCull();
        }
    }

    public static void drawCircle(double centerX, double centerY, double centerZ, double radius, int segments, int color) {
        RenderUtil.setColor(color);
        GL11.glLineWidth(3.0f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= segments; ++i) {
            double d5 = (double) i * (Math.PI * 2 / (double) segments);
            GL11.glVertex3d(centerX + Math.cos(d5) * radius, centerY, centerZ + Math.sin(d5) * radius);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawEntityCircle(Entity entity, double radius, int segments, int color) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        RenderUtil.drawCircle(d2, d3, d4, radius, segments, color);
    }

    public static void drawFilledBox(AxisAlignedBB axisAlignedBB, int red, int green, int blue) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        tessellator.draw();
    }

    public static void drawBoundingBox(AxisAlignedBB axisAlignedBB, int red, int green, int blue, int alpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        RenderGlobal.drawOutlinedBoundingBox(axisAlignedBB, red, green, blue, alpha);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
    }

    public static void drawEntityBox(Entity entity, int red, int green, int blue) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
        RenderUtil.drawFilledBox(entity.getEntityBoundingBox().expand(0.1f, 0.1f, 0.1f).offset(d2 - entity.posX, d3 - entity.posY, d4 - entity.posZ).offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), red, green, blue);
    }

    public static void drawEntityBoundingBox(Entity entity, int red, int green, int blue, int alpha, float lineWidth, double expand) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
        RenderUtil.drawBoundingBox(entity.getEntityBoundingBox().expand(expand, expand, expand).offset(d2 - entity.posX, d3 - entity.posY, d4 - entity.posZ).offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), red, green, blue, alpha, lineWidth);
    }

    public static void drawBlockBox(BlockPos blockPos, double height, int red, int green, int blue) {
        RenderUtil.drawFilledBox(new AxisAlignedBB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), (double) blockPos.getX() + 1.0, (double) blockPos.getY() + height, (double) blockPos.getZ() + 1.0).offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), red, green, blue);
    }

    public static void drawBlockBoundingBox(BlockPos blockPos, double height, int red, int green, int blue, int alpha, float lineWidth) {
        RenderUtil.drawBoundingBox(new AxisAlignedBB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), (double) blockPos.getX() + 1.0, (double) blockPos.getY() + height, (double) blockPos.getZ() + 1.0).offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), red, green, blue, alpha, lineWidth);
    }

    public static void drawCornerESP(EntityPlayer entity, float red, float green, float blue) {
        float x = (float) (RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX());
        float y = (float) (RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY());
        float z = (float) (RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height / 2.0F, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-0.098F, -0.098F, 0.098F);
        float width = (float) (26.6 * entity.width / 2.0);
        float height = 12.0F;
        GlStateManager.color(red, green, blue);
        draw3DRect(width, height - 1.0F, width - 4.0F, height);
        draw3DRect(-width, height - 1.0F, -width + 4.0F, height);
        draw3DRect(-width, height, -width + 1.0F, height - 4.0F);
        draw3DRect(width, height, width - 1.0F, height - 4.0F);
        draw3DRect(width, -height, width - 4.0F, -height + 1.0F);
        draw3DRect(-width, -height, -width + 4.0F, -height + 1.0F);
        draw3DRect(-width, -height + 1.0F, -width + 1.0F, -height + 4.0F);
        draw3DRect(width, -height + 1.0F, width - 1.0F, -height + 4.0F);
        GlStateManager.color(0.0F, 0.0F, 0.0F);
        draw3DRect(width, height, width - 4.0F, height + 0.2F);
        draw3DRect(-width, height, -width + 4.0F, height + 0.2F);
        draw3DRect(-width - 0.2F, height + 0.2F, -width, height - 4.0F);
        draw3DRect(width + 0.2F, height + 0.2F, width, height - 4.0F);
        draw3DRect(width + 0.2F, -height, width - 4.0F, -height - 0.2F);
        draw3DRect(-width - 0.2F, -height, -width + 4.0F, -height - 0.2F);
        draw3DRect(-width - 0.2F, -height, -width, -height + 4.0F);
        draw3DRect(width + 0.2F, -height, width, -height + 4.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public static void drawFake2DESP(EntityPlayer entity, float red, float green, float blue) {
        float x = (float) (RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX());
        float y = (float) (RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY());
        float z = (float) (RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height / 2.0F, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-0.1F, -0.1F, 0.1F);
        GlStateManager.color(red, green, blue);
        float width = (float) (23.3 * entity.width / 2.0);
        float height = 12.0F;
        draw3DRect(width, height, -width, height + 0.4F);
        draw3DRect(width, -height, -width, -height + 0.4F);
        draw3DRect(width, -height + 0.4F, width - 0.4F, height + 0.4F);
        draw3DRect(-width, -height + 0.4F, -width + 0.4F, height + 0.4F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public static void draw3DRect(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }

    public static Vector4d projectToScreen(Entity entity, double screenScale) {
        Vector4d vector4d;
        {
            double d3 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
            double d4 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
            double d5 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, ((IAccessorMinecraft) RenderUtil.mc).getTimer().renderPartialTicks);
            AxisAlignedBB axisAlignedBB = entity.getEntityBoundingBox().expand(0.1f, 0.1f, 0.1f).offset(d3 - entity.posX, d4 - entity.posY, d5 - entity.posZ);
            vector4d = null;
            for (Vector3d vector3d : new Vector3d[]{new Vector3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vector3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vector3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vector3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vector3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vector3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ), new Vector3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vector3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ)}) {
                GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
                GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
                GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
                if (!GLU.gluProject((float) (vector3d.x - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX()), (float) (vector3d.y - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()), (float) (vector3d.z - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), modelViewBuffer, projectionBuffer, viewportBuffer, vectorBuffer))
                    continue;
                vector3d = new Vector3d((double) vectorBuffer.get(0) / screenScale, (double) ((float) Display.getHeight() - vectorBuffer.get(1)) / screenScale, vectorBuffer.get(2));
                if (!(vector3d.z >= 0.0) || !(vector3d.z < 1.0)) continue;
                if (vector4d == null) {
                    vector4d = new Vector4d(vector3d.x, vector3d.y, vector3d.z, 0.0);
                }
                vector4d.x = Math.min(vector3d.x, vector4d.x);
                vector4d.y = Math.min(vector3d.y, vector4d.y);
                vector4d.z = Math.max(vector3d.x, vector4d.z);
                vector4d.w = Math.max(vector3d.y, vector4d.w);
            }
        }
        return vector4d;
    }

    public static boolean isInViewFrustum(AxisAlignedBB axisAlignedBB, double expand) {
        cameraFrustum.setPosition(RenderUtil.mc.getRenderViewEntity().posX, RenderUtil.mc.getRenderViewEntity().posY, RenderUtil.mc.getRenderViewEntity().posZ);
        return cameraFrustum.isBoundingBoxInFrustum(axisAlignedBB.expand(expand, expand, expand));
    }
    public static void drawRoundedRectWithGl(float x1, float y1, float x2, float y2, float radius, int color) {
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRect(x1, y1, x2, y2, radius, color);
        RenderUtil.disableRenderState();
    }
    public static void enableRenderState() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
    }

    public static void disableRenderState() {
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void setColor(int argb) {
        float f = (float) (argb >> 24 & 0xFF) / 255.0f;
        float f2 = (float) (argb >> 16 & 0xFF) / 255.0f;
        float f3 = (float) (argb >> 8 & 0xFF) / 255.0f;
        float f4 = (float) (argb & 0xFF) / 255.0f;
        GlStateManager.color(f2, f3, f4, f);
    }

    public static float lerpFloat(float current, float previous, float t) {
        return previous + (current - previous) * t;
    }

    public static double lerpDouble(double current, double previous, double t) {
        return previous + (current - previous) * t;
    }

    public static final class EnchantmentData {
        public final String shortName;
        public final int maxLevel;

        public EnchantmentData(String shortName, int maxLevel) {
            this.shortName = shortName;
            this.maxLevel = maxLevel;
        }
    }

    static final class EnchantmentMap extends HashMap<Integer, EnchantmentData> {
        EnchantmentMap() {
            this.put(0, new EnchantmentData("Pr", 4));
            this.put(1, new EnchantmentData("Fp", 4));
            this.put(2, new EnchantmentData("Ff", 4));
            this.put(3, new EnchantmentData("Bp", 4));
            this.put(4, new EnchantmentData("Pp", 4));
            this.put(5, new EnchantmentData("Re", 3));
            this.put(6, new EnchantmentData("Aq", 1));
            this.put(7, new EnchantmentData("Th", 3));
            this.put(8, new EnchantmentData("Ds", 3));
            this.put(16, new EnchantmentData("Sh", 5));
            this.put(17, new EnchantmentData("Sm", 5));
            this.put(18, new EnchantmentData("BoA", 5));
            this.put(19, new EnchantmentData("Kb", 2));
            this.put(20, new EnchantmentData("Fa", 2));
            this.put(21, new EnchantmentData("Lo", 3));
            this.put(32, new EnchantmentData("Ef", 5));
            this.put(33, new EnchantmentData("St", 1));
            this.put(34, new EnchantmentData("Ub", 3));
            this.put(35, new EnchantmentData("Fo", 3));
            this.put(48, new EnchantmentData("Po", 5));
            this.put(49, new EnchantmentData("Pu", 2));
            this.put(50, new EnchantmentData("Fl", 1));
            this.put(51, new EnchantmentData("Inf", 1));
            this.put(61, new EnchantmentData("LoS", 3));
            this.put(62, new EnchantmentData("Lu", 3));
        }
    }

    public static void setAlphaLimit(float limit) {
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, (float) (limit * .01));
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer) {
        return createFrameBuffer(framebuffer, false);
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer, boolean depth) {
        if (framebuffer == null || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, depth);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return framebuffer;
    }

    public static void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }
}
