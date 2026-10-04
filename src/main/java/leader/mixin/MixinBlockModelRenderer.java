package leader.mixin;

import leader.Leader;
import leader.module.modules.render.Xray;
import leader.module.modules.render.BetterFPS;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {BlockModelRenderer.class}, priority = 9999)
public abstract class MixinBlockModelRenderer {
    @Unique
    private static final EnumFacing[] leader$renderFaces = EnumFacing.values();

    @Redirect(method = {"renderModelAmbientOcclusion", "renderModelStandard", "fillQuadBounds"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/EnumFacing;values()[Lnet/minecraft/util/EnumFacing;"),
            require = 0)
    private EnumFacing[] betterFPSRenderFaces() {
        // These loops only read the directions. Avoid cloning the enum array for each block model.
        return BetterFPS.using ? leader$renderFaces : EnumFacing.values();
    }

    @Shadow
    public boolean renderModelAmbientOcclusion(
            IBlockAccess iBlockAccess, IBakedModel iBakedModel, Block block, BlockPos blockPos, WorldRenderer worldRenderer, boolean boolean6
    ) {
        return false;
    }

    @Redirect(method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;Z)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isAmbientOcclusionEnabled()Z"), require = 0)
    private boolean betterFPSAmbientOcclusion() {
        // Keep vanilla/OptiFine's model selection, custom colors and exception handling intact.
        return !BetterFPS.useFastBlockLighting() && net.minecraft.client.Minecraft.isAmbientOcclusionEnabled();
    }

    @Inject(
            method = {"renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;Z)Z"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void renderModel(
            IBlockAccess iBlockAccess,
            IBakedModel iBakedModel,
            IBlockState iBlockState,
            BlockPos blockPos,
            WorldRenderer worldRenderer,
            boolean boolean6,
            CallbackInfoReturnable<Boolean> callbackInfoReturnable
    ) {
        if (Leader.moduleManager != null) {
            if (Leader.moduleManager.modules.get(Xray.class).isEnabled()) {
                callbackInfoReturnable.setReturnValue(
                        this.renderModelAmbientOcclusion(iBlockAccess, iBakedModel, iBlockState.getBlock(), blockPos, worldRenderer, boolean6)
                );
            }
        }
    }
}
