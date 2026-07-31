package leader.client.util.player;

import leader.client.util.InstanceAccess;
import leader.mixin.accessor.IAccessorEntity;
import leader.client.util.math.RandomUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;

public class RotationUtil implements InstanceAccess {
    public static float[] nearestRotation(final AxisAlignedBB box, float currentYaw, float currentPitch,
                                   float maxAngle, float smoothFactor) {
        if (mc.thePlayer == null) return null;

        Vec3 targetPoint = getNearestPointBB(box);
        if (targetPoint == null) return null;

        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);

        double diffX = targetPoint.xCoord - eyePos.xCoord;
        double diffY = targetPoint.yCoord - eyePos.yCoord;
        double diffZ = targetPoint.zCoord - eyePos.zCoord;

        double horizontalDist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(diffY, horizontalDist) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f :
                smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f :
                smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);

        return new float[]{
                quantizeAngle(currentYaw + yawDelta),
                quantizeAngle(currentPitch + pitchDelta)
        };
    }

    public static Vec3 getNearestPointBB(AxisAlignedBB box) {
        if (mc.thePlayer == null) return null;

        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);

        double x = MathHelper.clamp_double(eyePos.xCoord, box.minX, box.maxX);
        double y = MathHelper.clamp_double(eyePos.yCoord, box.minY, box.maxY);
        double z = MathHelper.clamp_double(eyePos.zCoord, box.minZ, box.maxZ);
        Vec3 nearestPoint = new Vec3(x, y, z);

        if (isVisible(nearestPoint)) {
            return nearestPoint;
        }

        AxisAlignedBB scanBox = box.expand(-0.002, -0.002, -0.002);

        double stepX = scanBox.maxX - scanBox.minX;
        double stepY = scanBox.maxY - scanBox.minY;
        double stepZ = scanBox.maxZ - scanBox.minZ;

        Vec3 bestPoint = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (double sX = scanBox.minX; sX <= scanBox.maxX; sX += stepX / 2.0) {
            for (double sY = scanBox.minY; sY <= scanBox.maxY; sY += stepY / 2.0) {
                for (double sZ = scanBox.minZ; sZ <= scanBox.maxZ; sZ += stepZ / 2.0) {
                    Vec3 currentPoint = new Vec3(sX, sY, sZ);

                    if (isVisible(currentPoint)) {
                        double distSq = eyePos.squareDistanceTo(currentPoint);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            bestPoint = currentPoint;
                        }
                    }
                }
            }
        }

        return (bestPoint != null) ? bestPoint : nearestPoint;
    }

    public static boolean isVisible(Vec3 targetVec) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;

        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);

        if (eyePos.squareDistanceTo(targetVec) > 4096.0) return false;

        Vec3 lookVec = mc.thePlayer.getLookVec();
        Vec3 toTarget = targetVec.subtract(eyePos).normalize();
        if (lookVec.dotProduct(toTarget) < 0) return false;
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(eyePos, targetVec, false, true, false);
        return result == null;
    }

    public float getDistanceToEntity(EntityLivingBase target) {
        if (mc.thePlayer == null || target == null) return 0.0f;

        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);
        Vec3 nearestPoint = getNearestPointBB(target.getEntityBoundingBox());
        return (float) eyePos.distanceTo(nearestPoint);
    }

    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }
    public static boolean hasVisiblePoint(AxisAlignedBB boundingBox) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        double centerX = (boundingBox.minX + boundingBox.maxX) / 2.0;
        double centerZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
        double height = boundingBox.maxY - boundingBox.minY;
        double[] yRatios = new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};

        for (double ratio : yRatios) {
            double targetY = boundingBox.minY + ratio * height;
            Vec3 targetPoint = new Vec3(centerX, targetY, centerZ);
            if (RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPoint) == null) {
                return true;
            }
        }
        return false;
    }
    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.max(0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096f);
    }
    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return RotationUtil.getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }
    public static float[] getRotationsToBox(AxisAlignedBB boundingBox, float yaw, float pitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
        double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
        double deltaY = eyePos.yCoord >= maxTargetY ? maxTargetY - eyePos.yCoord : (eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0);
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
        return RotationUtil.getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    private static Vec3 findVisiblePointFromHead(Vec3 eyePos, AxisAlignedBB bb) {
        double headX = (bb.minX + bb.maxX) / 2.0;
        double headY = bb.minY + (bb.maxY - bb.minY) * 0.75;
        double headZ = (bb.minZ + bb.maxZ) / 2.0;
        double step = 0.2;
        for (double xOff = 0; xOff <= 0.5; xOff += step) {
            for (double yOff = 0; yOff <= 0.5; yOff += step) {
                for (double zOff = 0; zOff <= 0.5; zOff += step) {
                    double[] xOffsets = {xOff, -xOff};
                    double[] yOffsets = {yOff, -yOff};
                    double[] zOffsets = {zOff, -zOff};
                    for (double x : xOffsets) {
                        for (double y : yOffsets) {
                            for (double z : zOffsets) {
                                double targetX = headX + x;
                                double targetY = headY + y;
                                double targetZ = headZ + z;
                                if (targetX >= bb.minX && targetX <= bb.maxX &&
                                        targetY >= bb.minY && targetY <= bb.maxY &&
                                        targetZ >= bb.minZ && targetZ <= bb.maxZ) {
                                    Vec3 testPoint = new Vec3(targetX, targetY, targetZ);
                                    if (isPointVisible(eyePos, testPoint)) {
                                        return testPoint;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isPointVisible(Vec3 start, Vec3 end) {
        MovingObjectPosition rayTrace = RotationUtil.mc.theWorld.rayTraceBlocks(start, end, false, true, false);
        return rayTrace == null;
    }

    public static float[] getRotations(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float((float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float((float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{RotationUtil.quantizeAngle(currentYaw + yawDelta), RotationUtil.quantizeAngle(currentPitch + pitchDelta)};
    }

    public static Vec3 getClosestPointOnBox(Vec3 point, AxisAlignedBB bb) {
        double x = MathHelper.clamp_double(point.xCoord, bb.minX, bb.maxX);
        double y = MathHelper.clamp_double(point.yCoord, bb.minY, bb.maxY);
        double z = MathHelper.clamp_double(point.zCoord, bb.minZ, bb.maxZ);
        return new Vec3(x, y, z);
    }

    public static double distanceToEntity(Entity entity) {
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return RotationUtil.distanceToBox(boundingBox);
    }

    public static double distanceToBox(Entity entity, Vec3 point) {
        float borderSize = entity.getCollisionBorderSize();
        return RotationUtil.getDistanceToBox(entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize), point);
    }

    public static double distanceToBox(AxisAlignedBB boundingBox) {
        return RotationUtil.getDistanceToBox(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f));
    }

    public static double getDistanceToBox(AxisAlignedBB bb, Vec3 point) {
        if (bb.isVecInside(point)) {
            return 0.0;
        }
        Vec3 closestPoint = getClosestPointOnBox(point, bb);
        return point.distanceTo(closestPoint);
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f;
        }
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float((float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw)) * 2.0f;
    }

    public static float getYawBetween(double x1, double z1, double x2, double z2) {
        return MathHelper.wrapAngleTo180_float((float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw);
    }

    public static MovingObjectPosition rayTrace(float yaw, float pitch, double distance, float partialTicks) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 lookVec = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    public static MovingObjectPosition rayTrace(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        Vec3 targetPos = RotationUtil.getClosestPointOnBox(eyePos, entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
        return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    public static MovingObjectPosition rayTrace(AxisAlignedBB boundingBox, float yaw, float pitch, double distance) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return boundingBox.calculateIntercept(eyePos, targetPos);
    }
    public static float[] getRotations(Vec3 vec) {
        return getRotations(vec.xCoord, vec.yCoord, vec.zCoord);
    }
    public static float[] getRotations(BlockPos blockPos) {
        return getRotations(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, mc.thePlayer.posX, mc.thePlayer.posY + (double)mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }
    public static float[] getRotations(double posX, double posY, double posZ) {
        return getRotations(posX, posY, posZ, mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }
    public static float[] getRotations(double rotX, double rotY, double rotZ, double startX, double startY, double startZ) {
        double x = rotX - startX;
        double y = rotY - startY;
        double z = rotZ - startZ;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static float[] getRotations(BlockPos blockPos, EnumFacing enumFacing) {
        double d = (double) blockPos.getX() + 0.5 - mc.thePlayer.posX + (double) enumFacing.getFrontOffsetX() * 0.25;
        double d2 = (double) blockPos.getZ() + 0.5 - mc.thePlayer.posZ + (double) enumFacing.getFrontOffsetZ() * 0.25;
        double d3 = mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight() - blockPos.getY() - (double) enumFacing.getFrontOffsetY() * 0.25;
        double d4 = MathHelper.sqrt_double(d * d + d2 * d2);
        float f = (float) (Math.atan2(d2, d) * 180.0 / Math.PI) - 90.0f;
        float f2 = (float) (Math.atan2(d3, d4) * 180.0 / Math.PI);
        return new float[]{MathHelper.wrapAngleTo180_float(f), f2};
    }
    public static class RotationVec {
        public float x;
        public float y;

        public RotationVec(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public RotationVec add(float x, float y) {
            return new RotationVec(this.x + x, this.y + y);
        }

        public float getX() {
            return this.x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return this.y;
        }

        public void setY(float y) {
            this.y = y;
        }
    }
}