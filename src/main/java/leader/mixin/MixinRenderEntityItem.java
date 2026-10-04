package leader.mixin;

import leader.module.modules.render.BetterFPS;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderEntityItem.class)
public abstract class MixinRenderEntityItem {
    @Inject(method = "func_177077_a", at = @At("RETURN"), cancellable = true, require = 0)
    private void betterFPSItemCopies(EntityItem item, double x, double y, double z, float partialTicks,
                                     IBakedModel model, CallbackInfoReturnable<Integer> ci) {
        // Keep one complete textured model instead of drawing 2-5 identical copies for a distant stack.
        if (BetterFPS.singleItemModel.getValue() && BetterFPS.distantModel(item) && ci.getReturnValue() > 1) {
            ci.setReturnValue(1);
        }
    }
}
