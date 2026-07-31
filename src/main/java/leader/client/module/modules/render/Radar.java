package leader.client.module.modules.render;

import leader.client.Leader;
import leader.client.util.misc.ChatColors;
import leader.client.event.EventTarget;
import leader.client.event.types.Priority;
import leader.client.events.Render2DEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ColorValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.util.render.RenderUtil;
import leader.client.util.player.TeamUtil;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.stream.Collectors;

public class Radar extends Module {

    public final ListValue colorMode = new ListValue("color", new String[]{"DEFAULT", "TEAMS", "HUD"}, "DEFAULT", this);
    public final SliderValue position = new SliderValue("position", 0, 0, 4, Representation.INT, this);
    public final SliderValue offsetX = new SliderValue("offset-x", 60, 0, 1000, () -> this.position.getValue().intValue() != 4, Representation.INT, this);
    public final SliderValue offsetY = new SliderValue("offset-y", 60, 0, 1000, () -> this.position.getValue().intValue() != 4, Representation.INT, this);
    public final SliderValue radarRadius = new SliderValue("radar-radius", 55, 10, 200, Representation.INT, this);
    public final SliderValue dotRadius = new SliderValue("dot-radius", 1.5, 0.1, 5.0, Representation.FLOAT, this);
    public final BoolValue showPlayers = new BoolValue("players", true, this);
    public final BoolValue showFriends = new BoolValue("friends", true, this);
    public final BoolValue showEnemies = new BoolValue("enemies", true, this);
    public final BoolValue showBots = new BoolValue("bots", false, this);
    public final BoolValue showPVP = new BoolValue("show-pvp", false, this);
    public final ColorValue fillColor = new ColorValue("fill-color", Color.GRAY, this);
    public final ColorValue outlineColor = new ColorValue("outline-color", Color.DARK_GRAY, this);
    public final ColorValue crossColor = new ColorValue("cross-color", Color.LIGHT_GRAY, this);

