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
