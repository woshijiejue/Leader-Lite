package leader.mixin;

import leader.module.modules.render.BetterFPS;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcher {
    @Inject(method = "renderTileEntity", at = @At("HEAD"), cancellable = true)
    private void betterFPSTile(TileEntity tile, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (tile != null && BetterFPS.shouldCancelTileEntity(tile, destroyStage)) ci.cancel();
    }
}
