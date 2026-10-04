package leader.module.modules.render;

import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.ModeProperty;
import leader.util.FontRender;

public class FontManager extends Module {

    public static final BooleanProperty customFont = new BooleanProperty("CustomFont", false);
    public static final ModeProperty font = new ModeProperty("Font", 0, new String[]{
            "Xylitol", "Xylitol Bold", "HarmonyOS", "HarmonyOS Med",
            "Inter", "NotoSans", "NotoSansSC", "Nursultan",
            "ProductSans", "SF Display", "SF Rounded B", "SF Rounded M", "SF Rounded R"
    });
    private static int lastFontMode = -1;

    public FontManager() {
        super("FontManager", false);
    }

    @Override
    public void onEnabled() {
        syncFontMode();
    }

    @Override
    public void onDisabled() {
    }

    private static void syncFontMode() {
        int currentMode = font.getValue();
        if (currentMode != lastFontMode) {
            lastFontMode = currentMode;
            FontRender.setFontMode(currentMode);
        }
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow) {
        drawString(text, x, y, color, shadow, 18.0F);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow, float size) {
        syncFontMode();
        FontRender.drawString(text, x, y, color, shadow, size, customFont.getValue());
    }

    /** Optional soft text halo, independent of fullscreen bloom and off by default in HUD modules. */
    public static void drawStringWithGlow(String text, float x, float y, int color, float size, float strength) {
        if (text == null || text.isEmpty() || (color >>> 24) < 4) return;
        syncFontMode();
        float a = (color >>> 24) * Math.max(0, Math.min(1, strength));
        String halo = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(text);
        int layers = BetterFPS.optimizedHUD() ? 1 : 2;
        for (int layer = layers; layer >= 1; layer--) {
            int alpha = Math.round(a * (layer == 1 ? 0.18F : 0.08F));
            if (alpha < 4) continue; // Vanilla treats tiny alpha values as opaque.
            int glow = (color & 0xFFFFFF) | alpha << 24;
            float offset = layer * 0.7F;
            FontRender.drawString(halo, x - offset, y, glow, false, size, customFont.getValue());
            FontRender.drawString(halo, x + offset, y, glow, false, size, customFont.getValue());
            FontRender.drawString(halo, x, y - offset, glow, false, size, customFont.getValue());
            FontRender.drawString(halo, x, y + offset, glow, false, size, customFont.getValue());
        }
        FontRender.drawString(text, x, y, color, false, size, customFont.getValue());
    }

    public static void drawStringWithShadow(String text, float x, float y, int color) {
        drawStringWithShadow(text, x, y, color, 18.0F);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color, float size) {
        syncFontMode();
        FontRender.drawStringWithShadow(text, x, y, color, size, customFont.getValue());
    }

    public static int getStringWidth(String text) {
        return getStringWidth(text, 18.0F);
    }

    public static int getStringWidth(String text, float size) {
        syncFontMode();
        return FontRender.getStringWidth(text, size, customFont.getValue());
    }

    public static int getFontHeight() {
        return getFontHeight(18.0F);
    }

    public static int getFontHeight(float size) {
        syncFontMode();
        return FontRender.getFontHeight(size, customFont.getValue());
    }

    public static float getBaseline(float size) {
        syncFontMode();
        return FontRender.getBaseline(size, customFont.getValue());
    }

    public static float getCapHeight(float size) {
        syncFontMode();
        return FontRender.getCapHeight(size, customFont.getValue());
    }
}
