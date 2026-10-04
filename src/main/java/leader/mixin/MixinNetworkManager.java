package leader.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.GenericFutureListener;
import leader.Leader;
import leader.event.EventManager;
import leader.event.types.EventType;
import leader.events.PacketEvent;
import leader.management.PingTracker;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S37PacketStatistics;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Future;

@SideOnly(Side.CLIENT)
@Mixin(value = {NetworkManager.class}, priority = 9999)
public abstract class MixinNetworkManager {
    @Inject(
            method = {"channelRead0*"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo callbackInfo) {
        // Timestamp at network arrival, before artificial packet delay or main-thread processing.
        if (packet instanceof S37PacketStatistics) PingTracker.INSTANCE.received((NetworkManager) (Object) this, packet);
        if (!packet.getClass().getName().startsWith("net.minecraft.network.play.client")) {
            if (Leader.delayManager != null && Leader.delayManager.shouldDelay((Packet<INetHandlerPlayClient>) packet)) {
                callbackInfo.cancel();
            } else {
                PacketEvent event = new PacketEvent(EventType.RECEIVE, packet);
                EventManager.call(event);
                if (event.isCancelled()) {
                    callbackInfo.cancel();
                }
            }
        }
    }

    @Inject(method = "dispatchPacket", at = @At("HEAD"))
    private void trackPingSend(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>>[] listeners,
                               CallbackInfo ci) {
        if (packet instanceof C16PacketClientStatus) PingTracker.INSTANCE.sent((NetworkManager) (Object) this, packet);
    }

    @Inject(
            method = {"sendPacket(Lnet/minecraft/network/Packet;)V"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void sendPacket(Packet<?> packet, CallbackInfo callbackInfo) {
        if (!packet.getClass().getName().startsWith("net.minecraft.network.play.server")) {
            PacketEvent event = new PacketEvent(EventType.SEND, packet);
            EventManager.call(event);
            if (event.isCancelled()) {
                callbackInfo.cancel();
            } else if (Leader.playerStateManager != null && Leader.blinkManager != null && Leader.lagManager != null) {
                if (!Leader.lagManager.isFlushing()) {
                    Leader.playerStateManager.handlePacket(packet);
                    if (Leader.blinkManager.isBlinking()) {
                        if (Leader.blinkManager.offerPacket(packet)) {
                            callbackInfo.cancel();
                            return;
                        }
                    }
                    if (Leader.lagManager.handlePacket(packet)) {
                        callbackInfo.cancel();
                    }
                }
            }
        }
    }

    @Inject(
            method = {"sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void sendPacket2(
            Packet<?> packet,
            GenericFutureListener<? extends Future<? super Void>> genericFutureListener,
            GenericFutureListener<? extends Future<? super Void>>[] arr,
            CallbackInfo callbackInfo
    ) {
        if (!packet.getClass().getName().startsWith("net.minecraft.network.play.server")) {
            if (Leader.playerStateManager != null && Leader.blinkManager != null && Leader.lagManager != null) {
                if (!Leader.lagManager.isFlushing()) {
                    Leader.playerStateManager.handlePacket(packet);
                    if (Leader.blinkManager.isBlinking()) {
                        if (Leader.blinkManager.offerPacket(packet)) {
                            callbackInfo.cancel();
                            return;
                        }
                    }
                    if (Leader.lagManager.handlePacket(packet)) {
                        callbackInfo.cancel();
                    }
                }
            }
        }
    }
}
