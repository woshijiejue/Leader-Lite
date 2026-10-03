package leader.util;

/**
 * @Description: Kiss my ass
 * @Author: Eremin12
 * @Date: 2026/9/9 09:49
 */
public class TimerUtil {
    private long lastMS = System.currentTimeMillis();

    public boolean hasTimeElapsed(long time, boolean reset) {
        if (System.currentTimeMillis() - lastMS > time) {
            if (reset) {
                reset();
            }
            return true;
        }
        return false;
    }

    public boolean hasTimeElapsed(long ms) {
        return getElapsedTime() >= ms;
    }

    public boolean getPass(long time) {
        return getElapsedTime() >= time;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    public void setTime() {
        lastMS = 0L;
    }
}