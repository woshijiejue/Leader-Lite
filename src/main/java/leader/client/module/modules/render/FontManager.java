package leader.client.module.modules.render;

import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.util.CustomFontRenderer;

import java.util.List;

public class FontManager extends Module {


    private static final String[] FONT_PATHS = {
            "/leader/font/xylitol_font.ttf",
            "/leader/font/xylitol_bold.ttf",
            "/leader/font/harmonyos_sans_sc_regular.ttf",
            "/leader/font/harmonyos_sans_sc_medium.ttf",
            "/leader/font/Inter_SemiBold.ttf",
            "/leader/font/NotoSans-Regular.ttf",
            "/leader/font/NotoSansSC-Regular.ttf",
            "/leader/font/Nursultan.ttf",
            "/leader/font/product_sans_regular.ttf",
            "/leader/font/SF-Pro-Display-Semibold.otf",
            "/leader/font/SF-Pro-Rounded-Bold.otf",
            "/leader/font/SF-Pro-Rounded-Medium.otf",
            "/leader/font/SF-Pro-Rounded-Regular.otf"
    };

    public static BoolValue customFont = new BoolValue("CustomFont", false, null);
    public static ListValue font = new ListValue("Font", new String[]{
            "Xylitol", "Xylitol Bold", "HarmonyOS", "HarmonyOS Med",
            "Inter", "NotoSans", "NotoSansSC", "Nursultan",
            "ProductSans", "SF Display", "SF Rounded B", "SF Rounded M", "SF Rounded R"
    }, "Xylitol", null);
    public static SliderValue fontSize = new SliderValue("FontSize", 18.0, 10.0, 30.0, Representation.FLOAT, null);

    public static CustomFontRenderer customFontRenderer;
    private static String lastFontMode = null;
    private static float lastFontSize = -1.0F;

    public FontManager() {
        super("FontManager", false);
    }

    @Override
    public void onEnabled() {
        checkAndRebuild();
    }

    @Override
    public void onDisabled() {
    }

    private static void checkAndRebuild() {
        String currentMode = font.getValue();
        float currentSize = fontSize.getValue();
        if (customFontRenderer == null || !currentMode.equals(lastFontMode) || currentSize != lastFontSize) {
            if (customFontRenderer != null) {
                customFontRenderer.dispose();
            }
            lastFontMode = currentMode;
            lastFontSize = currentSize;
            int fontIndex = 0;
            List<String> options = font.getModes();
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).equals(currentMode)) {
                    fontIndex = i;
                    break;
                }
            }
            customFontRenderer = new CustomFontRenderer(FONT_PATHS[fontIndex], currentSize, true);
        }
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow) {
        if (customFont.getValue()) {
            checkAndRebuild();
            if (customFontRenderer != null) {
                if (shadow) {
                    customFontRenderer.drawStringWithShadow(text, x, y, color);
                } else {
                    customFontRenderer.drawString(text, x, y, color);
                }
                return;
            }
        }
        mc.fontRendererObj.drawString(text, x, y, color, shadow);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color) {
        if (customFont.getValue()) {
            checkAndRebuild();
            if (customFontRenderer != null) {
                customFontRenderer.drawStringWithShadow(text, x, y, color);
                return;
            }
        }
        mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
    }

    public static int getStringWidth(String text) {
        if (customFont.getValue()) {
            checkAndRebuild();
            if (customFontRenderer != null) {
                return customFontRenderer.getStringWidth(text);
            }
        }
        return mc.fontRendererObj.getStringWidth(text);
    }

    public static int getFontHeight() {
        if (customFont.getValue()) {
            checkAndRebuild();
            if (customFontRenderer != null) {
                return (int) customFontRenderer.getFontHeight();
            }
        }
        return mc.fontRendererObj.FONT_HEIGHT;
    }
}
