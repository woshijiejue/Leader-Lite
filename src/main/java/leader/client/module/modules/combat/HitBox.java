package leader.client.module.modules.combat;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.LeftClickMouseEvent;
import leader.client.events.Render3DEvent;
import leader.client.events.TickEvent;
import leader.mixin.accessor.IAccessorRenderManager;
import leader.client.module.Module;
import leader.client.util.render.RenderUtil;
import leader.client.util.player.TeamUtil;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ColorValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class HitBox extends Module {
    
    private MovingObjectPosition targetEntity = null;
    public final SliderValue multiplier = (SliderValue) new SliderValue("multiplier", 1.2, 1.0, 5.0, Representation.FLOAT, this)
            .displayName("Multi Plier");
    public final ListValue showHitbox = new ListValue("show-hitbox", new String[]{"None", "Players", "Mobs", "Animals", "All"}, "None", this);
    public final ColorValue color = new ColorValue("color", new Color(255, 255, 255), () -> !this.showHitbox.is("None"), this);
    public final BoolValue teams = new BoolValue("teams", true, () -> this.showHitbox.is("Players") || this.showHitbox.is("All"), this);
    public final BoolValue botCheck = new BoolValue("bot-check", true, () -> this.showHitbox.is("Players") || this.showHitbox.is("All"), this);

    public HitBox() {
        super("HitBox", false);
    }

    public static float getExpansion(Entity entity) {
        HitBox hitBox = (HitBox) Leader.moduleManager.modules.get(HitBox.class);
        if (hitBox != null && hitBox.isEnabled() && entity instanceof EntityLivingBase) {
            return hitBox.multiplier.getValue();
        }
        return 1.0F;
    }

    private void calculateMouseOver(float partialTicks) {
        if (mc.getRenderViewEntity() != null && mc.theWorld != null) {
            mc.pointedEntity = null;
            Entity pointedEntity = null;
            double reach = 3.0;
            this.targetEntity = mc.getRenderViewEntity().rayTrace(reach, partialTicks);
            double distance = reach;
            Vec3 eyePos = mc.getRenderViewEntity().getPositionEyes(partialTicks);
            if (this.targetEntity != null) {
                distance = this.targetEntity.hitVec.distanceTo(eyePos);
            }
            Vec3 lookVec = mc.getRenderViewEntity().getLook(partialTicks);
            Vec3 reachVec = eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);
            Vec3 hitVec = null;
            float expansion = 1.0F;
            List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(
                    mc.getRenderViewEntity(),
                    mc.getRenderViewEntity()
                            .getEntityBoundingBox()
                            .addCoord(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach)
                            .expand(expansion, expansion, expansion)
            );
            double closestDistance = distance;
            for (Entity entity : entities) {
                if (entity.canBeCollidedWith()) {
                    float collisionSize = (float) ((double) entity.getCollisionBorderSize() * getExpansion(entity));
                    AxisAlignedBB expandedBox = entity.getEntityBoundingBox().expand(collisionSize, collisionSize, collisionSize);
                    MovingObjectPosition intercept = expandedBox.calculateIntercept(eyePos, reachVec);
                    if (expandedBox.isVecInside(eyePos)) {
                        if (0.0 < closestDistance || closestDistance == 0.0) {
                            pointedEntity = entity;
                            hitVec = intercept == null ? eyePos : intercept.hitVec;
                            closestDistance = 0.0;
                        }
                    } else if (intercept != null) {
                        double interceptDistance = eyePos.distanceTo(intercept.hitVec);
                        if (interceptDistance < closestDistance || closestDistance == 0.0) {
                            if (entity == mc.getRenderViewEntity().ridingEntity && !entity.canRiderInteract()) {
                                if (closestDistance == 0.0) {
                                    pointedEntity = entity;
                                    hitVec = intercept.hitVec;
                                }
                            } else {
                                pointedEntity = entity;
                                hitVec = intercept.hitVec;
                                closestDistance = interceptDistance;
                            }
                        }
                    }
                }
            }
            if (pointedEntity != null && (closestDistance < distance || this.targetEntity == null)) {
                this.targetEntity = new MovingObjectPosition(pointedEntity, hitVec);
                if (pointedEntity instanceof EntityLivingBase || pointedEntity instanceof EntityItemFrame) {
                    mc.pointedEntity = pointedEntity;
                }
            }
        }
    }

    private boolean shouldShowEntity(EntityLivingBase entity) {
        if (entity == mc.thePlayer) {
            return false;
        }
        if (entity.deathTime > 0 || entity instanceof EntityArmorStand || entity.isInvisible()) {
            return false;
        }
        if (mc.getRenderViewEntity().getDistanceToEntity(entity) > 128.0F) {
            return false;
        }
        if (!entity.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 0.1F)) {
            return false;
        }
        if (this.showHitbox.is("None")) {
            return false;
        }
        if (this.showHitbox.is("Players")) {
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (TeamUtil.isFriend(player)) {
                    return false;
                }
                if (this.teams.getValue() && TeamUtil.isSameTeam(player)) {
                    return false;
                }
                if (this.botCheck.getValue() && TeamUtil.isBot(player)) {
                    return false;
                }
                return true;
            }
            return false;
        }
        if (this.showHitbox.is("Mobs")) {
            if (entity instanceof EntityDragon || entity instanceof EntityWither) {
                return true;
            }
            if (entity instanceof EntityMob || entity instanceof EntitySlime) {
                return !(entity instanceof EntitySilverfish);
            }
            return false;
        }
        if (this.showHitbox.is("Animals")) {
            return entity instanceof EntityAnimal
                    || entity instanceof EntityBat
                    || entity instanceof EntitySquid
                    || entity instanceof EntityVillager
                    || entity instanceof EntityIronGolem;
        }
        if (this.showHitbox.is("All")) {
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (TeamUtil.isFriend(player)) {
                    return false;
                }
                if (this.teams.getValue() && TeamUtil.isSameTeam(player)) {
                    return false;
                }
                if (this.botCheck.getValue() && TeamUtil.isBot(player)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.calculateMouseOver(1.0F);
        }
    }

    @EventTarget(Priority.HIGH)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !event.isCancelled() && this.targetEntity != null) {
            mc.objectMouseOver = this.targetEntity;
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && !this.showHitbox.is("None")) {
            List<EntityLivingBase> entities = mc.theWorld.loadedEntityList
                    .stream()
                    .filter(entity -> entity instanceof EntityLivingBase)
                    .map(entity -> (EntityLivingBase) entity)
                    .filter(this::shouldShowEntity)
                    .collect(Collectors.toList());
            if (!entities.isEmpty()) {
                RenderUtil.enableRenderState();
                Color renderColor = this.color.getValue();
                for (EntityLivingBase entity : entities) {
                    float collisionSize = (float) ((double) entity.getCollisionBorderSize() * this.multiplier.getValue());
                    AxisAlignedBB expandedBox = entity.getEntityBoundingBox().expand(collisionSize, collisionSize, collisionSize);
                    AxisAlignedBB offsetBox = new AxisAlignedBB(
                            expandedBox.minX - entity.posX + (RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, event.getPartialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX()),
                            expandedBox.minY - entity.posY + (RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, event.getPartialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()),
                            expandedBox.minZ - entity.posZ + (RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, event.getPartialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()),
                            expandedBox.maxX - entity.posX + (RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, event.getPartialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX()),
                            expandedBox.maxY - entity.posY + (RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, event.getPartialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()),
                            expandedBox.maxZ - entity.posZ + (RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, event.getPartialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ())
                    );
                    RenderUtil.drawBoundingBox(offsetBox, renderColor.getRed(), renderColor.getGreen(), renderColor.getBlue(), 150, 1.5F);
                }
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%.1fx", this.multiplier.getValue())};
    }
}
