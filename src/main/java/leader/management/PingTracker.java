package leader.management;

import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.TickEvent;
import leader.module.modules.render.Island;
import leader.module.modules.render.Watermark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S37PacketStatistics;

/** Shared HUD latency estimate. A statistics request measures a real application round trip,
 * not the time between receiving and replying to a keep-alive (which is NOT ping).
 * Tab latency of 0/1 is treated as unavailable; unavailable values are displayed as --.
 */
public final class PingTracker {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long INTERVAL = 15000000000L;
    private static final long TIMEOUT = 5000000000L;
    private static final long MAX_AGE = 45000000000L;
    public static final PingTracker INSTANCE = new PingTracker();
    private volatile NetworkManager connection;
    private volatile Packet<?> request;
    private volatile long sentAt;
    private volatile long sampleAt;
    private volatile int measured = -1;
    private long nextProbe;
    private long requestedAt;

    private PingTracker() { }

    @EventTarget
    public synchronized void onTick(TickEvent event) {
        if (event.getType() != EventType.POST) return;
        NetworkManager current = mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager();
        if (current != connection) {
            connection = current;
            request = null; sentAt = sampleAt = nextProbe = requestedAt = 0;
            measured = -1;
        }
        if (current == null || mc.thePlayer == null || mc.theWorld == null || mc.isSingleplayer() || !needed()) return;
        long now = System.nanoTime();
        if (request != null && now - requestedAt > TIMEOUT) {
            request = null;
            sentAt = 0;
        }
        if (now < nextProbe || request != null) return;
        // Go through the normal packet pipeline; do not bypass Blink/Delay or change keep-alives.
        nextProbe = now + INTERVAL;
        requestedAt = now;
        Packet<?> probe = new C16PacketClientStatus(C16PacketClientStatus.EnumState.REQUEST_STATS);
        request = probe;
        current.sendPacket(probe);
    }

    private boolean needed() {
        if (Leader.moduleManager == null) return false;
        Island island = (Island) Leader.moduleManager.modules.get(Island.class);
        Watermark watermark = (Watermark) Leader.moduleManager.modules.get(Watermark.class);
        return (island != null && island.isEnabled() && island.showMetrics.getValue())
                || (watermark != null && watermark.isEnabled());
    }

    public synchronized void sent(NetworkManager manager, Packet<?> packet) {
        if (manager != connection || !(packet instanceof C16PacketClientStatus)
                || ((C16PacketClientStatus) packet).getStatus() != C16PacketClientStatus.EnumState.REQUEST_STATS) return;
        if (packet == request) sentAt = System.nanoTime();
        else {
            // Vanilla statistics-screen requests have no response IDs; don't misattribute their replies.
            request = null;
            sentAt = 0;
        }
    }

    public synchronized void received(NetworkManager manager, Packet<?> packet) {
        long start = sentAt;
        if (manager != connection || !(packet instanceof S37PacketStatistics) || request == null || start == 0) return;
        long now = System.nanoTime(), elapsed = now - start;
        request = null;
        sentAt = 0;
        if (elapsed <= 0 || elapsed > TIMEOUT) return;
        // Includes server processing time; do not invent a value when there is no measured response.
        measured = Math.max(1, (int) Math.round(elapsed / 1000000.0));
        sampleAt = now;
    }

    public String display() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) return "--";
        if (mc.isSingleplayer()) return "0";
        if (mc.getNetHandler().getNetworkManager() != connection) return "--";
        long now = System.nanoTime();
        if (measured >= 0 && sampleAt != 0 && now - sampleAt <= MAX_AGE) return Integer.toString(measured);
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        int tabPing = info == null ? -1 : info.getResponseTime();
        return tabPing > 1 ? Integer.toString(tabPing) : "--";
    }
}
