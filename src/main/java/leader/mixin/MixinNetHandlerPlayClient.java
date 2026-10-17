package leader.mixin;

import leader.Leader;
import leader.module.modules.combat.BackTrack;
import leader.module.modules.combat.KillAura;
import leader.module.modules.combat.Velocity;
import leader.module.modules.movement.Stuck;
import leader.util.SkipTickPacketPolicy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = NetHandlerPlayClient.class, priority = 9999)
public abstract class MixinNetHandlerPlayClient {
    @Unique private boolean leader$skipTickCorrection;

    @Inject(method = "handlePlayerPosLook", at = @At("HEAD"))
    private void leader$stopSkipTick(S08PacketPlayerPosLook packet, CallbackInfo ci) {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) return;
        BackTrack.handleMainThreadPacket(packet);
        Stuck stuck =
                Leader.moduleManager == null
                        ? null
                        : (Stuck) Leader.moduleManager.getModule(Stuck.class);
        leader$skipTickCorrection =
                SkipTickPacketPolicy.consumeCorrection(packet)
                        || Velocity.isSkipTickSequenceActive()
                        || KillAura.isSkipTickAutoBlockActive()
                        || stuck != null
                                && (stuck.isExternalSkipFreezeActive()
                                        || stuck.hasPendingSkipFreeze());
        if (!leader$skipTickCorrection) return;
        Velocity.abortSkipTickForCorrection();
        if (stuck != null && (stuck.isExternalSkipFreezeActive() || stuck.hasPendingSkipFreeze()))
            stuck.abortExternalSkipFreeze();
        KillAura killAura =
                Leader.moduleManager == null
                        ? null
                        : (KillAura) Leader.moduleManager.getModule(KillAura.class);
        if (killAura != null) killAura.handleSkipTickCorrection();
    }

    @Redirect(
            method = "handlePlayerPosLook",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/NetworkManager;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void leader$acknowledgeCorrection(NetworkManager network, Packet<?> packet) {
        if (leader$skipTickCorrection)
            SkipTickPacketPolicy.sendCorrectionAcknowledgement(network, packet);
        else network.sendPacket(packet);
        leader$skipTickCorrection = false;
    }

    @Unique
    private void leader$handleBacktrack(Packet<?> packet, CallbackInfo ci) {
        if (BackTrack.handleMainThreadPacket(packet)) ci.cancel();
    }

    @Inject(method = "handleEntityMovement", at = @At("HEAD"), cancellable = true)
    private void leader$backtrackMovement(S14PacketEntity packet, CallbackInfo ci) {
        leader$handleBacktrack(packet, ci);
    }

    @Inject(method = "handleEntityTeleport", at = @At("HEAD"), cancellable = true)
    private void leader$backtrackTeleport(S18PacketEntityTeleport packet, CallbackInfo ci) {
        leader$handleBacktrack(packet, ci);
    }

    @Inject(method = "handleDestroyEntities", at = @At("HEAD"), cancellable = true)
    private void leader$backtrackDestroy(S13PacketDestroyEntities packet, CallbackInfo ci) {
        leader$handleBacktrack(packet, ci);
    }

    @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
    private void leader$backtrackVelocity(S12PacketEntityVelocity packet, CallbackInfo ci) {
        leader$handleBacktrack(packet, ci);
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    private void leader$backtrackExplosion(S27PacketExplosion packet, CallbackInfo ci) {
        leader$handleBacktrack(packet, ci);
    }

    @Inject(method = "handleJoinGame", at = @At("HEAD"), cancellable = true)
    private void leader$backtrackJoin(S01PacketJoinGame packet, CallbackInfo ci) {
        leader$handleBacktrack(packet, ci);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"), cancellable = true)
    private void leader$backtrackRespawn(S07PacketRespawn packet, CallbackInfo ci) {
        leader$handleBacktrack(packet, ci);
    }
}
