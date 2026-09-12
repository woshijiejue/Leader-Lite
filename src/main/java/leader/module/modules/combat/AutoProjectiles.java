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

    private float[] calculateSimulatedRotations(EntityLivingBase target) {
        double ping = 0;
        try {
            ping = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
        } catch (Exception ignored) {
        }
        double diffX = target.posX - mc.thePlayer.posX;
        double diffZ = target.posZ - mc.thePlayer.posZ;
        double horizontalDist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        double flightTicks = horizontalDist / 1.5;
        double totalPredictTicks = flightTicks + (ping / 50.0) + 1.0;
        Vec3 predictedPos;
        if (this.prediction.getValue()) {
            double relVelX = (target.posX - target.prevPosX) - (mc.thePlayer.posX - mc.thePlayer.prevPosX);
            double relVelZ = (target.posZ - target.prevPosZ) - (mc.thePlayer.posZ - mc.thePlayer.prevPosZ);
            double predictedX = target.posX + relVelX * totalPredictTicks;
            double predictedZ = target.posZ + relVelZ * totalPredictTicks;
            double predictedY = target.posY + (target.posY - target.prevPosY) * Math.min(totalPredictTicks, 2.0);
            predictedPos = new Vec3(predictedX, predictedY, predictedZ);
        } else {
            predictedPos = new Vec3(target.posX, target.posY, target.posZ);
        }
        double pDiffX = predictedPos.xCoord - mc.thePlayer.posX;
        double pDiffZ = predictedPos.zCoord - mc.thePlayer.posZ;
        double pDiffY = (predictedPos.yCoord + target.getEyeHeight() * 0.7) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        float yaw = (float) (Math.atan2(pDiffZ, pDiffX) * 180.0 / Math.PI) - 90.0F;
        double pHorizontalDist = Math.sqrt(pDiffX * pDiffX + pDiffZ * pDiffZ);
        float bestPitch = 0;
        double minDiff = Double.MAX_VALUE;
        boolean found = false;
        for (float pitch = -90; pitch < 90; pitch += 0.5F) {
            double simulatedY = simulateProjectile(pHorizontalDist, pitch);
            double currentDiff = Math.abs(simulatedY - pDiffY);
            if (currentDiff < minDiff) {
                minDiff = currentDiff;
                bestPitch = pitch;
                found = true;
            }
        }
        if (!found) return null;
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(
                new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ),
                new Vec3(predictedPos.xCoord, predictedPos.yCoord + target.getEyeHeight(), predictedPos.zCoord),
                false, true, false);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return null;
        return new float[]{yaw, bestPitch};
    }

    private double simulateProjectile(double dist, float pitch) {
        double v = 1.5;
        double vY = -Math.sin(Math.toRadians(pitch)) * v;
        double vH = Math.cos(Math.toRadians(pitch)) * v;
        double curH = 0;
        double curY = 0;
        for (int i = 0; i < 100; i++) {
            curH += vH;
            curY += vY;
            vH *= 0.99;
            vY *= 0.99;
            vY -= 0.03;
            if (curH >= dist) return curY;
        }
        return curY;
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
                if (rots != null) {
                    if (this.useRotations.getValue()) {
                        event.setRotation(rots[0], rots[1], 2);
                        event.setPervRotation(rots[0], 2);
                    }
                    this.hasRotated = this.useRotations.getValue();
                    this.throwState = 3;
                } else {
                    this.throwState = 4;
                }
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