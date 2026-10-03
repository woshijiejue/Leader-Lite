package leader.module.modules.render;

import leader.enums.ChatColors;
import leader.Leader;
import leader.event.EventTarget;
import leader.events.Render2DEvent;
import leader.module.Module;
import leader.util.RenderUtil;
import leader.util.RotationUtil;
import leader.util.TeamUtil;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class Indicators extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty scale = new FloatProperty("scale", 1.0f, 0.5f, 1.5f);
    public final FloatProperty offset = new FloatProperty("offset", 50.0f, 0.0f, 255.0f);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty fireballs = new BooleanProperty("fireballs", true);
    public final BooleanProperty pearls = new BooleanProperty("pearls", true);
    public final BooleanProperty arrows = new BooleanProperty("arrows", true);
    public final BooleanProperty egg = new BooleanProperty("egg", true);
    public final BooleanProperty snowball = new BooleanProperty("snowball", true);

    private static int trackedCount = 0;

    private boolean shouldRender(Entity entity) {
        double d = (entity.posX - entity.lastTickPosX) * (Indicators.mc.thePlayer.posX - entity.posX) + (entity.posY - entity.lastTickPosY) * (Indicators.mc.thePlayer.posY + (double) Indicators.mc.thePlayer.getEyeHeight() - entity.posY - (double) entity.height / 2.0) + (entity.posZ - entity.lastTickPosZ) * (Indicators.mc.thePlayer.posZ - entity.posZ);
        if (d == 0.0) {
            return false;
        }
        if (d < 0.0) {
            if (this.directionCheck.getValue()) {
                return false;
            }
        }
        if (this.fireballs.getValue() && entity instanceof EntityFireball) return true;
        if (this.pearls.getValue() && entity instanceof EntityEnderPearl) return true;
        if (this.arrows.getValue() && entity instanceof EntityArrow) return true;
        if (this.egg.getValue() && entity instanceof EntityEgg) return true;
        if (this.snowball.getValue() && entity instanceof EntitySnowball) return true;
        return false;
    }

    private Item getIndicatorItem(Entity entity) {
        if (entity instanceof EntityFireball) {
            return Items.fire_charge;
        }
        if (entity instanceof EntityEnderPearl) {
            return Items.ender_pearl;
        }
        if (entity instanceof EntityArrow) {
            return Items.arrow;
        }
        if (entity instanceof EntityEgg) {
            return Items.egg;
        }
        if (entity instanceof EntitySnowball) {
            return Items.snowball;
        }
        return new Item();
    }

    private Color getIndicatorColor(Entity entity) {
        if (entity instanceof EntityFireball) {
            return new Color(12676363);
        }
        if (entity instanceof EntityEnderPearl) {
            return new Color(2458740);
        }
        if (entity instanceof EntityArrow) {
            return new Color(0x969696);
        }
        return new Color(-1);
    }

    public static int tracked() {
        return trackedCount;
    }

    public Indicators() {
        super("Indicators", false, true);
    }

    @EventTarget
    public void onRender(Render2DEvent render2DEvent) {
        if (!this.isEnabled() || Leader.hudElementManager.isSuppressed("Indicators")) {
            return;
        }
        List<Entity> targets = TeamUtil.getLoadedEntitiesSorted().stream().filter(this::shouldRender).collect(Collectors.toList());
        trackedCount = targets.size();
        for (Entity entity : targets) {
            float offset = 10.0f + this.offset.getValue();
            float yawBetween = RotationUtil.getYawBetween(RenderUtil.lerpDouble(Indicators.mc.thePlayer.posX, Indicators.mc.thePlayer.prevPosX, render2DEvent.getPartialTicks()), RenderUtil.lerpDouble(Indicators.mc.thePlayer.posZ, Indicators.mc.thePlayer.prevPosZ, render2DEvent.getPartialTicks()), RenderUtil.lerpDouble(entity.posX, entity.prevPosX, render2DEvent.getPartialTicks()), RenderUtil.lerpDouble(entity.posZ, entity.prevPosZ, render2DEvent.getPartialTicks()));
            if (Indicators.mc.gameSettings.thirdPersonView == 2) {
                yawBetween += 180.0f;
            }
            float x = (float) Math.sin(Math.toRadians(yawBetween));
            float z = (float) Math.cos(Math.toRadians(yawBetween)) * -1.0f;
            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0f);
            ScaledResolution sr = new ScaledResolution(mc);
            float radius = 10.0F + this.offset.getValue();
            float originX = Leader.hudElementManager.x("Indicators", sr.getScaledWidth() / 2.0F - radius, sr.getScaledHeight() / 2.0F - radius);
            float originY = Leader.hudElementManager.y("Indicators", sr.getScaledWidth() / 2.0F - radius, sr.getScaledHeight() / 2.0F - radius);
            GlStateManager.translate((originX + radius) / this.scale.getValue(), (originY + radius) / this.scale.getValue(), 0.0f);
            GlStateManager.pushMatrix();
            GlStateManager.translate((offset + 0.0f) * x - 8.0f, (offset + 0.0f) * z - 8.0f, -300.0f);
            mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(this.getIndicatorItem(entity)), 0, 0);
            GlStateManager.popMatrix();
            String string = String.format("%dm", (int) Indicators.mc.thePlayer.getDistanceToEntity(entity));
            GlStateManager.pushMatrix();
            GlStateManager.translate((offset + 0.0f) * x - (float) FontManager.getStringWidth(string) / 2.0f + 1.0f, (offset + 0.0f) * z + 1.0f, -100.0f);
            FontManager.drawStringWithShadow(string, 0.0f, 0.0f, ChatColors.GRAY.toAwtColor() & 0xFFFFFF | 0xBF000000);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.translate((offset + 15.0f) * x + 1.0f, (offset + 15.0f) * z + 1.0f, -100.0f);
            RenderUtil.enableRenderState();
            RenderUtil.drawArrow(0.0f, 0.0f, (float) (Math.atan2(z, x) + Math.PI), 7.5f, 1.5f, this.getIndicatorColor(entity).getRGB());
            RenderUtil.disableRenderState();
            GlStateManager.popMatrix();
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }

    public float[] previewSize() {
        float radius = 10 + offset.getValue();
        float extent = radius * 1.3F * scale.getValue() + 18;
        return new float[]{extent * 2, extent * 2};
    }

    public void renderPreview(float x, float y, float alpha) {
        float uiScale = this.scale.getValue();
        float iconSize = 16.0F;
        float spacing = 10.0F + this.offset.getValue();
        float centerX = x + spacing;
        float centerY = y + spacing;

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.scale(uiScale, uiScale, 1.0F);

        Item[] items = {Items.fire_charge, Items.ender_pearl, Items.arrow, Items.egg, Items.snowball};
        Color[] colors = {new Color(12676363), new Color(2458740), new Color(0x969696), new Color(0xB6A890), new Color(0xD9E2EB)};
        float[] angles = {-90, -18, 54, 126, 198};

        for (int i = 0; i < items.length; i++) {
            if ((i == 0 && !fireballs.getValue()) || (i == 1 && !pearls.getValue()) || (i == 2 && !arrows.getValue())
                    || (i == 3 && !egg.getValue()) || (i == 4 && !snowball.getValue())) continue;
            float rad = (float) Math.toRadians(angles[i]);
            float offsetX = (float) Math.cos(rad) * spacing;
            float offsetY = (float) Math.sin(rad) * spacing;

            GlStateManager.pushMatrix();
            GlStateManager.translate(offsetX - iconSize / 2.0F, offsetY - iconSize / 2.0F, 0.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(items[i]), 0, 0);
            GlStateManager.popMatrix();

            GlStateManager.color(1, 1, 1, 1);

            String dist = (i + 1) * 15 + "m";
            float textW = FontManager.getStringWidth(dist, 12);
            GlStateManager.pushMatrix();
            GlStateManager.translate(offsetX - textW / 2.0F, offsetY + iconSize / 2.0F + 2.0F, 0.0F);
            FontManager.drawString(dist, 0.0F, 0.0F,
                    new Color(174, 185, 202, (int)(alpha * 255)).getRGB(), false, 12);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(offsetX * 1.3F, offsetY * 1.3F, 0.0F);
            GlStateManager.rotate((float) Math.toDegrees(Math.atan2(offsetY, offsetX)), 0.0F, 0.0F, 1.0F);
            RenderUtil.enableRenderState();
            Color arrowColor = colors[i];
            RenderUtil.drawArrow(0.0F, 0.0F, 0.0F, 7.5F, 1.5F,
                    new Color(arrowColor.getRed(), arrowColor.getGreen(), arrowColor.getBlue(),
                            (int)(arrowColor.getAlpha() * alpha)).getRGB());
            RenderUtil.disableRenderState();
            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();
    }
}
