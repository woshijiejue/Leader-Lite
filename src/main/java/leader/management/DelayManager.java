package leader.management;

import leader.enums.DelayModules;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.PacketEvent;
import leader.events.TickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DelayManager {
    public static Minecraft mc = Minecraft.getMinecraft();
    public volatile DelayModules delayModule = DelayModules.NONE;
    public long delay = 0L;
    public Deque<Packet<INetHandlerPlayClient>> delayedPacket = new ConcurrentLinkedDeque<>();
    private int replayDepth;

    public boolean isReplaying() {
        return replayDepth > 0;
    }

    public synchronized boolean shouldDelay(Packet<INetHandlerPlayClient> packet) {
        if (delayModule == DelayModules.STUCK || delayModule == DelayModules.VELOCITY_SKIP_TICK) {
            boolean knockback =
                    packet instanceof S27PacketExplosion
                            || packet instanceof S12PacketEntityVelocity
                                    && mc.thePlayer != null
                                    && ((S12PacketEntityVelocity) packet).getEntityID()
                                            == mc.thePlayer.getEntityId();
            if (knockback) delayedPacket.offer(packet);
            return knockback;
        }
        if (this.delayModule == DelayModules.NONE) {
            return false;
        } else if (packet instanceof S00PacketKeepAlive) {
            return false;
        } else if (!(packet instanceof S01PacketJoinGame)
                && !(packet instanceof S07PacketRespawn)) {
            if (packet instanceof S19PacketEntityStatus) {
                S19PacketEntityStatus s19 = (S19PacketEntityStatus) packet;
                Entity entity = s19.getEntity(mc.theWorld);
                if (entity != null && (!entity.equals(mc.thePlayer) || s19.getOpCode() != 2)) {
                    return false;
                }
            }
            this.delayedPacket.offer(packet);
            return true;
        } else {
            this.setDelayState(false, this.delayModule);
            return false;
        }
    }

    public synchronized boolean setDelayState(boolean state, DelayModules delayModule) {
        if (state) {
            this.delay = 0;
            this.delayModule = delayModule;
        } else {
            if (this.delayModule != delayModule) return this.delayModule != DelayModules.NONE;
            this.delayModule = DelayModules.NONE;
            replayDepth++;
            try {
                Packet<INetHandlerPlayClient> packet;
                while ((packet = this.delayedPacket.poll()) != null) {
                    if (mc.getNetHandler() != null) packet.processPacket(mc.getNetHandler());
                }
            } finally {
                replayDepth--;
            }
        }
        return this.delayModule != DelayModules.NONE;
    }

    public DelayModules getDelayModule() {
        return this.delayModule;
    }

    public void delay(DelayModules modules) {
        this.delayModule = modules;
    }

    public synchronized boolean beginDelay(
            DelayModules owner, Packet<INetHandlerPlayClient> firstPacket) {
        if (owner == DelayModules.NONE
                || delayModule != DelayModules.NONE
                || !delayedPacket.isEmpty()) return false;
        delayedPacket.offer(firstPacket);
        delay = 0;
        delayModule = owner;
        return true;
    }

    public synchronized void discardDelay(DelayModules owner) {
        if (delayModule != owner) return;
        delayModule = DelayModules.NONE;
        delayedPacket.clear();
        delay = 0;
    }

    public synchronized void releaseDelay(DelayModules owner) {
        if (delayModule != owner) return;
        if (mc.getNetHandler() == null) discardDelay(owner);
        else setDelayState(false, owner);
    }

    public long getDelay() {
        return this.delay;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.setDelayState(false, this.delayModule);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer == null || mc.thePlayer.isDead) {
                this.discardDelay(this.delayModule);
            }
            if (this.delayModule != DelayModules.NONE) {
                this.delay++;
            }
        }
    }
}
