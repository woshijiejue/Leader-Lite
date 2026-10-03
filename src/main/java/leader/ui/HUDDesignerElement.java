package leader.ui;

import leader.Leader;
import leader.module.Module;
import leader.module.modules.player.Scaffold;
import leader.module.modules.render.*;
import leader.module.modules.render.notification.Notification;
import leader.property.Property;
import leader.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** One source of truth for editor previews, bounds and HUD-only settings. */
final class HUDDesignerElement {
    final String name;
    final Module module;

    HUDDesignerElement(String name, Module module) {
        this.name = name;
        this.module = module;
    }

    float[] size(int islandState) {
        if (module instanceof Island) return ((Island) module).previewSize(islandState);
        if (module instanceof HUD) return ((HUD) module).previewSize();
        if (module instanceof TargetHUD) return ((TargetHUD) module).previewSize();
        if (module instanceof Notification) return ((Notification) module).previewSize();
        if (module instanceof Watermark) return ((Watermark) module).previewSize();
        if (module instanceof GifDisplay) return ((GifDisplay) module).previewSize();
        if (module instanceof Potion) return ((Potion) module).previewSize();
        if (module instanceof BedTracker) return ((BedTracker) module).previewSize();
        if (module instanceof Indicators) return ((Indicators) module).previewSize();
        if (module instanceof Scaffold) return ((Scaffold) module).counterPreviewSize();
        return new float[]{148, 30};
    }

    float[] defaults() {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        float[] size = size(0);
        if (module instanceof Island) return new float[]{(sr.getScaledWidth() - size[0]) / 2, 8};
        if (module instanceof Scaffold) return new float[]{(sr.getScaledWidth() - size[0]) / 2, sr.getScaledHeight() / 2.0F + 16};
        if (module instanceof HUD) return new float[]{((HUD) module).align.getValue() == 1 ? sr.getScaledWidth() - 2 : 2, 2};
        if (module instanceof Indicators) {
            float radius = 10 + ((Indicators) module).offset.getValue();
            return new float[]{sr.getScaledWidth() / 2.0F - radius, sr.getScaledHeight() / 2.0F - radius};
        }
        if (module instanceof Watermark) return new float[]{4, 4};
        if (module instanceof GifDisplay) return new float[]{200, 100};
        if (module instanceof Notification) return new float[]{2, 20};
        if (module instanceof Potion || module instanceof BedTracker) return new float[]{2, 2};
        return new float[]{sr.getScaledWidth() / 2.0F - 75, 40};
    }

    float[] origin() {
        float[] d = defaults();
        float x = Leader.hudElementManager.x(name, d[0], d[1]);
        float y = Leader.hudElementManager.y(name, d[0], d[1]);
        if (module instanceof Potion) {
            Potion potion = (Potion) module;
            int pad = potion.displayMode.getValue() <= 1 ? 4 : 6;
            x += pad;
            y += pad;
            if (potion.mode.getValue() == 0) {
                x = new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth()
                        - potion.editorAnchorWidth() - x;
            }
        } else if (module instanceof Notification) {
            boolean classic = ((Notification) module).style.getValue() == 0;
            x += classic ? 4 : 6;
            y += classic ? 4 : 8;
        }
        return new float[]{x, y};
    }

    void setOrigin(float x, float y) {
        if (module instanceof Potion) {
            Potion potion = (Potion) module;
            int pad = potion.displayMode.getValue() <= 1 ? 4 : 6;
            if (potion.mode.getValue() == 0) {
                x = new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth()
                        - potion.editorAnchorWidth() - x;
            }
            x -= pad;
            y -= pad;
        } else if (module instanceof Notification) {
            boolean classic = ((Notification) module).style.getValue() == 0;
            x -= classic ? 4 : 6;
            y -= classic ? 4 : 8;
        }
        Leader.hudElementManager.set(name, x, y);
    }

    float[] bounds(int islandState) {
        float[] p = origin(), size = size(islandState);
        float x = p[0], y = p[1];
        if (module instanceof HUD) {
            float sc = ((HUD) module).scale.getValue();
            x -= ((HUD) module).align.getValue() == 1 ? size[0] - 3 * sc : 3 * sc;
        } else if (module instanceof Indicators) {
            Indicators indicators = (Indicators) module;
            float radius = 10 + indicators.offset.getValue();
            x += radius - size[0] / 2;
            y += radius - size[1] / 2;
        }
        return new float[]{x, y, size[0], size[1]};
    }