    public Radar() {
        super("Radar", false);
    }

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(entityPlayer)) {
                return this.showBots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.showFriends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.showEnemies.getValue() : this.showPlayers.getValue();
            }
        } else {
            return false;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = Leader.friendComponent.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = Leader.targetComponent.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        } else {
            if (this.colorMode.is("DEFAULT")) {
                return TeamUtil.getTeamColor(entityPlayer, 1.0F);
            } else if (this.colorMode.is("TEAMS")) {
                int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                return new Color(teamColor | 255 << 24, true);
            } else if (this.colorMode.is("HUD")) {
                int color = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                return new Color(color | 255 << 24, true);
            } else {
                return Color.WHITE;
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);

        double centerX, centerY;
        int posVal = this.position.getValue().intValue();
        int offXVal = this.offsetX.getValue().intValue();
        int offYVal = this.offsetY.getValue().intValue();
        int radiusVal = this.radarRadius.getValue().intValue();
        if (posVal == 4) {
            centerX = sr.getScaledWidth() / 2.0F;
            centerY = sr.getScaledHeight() / 2.0F;
        } else {
            centerX = (posVal & 0x1) == 0x1 ? Math.max(sr.getScaledWidth() - offXVal, 0) : Math.min(offXVal, sr.getScaledWidth());
            centerY = (posVal & 0x2) == 0x2 ? Math.max(sr.getScaledHeight() - offYVal, 0) : Math.min(offYVal, sr.getScaledHeight());
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(hud.scale.getValue(), hud.scale.getValue(), 1.0f);
        GlStateManager.translate(centerX, centerY, 0.0f);

        RenderUtil.enableRenderState();

        float yaw = (float)Math.toRadians(mc.thePlayer.rotationYaw);
        if (mc.gameSettings.thirdPersonView != 2) {
            yaw += (float)Math.toRadians(180.0F);
        }
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        Color fill = this.fillColor.getValue();
        this.drawRadarCircle(0.0, 0, yaw, radiusVal, 64, new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 100).getRGB(), this.outlineColor.getValue().getRGB(), this.crossColor.getValue().getRGB());
        for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
            double dx = (player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks()) - mc.thePlayer.posX;
            double dz = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks()) - mc.thePlayer.posZ;

            double relX = dx * cos + dz * sin;
            double relY = dz * cos - dx * sin;

            double dist = Math.sqrt(relX * relX + relY * relY);
            double scale = dist < radiusVal ? 1.0F : (double) radiusVal / dist;
            double px = relX * scale;
            double py = relY * scale;

            RenderUtil.fillCircle(px, py, dotRadius.getValue(), 12, getEntityColor(player).getRGB());
        }
        if (this.showPVP.getValue()) {
            double dx = -mc.thePlayer.posX;
            double dz = -mc.thePlayer.posZ;

            double relX = dx * cos + dz * sin;
            double relY = dz * cos - dx * sin;

            double dist = Math.sqrt(relX * relX + relY * relY);
            double scale = dist < radiusVal * 2 ? 1.0F : (double) radiusVal * 2 / dist;
            double px = relX * scale;
            double py = relY * scale;
            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.scale(hud.scale.getValue() / 2, hud.scale.getValue() / 2, 1.0f);
            FontManager.drawString("PVP",
                    (float) (px - FontManager.getStringWidth("PVP") / 2.0F),
                    (float) (py - FontManager.getFontHeight() / 2.0F),
                    Color.WHITE.getRGB(), hud.shadow.getValue());
            GlStateManager.popMatrix();
        }
        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }

    public void drawRadarCircle(double x, double y, double angle, double radius,
                                       int segments,
                                       int fillColor,
                                       int outlineColor,
                                       int crossColor) {

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if ((fillColor >>> 24) != 0) {
            RenderUtil.setColor(fillColor);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2d(x, y);
            for (int i = 0; i <= segments; i++) {
                double angle1 = i * (Math.PI * 2 / segments);
                GL11.glVertex2d(
                        x + Math.cos(angle1) * radius,
                        y + Math.sin(angle1) * radius
                );
            }
            GL11.glEnd();
        }

        if ((outlineColor >>> 24) != 0) {
            RenderUtil.setColor(outlineColor);
            GL11.glLineWidth(2f);

            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i <= segments; i++) {
                double angle1 = i * (Math.PI * 2 / segments);
                GL11.glVertex2d(
                        x + Math.cos(angle1) * radius,
                        y + Math.sin(angle1) * radius
                );
            }
            GL11.glEnd();
        }

        if ((crossColor >>> 24) != 0) {
            RenderUtil.setColor(crossColor);
            GL11.glLineWidth(1.5f);
            GL11.glBegin(GL11.GL_LINES);

            double dx1 = Math.sin(angle);
            double dy1 = Math.cos(angle);

            double dx2 = Math.sin(angle + Math.PI / 2);
            double dy2 = Math.cos(angle + Math.PI / 2);

            GL11.glVertex2d(x - dx1 * radius, y - dy1 * radius);
            GL11.glVertex2d(x + dx1 * radius, y + dy1 * radius);

            GL11.glVertex2d(x - dx2 * radius, y - dy2 * radius);
            GL11.glVertex2d(x + dx2 * radius, y + dy2 * radius);

            GL11.glEnd();

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
            int color = hud.getColor(System.currentTimeMillis()).getRGB();
            FontManager.drawString("N",
                    (float) (x - dx1 * (radius + 5)) - FontManager.getStringWidth("N") / 2.0F,
                    (float) (y - dy1 * (radius + 5)) - FontManager.getFontHeight() / 2.0F,
                    color, hud.shadow.getValue());
            FontManager.drawString("E",
                    (float) (x + dx2 * (radius + 5)) - FontManager.getStringWidth("E") / 2.0F,
                    (float) (y + dy2 * (radius + 5)) - FontManager.getFontHeight() / 2.0F,
                    color, hud.shadow.getValue());
            FontManager.drawString("S",
                    (float) (x + dx1 * (radius + 5)) - FontManager.getStringWidth("S") / 2.0F,
                    (float) (y + dy1 * (radius + 5)) - FontManager.getFontHeight() / 2.0F,
                    color, hud.shadow.getValue());
            FontManager.drawString("W",
                    (float) (x - dx2 * (radius + 5)) - FontManager.getStringWidth("W") / 2.0F,
                    (float) (y - dy2 * (radius + 5)) - FontManager.getFontHeight() / 2.0F,
                    color, hud.shadow.getValue());
            GlStateManager.disableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
        }

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }
}
