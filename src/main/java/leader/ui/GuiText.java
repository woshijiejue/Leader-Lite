package leader.ui;

import leader.module.modules.render.FontManager;

public final class GuiText {

    private GuiText() {
    }

    public static void draw(String text, float x, float y, int color) {
        FontManager.drawString(text, x, y, color, false);
    }

    public static void draw(String text, float x, float y, int color, float size) {
        FontManager.drawString(text, x, y, color, false, size);
    }

    public static void drawShadow(String text, float x, float y, int color) {
        FontManager.drawStringWithShadow(text, x, y, color);
    }

    public static void drawShadow(String text, float x, float y, int color, float size) {
        FontManager.drawStringWithShadow(text, x, y, color, size);
    }

    public static int width(String text) {
        return FontManager.getStringWidth(text);
    }

    public static int width(String text, float size) {
        return FontManager.getStringWidth(text, size);
    }

    public static int height() {
        return FontManager.getFontHeight();
    }

    public static int height(float size) {
        return FontManager.getFontHeight(size);
    }

    public static String trim(String text, int maxWidth) {
        return trim(text, maxWidth, 18.0F);
    }

    public static String trim(String text, int maxWidth, float size) {
        if (text == null) return "";
        if (width(text, size) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = width(suffix, size);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = result.toString() + text.charAt(i);
            if (width(candidate, size) + suffixWidth > maxWidth) break;
            result.append(text.charAt(i));
        }
        return result.append(suffix).toString();
    }

    public static String trimLabelValue(String name, String value, int maxWidth) {
        return trimLabelValue(name, value, maxWidth, 18.0F);
    }

    public static String trimLabelValue(String name, String value, int maxWidth, float size) {
        int valueWidth = width(value, size);
        return trim(name, Math.max(0, maxWidth - valueWidth), size) + value;
    }

    public static void drawCentered(String text, float centerX, float y, int color) {
        drawCentered(text, centerX, y, color, 18.0F);
    }

    public static void drawCentered(String text, float centerX, float y, int color, float size) {
        draw(text, centerX - width(text, size) / 2.0F, y, color, size);
    }
}
