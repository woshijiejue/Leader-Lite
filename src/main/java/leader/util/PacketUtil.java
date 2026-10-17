package leader.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;

public class PacketUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ThreadLocal<SendAttempt> sendAttempt = new ThreadLocal<>();

    public static boolean trySendPacket(Packet<?> packet) {
        if (packet == null || mc.getNetHandler() == null) return false;
        SendAttempt previous = sendAttempt.get();
        SendAttempt attempt = new SendAttempt(packet, previous);
        sendAttempt.set(attempt);
        try {
            sendPacket(packet);
            return !attempt.cancelled;
        } finally {
            if (previous == null) sendAttempt.remove();
            else sendAttempt.set(previous);
        }
    }

    public static void markSendCancelled(Packet<?> packet) {
        for (SendAttempt attempt = sendAttempt.get(); attempt != null; attempt = attempt.previous) {
            if (attempt.packet == packet) attempt.cancelled = true;
        }
    }

    private static final class SendAttempt {
        private final Packet<?> packet;
        private final SendAttempt previous;
        private boolean cancelled;

        private SendAttempt(Packet<?> packet, SendAttempt previous) {
            this.packet = packet;
            this.previous = previous;
        }
    }

    public static void sendPacket(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet);
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet, null);
    }
    public static void receivePacket(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            Packet<INetHandlerPlayClient> casted = castPacket(packet);
            casted.processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }
    @SuppressWarnings("unchecked")
    public static <H extends INetHandler> Packet<H> castPacket(Packet<?> packet) throws ClassCastException {
        return (Packet<H>) packet;
    }
}
