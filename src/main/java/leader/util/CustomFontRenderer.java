package leader.util;

import leader.mixin.FontRendererAccessor;
import leader.module.modules.render.BetterFPS;
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
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;

public class CustomFontRenderer {

    private static final String[] FALLBACK_FONT_NAMES = {
            "Microsoft YaHei", "Microsoft YaHei UI", "Microsoft JhengHei", "SimSun", "NSimSun",
            "Segoe UI Emoji", "Segoe UI Symbol", "Arial Unicode MS", "Noto Sans CJK SC",
            "WenQuanYi Micro Hei", "PingFang SC", "SansSerif"
    };

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final boolean antiAlias;
    private final byte[][] charWidths = new byte[256][];
    private final int[] textures = new int[256];
    private final Map<Integer, CodePointGlyph> glyphCache = new HashMap<>();
    private final Map<Integer, Font> fallbackFontCache = new HashMap<>();
    private final Map<String, TextLayout> textLayouts = new LinkedHashMap<String, TextLayout>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TextLayout> eldest) { return size() > 256; }
    };
    private final FontRenderContext context;
    private Font font;
    private int fontWidth;
    private int fontHeight;
    private int textureWidth;
    private int textureHeight;
    private int ascent = -1;
    private float capHeight = -1.0F;

    private static class CodePointGlyph {
        final int textureId;
        final int width;
        final int height;

        CodePointGlyph(int textureId, int width, int height) {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
        }
    }

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
            font = new Font("SansSerif", Font.PLAIN, Math.max(1, Math.round(size)));
        }
        context = new FontRenderContext(font.getTransform(), antiAlias, antiAlias);
        Rectangle2D maxBounds = font.getMaxCharBounds(context);
        this.fontWidth = Math.max(1, (int) Math.ceil(maxBounds.getWidth()));
        this.fontHeight = Math.max(1, (int) Math.ceil(maxBounds.getHeight()));
        this.textureWidth = nextPowerOfTwo(fontWidth * 16);
        this.textureHeight = nextPowerOfTwo(fontHeight * 16);
    }

    public CustomFontRenderer(Font font, boolean antiAlias) {
        this.antiAlias = antiAlias;
        this.font = font;
        Arrays.fill(textures, -1);
        context = new FontRenderContext(font.getTransform(), antiAlias, antiAlias);
        Rectangle2D maxBounds = font.getMaxCharBounds(context);
        this.fontWidth = Math.max(1, (int) Math.ceil(maxBounds.getWidth()));
        this.fontHeight = Math.max(1, (int) Math.ceil(maxBounds.getHeight()));
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
        if (text == null || text.isEmpty()) {
            return;
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean matrix = false;
        try {
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
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
            matrix = true;
            GL11.glScaled(0.5, 0.5, 0.5);
            int[] mcColors = ((FontRendererAccessor) mc.fontRendererObj).getColorCode();
            if (BetterFPS.optimizedHUD()) {
                drawBatched(text, x, y, r, g, b, a, darken, mcColors);
                return;
            }
            int offset = 0;
            int i = 0;
            int len = text.length();
            while (i < len) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == '\u00a7' && i < len) {
                    int ci = text.codePointAt(i);
                    i += Character.charCount(ci);
                    int colorIndex = "0123456789abcdef".indexOf(ci);
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
                offset += drawChar(cp, x + offset, y);
            }
        } finally {
            if (matrix) {
                GL11.glPopMatrix();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.bindTexture(0);
            if (depth && !GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) {
                GlStateManager.enableDepth();
            } else if (!depth && GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) {
                GlStateManager.disableDepth();
            }
            if (blend && !GL11.glIsEnabled(GL11.GL_BLEND)) {
                GlStateManager.enableBlend();
            } else if (!blend && GL11.glIsEnabled(GL11.GL_BLEND)) {
                GlStateManager.disableBlend();
            }
            if (texture && !GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
                GlStateManager.enableTexture2D();
            } else if (!texture && GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
                GlStateManager.disableTexture2D();
            }
        }
    }

    public int drawStringInternal(String text, float posX, float posY, int color, boolean shadowColors) {
        drawString(text, posX, posY, color, shadowColors);
        return (int) posX;
    }

    private static final class LayoutGlyph {
        int texture, width, height, offset, colorIndex = -1;
        float u1, v1, u2, v2;
    }

    private static final class TextLayout {
        final LayoutGlyph[] glyphs;
        TextLayout(List<LayoutGlyph> glyphs) { this.glyphs = glyphs.toArray(new LayoutGlyph[0]); }
    }

    private void drawBatched(String text, float x, float y, float red, float green, float blue, float alpha,
                             boolean darken, int[] colors) {
        if (BetterFPS.cachedTextLayout() && text.length() <= 256) {
            drawCachedLayout(text, x, y, red, green, blue, alpha, darken, colors);
            return;
        }
        // Texture uploads must happen BEFORE glBegin; warm only the glyphs actually used.
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\u00a7') {
                if (i < text.length()) i += Character.charCount(text.codePointAt(i));
                continue;
            }
            if (cp > 0xFFFF || !font.canDisplay(cp)) getOrGenerateGlyph(cp);
            else {
                getOrGenerateCharWidthMap(cp >> 8);
                getOrGenerateCharTexture(cp >> 8);
            }
        }
        GlStateManager.color(red, green, blue, alpha);
        int activeTexture = -1;
        boolean drawing = false;
        int offset = 0;
        try {
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == '\u00a7' && i < text.length()) {
                    int code = text.codePointAt(i);
                    i += Character.charCount(code);
                    int index = "0123456789abcdef".indexOf(code);
                    if (index >= 0) {
                        if (darken) index |= 0x10;
                        int color = colors[index];
                        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                                (color & 255) / 255.0F, alpha);
                    }
                    continue;
                }
                int texture, width, height;
                float u1, v1, u2, v2;
                if (cp > 0xFFFF || !font.canDisplay(cp)) {
                    CodePointGlyph glyph = getOrGenerateGlyph(cp);
                    texture = glyph.textureId; width = glyph.width; height = glyph.height;
                    u1 = v1 = 0;
                    u2 = (float) width / nextPowerOfTwo(width);
                    v2 = (float) height / nextPowerOfTwo(height);
                } else {
                    int id = cp & 255;
                    texture = textures[cp >> 8];
                    width = charWidths[cp >> 8][id] & 255;
                    height = fontHeight;
                    int tx = (id & 15) * fontWidth, ty = (id >> 4) * fontHeight;
                    u1 = (float) tx / textureWidth; v1 = (float) ty / textureHeight;
                    u2 = (float) (tx + width) / textureWidth; v2 = (float) (ty + height) / textureHeight;
                }
                if (texture != activeTexture) {
                    if (drawing) GL11.glEnd();
                    drawing = false;
                    GlStateManager.bindTexture(texture);
                    GL11.glBegin(GL11.GL_QUADS);
                    drawing = true;
                    activeTexture = texture;
                }
                float left = x + offset;
                GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(left, y);
                GL11.glTexCoord2f(u1, v2); GL11.glVertex2f(left, y + height);
                GL11.glTexCoord2f(u2, v2); GL11.glVertex2f(left + width, y + height);
                GL11.glTexCoord2f(u2, v1); GL11.glVertex2f(left + width, y);
                offset += width;
            }
        } finally {
            if (drawing) GL11.glEnd();
        }
    }

    private TextLayout layout(String text) {
        TextLayout cached = textLayouts.get(text);
        if (cached != null) return cached;
        List<LayoutGlyph> glyphs = new ArrayList<>();
        int offset = 0, colorIndex = -1;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\u00a7' && i < text.length()) {
                int code = text.codePointAt(i);
                i += Character.charCount(code);
                int index = "0123456789abcdef".indexOf(code);
                if (index >= 0) colorIndex = index;
                continue;
            }
            LayoutGlyph glyph = new LayoutGlyph();
            glyph.offset = offset;
            glyph.colorIndex = colorIndex;
            if (cp > 0xFFFF || !font.canDisplay(cp)) {
                CodePointGlyph source = getOrGenerateGlyph(cp);
                glyph.texture = source.textureId; glyph.width = source.width; glyph.height = source.height;
                glyph.u2 = (float) source.width / nextPowerOfTwo(source.width);
                glyph.v2 = (float) source.height / nextPowerOfTwo(source.height);
            } else {
                int id = cp & 255, region = cp >> 8;
                glyph.width = getOrGenerateCharWidthMap(region)[id] & 255;
                glyph.height = fontHeight;
                glyph.texture = getOrGenerateCharTexture(region);
                int tx = (id & 15) * fontWidth, ty = (id >> 4) * fontHeight;
                glyph.u1 = (float) tx / textureWidth; glyph.v1 = (float) ty / textureHeight;
                glyph.u2 = (float) (tx + glyph.width) / textureWidth; glyph.v2 = (float) (ty + fontHeight) / textureHeight;
            }
            offset += glyph.width;
            glyphs.add(glyph);
        }
        cached = new TextLayout(glyphs);
        textLayouts.put(text, cached);
        return cached;
    }

    private void drawCachedLayout(String text, float x, float y, float red, float green, float blue, float alpha,
                                  boolean darken, int[] colors) {
        // Build all UVs/textures before glBegin, then reuse across shadows, glow layers and frames.
        TextLayout layout = layout(text);
        GlStateManager.color(red, green, blue, alpha);
        int activeTexture = -1, activeColor = -1;
        boolean drawing = false;
        try {
            for (LayoutGlyph glyph : layout.glyphs) {
                if (glyph.colorIndex != activeColor) {
                    if (glyph.colorIndex < 0) GlStateManager.color(red, green, blue, alpha);
                    else {
                        int color = colors[glyph.colorIndex | (darken ? 16 : 0)];
                        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                                (color & 255) / 255.0F, alpha);
                    }
                    activeColor = glyph.colorIndex;
                }
                if (glyph.texture != activeTexture) {
                    if (drawing) GL11.glEnd();
                    drawing = false;
                    GlStateManager.bindTexture(glyph.texture);
                    GL11.glBegin(GL11.GL_QUADS);
                    drawing = true;
                    activeTexture = glyph.texture;
                }
                float left = x + glyph.offset;
                GL11.glTexCoord2f(glyph.u1, glyph.v1); GL11.glVertex2f(left, y);
                GL11.glTexCoord2f(glyph.u1, glyph.v2); GL11.glVertex2f(left, y + glyph.height);
                GL11.glTexCoord2f(glyph.u2, glyph.v2); GL11.glVertex2f(left + glyph.width, y + glyph.height);
                GL11.glTexCoord2f(glyph.u2, glyph.v1); GL11.glVertex2f(left + glyph.width, y);
            }
        } finally {
            if (drawing) GL11.glEnd();
        }
    }

    private int drawChar(int codePoint, float x, float y) {
        if (codePoint > 0xFFFF || !font.canDisplay(codePoint)) {
            return drawCodePointGlyph(codePoint, x, y);
        }
        int region = codePoint >> 8;
        int id = codePoint & 0xFF;
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

    private int drawCodePointGlyph(int codePoint, float x, float y) {
        CodePointGlyph glyph = getOrGenerateGlyph(codePoint);
        int tw = nextPowerOfTwo(glyph.width);
        int th = nextPowerOfTwo(glyph.height);
        GlStateManager.bindTexture(glyph.textureId);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(0.0D, 0.0D);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2d(0.0D, (double) glyph.height / th);
        GL11.glVertex2f(x, y + glyph.height);
        GL11.glTexCoord2d((double) glyph.width / tw, (double) glyph.height / th);
        GL11.glVertex2f(x + glyph.width, y + glyph.height);
        GL11.glTexCoord2d((double) glyph.width / tw, 0.0D);
        GL11.glVertex2f(x + glyph.width, y);
        GL11.glEnd();
        return glyph.width;
    }

    public int getStringWidth(String text) {
        if (text == null) return 0;
        int width = 0;
        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\u00a7') {
                if (i < len) {
                    i += Character.charCount(text.codePointAt(i));
                }
            } else if (cp > 0xFFFF || !font.canDisplay(cp)) {
                width += getOrGenerateGlyph(cp).width;
            } else {
                width += getOrGenerateCharWidthMap(cp >> 8)[cp & 0xFF] & 0xFF;
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

    public float getBaseline() {
        if (ascent < 0) {
            Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            if (antiAlias) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.setFont(font);
            ascent = g.getFontMetrics().getAscent();
            g.dispose();
        }
        return ascent / 2.0F - 1.0F;
    }

    public float getCapHeight() {
        if (capHeight < 0.0F) {
            capHeight = (float) font.createGlyphVector(context, "H").getVisualBounds().getHeight();
        }
        return capHeight / 2.0F;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font, boolean antiAlias) {
        dispose();
        this.font = font;
        Arrays.fill(textures, -1);
        Arrays.fill(charWidths, null);
        glyphCache.clear();
        fallbackFontCache.clear();
        this.ascent = -1;
        this.capHeight = -1.0F;
        Rectangle2D maxBounds = font.getMaxCharBounds(context);
        this.fontWidth = Math.max(1, (int) Math.ceil(maxBounds.getWidth()));
        this.fontHeight = Math.max(1, (int) Math.ceil(maxBounds.getHeight()));
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

    private Font getFontForCodePoint(int codePoint) {
        if (font.canDisplay(codePoint)) return font;
        Font cached = fallbackFontCache.get(codePoint);
        if (cached != null) return cached;
        int size = Math.max(1, Math.round(font.getSize2D()));
        for (String name : FALLBACK_FONT_NAMES) {
            Font candidate = new Font(name, Font.PLAIN, size);
            if (candidate.canDisplay(codePoint)) {
                fallbackFontCache.put(codePoint, candidate);
                return candidate;
            }
        }
        fallbackFontCache.put(codePoint, font);
        return font;
    }

    private CodePointGlyph getOrGenerateGlyph(int codePoint) {
        CodePointGlyph cached = glyphCache.get(codePoint);
        if (cached != null) return cached;
        Font renderFont = getFontForCodePoint(codePoint);
        String str = new String(Character.toChars(codePoint));
        Rectangle2D bounds = renderFont.getStringBounds(str, context);
        int gw = Math.max(1, (int) Math.ceil(bounds.getWidth()));
        int gh = Math.max(1, (int) Math.ceil(bounds.getHeight()));
        int tw = nextPowerOfTwo(gw);
        int th = nextPowerOfTwo(gh);
        BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        if (antiAlias) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setFont(renderFont);
        g.setColor(Color.WHITE);
        g.drawString(str, 0, (int) Math.ceil(-bounds.getY()));
        g.dispose();
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        int[] pixels = img.getRGB(0, 0, tw, th, null, 0, tw);
        ByteBuffer buf = ByteBuffer.allocateDirect(pixels.length * 4).order(ByteOrder.nativeOrder());
        for (int pixel : pixels) {
            buf.put((byte) ((pixel >> 16) & 0xFF));
            buf.put((byte) ((pixel >> 8) & 0xFF));
            buf.put((byte) (pixel & 0xFF));
            buf.put((byte) ((pixel >> 24) & 0xFF));
        }
        buf.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, tw, th, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GlStateManager.bindTexture(0);
        CodePointGlyph glyph = new CodePointGlyph(textureId, gw, gh);
        glyphCache.put(codePoint, glyph);
        return glyph;
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
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
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
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
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
        textLayouts.clear();
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] != -1) {
                GL11.glDeleteTextures(textures[i]);
                textures[i] = -1;
            }
        }
        for (CodePointGlyph glyph : glyphCache.values()) {
            GL11.glDeleteTextures(glyph.textureId);
        }
        glyphCache.clear();
        fallbackFontCache.clear();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
    }
}
