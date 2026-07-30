package leader.client.management;

import leader.client.Leader;
import leader.client.enums.BlinkModules;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.PacketEvent;
import leader.client.events.TickEvent;
import leader.client.module.modules.player.BlinkSettings;
import leader.client.util.InstanceAccess;
import leader.client.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class BlinkManager implements InstanceAccess {
    public BlinkModules blinkModule = BlinkModules.NONE;
    public boolean blinking = false;
    public Deque<Packet<?>> blinkedPackets = new ConcurrentLinkedDeque<>();
    private boolean slowReleasing = false;
    private int slowReleaseTicks = 0;

    public boolean offerPacket(Packet<?> packet) {
        if (this.blinkModule == BlinkModules.NONE || packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return false;
        } else if (this.blinkedPackets.isEmpty() && packet instanceof C0FPacketConfirmTransaction) {
            return false;
        } else {
            this.blinkedPackets.offer(packet);
            return true;
        }
    }

    private BlinkSettings getBlinkSettings() {
        if (Leader.moduleManager == null) return null;
        return (BlinkSettings) Leader.moduleManager.modules.get(BlinkSettings.class);
    }

    public boolean setBlinkState(boolean state, BlinkModules module) {
        if (module == BlinkModules.NONE) {
            return false;
        }
        if (state) {
            this.blinkModule = module;
            this.blinking = true;
            BlinkSettings settings = getBlinkSettings();
            if (settings != null && settings.slowRelease.getValue() && settings.slowReleaseTime.getValue() == 0) {
                this.slowReleasing = true;
                this.slowReleaseTicks = 0;
            }
        } else {
            if (blinkModule != module) {
                return false;
            }
            BlinkSettings settings = getBlinkSettings();
            if (settings != null && settings.slowRelease.getValue() && settings.slowReleaseTime.getValue() == 1) {
                this.blinking = false;
                this.slowReleasing = true;
                this.slowReleaseTicks = 0;
                return true;
            }
            this.blinking = false;
            this.slowReleasing = false;
            if (Minecraft.getMinecraft().getNetHandler() != null && this.blinkedPackets.isEmpty()) {
                this.blinkModule = BlinkModules.NONE;
                return true;
            }
            for (Packet<?> blinkedPacket : blinkedPackets) {
                PacketUtil.sendPacketNoEvent(blinkedPacket);
            }
            this.blinkedPackets.clear();
            this.blinkModule = BlinkModules.NONE;
        }
        return true;
    }

    public BlinkModules getBlinkingModule() {
        return this.blinkModule;
    }

    public long countMovement() {
        return this.blinkedPackets.stream().filter(packet -> packet instanceof C03PacketPlayer).count();
    }

    public boolean isBlinking() {
        return blinking;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.setBlinkState(false, this.blinkModule);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer.isDead) {
                this.slowReleasing = false;
                this.setBlinkState(false, this.blinkModule);
            }
            if (slowReleasing) {
                processSlowRelease();
            }
        }
    }

    private void processSlowRelease() {
        BlinkSettings settings = getBlinkSettings();
        if (settings == null || !settings.slowRelease.getValue()) {
            slowReleasing = false;
            flushRemaining();
            return;
        }
        slowReleaseTicks++;
        if (slowReleaseTicks < settings.slowReleaseDelay.getValue()) {
            return;
        }
        slowReleaseTicks = 0;
        int maxTotal = settings.maxPacketsPerTick.getValue();
        int maxC03 = settings.maxC03PacketsPerTick.getValue();
        int released = 0;
        int c03Released = 0;
        int size = blinkedPackets.size();
        for (int i = 0; i < size && released < maxTotal; i++) {
            Packet<?> pkt = blinkedPackets.poll();
            if (pkt == null) break;
            if (pkt instanceof C03PacketPlayer) {
                if (c03Released >= maxC03) {
                    blinkedPackets.offer(pkt);
                    continue;
                }
                c03Released++;
            }
            boolean wasBlinking = this.blinking;
            this.blinking = false;
            PacketUtil.sendPacketNoEvent(pkt);
            this.blinking = wasBlinking;
            released++;
        }
        if (blinkedPackets.isEmpty()) {
            if (!blinking) {
                slowReleasing = false;
                this.blinkModule = BlinkModules.NONE;
            }
        }
    }

    private void flushRemaining() {
        boolean wasBlinking = this.blinking;
        this.blinking = false;
        for (Packet<?> blinkedPacket : blinkedPackets) {
            PacketUtil.sendPacketNoEvent(blinkedPacket);
        }
        this.blinking = wasBlinking;
        blinkedPackets.clear();
        if (!wasBlinking) {
            this.blinkModule = BlinkModules.NONE;
        }
    }
}
