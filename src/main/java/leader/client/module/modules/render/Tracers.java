package leader.client.module.modules.render;

import leader.client.Leader;
import leader.client.util.misc.ChatColors;
import leader.client.event.EventTarget;
import leader.client.events.Render2DEvent;
import leader.client.events.Render3DEvent;
import leader.mixin.accessor.IAccessorMinecraft;
import leader.client.module.Module;
import leader.client.util.render.RenderUtil;
import leader.client.util.player.RotationUtil;
import leader.client.util.player.TeamUtil;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.stream.Collectors;

public class Tracers extends Module {
    
    public final ListValue colorMode = new ListValue("color", new String[]{"DEFAULT", "TEAMS", "HUD"}, "DEFAULT", this);
    public final BoolValue drawLines = new BoolValue("lines", true, this);
    public final BoolValue drawArrows = new BoolValue("arrows", false, this);
    public final SliderValue opacity = new SliderValue("opacity", 100, 0, 100, Representation.INT, this);
    public final SliderValue distance = new SliderValue("distance", 512, 0, 512, Representation.INT, this);
    public final BoolValue showPlayers = new BoolValue("players", true, this);
    public final BoolValue showFriends = new BoolValue("friends", true, this);
    public final BoolValue showEnemies = new BoolValue("enemies", true, this);
    public final BoolValue showBots = new BoolValue("bots", false, this);

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > this.distance.getValue().floatValue()) {
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

    private Color getEntityColor(EntityPlayer entityPlayer, float alpha) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = Leader.friendComponent.getColor();
            return new Color((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, alpha);
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = Leader.targetComponent.getColor();
            return new Color((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, alpha);
        } else {
            if (this.colorMode.is("DEFAULT")) {
                return TeamUtil.getTeamColor(entityPlayer, alpha);
            } else if (this.colorMode.is("TEAMS")) {
                int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                return new Color(teamColor & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
            } else if (this.colorMode.is("HUD")) {
                int color = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                return new Color(color & Color.WHITE.getRGB() | (int) (alpha * 255.0F) << 24, true);
            } else {
                return new Color(1.0F, 1.0F, 1.0F, alpha);
            }
        }
    }

    public Tracers() {
        super("Tracers", false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled() && this.drawLines.getValue()) {
            RenderUtil.enableRenderState();
            Vec3 position;
            if (mc.gameSettings.thirdPersonView == 0) {
                position = new Vec3(0.0, 0.0, 1.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationPitch,
                                                        mc.getRenderViewEntity().prevRotationPitch,
                                                        ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationYaw,
                                                        mc.getRenderViewEntity().prevRotationYaw,
                                                        ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        );
            } else {
                position = new Vec3(0.0, 0.0, 0.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                                        )
                                )
                        );
            }
            position = new Vec3(position.xCoord, position.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), position.zCoord);
            for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
                Color color = this.getEntityColor(player, this.opacity.getValue().floatValue() / 100.0F);
                double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks()) - (player.isSneaking() ? 0.125 : 0.0);
                double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks());
                RenderUtil.drawLine3D(
                        position,
                        x,
                        y + (double) player.getEyeHeight(),
                        z,
                        (float) color.getRed() / 255.0F,
                        (float) color.getGreen() / 255.0F,
                        (float) color.getBlue() / 255.0F,
                        (float) color.getAlpha() / 255.0F,
                        1.5F
                );
            }
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && this.drawArrows.getValue()) {
            for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
                float yawBetween = RotationUtil.getYawBetween(
                        RenderUtil.lerpDouble(mc.thePlayer.posX, mc.thePlayer.prevPosX, event.getPartialTicks()),
                        RenderUtil.lerpDouble(mc.thePlayer.posZ, mc.thePlayer.prevPosZ, event.getPartialTicks()),
                        RenderUtil.lerpDouble(player.posX, player.prevPosX, event.getPartialTicks()),
                        RenderUtil.lerpDouble(player.posZ, player.prevPosZ, event.getPartialTicks())
                );
                if (mc.gameSettings.thirdPersonView == 2) {
                    yawBetween += 180.0F;
                }
                float arrowDirX = (float) Math.sin(Math.toRadians(yawBetween));
                float arrowDirY = (float) Math.cos(Math.toRadians(yawBetween)) * -1.0F;
                float opacity = this.opacity.getValue().floatValue() / 100.0F;
                yawBetween = Math.abs(MathHelper.wrapAngleTo180_float(yawBetween));
                if (yawBetween < 30.0F) {
                    opacity = 0.0F;
                } else if (yawBetween < 60.0F) {
                    opacity *= (yawBetween - 30.0F) / 30.0F;
                }
                HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
                GlStateManager.pushMatrix();
                GlStateManager.scale(hud.scale.getValue(), hud.scale.getValue(), 0.0F);
                GlStateManager.translate(
                        (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / hud.scale.getValue(),
                        (float) new ScaledResolution(mc).getScaledHeight() / 2.0F / hud.scale.getValue(),
                        0.0F
                );
                GlStateManager.pushMatrix();
                GlStateManager.translate(55.0F * arrowDirX + 1.0F, 55.0F * arrowDirY + 1.0F, -100.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawTriangle(
                        0.0F,
                        0.0F,
                        (float) (Math.atan2(arrowDirY, arrowDirX) + Math.PI),
                        10.0F,
                        this.getEntityColor(player, opacity).getRGB()
                );
                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();
                GlStateManager.popMatrix();
            }
        }
    }
}
