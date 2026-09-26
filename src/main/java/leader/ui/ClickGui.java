package leader.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leader.Leader;
import leader.module.Module;
import leader.module.modules.render.FontManager;
import leader.module.modules.render.GuiModule;
import leader.module.modules.render.HUD;
import leader.property.Property;
import leader.property.properties.BooleanProperty;
import leader.property.properties.ColorProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.property.properties.PercentProperty;
import leader.property.properties.TextProperty;
import leader.ui.callback.GuiInput;
import leader.util.Icon;
import leader.util.KeyBindUtil;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ClickGui extends GuiScreen {
    private static final int W = 560;
    private static final int H = 350;
    private static final int SIDEBAR = 132;
    private static final int LIST_W = 164;
    private static final int HEADER = 46;
    private static final float ROW = 26.0F;
    private static final float ROW_GAP = 3.0F;
    private static final String[] CATEGORY_NAMES = {"Combat", "Movement", "Render", "Player", "Misc", "Legit"};

    private static ClickGui instance;

    private final File configFile = new File("./config/Leader/", "clickgui.txt");
    private final Map<String, List<Module>> categories = new LinkedHashMap<>();
    private final Map<String, Float> anims = new HashMap<>();
    private final Map<ColorProperty, float[]> colorStates = new HashMap<>();
    private final Set<ColorProperty> expandedColors = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();

    private int selectedCategory;
    private Module selectedModule;
    private String search = "";
    private boolean searchFocused;
    private float moduleScroll;
    private float moduleScrollTarget;
    private float settingScroll;
    private float settingScrollTarget;
    private float categoryIndicator = -1.0F;
    private int windowX;
    private int windowY;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragging;
    private boolean initialized;
    private boolean positionsLoaded;
    private long openedAt;
    private long lastFrame;
    private float dt = 0.016F;
    private float alpha = 1.0F;
    private Color accent = new Color(110, 170, 255);

    private Property<?> draggingSlider;
    private float sliderTrackX;
    private float sliderTrackW;
    private ColorProperty draggingColor;
    private int draggingColorBar;
    private float colorTrackX;
    private float colorTrackW;
    private Module bindingModule;

    private float clipTop = -100000.0F;
    private float clipBottom = 100000.0F;
    private float listX1, listY1, listX2, listY2;
    private float setX1, setY1, setX2, setY2;

    private interface ClickAction {
        void click(int button, int mouseX, int mouseY);
    }

    private static final class Hit {
        final float x1, y1, x2, y2;
        final ClickAction action;

        Hit(float x1, float y1, float x2, float y2, ClickAction action) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.action = action;
        }
    }

    public ClickGui() {
        instance = this;
        for (String name : CATEGORY_NAMES) categories.put(name, new ArrayList<>());
        for (Module module : Leader.moduleManager.modules.values()) {
            String pkg = module.getClass().getPackage().getName().toLowerCase(Locale.ROOT);
            for (String name : CATEGORY_NAMES) {
                if (pkg.contains(name.toLowerCase(Locale.ROOT))) {
                    categories.get(name).add(module);
                    break;
                }
            }
        }
        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT));
        categories.values().forEach(list -> list.sort(comparator));
        loadPositions();
        positionsLoaded = true;
        if (selectedModule == null) {
            List<Module> first = categories.get(CATEGORY_NAMES[selectedCategory]);
            if (!first.isEmpty()) selectedModule = first.get(0);
        }
    }

    public static ClickGui getInstance() {
        return instance;
    }

    public static void setInstance(ClickGui screen) {
        instance = screen;
    }

    @Override
    public void initGui() {
        if (!initialized) {
            if (!positionsLoaded || (windowX == 0 && windowY == 0)) {
                windowX = (this.width - W) / 2;
                windowY = (this.height - H) / 2;
            }
            initialized = true;
        }
        clampWindow();
        openedAt = System.currentTimeMillis();
        lastFrame = 0L;
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        float open = clamp01((now - openedAt) / 240.0F);
        float ease = 1.0F - (1.0F - open) * (1.0F - open) * (1.0F - open);
        alpha = ease;

        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        accent = hud != null ? hud.getColor(now) : new Color(110, 170, 255);

        if (dragging) {
            windowX = mouseX - dragOffsetX;
            windowY = mouseY - dragOffsetY;
            clampWindow();
        }
        updateDrags(mouseX);

        hits.clear();
        resetClip();
        float x = windowX;
        float y = windowY + (1.0F - ease) * 14.0F;

        RenderUtil.enableRenderState();
        RenderUtil.drawRect(0.0F, 0.0F, this.width, this.height, col(0, 0, 0, 100));
        RenderUtil.disableRenderState();

        final float mx = x;
        final float my = y;
        ShaderElement.addBlurTask(() -> RenderUtil.drawRoundedRectWithGl(mx, my, mx + W, my + H, 12.0F,
                new Color(18, 20, 28, 255).getRGB()));

        RenderUtil.drawRoundedRectWithGl(x - 0.5F, y - 0.5F, x + W + 0.5F, y + H + 0.5F, 12.5F, col(255, 255, 255, 18));
        RenderUtil.drawRoundedRectWithGl(x, y, x + W, y + H, 12.0F, col(14, 15, 20, 200));
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(x + SIDEBAR, y + 12.0F, x + SIDEBAR + 0.5F, y + H - 12.0F, col(255, 255, 255, 14));
        RenderUtil.drawRect(x + SIDEBAR + 12.0F, y + HEADER, x + W - 12.0F, y + HEADER + 0.5F, col(255, 255, 255, 12));
        RenderUtil.disableRenderState();

        addHit(x, y, x + W, y + HEADER, (button, mX, mY) -> {
            if (button == 0) {
                dragging = true;
                dragOffsetX = mX - windowX;
                dragOffsetY = mY - windowY;
            }
        });

        drawSidebar(x, y, mouseX, mouseY);
        drawHeader(x, y);
        drawModuleList(x, y, mouseX, mouseY);
        drawSettings(x, y, mouseX, mouseY);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSidebar(float x, float y, int mouseX, int mouseY) {
        float chipX = x + 14.0F;
        float chipY = y + 12.0F;
        float centerY = chipY + 11.0F;
        RenderUtil.drawRoundedRectWithGl(chipX, chipY, chipX + 22.0F, chipY + 22.0F, 7.0F, col(accent, 48));
        Icon.CROWN.drawCentered(chipX + 11.0F, centerY, 13.0F, accent.getRGB(), alpha);
        text("Leader", chipX + 30.0F, centerY, col(245, 247, 252, 255), 18.0F);
        text("Lite", chipX + 30.0F + width("Leader", 18.0F) + 3.0F, centerY, col(accent, 255), 18.0F);

        text("CATEGORIES", x + 16.0F, y + 62.0F, col(120, 128, 145, 255), 11.0F);

        float rowH = 26.0F;
        float gap = 3.0F;
        float top = y + 72.0F;
        float target = top + selectedCategory * (rowH + gap);
        if (!search.isEmpty()) target = -1000.0F;
        if (categoryIndicator < 0.0F || target < 0.0F) categoryIndicator = target;
        categoryIndicator += (target - categoryIndicator) * (1.0F - (float) Math.exp(-dt * 16.0F));
        if (search.isEmpty()) {
            RenderUtil.drawRoundedRectWithGl(x + 10.0F, categoryIndicator, x + SIDEBAR - 10.0F, categoryIndicator + rowH, 8.0F, col(accent, 40));
            RenderUtil.drawRoundedRectWithGl(x + 10.0F, categoryIndicator + 7.0F, x + 12.5F, categoryIndicator + rowH - 7.0F, 1.25F, col(accent, 255));
        }

        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            final int index = i;
            String name = CATEGORY_NAMES[i];
            float ry = top + i * (rowH + gap);
            boolean hovered = inside(mouseX, mouseY, x + 10.0F, ry, x + SIDEBAR - 10.0F, ry + rowH);
            float hover = anim("ch" + i, hovered ? 1.0F : 0.0F, 14.0F);
            float sel = anim("cs" + i, search.isEmpty() && selectedCategory == i ? 1.0F : 0.0F, 14.0F);
            if (hover > 0.01F) {
                RenderUtil.drawRoundedRectWithGl(x + 10.0F, ry, x + SIDEBAR - 10.0F, ry + rowH, 8.0F, col(255, 255, 255, (int) (8 * hover * (1.0F - sel))));
            }
            float cy = ry + rowH / 2.0F;
            int iconColor = mix(new Color(125, 133, 150), accent, sel).getRGB();
            categoryIcon(name).drawCentered(x + 26.0F, cy, 12.0F, iconColor, alpha);
            text(name, x + 40.0F, cy, mixCol(new Color(165, 172, 188), new Color(246, 248, 252), Math.max(sel, hover * 0.6F), 255), 15.0F);
            String count = String.valueOf(categories.get(name).size());
            text(count, x + SIDEBAR - 18.0F - width(count, 12.0F), cy, col(110, 118, 135, 255), 12.0F);
            addHit(x + 10.0F, ry, x + SIDEBAR - 10.0F, ry + rowH, (button, mX, mY) -> {
                search = "";
                if (selectedCategory != index) {
                    selectedCategory = index;
                    List<Module> list = categories.get(CATEGORY_NAMES[index]);
                    selectedModule = list.isEmpty() ? null : list.get(0);
                    settingScroll = settingScrollTarget = 0.0F;
                }
                moduleScroll = moduleScrollTarget = 0.0F;
            });
        }

        float cardY1 = y + H - 46.0F;
        float cardY2 = y + H - 12.0F;
        RenderUtil.drawRoundedRectWithGl(x + 10.0F, cardY1, x + SIDEBAR - 10.0F, cardY2, 8.0F, col(255, 255, 255, 7));
        float headSize = 20.0F;
        float headX = x + 17.0F;
        float cy = (cardY1 + cardY2) / 2.0F;
        ResourceLocation skin = null;
        if (mc.thePlayer != null && mc.getNetHandler() != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info != null) skin = info.getLocationSkin();
        }
        if (skin != null) {
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            mc.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect((int) headX, (int) (cy - headSize / 2.0F), 8.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect((int) headX, (int) (cy - headSize / 2.0F), 40.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            RenderUtil.drawRoundedRectWithGl(headX, cy - headSize / 2.0F, headX + headSize, cy + headSize / 2.0F, 5.0F, col(accent, 60));
        }
        String user = mc.getSession() != null ? mc.getSession().getUsername() : "Player";
        String server = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "Singleplayer";
        float tx = headX + headSize + 7.0F;
        float maxW = x + SIDEBAR - 16.0F - tx;
        text(GuiText.trim(user, (int) maxW, 14.0F), tx, cy - 5.0F, col(240, 243, 248, 255), 14.0F);
        text(GuiText.trim(server, (int) maxW, 11.0F), tx, cy + 6.0F, col(120, 128, 145, 255), 11.0F);
    }

    private void drawHeader(float x, float y) {
        float hx = x + SIDEBAR + 16.0F;
        float cy = y + HEADER / 2.0F;
        String title = search.isEmpty() ? CATEGORY_NAMES[selectedCategory] : "Search";
        text(title, hx, cy, col(246, 248, 252, 255), 20.0F);
        int count = visibleModules().size();
        text(count + (count == 1 ? " module" : " modules"), hx + width(title, 20.0F) + 8.0F, cy, col(120, 128, 145, 255), 12.0F);

        float sw = 150.0F;
        float sh = 22.0F;
        float sx = x + W - 14.0F - sw;
        float sy = cy - sh / 2.0F;
        boolean active = !search.isEmpty() || searchFocused;
        float focus = anim("search", searchFocused ? 1.0F : 0.0F, 12.0F);
        addHit(sx, sy, sx + sw, sy + sh, (button, mX, mY) -> {
            searchFocused = true;
            if (button == 1) {
                search = "";
                moduleScroll = moduleScrollTarget = 0.0F;
            }
        });
        if (focus > 0.01F) {
            RenderUtil.drawRoundedRectWithGl(sx - 0.75F, sy - 0.75F, sx + sw + 0.75F, sy + sh + 0.75F, 7.5F, col(accent, (int) (120 * focus)));
        }
        RenderUtil.drawRoundedRectWithGl(sx, sy, sx + sw, sy + sh, 7.0F, col(22, 24, 31, 255));
        boolean caret = (System.currentTimeMillis() / 500L) % 2L == 0L;
        if (active) {
            String shown = search;
            while (shown.length() > 0 && width(shown, 14.0F) > sw - 22.0F) shown = shown.substring(1);
            text(shown, sx + 9.0F, cy, col(240, 243, 248, 255), 14.0F);
            if (caret && searchFocused) {
                float cx = sx + 9.0F + width(shown, 14.0F) + 1.0F;
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(cx, cy - 4.5F, cx + 0.75F, cy + 4.5F, col(accent, 255));
                RenderUtil.disableRenderState();
            }
        } else {
            text("Search  Ctrl+F", sx + 9.0F, cy, col(105, 112, 128, 255), 14.0F);
        }
    }

    private void drawModuleList(float x, float y, int mouseX, int mouseY) {
        float lx = x + SIDEBAR + 10.0F;
        float ly = y + HEADER + 8.0F;
        float lw = LIST_W;
        float lh = H - HEADER - 18.0F;
        listX1 = lx;
        listY1 = ly;
        listX2 = lx + lw;
        listY2 = ly + lh;

        List<Module> modules = visibleModules();
        float total = modules.isEmpty() ? 0.0F : modules.size() * (ROW + ROW_GAP) - ROW_GAP;
        float maxScroll = Math.max(0.0F, total - lh);
        moduleScrollTarget = Math.max(0.0F, Math.min(moduleScrollTarget, maxScroll));
        moduleScroll += (moduleScrollTarget - moduleScroll) * (1.0F - (float) Math.exp(-dt * 18.0F));

        if (modules.isEmpty()) {
            String empty = search.isEmpty() ? "No modules" : "No results";
            text(empty, lx + (lw - width(empty, 14.0F)) / 2.0F, ly + 30.0F, col(120, 128, 145, 255), 14.0F);
            return;
        }

        scissor(lx, ly, lw, lh);
        setClip(ly, ly + lh);
        for (int i = 0; i < modules.size(); i++) {
            final Module module = modules.get(i);
            float ry = ly + i * (ROW + ROW_GAP) - moduleScroll;
            if (ry + ROW < ly || ry > ly + lh) continue;
            String key = module.getName();
            boolean selected = module == selectedModule;
            boolean hovered = inside(mouseX, mouseY, lx, Math.max(ry, ly), lx + lw, Math.min(ry + ROW, ly + lh));
            float hover = anim("mh" + key, hovered ? 1.0F : 0.0F, 14.0F);
            float sel = anim("ms" + key, selected ? 1.0F : 0.0F, 14.0F);
            float on = anim("me" + key, module.isEnabled() ? 1.0F : 0.0F, 14.0F);

            RenderUtil.drawRoundedRectWithGl(lx, ry, lx + lw - 4.0F, ry + ROW, 7.0F, col(255, 255, 255, (int) (6 + 6 * hover)));
            if (sel > 0.01F) {
                RenderUtil.drawRoundedRectWithGl(lx, ry, lx + lw - 4.0F, ry + ROW, 7.0F, col(accent, (int) (38 * sel)));
            }
            float cy = ry + ROW / 2.0F;
            float swW = 20.0F;
            float swX = lx + lw - 4.0F - 9.0F - swW;
            text(GuiText.trim(module.getName(), (int) (swX - lx - 18.0F), 15.0F), lx + 10.0F, cy,
                    mixCol(new Color(160, 167, 183), new Color(246, 248, 252), Math.max(on, sel), 255), 15.0F);
            drawSwitch(swX, cy, swW, 11.0F, on);

            addHit(swX - 4.0F, ry, lx + lw - 4.0F, ry + ROW, (button, mX, mY) -> {
                if (button == 0 || button == 1) module.toggle();
            });
            addHit(lx, ry, swX - 4.0F, ry + ROW, (button, mX, mY) -> {
                if (button == 0) {
                    if (selectedModule != module) {
                        selectedModule = module;
                        settingScroll = settingScrollTarget = 0.0F;
                    }
                } else if (button == 1) {
                    module.toggle();
                }
            });
        }
        resetClip();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (total > lh) {
            float thumbH = Math.max(20.0F, lh * lh / total);
            float thumbY = ly + (lh - thumbH) * (moduleScroll / Math.max(1.0F, maxScroll));
            RenderUtil.drawRoundedRectWithGl(lx + lw - 2.0F, thumbY, lx + lw, thumbY + thumbH, 1.0F, col(255, 255, 255, 50));
        }
    }

    private void drawSettings(float x, float y, int mouseX, int mouseY) {
        float sx = x + SIDEBAR + 10.0F + LIST_W + 8.0F;
        float sy = y + HEADER + 8.0F;
        float sw = x + W - 10.0F - sx;
        float sh = H - HEADER - 18.0F;
        setX1 = sx;
        setY1 = sy;
        setX2 = sx + sw;
        setY2 = sy + sh;
        RenderUtil.drawRoundedRectWithGl(sx, sy, sx + sw, sy + sh, 9.0F, col(255, 255, 255, 5));

        final Module module = selectedModule;
        if (module == null) {
            String hint = "Select a module";
            text(hint, sx + (sw - width(hint, 14.0F)) / 2.0F, sy + sh / 2.0F, col(120, 128, 145, 255), 14.0F);
            return;
        }

        float hy = sy + 18.0F;
        text(GuiText.trim(module.getName(), (int) (sw - 70.0F), 18.0F), sx + 12.0F, hy, col(246, 248, 252, 255), 18.0F);
        float on = anim("me" + module.getName(), module.isEnabled() ? 1.0F : 0.0F, 14.0F);
        String state = module.isEnabled() ? "ON" : "OFF";
        float swW = 24.0F;
        float swX = sx + sw - 12.0F - swW;
        text(state, swX - 6.0F - width(state, 11.0F), hy, mixCol(new Color(120, 128, 145), accent, on, 255), 11.0F);
        drawSwitch(swX, hy, swW, 13.0F, on);
        addHit(swX - 30.0F, hy - 9.0F, swX + swW, hy + 9.0F, (button, mX, mY) -> module.toggle());
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(sx + 12.0F, sy + 36.0F, sx + sw - 12.0F, sy + 36.5F, col(255, 255, 255, 12));
        RenderUtil.disableRenderState();

        float cy = sy + 40.0F;
        float ch = sh - 44.0F;
        float rx = sx + 12.0F;
        float rw = sw - 24.0F;

        List<Property<?>> props = Leader.propertyManager.properties.get(module.getClass());
        float offset = 4.0F;
        scissor(sx, cy, sw, ch);
        setClip(cy, cy + ch);
        if (props != null) {
            for (Property<?> property : props) {
                if (!property.isVisible()) continue;
                float ry = cy + offset - settingScroll;
                offset += drawProperty(property, rx, ry, rw, mouseX, mouseY) + 2.0F;
            }
        }
        offset += drawBind(module, rx, cy + offset - settingScroll, rw, mouseX, mouseY) + 4.0F;
        resetClip();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        float maxScroll = Math.max(0.0F, offset - ch);
        settingScrollTarget = Math.max(0.0F, Math.min(settingScrollTarget, maxScroll));
        settingScroll += (settingScrollTarget - settingScroll) * (1.0F - (float) Math.exp(-dt * 18.0F));
        if (maxScroll > 0.0F) {
            float thumbH = Math.max(20.0F, ch * ch / offset);
            float thumbY = cy + (ch - thumbH) * (settingScroll / maxScroll);
            RenderUtil.drawRoundedRectWithGl(sx + sw - 4.0F, thumbY, sx + sw - 2.0F, thumbY + thumbH, 1.0F, col(255, 255, 255, 50));
        }
    }

    private float drawProperty(Property<?> property, float rx, float ry, float rw, int mouseX, int mouseY) {
        if (property instanceof BooleanProperty) return drawBoolean((BooleanProperty) property, rx, ry, rw, mouseX, mouseY);
        if (property instanceof ModeProperty) return drawMode((ModeProperty) property, rx, ry, rw, mouseX, mouseY);
        if (property instanceof FloatProperty || property instanceof IntProperty || property instanceof PercentProperty) {
            return drawSlider(property, rx, ry, rw, mouseX, mouseY);
        }
        if (property instanceof ColorProperty) return drawColor((ColorProperty) property, rx, ry, rw, mouseX, mouseY);
        if (property instanceof TextProperty) return drawTextProperty((TextProperty) property, rx, ry, rw, mouseX, mouseY);
        return 0.0F;
    }

    private void rowBackground(String key, float rx, float ry, float rw, float h, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, rx - 4.0F, Math.max(ry, clipTop), rx + rw + 4.0F, Math.min(ry + h, clipBottom));
        float hover = anim(key, hovered ? 1.0F : 0.0F, 14.0F);
        if (hover > 0.01F) {
            RenderUtil.drawRoundedRectWithGl(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, 6.0F, col(255, 255, 255, (int) (7 * hover)));
        }
    }

    private String label(Property<?> property) {
        String name = property.getName().replace("-", " ").replace("_", " ");
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private float drawBoolean(final BooleanProperty property, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 22.0F;
        String id = "b" + System.identityHashCode(property);
        rowBackground(id + "h", rx, ry, rw, h, mouseX, mouseY);
        float cy = ry + h / 2.0F;
        float on = anim(id, property.getValue() ? 1.0F : 0.0F, 14.0F);
        text(GuiText.trim(label(property), (int) (rw - 30.0F), 14.0F), rx, cy, col(205, 210, 222, 255), 14.0F);
        drawSwitch(rx + rw - 20.0F, cy, 20.0F, 11.0F, on);
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) property.setValue(!property.getValue());
        });
        return h;
    }

    private float drawMode(final ModeProperty property, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 22.0F;
        String id = "m" + System.identityHashCode(property);
        rowBackground(id + "h", rx, ry, rw, h, mouseX, mouseY);
        float cy = ry + h / 2.0F;
        String value = property.getModeString().replace("_", " ");
        float pillW = Math.min(rw * 0.55F, width(value, 13.0F) + 16.0F);
        String shown = GuiText.trim(value, (int) (pillW - 12.0F), 13.0F);
        float px = rx + rw - pillW;
        text(GuiText.trim(label(property), (int) (px - rx - 6.0F), 14.0F), rx, cy, col(205, 210, 222, 255), 14.0F);
        RenderUtil.drawRoundedRectWithGl(px, cy - 8.0F, rx + rw, cy + 8.0F, 5.0F, col(accent, 34));
        text(shown, px + (pillW - width(shown, 13.0F)) / 2.0F, cy, col(accent, 255), 13.0F);
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) property.nextMode();
            else if (button == 1) property.previousMode();
        });
        return h;
    }

    private float drawSlider(final Property<?> property, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 30.0F;
        String id = "s" + System.identityHashCode(property);
        rowBackground(id + "h", rx, ry, rw, h, mouseX, mouseY);
        String value = sliderText(property);
        float nameY = ry + 9.0F;
        text(GuiText.trim(label(property), (int) (rw - width(value, 13.0F) - 8.0F), 14.0F), rx, nameY, col(205, 210, 222, 255), 14.0F);
        text(value, rx + rw - width(value, 13.0F), nameY, col(240, 243, 248, 255), 13.0F);

        float ratio = anim(id, sliderRatio(property), draggingSlider == property ? 40.0F : 16.0F);
        float trackY = ry + 21.0F;
        RenderUtil.drawRoundedRectWithGl(rx, trackY - 1.5F, rx + rw, trackY + 1.5F, 1.5F, col(255, 255, 255, 22));
        float fx = rx + rw * clamp01(ratio);
        if (fx - rx > 0.5F) {
            RenderUtil.drawRoundedRectGradientH(rx, trackY - 1.5F, fx, trackY + 1.5F, 1.5F,
                    col(accent, 170), col(accent, 255));
        }
        float knob = draggingSlider == property ? 4.5F : 4.0F;
        RenderUtil.drawRoundedRectWithGl(fx - knob - 1.0F, trackY - knob - 1.0F, fx + knob + 1.0F, trackY + knob + 1.0F, knob + 1.0F, col(accent, 60));
        RenderUtil.drawRoundedRectWithGl(fx - knob, trackY - knob, fx + knob, trackY + knob, knob, col(245, 247, 252, 255));

        final float tx = rx;
        final float tw = rw;
        addHit(rx - 4.0F, ry + 14.0F, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) {
                draggingSlider = property;
                sliderTrackX = tx;
                sliderTrackW = tw;
                setSliderRatio(property, clamp01((mX - tx) / tw));
            }
        });
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + 14.0F, (button, mX, mY) -> {
            if (button == 1 || button == 0) {
                GuiInput.prompt(label(property), rawValue(property), s -> setSliderText(property, s), this);
            }
        });
        return h;
    }

    private float drawTextProperty(final TextProperty property, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 22.0F;
        String id = "t" + System.identityHashCode(property);
        rowBackground(id + "h", rx, ry, rw, h, mouseX, mouseY);
        float cy = ry + h / 2.0F;
        float boxW = rw * 0.5F;
        float bx = rx + rw - boxW;
        text(GuiText.trim(label(property), (int) (bx - rx - 6.0F), 14.0F), rx, cy, col(205, 210, 222, 255), 14.0F);
        RenderUtil.drawRoundedRectWithGl(bx, cy - 8.0F, rx + rw, cy + 8.0F, 5.0F, col(22, 24, 31, 255));
        String value = property.getValue() == null ? "" : property.getValue();
        text(GuiText.trim(value, (int) (boxW - 12.0F), 13.0F), bx + 6.0F, cy, col(200, 205, 216, 255), 13.0F);
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) GuiInput.prompt(label(property), property.getValue(), property::setValue, this);
        });
        return h;
    }

    private float drawColor(final ColorProperty property, float rx, float ry, float rw, int mouseX, int mouseY) {
        boolean expanded = expandedColors.contains(property);
        String id = "c" + System.identityHashCode(property);
        float open = anim(id, expanded ? 1.0F : 0.0F, 14.0F);
        float h = 22.0F + 42.0F * open;
        rowBackground(id + "h", rx, ry, rw, 22.0F, mouseX, mouseY);
        float cy = ry + 11.0F;
        text(GuiText.trim(label(property), (int) (rw - 30.0F), 14.0F), rx, cy, col(205, 210, 222, 255), 14.0F);
        int rgb = property.getValue();
        RenderUtil.drawRoundedRectWithGl(rx + rw - 19.0F, cy - 6.0F, rx + rw + 1.0F, cy + 6.0F, 4.0F, col(255, 255, 255, 40));
        RenderUtil.drawRoundedRectWithGl(rx + rw - 18.0F, cy - 5.0F, rx + rw, cy + 5.0F, 3.5F,
                col(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255));
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + 22.0F, (button, mX, mY) -> {
            if (button == 0 || button == 1) {
                if (!expandedColors.remove(property)) expandedColors.add(property);
            }
        });

        if (open > 0.85F) {
            float[] hsb = colorStates.computeIfAbsent(property, p -> new float[3]);
            if (draggingColor != property) {
                Color c = new Color(rgb);
                Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsb);
            }
            float barH = 6.0F;
            float[] barY = {ry + 26.0F, ry + 38.0F, ry + 50.0F};
            float saveTop = clipTop;
            float saveBottom = clipBottom;
            setClip(Math.max(clipTop, ry), Math.min(clipBottom, ry + h));
            for (int i = 0; i < 6; i++) {
                float x1 = rx + rw * i / 6.0F;
                float x2 = rx + rw * (i + 1) / 6.0F;
                hGradient(x1, barY[0], x2, barY[0] + barH, Color.HSBtoRGB(i / 6.0F, 1.0F, 1.0F), Color.HSBtoRGB((i + 1) / 6.0F, 1.0F, 1.0F));
            }
            hGradient(rx, barY[1], rx + rw, barY[1] + barH, 0xFFFFFFFF, Color.HSBtoRGB(hsb[0], 1.0F, 1.0F));
            hGradient(rx, barY[2], rx + rw, barY[2] + barH, 0xFF000000, Color.HSBtoRGB(hsb[0], hsb[1], 1.0F));
            for (int i = 0; i < 3; i++) {
                final int bar = i;
                float px = rx + rw * hsb[i];
                RenderUtil.drawRoundedRectWithGl(px - 2.0F, barY[i] - 2.0F, px + 2.0F, barY[i] + barH + 2.0F, 1.5F, col(250, 250, 252, 255));
                final float tx = rx;
                final float tw = rw;
                addHit(rx - 2.0F, barY[i] - 3.0F, rx + rw + 2.0F, barY[i] + barH + 3.0F, (button, mX, mY) -> {
                    if (button == 0) {
                        draggingColor = property;
                        draggingColorBar = bar;
                        colorTrackX = tx;
                        colorTrackW = tw;
                        updateColorDrag(mX);
                    }
                });
            }
            setClip(saveTop, saveBottom);
        }
        return h;
    }

    private float drawBind(final Module module, float rx, float ry, float rw, int mouseX, int mouseY) {
        float h = 22.0F;
        rowBackground("bindh", rx, ry, rw, h, mouseX, mouseY);
        float cy = ry + h / 2.0F;
        boolean binding = bindingModule == module;
        String value = binding ? "Press a key..." : KeyBindUtil.getKeyName(module.getKey());
        if (value == null || value.isEmpty()) value = "NONE";
        float pillW = width(value, 13.0F) + 16.0F;
        text("Keybind", rx, cy, col(205, 210, 222, 255), 14.0F);
        RenderUtil.drawRoundedRectWithGl(rx + rw - pillW, cy - 8.0F, rx + rw, cy + 8.0F, 5.0F,
                binding ? col(accent, 70) : col(22, 24, 31, 255));
        text(value, rx + rw - pillW + 8.0F, cy, binding ? col(250, 250, 252, 255) : col(200, 205, 216, 255), 13.0F);
        addHit(rx - 4.0F, ry, rx + rw + 4.0F, ry + h, (button, mX, mY) -> {
            if (button == 0) bindingModule = module;
        });
        return h;
    }

    private void drawSwitch(float x, float centerY, float w, float h, float progress) {
        float y1 = centerY - h / 2.0F;
        Color off = new Color(58, 62, 74);
        RenderUtil.drawRoundedRectWithGl(x, y1, x + w, y1 + h, h / 2.0F, mixCol(off, accent, progress, 255));
        float knob = h - 3.0F;
        float kx = x + 1.5F + (w - 3.0F - knob) * progress;
        RenderUtil.drawRoundedRectWithGl(kx, y1 + 1.5F, kx + knob, y1 + 1.5F + knob, knob / 2.0F, col(248, 249, 252, 255));
    }

    private void updateDrags(int mouseX) {
        if (draggingSlider != null && sliderTrackW > 0.0F) {
            setSliderRatio(draggingSlider, clamp01((mouseX - sliderTrackX) / sliderTrackW));
        }
        if (draggingColor != null) {
            updateColorDrag(mouseX);
        }
    }

    private void updateColorDrag(int mouseX) {
        if (draggingColor == null || colorTrackW <= 0.0F) return;
        float[] hsb = colorStates.computeIfAbsent(draggingColor, p -> new float[3]);
        hsb[draggingColorBar] = clamp01((mouseX - colorTrackX) / colorTrackW);
        draggingColor.setValue(new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2])).getRGB());
    }

    private float sliderRatio(Property<?> property) {
        double min;
        double max;
        double value;
        if (property instanceof FloatProperty) {
            FloatProperty p = (FloatProperty) property;
            min = p.getMinimum();
            max = p.getMaximum();
            value = p.getValue();
        } else if (property instanceof IntProperty) {
            IntProperty p = (IntProperty) property;
            min = p.getMinimum();
            max = p.getMaximum();
            value = p.getValue();
        } else {
            PercentProperty p = (PercentProperty) property;
            min = p.getMinimum();
            max = p.getMaximum();
            value = p.getValue();
        }
        return max - min <= 0.0D ? 0.0F : clamp01((float) ((value - min) / (max - min)));
    }

    private void setSliderRatio(Property<?> property, float ratio) {
        if (property instanceof FloatProperty) {
            FloatProperty p = (FloatProperty) property;
            float min = p.getMinimum();
            float max = p.getMaximum();
            float step = max - min <= 2.0F ? 0.01F : 0.1F;
            float v = min + (max - min) * ratio;
            v = Math.round(v / step) * step;
            v = Math.round(v * 100.0F) / 100.0F;
            p.setValue(Math.max(min, Math.min(max, v)));
        } else if (property instanceof IntProperty) {
            IntProperty p = (IntProperty) property;
            p.setValue((int) Math.round(p.getMinimum() + (p.getMaximum() - p.getMinimum()) * (double) ratio));
        } else if (property instanceof PercentProperty) {
            PercentProperty p = (PercentProperty) property;
            p.setValue((int) Math.round(p.getMinimum() + (p.getMaximum() - p.getMinimum()) * (double) ratio));
        }
    }

    private void setSliderText(Property<?> property, String text) {
        try {
            if (property instanceof FloatProperty) {
                FloatProperty p = (FloatProperty) property;
                p.setValue(Math.max(p.getMinimum(), Math.min(p.getMaximum(), Float.parseFloat(text.trim()))));
            } else if (property instanceof IntProperty) {
                IntProperty p = (IntProperty) property;
                p.setValue(Math.max(p.getMinimum(), Math.min(p.getMaximum(), Integer.parseInt(text.trim()))));
            } else if (property instanceof PercentProperty) {
                PercentProperty p = (PercentProperty) property;
                p.setValue(Math.max(p.getMinimum(), Math.min(p.getMaximum(), Integer.parseInt(text.trim().replace("%", "")))));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private String rawValue(Property<?> property) {
        return String.valueOf(property.getValue());
    }

    private String sliderText(Property<?> property) {
        if (property instanceof FloatProperty) {
            String s = String.format(Locale.US, "%.2f", ((FloatProperty) property).getValue());
            if (s.contains(".")) {
                s = s.replaceAll("0+$", "");
                if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
            }
            return s;
        }
        if (property instanceof PercentProperty) return property.getValue() + "%";
        return String.valueOf(property.getValue());
    }

    private List<Module> visibleModules() {
        if (search.isEmpty()) return categories.get(CATEGORY_NAMES[selectedCategory]);
        String query = search.toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (List<Module> list : categories.values()) {
            for (Module module : list) {
                if (module.getName().toLowerCase(Locale.ROOT).contains(query)) result.add(module);
            }
        }
        result.sort(Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT)));
        return result;
    }

    private Icon categoryIcon(String name) {
        switch (name) {
            case "Combat":
                return Icon.STRENGTH;
            case "Movement":
                return Icon.SPEED;
            case "Render":
                return Icon.VISION;
            case "Player":
                return Icon.RESIST;
            case "Misc":
                return Icon.INFO;
            default:
                return Icon.CHECK;
        }
    }

    private void hGradient(float x1, float y1, float x2, float y2, int c1, int c2) {
        int a = (int) (255 * alpha);
        RenderUtil.enableRenderState();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y1, 0.0D).color(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, a).endVertex();
        wr.pos(x1, y2, 0.0D).color(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, a).endVertex();
        wr.pos(x2, y2, 0.0D).color(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, a).endVertex();
        wr.pos(x2, y1, 0.0D).color(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, a).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        RenderUtil.disableRenderState();
    }

    private void text(String s, float x, float centerY, int color, float size) {
        float cap = FontManager.getCapHeight(size);
        FontManager.drawString(s, x, centerY + cap / 2.0F - FontManager.getBaseline(size), color, false, size);
    }

    private float width(String s, float size) {
        return FontManager.getStringWidth(s, size);
    }

    private float anim(String key, float target, float speed) {
        Float current = anims.get(key);
        float value = current == null ? target : current;
        value += (target - value) * (1.0F - (float) Math.exp(-dt * speed));
        if (Math.abs(target - value) < 0.001F) value = target;
        anims.put(key, value);
        return value;
    }

    private int col(int r, int g, int b, int a) {
        int scaled = Math.round(a * alpha);
        if (a > 0) scaled = Math.max(4, scaled);
        return new Color(r, g, b, Math.max(0, Math.min(255, scaled))).getRGB();
    }

    private int col(Color c, int a) {
        return col(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private Color mix(Color a, Color b, float t) {
        t = clamp01(t);
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private int mixCol(Color a, Color b, float t, int alphaValue) {
        return col(mix(a, b, t), alphaValue);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static boolean inside(int mx, int my, float x1, float y1, float x2, float y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private void addHit(float x1, float y1, float x2, float y2, ClickAction action) {
        float top = Math.max(y1, clipTop);
        float bottom = Math.min(y2, clipBottom);
        if (bottom <= top) return;
        hits.add(new Hit(x1, top, x2, bottom, action));
    }

    private void setClip(float top, float bottom) {
        clipTop = top;
        clipBottom = bottom;
    }

    private void resetClip() {
        clipTop = -100000.0F;
        clipBottom = 100000.0F;
    }

    private void scissor(float x, float y, float w, float h) {
        ScaledResolution sr = new ScaledResolution(mc);
        int factor = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) Math.floor(x * factor), (int) Math.floor((sr.getScaledHeight() - y - h) * factor),
                (int) Math.ceil(w * factor), (int) Math.ceil(h * factor));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (bindingModule != null) {
            if (button != 0) bindingModule.setKey(button - 100);
            bindingModule = null;
            return;
        }
        searchFocused = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (inside(mouseX, mouseY, hit.x1, hit.y1, hit.x2, hit.y2)) {
                hit.action.click(button, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        draggingSlider = null;
        draggingColor = null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        float amount = wheel > 0 ? -32.0F : 32.0F;
        if (inside(mouseX, mouseY, listX1, listY1, listX2, listY2)) {
            moduleScrollTarget += amount;
        } else if (inside(mouseX, mouseY, setX1, setY1, setX2, setY2)) {
            settingScrollTarget += amount;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                bindingModule = null;
                return;
            }
            if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                bindingModule.setKey(bindingModule instanceof GuiModule ? Keyboard.KEY_RSHIFT : 0);
            } else {
                bindingModule.setKey(keyCode);
            }
            bindingModule = null;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (searchFocused || !search.isEmpty()) {
                search = "";
                searchFocused = false;
                moduleScroll = moduleScrollTarget = 0.0F;
            } else {
                mc.displayGuiScreen(null);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_F && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
            searchFocused = true;
            return;
        }
        if (!searchFocused) return;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            searchFocused = false;
            List<Module> found = visibleModules();
            if (!found.isEmpty()) selectedModule = found.get(0);
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                moduleScroll = moduleScrollTarget = 0.0F;
            }
            return;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && search.length() < 24) {
            if (search.isEmpty() && typedChar == ' ') return;
            search += typedChar;
            moduleScroll = moduleScrollTarget = 0.0F;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        dragging = false;
        draggingSlider = null;
        draggingColor = null;
        bindingModule = null;
        savePositions();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void clampWindow() {
        windowX = Math.max(5, Math.min(windowX, Math.max(5, width - W - 5)));
        windowY = Math.max(5, Math.min(windowY, Math.max(5, height - H - 5)));
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        json.addProperty("version", 5);
        json.addProperty("x", windowX);
        json.addProperty("y", windowY);
        json.addProperty("category", selectedCategory);
        if (selectedModule != null) json.addProperty("module", selectedModule.getName());
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            if (json.has("version") && json.get("version").getAsInt() >= 5) {
                if (json.has("x")) windowX = json.get("x").getAsInt();
                if (json.has("y")) windowY = json.get("y").getAsInt();
            }
            if (json.has("category")) {
                selectedCategory = Math.max(0, Math.min(json.get("category").getAsInt(), CATEGORY_NAMES.length - 1));
            }
            if (json.has("module")) {
                String name = json.get("module").getAsString();
                for (Module module : categories.get(CATEGORY_NAMES[selectedCategory])) {
                    if (module.getName().equalsIgnoreCase(name)) selectedModule = module;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
