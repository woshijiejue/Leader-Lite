package leader.util;

public final class SnapRotationPolicy {
    private SnapRotationPolicy() { }

    public static float yawDistance(float yaw, float referenceYaw) {
        float delta = (yaw - referenceYaw) % 360.0F;
        if (delta >= 180.0F) delta -= 360.0F;
        if (delta < -180.0F) delta += 360.0F;
        return Math.abs(delta);
    }

    public static float[] placementYaws(float placeYaw, float movementYaw, float referenceYaw) {
        return new float[]{referenceYaw, placeYaw, movementYaw, movementYaw - 45.0F,
                movementYaw + 45.0F, movementYaw - 67.5F, movementYaw + 67.5F,
                movementYaw - 67.48F, movementYaw + 67.48F};
    }

    public static boolean preferRotation(boolean candidateSprint, float candidateYaw, float candidatePitch,
                                         double candidateDistance, boolean currentSprint, float currentYaw,
                                         float currentPitch, double currentDistance,
                                         float referenceYaw, float referencePitch) {
        if (candidateSprint != currentSprint) return candidateSprint;
        double candidateAngle = rotationDistance(candidateYaw, candidatePitch, referenceYaw, referencePitch);
        double currentAngle = rotationDistance(currentYaw, currentPitch, referenceYaw, referencePitch);
        if (Math.abs(candidateAngle - currentAngle) > 1.0E-6) return candidateAngle < currentAngle;
        return candidateDistance < currentDistance;
    }

    public static boolean sameRotation(float yaw, float pitch, float otherYaw, float otherPitch) {
        return yawDistance(yaw, otherYaw) < 0.02F && Math.abs(pitch - otherPitch) < 0.02F;
    }

    private static double rotationDistance(float yaw, float pitch, float referenceYaw, float referencePitch) {
        double yawDelta = yawDistance(yaw, referenceYaw);
        double pitchDelta = pitch - referencePitch;
        return yawDelta * yawDelta + pitchDelta * pitchDelta;
    }
}
