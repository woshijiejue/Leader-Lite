package leader.module.modules.render;

/** Run with javac/java; the input-state machine requires no Minecraft or LWJGL context. */
public final class FreeLookActivationTest {
    private static int checks;

    public static void main(String[] args) {
        FreeLookActivation state = new FreeLookActivation();
        check(!state.update(false, true, false, true), "disabled hold is idle");
        check(state.press(), "first hold press accepted");
        check(state.update(true, true, true, true), "hold press activates");
        check(!state.press(), "repeat key event ignored");
        check(!state.update(true, true, false, true), "hold release stops camera");
        check(state.press(), "second hold press accepted");
        check(state.update(true, true, true, true), "second hold works without toggling module off");
        check(!state.update(false, true, true, true), "manual disable stops held camera");
        check(!state.press(), "held key cannot undo manual disable");
        state.update(false, true, false, true);
        check(state.press(), "fresh hold press after disable works");

        state = new FreeLookActivation();
        check(state.press(), "toggle press accepted");
        check(state.update(true, false, true, true), "toggle enable activates");
        check(state.update(true, false, false, true), "toggle release stays active");
        check(state.press(), "toggle-off press accepted");
        check(!state.update(false, false, true, true), "toggle disable stops");
        check(!state.press(), "toggle repeat cannot turn it back on");
        check(state.update(true, false, false, true), "GUI-enabled toggle works without a bind press");

        for (boolean hold : new boolean[]{false, true}) {
            for (int cycle = 0; cycle < 100; cycle++) {
                state = new FreeLookActivation();
                check(state.press(), "initial press");
                check(state.update(true, hold, true, true), "initial activation");
                check(!state.update(true, hold, true, false), "GUI/focus loss stops");
                check(!state.update(true, hold, true, true), "held key cannot reactivate after focus recovery");
                check(!state.press(), "repeat blocked after focus recovery");
                check(state.update(true, hold, false, true) == !hold, "release clears focus block");
                check(state.press(), "fresh press after GUI/focus loss");
                check(state.update(true, hold, true, true), "reactivation after fresh press");
                state.reset(true);
                check(!state.update(true, hold, true, true), "rebind/mode/world reset blocks held key");
                state.update(true, hold, false, true);
                check(state.press(), "release then press after reset");
                check(state.update(true, hold, true, true), "camera reactivates after reset");
                state.reset(false);
                check(state.update(true, hold, false, true) == !hold, "reset with key up follows current mode");
            }
        }
        for (boolean enabled : new boolean[]{false, true}) {
            for (boolean hold : new boolean[]{false, true}) {
                for (boolean down : new boolean[]{false, true}) {
                    state = new FreeLookActivation();
                    check(state.update(enabled, hold, down, true) == (enabled && (!hold || down)), "activation truth table");
                    check(!state.update(enabled, hold, down, false), "unavailable always inactive");
                }
            }
        }
        System.out.println("PASS: " + checks + " FreeLook activation checks");
    }

    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
    }
}
