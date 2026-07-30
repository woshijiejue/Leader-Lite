package leader.client.module.modules.render;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.events.AttackEvent;
import leader.client.events.Render3DEvent;
import leader.mixin.IAccessorRenderManager;
import leader.client.module.Module;
import leader.client.module.modules.combat.KillAura;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.FloatProperty;
import leader.client.property.properties.IntProperty;
import leader.client.property.properties.ModeProperty;
import leader.client.util.render.RenderUtil;
import leader.client.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class TargetESP extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Default", "Hud", "Scan"});
    public final BooleanProperty killAuraOnly = new BooleanProperty("killaura-only", false);
    public final IntProperty targetTime = new IntProperty("target-time", 1000, 500, 3000, () -> !killAuraOnly.getValue());
    public final FloatProperty scanThickness = new FloatProperty("scan-thickness", 0.6F, 0.1F, 2.5F, () -> mode.getValue() == 2);

    private EntityLivingBase attackTarget;
    private long lastAttackTime;

    public TargetESP() {
        super("TargetESP", false);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!killAuraOnly.getValue() && event.getTarget() instanceof EntityLivingBase) {
            attackTarget = (EntityLivingBase) event.getTarget();
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) return;

        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (killAuraOnly.getValue() && (killAura == null || !killAura.isEnabled())) return;

        EntityLivingBase target;
        if (killAuraOnly.getValue()) {
            target = killAura != null ? killAura.getTarget() : null;
        } else {
            if (attackTarget == null || System.currentTimeMillis() - lastAttackTime > targetTime.getValue()) return;
            target = attackTarget;
        }
        if (!TeamUtil.isEntityLoaded(target)) return;

        int modeVal = this.mode.getValue();
        if (modeVal == 2) {
            renderScan(event, target);
            return;
        }
        if (killAuraOnly.getValue() && killAura != null && !killAura.isAttackAllowed()) return;

        Color color;
        if (modeVal == 0) {
            color = target.hurtTime > 0 ? new Color(16733525) : new Color(5635925);
        } else {
            color = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
        }
        RenderUtil.enableRenderState();
        RenderUtil.drawEntityBox(target, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.disableRenderState();
    }

    private void renderScan(Render3DEvent event, EntityLivingBase target) {
        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        Vec3 interpolated = interpolate(
                new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ),
                target.getPositionVector(),
                event.getPartialTicks()
        );

        double height = target.height;
        long time = System.currentTimeMillis();
        double rawAngle = time / 300.0;
        double offset = (Math.sin(rawAngle) + 1) / 2.0 * height;

        double thicknessScale = 1.0 - Math.abs(Math.sin(rawAngle));
        double minScale = 0.15;
        thicknessScale = minScale + (1.0 - minScale) * thicknessScale;

        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord + offset - renderPosY;
        double z = interpolated.zCoord - renderPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableCull();

        float radius = 0.6f;
        double baseThickness = scanThickness.getValue();
        double thickness = baseThickness * thicknessScale;
        double halfThick = thickness / 2.0;
        double bottomY = -halfThick;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        int slices = 60;

        for (int i = 0; i < slices; i++) {
            double angle1 = Math.toRadians((i / (double) slices) * 360.0);
            double angle2 = Math.toRadians(((i + 1) / (double) slices) * 360.0);

            double x1 = Math.sin(angle1) * radius;
            double z1 = Math.cos(angle1) * radius;
            double x2 = Math.sin(angle2) * radius;
            double z2 = Math.cos(angle2) * radius;

            Color col1 = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor((int) (i * 360.0 / slices * 10));
            Color col2 = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor((int) ((i + 1) * 360.0 / slices * 10));
            float r1 = col1.getRed() / 255f;
            float g1 = col1.getGreen() / 255f;
            float b1 = col1.getBlue() / 255f;
            float r2 = col2.getRed() / 255f;
            float g2 = col2.getGreen() / 255f;
            float b2 = col2.getBlue() / 255f;

            float alphaTop, alphaBottom;
            if (Math.cos(rawAngle) > 0) {
                alphaBottom = 0.05f;
                alphaTop = 0.7f;
            } else {
                alphaBottom = 0.7f;
                alphaTop = 0.05f;
            }

            worldrenderer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            worldrenderer.pos(x1, bottomY, z1).color(r1, g1, b1, alphaBottom).endVertex();
            worldrenderer.pos(x1, halfThick, z1).color(r1, g1, b1, alphaTop).endVertex();
            worldrenderer.pos(x2, bottomY, z2).color(r2, g2, b2, alphaBottom).endVertex();
            worldrenderer.pos(x2, halfThick, z2).color(r2, g2, b2, alphaTop).endVertex();
            tessellator.draw();
        }
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static Vec3 interpolate(Vec3 prev, Vec3 current, float partialTicks) {
        return new Vec3(
                prev.xCoord + (current.xCoord - prev.xCoord) * partialTicks,
                prev.yCoord + (current.yCoord - prev.yCoord) * partialTicks,
                prev.zCoord + (current.zCoord - prev.zCoord) * partialTicks
        );
    }
}
