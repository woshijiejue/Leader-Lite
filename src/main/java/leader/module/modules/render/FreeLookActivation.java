package leader.module.modules.render;

/** Input edges and focus recovery, independent of Minecraft for regression testing. */
final class FreeLookActivation {
    private boolean pressed;
    private boolean blocked;

    boolean press() {
        if (pressed || blocked) return false;
        pressed = true;
        return true;
    }

    boolean update(boolean enabled, boolean hold, boolean down, boolean available) {
        if (!down) {
            pressed = false;
            blocked = false;
        }
        if (!available) {
            if (down) blocked = true;
            return false;
        }
        return enabled && !blocked && (!hold || down);
    }

    void reset(boolean down) {
        pressed = down;
        blocked = down;
    }
}