    void preview(float x, float y, float alpha, int islandState) {
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            renderPreview(x, y, alpha, islandState);
        } finally {
            GlStateManager.color(1, 1, 1, 1);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        }
    }

    private void renderPreview(float x, float y, float alpha, int islandState) {
        if (module instanceof Island) ((Island) module).renderPreview(x, y, alpha, islandState);
        else if (module instanceof HUD) ((HUD) module).renderPreview(x, y, alpha);
        else if (module instanceof TargetHUD) ((TargetHUD) module).renderPreview(x, y, alpha);
        else if (module instanceof Notification) ((Notification) module).renderPreview(x, y, alpha);
        else if (module instanceof Watermark) ((Watermark) module).renderPreview(x, y, alpha);
        else if (module instanceof GifDisplay) ((GifDisplay) module).renderPreview(x, y, alpha);
        else if (module instanceof Indicators) ((Indicators) module).renderPreview(x, y, alpha);
        else if (module instanceof Potion) ((Potion) module).renderPreview(x, y, alpha);
        else if (module instanceof BedTracker) ((BedTracker) module).renderPreview(x, y, alpha);
        else if (module instanceof Scaffold) ((Scaffold) module).renderCounterPreview(x, y, alpha);
    }

    String styleName() {
        if (module instanceof Island) return "Adaptive";
        if (module instanceof TargetHUD) return ((TargetHUD) module).mode.getModeString();
        if (module instanceof Notification) return ((Notification) module).style.getModeString();
        if (module instanceof Potion) return ((Potion) module).displayMode.getModeString();
        if (module instanceof Watermark) return ((Watermark) module).mode.getModeString();
        if (module instanceof HUD) return ((HUD) module).align.getModeString();
        if (module instanceof GifDisplay) return ((GifDisplay) module).gifMode.getModeString();
        return "Preview";
    }

    List<Property<?>> settings() {
        List<Property<?>> result = new ArrayList<>();
        if (module instanceof Scaffold) {
            Scaffold scaffold = (Scaffold) module;
            result.add(scaffold.blockCounter);
            result.add(scaffold.bPSRender);
        } else if (module instanceof BedTracker) {
            BedTracker tracker = (BedTracker) module;
            result.addAll(Arrays.asList(tracker.hud, tracker.hudScale, tracker.hudShadow));
        } else {
            List<Property<?>> properties = Leader.propertyManager.properties.get(module.getClass());
            if (properties != null) {
                for (Property<?> property : properties) {
                    String n = property.getName();
                    // The array list also owns unrelated chat/sound features; keep this panel HUD-specific.
                    if (module instanceof HUD && Arrays.asList("chat-outline", "blink-timer", "toggle-sounds", "toggle-alerts").contains(n)) continue;
                    if (property instanceof BooleanProperty || property instanceof ModeProperty || property instanceof FloatProperty
                            || property instanceof IntProperty || property instanceof PercentProperty || property instanceof ColorProperty) {
                        result.add(property);
                    }
                }
            }
        }
        result.removeIf(p -> !p.isVisible());
        return result;
    }

    boolean supportsBackground() {
        return !(module instanceof BedTracker || module instanceof Indicators || module instanceof GifDisplay)
                && (!(module instanceof Watermark) || ((Watermark) module).mode.getValue() != 0);
    }

    String description() {
        if (module instanceof Island) return "Adaptive status / fused HUD";
        if (module instanceof HUD) return "Module list / alignment and theme";
        if (module instanceof TargetHUD) return "Combat card / target tracking";
        if (module instanceof Scaffold) return "Block inventory / independent preview";
        if (module instanceof Indicators) return "Projectile radar / center and radius";
        if (module instanceof Notification) return "Alerts / style and timing";
        if (module instanceof Potion) return "Active effects / display style";
        if (module instanceof Watermark) return "Brand / metrics and typography";
        if (module instanceof GifDisplay) return "Image / size and aspect ratio";
        return "Bed status / text appearance";
    }
}
