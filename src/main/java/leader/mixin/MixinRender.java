package leader.mixin;

import leader.module.modules.render.BetterFPS;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
public abstract class MixinRender {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private void betterFPSShadow(Entity entity, double x, double y, double z, float alpha, float partialTicks, CallbackInfo ci) {
        if (BetterFPS.using && BetterFPS.noEntityShadows.getValue()) ci.cancel();
    }
}
