package leader.module.modules.render.notification;

/** Run with javac/java; no Minecraft instance or graphics context is required. */
public final class NotificationLayoutTest {
    private static int checks;

    public static void main(String[] args) {
        for (float scale : new float[]{0.5F, 0.75F, 1, 1.25F, 1.5F}) {
            for (float[] screen : new float[][]{{320, 240}, {640, 360}, {960, 540}}) {
                for (float[] preview : new float[][]{{100, 20}, {136, 34}, {140, 26}, {120, 30}, {96, 26}, {110, 35}, {116, 30}}) {
                    float originX = screen[0] - preview[0] * scale;
                    float originY = screen[1] - preview[1] * scale;
                    close(NotificationLayout.x(screen[0], originX, preview[0] * scale, preview[0], scale, true, 0)
                            * scale + preview[0] * scale, screen[0], "right edge");
                    close(NotificationLayout.y(screen[1], originY, preview[1] * scale, preview[1], scale, 0)
                            * scale + preview[1] * scale, screen[1], "bottom edge");
                    for (float width : new float[]{90, 160, 200}) {
                        close(NotificationLayout.x(screen[0], originX, preview[0] * scale, width, scale, true, 0)
                                * scale + width * scale, screen[0], "variable-width right edge");
                    }
                    for (float height : new float[]{20, 26, 40, 50}) {
                        close(NotificationLayout.y(screen[1], originY, preview[1] * scale, height, scale, 0)
                                * scale + height * scale, screen[1], "variable-height bottom edge");
                    }
                    float newerY = NotificationLayout.y(screen[1], originY, preview[1] * scale, 30, scale, 0);
                    float olderY = NotificationLayout.y(screen[1], originY, preview[1] * scale, 20, scale, 34);
                    close((olderY + 20) * scale, newerY * scale - 4 * scale, "mixed-height stack gap");
                    close(NotificationLayout.x(screen[0], 12, preview[0] * scale, preview[0], scale, false, 0)
                            * scale, 12, "left-mode position");
                    close(NotificationLayout.x(screen[0], 9999, preview[0] * scale, preview[0], scale, true, 18)
                            * scale + preview[0] * scale, screen[0], "slide cannot overflow");
                    close(NotificationLayout.y(screen[1], -999, preview[1] * scale, preview[1], scale, 0), 0, "top clamp");
                }
            }
        }
        System.out.println("PASS: " + checks + " notification layout checks");
    }

    private static void close(float actual, float expected, String label) {
        checks++;
        if (Math.abs(actual - expected) > 0.001F) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
}
