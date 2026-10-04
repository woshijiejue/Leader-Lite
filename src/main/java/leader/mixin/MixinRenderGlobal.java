package leader.mixin;

import leader.module.modules.render.BetterFPS;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {
    @Redirect(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;updateChunkNow(Lnet/minecraft/client/renderer/chunk/RenderChunk;)Z"), require = 0)
    private boolean betterFPSDeferChunkBuild(ChunkRenderDispatcher dispatcher, RenderChunk chunk) {
        if (BetterFPS.using && BetterFPS.aggressive() && BetterFPS.asyncChunkBuild.getValue()) {
            Minecraft mc = Minecraft.getMinecraft();
            // First meshes and the section being interacted with remain synchronous.
            // Queue rejection must fall back: vanilla clears needsUpdate after this call.
            boolean nearby = mc.thePlayer == null || chunk.boundingBox.expand(8, 8, 8).isVecInside(mc.thePlayer.getPositionEyes(1));
            if (!nearby && chunk.getCompiledChunk() != CompiledChunk.DUMMY && dispatcher.updateChunkLater(chunk)) return true;
        }
        return dispatcher.updateChunkNow(chunk);
    }

    @ModifyVariable(method = "updateChunks", at = @At("HEAD"), argsOnly = true, require = 0)
    private long betterFPSChunkBudget(long vanillaDeadline) {
        return BetterFPS.chunkUpdateDeadline(vanillaDeadline);
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void betterFPSClouds(float partialTicks, int pass, CallbackInfo ci) {
        if (BetterFPS.using && BetterFPS.noClouds.getValue()) ci.cancel();
    }
}
