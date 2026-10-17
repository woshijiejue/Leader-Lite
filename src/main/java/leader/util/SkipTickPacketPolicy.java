package leader.util;

import net.minecraft.network.Packet;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.*;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SkipTickPacketPolicy {
    private static final ThreadLocal<Packet<?>> correctionAcknowledgement = new ThreadLocal<>();
    private static final Set<Packet<?>> corrections = Collections.newSetFromMap(new WeakHashMap<>());

    private SkipTickPacketPolicy() { }

    public static synchronized void trackCorrection(Packet<?> packet) {
        corrections.add(packet);
    }

    public static synchronized boolean consumeCorrection(Packet<?> packet) {
        return corrections.remove(packet);
    }

    public static boolean isCorrectionAcknowledgement(Packet<?> packet) {
        return packet != null && correctionAcknowledgement.get() == packet;
    }

    public static void sendCorrectionAcknowledgement(NetworkManager network, Packet<?> packet) {
        Packet<?> previous = correctionAcknowledgement.get();
        correctionAcknowledgement.set(packet);
        try {
            network.sendPacket(packet);
        } finally {
            if (previous == null) correctionAcknowledgement.remove();
            else correctionAcknowledgement.set(previous);
        }
    }

    public static boolean isGameplayPacket(Packet<?> packet) {
        return packet instanceof C02PacketUseEntity || packet instanceof C03PacketPlayer
                || packet instanceof C07PacketPlayerDigging || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange || packet instanceof C0APacketAnimation
                || packet instanceof C0BPacketEntityAction || packet instanceof C0CPacketInput
                || packet instanceof C0DPacketCloseWindow || packet instanceof C0EPacketClickWindow
                || packet instanceof C10PacketCreativeInventoryAction || packet instanceof C11PacketEnchantItem
                || packet instanceof C12PacketUpdateSign || packet instanceof C13PacketPlayerAbilities
                || packet instanceof C17PacketCustomPayload || packet instanceof C18PacketSpectate;
    }
}
