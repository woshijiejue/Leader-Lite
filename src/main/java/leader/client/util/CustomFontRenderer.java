package leader.client.util;

import leader.mixin.FontRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class CustomFontRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final boolean antiAlias;
    private final byte[][] charWidths = new byte[256][];
    private final int[] textures = new int[256];
    private final FontRenderContext context;
    private Font font;
    private int fontWidth;
    private int fontHeight;
    private int textureWidth;
    private int textureHeight;

    public CustomFontRenderer(String resourcePath, float size, boolean antiAlias) {
        this.antiAlias = antiAlias;
        Arrays.fill(textures, -1);
        try {
            InputStream is = CustomFontRenderer.class.getResourceAsStream(resourcePath);
            if (is != null) {
                font = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(size);
                is.close();
            }
        } catch (Exception ignored) {
        }
        if (font == null) {
            font = new Font("SansSerif", Font.PLAIN, (int) size);
        }
        context = new FontRenderContext(font.getTransform(), antiAlias, antiAlias);
        Rectangle2D maxBounds = font.getMaxCharBounds(context);
        this.fontWidth = (int) Math.ceil(maxBounds.getWidth());
        this.fontHeight = (int) Math.ceil(maxBounds.getHeight());
        this.textureWidth = nextPowerOfTwo(fontWidth * 16);
        this.textureHeight = nextPowerOfTwo(fontHeight * 16);
    }

    public CustomFontRenderer(Font font, boolean antiAlias) {
        this.antiAlias = antiAlias;
        this.font = font;
        Arrays.fill(textures, -1);
        context = new FontRenderContext(font.getTransform(), antiAlias, antiAlias);
        Rectangle2D maxBounds = font.getMaxCharBounds(context);
        this.fontWidth = (int) Math.ceil(maxBounds.getWidth());
        this.fontHeight = (int) Math.ceil(maxBounds.getHeight());
        this.textureWidth = nextPowerOfTwo(fontWidth * 16);
        this.textureHeight = nextPowerOfTwo(fontHeight * 16);
    }

    public void drawString(String text, float x, float y, int color) {
        drawString(text, x, y, color, false);
    }

    public void drawStringWithShadow(String text, float x, float y, int color) {
        drawString(text, x + 0.5F, y + 0.5F, color, true);
        drawString(text, x, y, color, false);
    }

    public void drawCenteredString(String text, float x, float y, int color) {
        drawString(text, x - getStringWidth(text) / 2.0F, y, color);
    }

    public void drawString(String text, float x, float y, int color, boolean darken) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        x *= 2.0F;
        y *= 2.0F;
        y -= 2.0F;
        if (darken) {
            color = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
        }
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;
        float a = (float) (color >> 24 & 0xFF) / 255.0F;
        if (a == 0.0F) a = 1.0F;
        GlStateManager.color(r, g, b, a);
        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        int[] mcColors = ((FontRendererAccessor) mc.fontRendererObj).getColorCode();
        char[] chars = text.toCharArray();
        int offset = 0;
        for (int i = 0; i < chars.length; i++) {
            char chr = chars[i];
            if (chr == '\u00a7' && i != chars.length - 1) {
                i++;
                int colorIndex = "0123456789abcdef".indexOf(chars[i]);
                if (colorIndex != -1) {
                    if (darken) colorIndex |= 0x10;
                    int mcColor = mcColors[colorIndex];
                    r = (float) (mcColor >> 16 & 0xFF) / 255.0F;
                    g = (float) (mcColor >> 8 & 0xFF) / 255.0F;
                    b = (float) (mcColor & 0xFF) / 255.0F;
                    GlStateManager.color(r, g, b, a);
                }
                continue;
            }
            offset += drawChar(chr, x + offset, y);
        }
        GL11.glPopMatrix();
    }

    public int drawStringInternal(String text, float posX, float posY, int color, boolean shadowColors) {
        drawString(text, posX, posY, color, shadowColors);
        return (int) posX;
    }

    private int drawChar(char chr, float x, float y) {
        int region = chr >> 8;
        int id = chr & 0xFF;
        int xTexCoord = (id & 0xF) * fontWidth;
        int yTexCoord = (id >> 4) * fontHeight;
        int width = getOrGenerateCharWidthMap(region)[id] & 0xFF;
        GlStateManager.bindTexture(getOrGenerateCharTexture(region));
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(wrapTexCoord(xTexCoord, textureWidth), wrapTexCoord(yTexCoord, textureHeight));
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2d(wrapTexCoord(xTexCoord, textureWidth), wrapTexCoord(yTexCoord + fontHeight, textureHeight));
        GL11.glVertex2f(x, y + fontHeight);
        GL11.glTexCoord2d(wrapTexCoord(xTexCoord + width, textureWidth), wrapTexCoord(yTexCoord + fontHeight, textureHeight));
        GL11.glVertex2f(x + width, y + fontHeight);
        GL11.glTexCoord2d(wrapTexCoord(xTexCoord + width, textureWidth), wrapTexCoord(yTexCoord, textureHeight));
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        return width;
    }

    public int getStringWidth(String text) {
        if (text == null) return 0;
        int width = 0;
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char chr = chars[i];
            if (chr == '\u00a7') {
                i++;
            } else {
                width += getOrGenerateCharWidthMap(chr >> 8)[chr & 0xFF] & 0xFF;
            }
        }
        return width / 2;
    }

    public float getFontHeight() {
        return fontHeight / 2.0F;
    }

    public int getHeight() {
        return fontHeight / 2;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font, boolean antiAlias) {
        this.font = font;
        Arrays.fill(textures, -1);
        Arrays.fill(charWidths, null);
        Rectangle2D maxBounds = font.getMaxCharBounds(context);
        this.fontWidth = (int) Math.ceil(maxBounds.getWidth());
        this.fontHeight = (int) Math.ceil(maxBounds.getHeight());
        this.textureWidth = nextPowerOfTwo(fontWidth * 16);
        this.textureHeight = nextPowerOfTwo(fontHeight * 16);
    }

    public void setFont(String resourcePath, float size, boolean antiAlias) {
        Font newFont = null;
        try {
            InputStream is = CustomFontRenderer.class.getResourceAsStream(resourcePath);
            if (is != null) {
                newFont = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(size);
                is.close();
            }
        } catch (Exception ignored) {
        }
        if (newFont != null) {
            setFont(newFont, antiAlias);
        }
    }

    private int generateCharTexture(int id) {
        int textureId = GL11.glGenTextures();
        int offset = id << 8;
        BufferedImage img = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        if (antiAlias) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                String chr = String.valueOf((char) ((y << 4 | x) | offset));
                g.drawString(chr, x * fontWidth, y * fontHeight + fm.getAscent());
            }
        }
        g.dispose();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        int[] pixels = img.getRGB(0, 0, textureWidth, textureHeight, null, 0, textureWidth);
        ByteBuffer buf = ByteBuffer.allocateDirect(pixels.length * 4).order(ByteOrder.nativeOrder());
        for (int pixel : pixels) {
            buf.put((byte) ((pixel >> 16) & 0xFF));
            buf.put((byte) ((pixel >> 8) & 0xFF));
            buf.put((byte) (pixel & 0xFF));
            buf.put((byte) ((pixel >> 24) & 0xFF));
        }
        buf.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, textureWidth, textureHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GlStateManager.bindTexture(0);
        return textureId;
    }

    private int getOrGenerateCharTexture(int id) {
        if (textures[id] == -1) {
            return textures[id] = generateCharTexture(id);
        }
        return textures[id];
    }

    private byte[] generateCharWidthMap(int id) {
        int offset = id << 8;
        byte[] widthMap = new byte[256];
        for (int i = 0; i < widthMap.length; i++) {
            widthMap[i] = (byte) Math.ceil(font.getStringBounds(String.valueOf((char) (i | offset)), context).getWidth());
        }
        return widthMap;
    }

    private byte[] getOrGenerateCharWidthMap(int id) {
        if (charWidths[id] == null) {
            return charWidths[id] = generateCharWidthMap(id);
        }
        return charWidths[id];
    }

    private static double wrapTexCoord(int coord, int size) {
        return (double) coord / (double) size;
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    public void dispose() {
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] != -1) {
                GL11.glDeleteTextures(textures[i]);
                textures[i] = -1;
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
    }
}
