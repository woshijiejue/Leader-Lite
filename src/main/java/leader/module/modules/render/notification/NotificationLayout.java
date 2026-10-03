package leader.module.modules.render.notification;

/** Screen-space anchors shared by every notification style, independent of Minecraft rendering. */
final class NotificationLayout {
    private NotificationLayout() { }

    static float x(float screenWidth, float originX, float previewWidth, float cardWidth,
                   float scale, boolean right, float slide) {
        float x = originX + (right ? previewWidth - cardWidth * scale : 0) + slide;
        return clamp(x, screenWidth - cardWidth * scale) / scale;
    }

    static float y(float screenHeight, float originY, float previewHeight, float cardHeight,
                   float scale, float stackOffset) {
        return clamp(originY + previewHeight - (cardHeight + stackOffset) * scale,
                screenHeight - cardHeight * scale) / scale;
    }

    private static float clamp(float position, float maximum) {
        return Math.max(0, Math.min(Math.max(0, maximum), position));
    }
}
