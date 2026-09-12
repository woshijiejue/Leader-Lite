package leader.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.MoveInputEvent;
import leader.events.UpdateEvent;
import leader.management.RotationState;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.util.MoveUtil;
import leader.util.PacketUtil;
import leader.util.RotationUtil;
import leader.util.TeamUtil;

import java.util.ArrayList;
import java.util.Comparator;

public class AutoProjectiles extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty range = new FloatProperty("MaxRange", 8.0F, 3.0F, 20.0F);
    public final FloatProperty minRange = new FloatProperty("MinRange", 5.0F, 3.0F, 20.0F);

    public final BooleanProperty smartDelay = new BooleanProperty("Smart Delay", false);
    public final IntProperty throwDelay = new IntProperty("Throw Delay Ticks", 3, 1, 15, () -> !smartDelay.getValue());
    public final BooleanProperty prediction = new BooleanProperty("Prediction", true);
    public final BooleanProperty useRotations = new BooleanProperty("Use Rotations", true);
    public final FloatProperty fov = new FloatProperty("FOV", 90.0F, 10.0F, 180.0F);
    public final BooleanProperty teams = new BooleanProperty("Teams", true);
    public final BooleanProperty invCheck = new BooleanProperty("Inv Check", true);
    public final BooleanProperty botCheck = new BooleanProperty("Bot Check", true);
    public final BooleanProperty rod = new BooleanProperty("Rod", false);
    public final IntProperty rodHoldTicks = new IntProperty("Rod Hold Ticks", 3, 1, 10, this.rod::getValue);

    private EntityLivingBase target = null;
    private int lastSlot = -1;
    private int switchedSlot = -1;
    private long lastThrowTime = 0L;
    private int throwState = 0;
    private boolean hasRotated = false;
    private int rodHoldTimer = 0;

    public AutoProjectiles() {
        super("AutoProjectiles", false);
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == mc.thePlayer || entity.deathTime > 0) return false;
        if (!(entity instanceof EntityOtherPlayerMP)) return false;
        if (!mc.thePlayer.canEntityBeSeen(target))return false;
        if (RotationUtil.distanceToEntity(entity) > this.range.getValue()) return false;
        if (RotationUtil.distanceToEntity(entity) < this.minRange.getValue()) return false;
        if (getYawDifference(entity) > this.fov.getValue() / 2.0F) return false;
        EntityPlayer player = (EntityPlayer) entity;
        if (!isEntityHeightVisible(entity)) return false;
        return (!this.teams.getValue() || !TeamUtil.isSameTeam(player)) && (!this.botCheck.getValue() || !TeamUtil.isBot(player));
    }

    private float getYawDifference(Entity entity) {
        double diffX = entity.posX - mc.thePlayer.posX;
        double diffZ = entity.posZ - mc.thePlayer.posZ;
        float targetYaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        return Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw));
    }

    private boolean isEntityHeightVisible(EntityLivingBase entity) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 top = new Vec3(entity.posX, entity.posY + entity.height, entity.posZ);
        Vec3 bottom = new Vec3(entity.posX, entity.posY, entity.posZ);
        return mc.theWorld.rayTraceBlocks(eyePos, top) == null || mc.theWorld.rayTraceBlocks(eyePos, bottom) == null;
    }

    private EntityLivingBase getTarget() {
        ArrayList<EntityLivingBase> targets = new ArrayList<>();
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (obj instanceof EntityLivingBase) {
                EntityLivingBase entity = (EntityLivingBase) obj;
                if (isValidTarget(entity)) targets.add(entity);
            }
        }
        if (targets.isEmpty()) return null;
        targets.sort(Comparator.comparingDouble(RotationUtil::distanceToEntity));
        return targets.get(0);
    }

    private int getDelay() {
        if (!smartDelay.getValue()) {
            return throwDelay.getValue();
        }
        EntityLivingBase t = getTarget();
        if (t == null) return throwDelay.getValue();
        if (mc.gameSettings.keyBindBack.isKeyDown()) return 1;
        double dist = RotationUtil.distanceToEntity(t);
        if (dist <= 4.5) return 1;
        if (dist <= 6) return 2;
        if (dist <= 8) return 3;
        if (dist <= 9) return 5;
        if (dist <= 15) return 8;
        return 20;
    }

    private boolean hasProjectile() {
        for (int i = 0; i < 9; i++) {
            if (isProjectile(mc.thePlayer.inventory.getStackInSlot(i))) return true;
        }
        return false;
    }

    private boolean isProjectile(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return item instanceof ItemSnowball || item instanceof ItemEgg || (this.rod.getValue() && item instanceof ItemFishingRod);
    }

    private boolean isHoldingRod() {
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        return stack != null && stack.getItem() instanceof ItemFishingRod;
    }

    private int getProjectileSlot() {
        for (int i = 0; i < 9; i++) {
            if (isProjectile(mc.thePlayer.inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private Vec3 predictTargetPos(EntityLivingBase target, double flightTicks) {
        double relVelX = (target.posX - target.prevPosX) - (mc.thePlayer.posX - mc.thePlayer.prevPosX);
        double relVelZ = (target.posZ - target.prevPosZ) - (mc.thePlayer.posZ - mc.thePlayer.prevPosZ);
        double predictedX = target.posX + relVelX * flightTicks;
        double predictedZ = target.posZ + relVelZ * flightTicks;
        double predictedY = target.posY + (target.posY - target.prevPosY) * Math.min(flightTicks, 2.0);
        return new Vec3(predictedX, predictedY, predictedZ);
    }

    private float yawTo(Vec3 aimPoint) {
        double diffX = aimPoint.xCoord - mc.thePlayer.posX;
        double diffZ = aimPoint.zCoord - mc.thePlayer.posZ;
        return (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
    }

    private double horizontalDistanceTo(Vec3 aimPoint) {
        double diffX = aimPoint.xCoord - mc.thePlayer.posX;
        double diffZ = aimPoint.zCoord - mc.thePlayer.posZ;
        return Math.sqrt(diffX * diffX + diffZ * diffZ);
    }

    private double estimateFlightTicks(double horizontalDist, float pitch) {
        double vH = Math.cos(Math.toRadians(pitch)) * 1.5;
        double travelled = 0.0;
        for (int t = 1; t <= 100; t++) {
            travelled += vH;
            vH *= 0.99;
            if (travelled >= horizontalDist) return t;
        }
        return 100.0;
    }

    private double arcClosestDistance(Vec3 aimPoint, float yaw, float pitch) {
        double vX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 1.5;
        double vY = -Math.sin(Math.toRadians(pitch)) * 1.5;
        double vZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 1.5;
        double x = mc.thePlayer.posX - Math.cos(Math.toRadians(yaw)) * 0.16;
        double y = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.1;
        double z = mc.thePlayer.posZ - Math.sin(Math.toRadians(yaw)) * 0.16;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 100; i++) {
            x += vX;
            y += vY;
            z += vZ;
            vX *= 0.99;
            vY *= 0.99;
            vZ *= 0.99;
            vY -= 0.03;
            double dx = x - aimPoint.xCoord;
            double dy = y - aimPoint.yCoord;
            double dz = z - aimPoint.zCoord;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < best) best = distSq;
        }
        return Math.sqrt(best);
    }

    private float searchPitch(Vec3 aimPoint, float yaw) {
        float bestPitch = 0.0F;
        double bestDist = Double.MAX_VALUE;
        for (float pitch = -80.0F; pitch <= 80.0F; pitch += 0.5F) {
            double dist = this.arcClosestDistance(aimPoint, yaw, pitch);
            if (dist < bestDist) {
                bestDist = dist;
                bestPitch = pitch;
            }
        }
        return bestPitch;
    }

    private float[] calculateSimulatedRotations(EntityLivingBase target) {
        double ping = 0;
        try {
            ping = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
        } catch (Exception ignored) {
        }
        double diffX = target.posX - mc.thePlayer.posX;
        double diffZ = target.posZ - mc.thePlayer.posZ;
        double horizontalDist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        Vec3 predicted;
        if (this.prediction.getValue()) {
            predicted = this.predictTargetPos(target, horizontalDist / 1.5 + ping / 50.0 + 1.0);
            float yaw = this.yawTo(predicted);
            float pitch = this.searchPitch(new Vec3(predicted.xCoord,
                    predicted.yCoord + target.getEyeHeight() * 0.7, predicted.zCoord), yaw);
            double flightTicks = this.estimateFlightTicks(this.horizontalDistanceTo(predicted), pitch)
                    + ping / 50.0 + 1.0;
            predicted = this.predictTargetPos(target, flightTicks);
        } else {
            predicted = new Vec3(target.posX, target.posY, target.posZ);
        }

        Vec3 aimPoint = new Vec3(predicted.xCoord,
                predicted.yCoord + target.getEyeHeight() * 0.7, predicted.zCoord);
        float yaw = this.yawTo(aimPoint);
        float pitch = this.searchPitch(aimPoint, yaw);
        return new float[]{yaw, pitch};
    }

    private void switchToProjectile() {
        int projectileSlot = this.getProjectileSlot();
        if (projectileSlot != -1) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
            this.switchedSlot = projectileSlot;
            mc.thePlayer.inventory.currentItem = projectileSlot;
        }
    }

    private void switchBack() {
        if (this.lastSlot != -1) {
            if (mc.thePlayer.inventory.currentItem == this.switchedSlot) {
                mc.thePlayer.inventory.currentItem = this.lastSlot;
            }
            this.lastSlot = -1;
            this.switchedSlot = -1;
        }
    }

    private void throwProjectile() {
        int projectileSlot = this.getProjectileSlot();
        if (projectileSlot != -1) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(projectileSlot);
            if (isProjectile(stack)) {
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        if ((this.invCheck.getValue() && mc.currentScreen instanceof GuiContainer)) return;

        if (!this.hasProjectile()) {
            this.target = null;
            this.throwState = 0;
            this.switchBack();
            return;
        }
        this.target = this.getTarget();
        if (this.target == null) {
            this.throwState = 0;
            this.switchBack();
            return;
        }
        KillAura aura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (aura.isEnabled() && aura.isPlayerBlocking()){
            this.target = null;
            this.throwState = 0;
            this.switchBack();
            return;
        }
        if (target != null && RotationUtil.distanceToEntity(target) <= minRange.getValue()){
            this.target = null;
            this.throwState = 0;
            this.switchBack();
            return;
        }
        switch (this.throwState) {
            case 0:
                if (System.currentTimeMillis() - this.lastThrowTime < getDelay() * 50F) return;
                this.throwState = 1;
                break;

            case 1:
                this.switchToProjectile();
                this.throwState = 2;
                break;
            case 2:
                float[] rots = calculateSimulatedRotations(this.target);
                if (this.useRotations.getValue()) {
                    event.setRotation(rots[0], rots[1], 2);
                    event.setPervRotation(rots[0], 2);
                }
                this.hasRotated = this.useRotations.getValue();
                this.throwState = 3;
                break;

            case 3:
                this.throwProjectile();
                this.lastThrowTime = System.currentTimeMillis();
                if (this.rod.getValue() && this.isHoldingRod()) {
                    this.rodHoldTimer = this.rodHoldTicks.getValue();
                    this.throwState = 5;
                } else {
                    this.throwState = 4;
                }
                break;

            case 5:
                this.rodHoldTimer--;
                if (this.rodHoldTimer <= 0) {
                    this.throwState = 4;
                }
                break;

            case 4:
                this.switchBack();
                this.target = null;
                this.hasRotated = false;
                this.throwState = 0;
                break;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && this.hasRotated && RotationState.isActived() && RotationState.getPriority() == 2.0F && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @Override
    public void onEnabled() {
        this.target = null;
        this.lastSlot = -1;
        this.switchedSlot = -1;
        this.throwState = 0;
        this.hasRotated = false;
        this.lastThrowTime = 0L;
    }

    @Override
    public void onDisabled() {
        this.switchBack();
        this.target = null;
        this.throwState = 0;
        this.hasRotated = false;
    }
}