package leader.ui.theme;

/** Visible text baselines and avatar alignment shared by the Xylitol HUD cards. */
public final class HUDCardLayout {
    public final float height;
    public final float firstBaseline;
    public final float secondBaseline;
    public final float iconTop;
    public final float iconCenter;
    public final float barTop;

    public HUDCardLayout(float firstCap, float secondCap, float iconHeight,
                         float padding, float lineGap, float barGap, float barHeight, boolean inlineBar) {
        float textHeight = firstCap + lineGap + secondCap;
        float contentHeight = Math.max(iconHeight, textHeight + (inlineBar ? barGap + barHeight : 0));
        height = padding * 2 + contentHeight + (inlineBar ? 0 : barGap + barHeight);
        float top = padding + (contentHeight - textHeight - (inlineBar ? barGap + barHeight : 0)) / 2;
        firstBaseline = top + firstCap;
        secondBaseline = firstBaseline + lineGap + secondCap;
        iconTop = padding + (contentHeight - iconHeight) / 2;
        iconCenter = padding + contentHeight / 2;
        barTop = inlineBar ? secondBaseline + barGap : height - barHeight;
    }
}
