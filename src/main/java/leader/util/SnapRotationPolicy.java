package leader.util;

public final class SnapRotationPolicy {
    private SnapRotationPolicy() { }

    public static float yawDistance(float yaw, float movementYaw) {
        float delta = (yaw - movementYaw) % 360;
        if (delta >= 180) delta -= 360;
        if (delta < -180) delta += 360;
        return Math.abs(delta);
    }

    public static boolean keepsForwardInput(float yaw, float movementYaw) {

        return yawDistance(yaw, movementYaw) <= 67;
    }

    public static float snap45(float placeYaw) {
        float yaw = Math.round(placeYaw / 45.0F) * 45.0F % 360;
        if (yaw >= 180) yaw -= 360;
        if (yaw < -180) yaw += 360;
        return yaw;
    }

    public static float preferredSprintYaw(float movementYaw, boolean right) {
        return snap45(movementYaw) + (right ? 45.0F : -45.0F);
    }

    public static float[] placementYaws(float placeYaw, float movementYaw, boolean right) {
        float preferred = preferredSprintYaw(movementYaw, right);
        float snapped = snap45(placeYaw);
        return new float[]{preferred, preferredSprintYaw(movementYaw, !right), snapped,
                snapped - 45, snapped + 45, snapped - 90, snapped + 90,
                snapped - 135, snapped + 135, snapped + 180};
    }

    public static float[] placementYaws(float placeYaw, float movementYaw, float referenceYaw) {
        float snapped = snap45(placeYaw);
        float heading = snap45(movementYaw);
        return new float[]{referenceYaw, placeYaw, movementYaw, heading - 45, heading + 45,
                snapped, snapped - 45, snapped + 45, snapped - 90, snapped + 90,
                snapped - 135, snapped + 135, snapped + 180};
    }

    public static boolean preferRotation(float candidateYaw, float candidatePitch, double candidateDistance,
                                         float currentYaw, float currentPitch, double currentDistance,
                                         float movementYaw, float referenceYaw, float referencePitch) {
        boolean candidateSprint = keepsForwardInput(candidateYaw, movementYaw);
        boolean currentSprint = keepsForwardInput(currentYaw, movementYaw);
        if (candidateSprint != currentSprint) return candidateSprint;
        if (candidateSprint) {
            double candidateAngle = rotationDistance(candidateYaw, candidatePitch, referenceYaw, referencePitch);
            double currentAngle = rotationDistance(currentYaw, currentPitch, referenceYaw, referencePitch);
            if (Math.abs(candidateAngle - currentAngle) > 1.0E-4) return candidateAngle < currentAngle;
        }
        return candidateDistance < currentDistance;
    }

    private static double rotationDistance(float yaw, float pitch, float referenceYaw, float referencePitch) {
        double yawDelta = yawDistance(yaw, referenceYaw);
        double pitchDelta = pitch - referencePitch;
        return yawDelta * yawDelta + pitchDelta * pitchDelta;
    }

    public static boolean prefer(float candidateYaw, double candidateDistance, float currentYaw,
                                 double currentDistance, float movementYaw, boolean right) {
        boolean candidateSprint = keepsForwardInput(candidateYaw, movementYaw);
        boolean currentSprint = keepsForwardInput(currentYaw, movementYaw);
        if (candidateSprint != currentSprint) return candidateSprint;
        if (candidateSprint) {
            float preferred = preferredSprintYaw(movementYaw, right);
            float candidateDelta = yawDistance(candidateYaw, preferred);
            float currentDelta = yawDistance(currentYaw, preferred);
            if (Math.abs(candidateDelta - currentDelta) > 0.5F) return candidateDelta < currentDelta;
        }
        return candidateDistance < currentDistance;
    }

    public static boolean prefer(float candidateYaw, double candidateDistance, float currentYaw,
                                 double currentDistance, float movementYaw) {
        boolean candidateSprint = keepsForwardInput(candidateYaw, movementYaw);
        boolean currentSprint = keepsForwardInput(currentYaw, movementYaw);
        if (candidateSprint != currentSprint) return candidateSprint;

        return candidateDistance < currentDistance;
    }
}
