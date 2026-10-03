package leader.ui;

/** Small frame-rate independent easing value used by the ClickGUI. */
public final class AnimationValue {
    private final long duration;
    private float start;
    private float target;
    private long startedAt;

    public AnimationValue(float initial, long duration) {
        this.duration = Math.max(1L, duration);
        this.start = initial;
        this.target = initial;
        this.startedAt = System.currentTimeMillis();
    }

    public void setTarget(float target) {
        if (Float.isNaN(target) || Math.abs(target - this.target) < 0.0001F) return;
        this.start = get();
        this.target = target;
        this.startedAt = System.currentTimeMillis();
    }

    public float get() {
        float progress = Math.min(1.0F,
                (System.currentTimeMillis() - startedAt) / (float) duration);
        // TrollHack-style OUT_CUBIC easing: responsive at the start, soft at the end.
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 3.0D);
        return start + (target - start) * eased;
    }
}
