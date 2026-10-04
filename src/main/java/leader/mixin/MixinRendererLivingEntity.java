package leader.mixin;

import leader.Leader;
import leader.event.EventManager;
import leader.event.types.EventType;
import leader.events.RenderLivingEvent;
import leader.module.modules.render.ESP;
import leader.module.modules.render.NameTags;
import leader.module.modules.render.BetterFPS;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(
        value = {RendererLivingEntity.class},
        priority = 9991
)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> extends Render<T> {
    protected MixinRendererLivingEntity(RenderManager renderManager) {
        super(renderManager);
    }

    @Redirect(method = "renderLayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/LayerRenderer;doRenderLayer(Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V"), require = 0)
    private void betterFPSLayers(LayerRenderer<EntityLivingBase> layer, EntityLivingBase entity, float limbSwing,
                                 float limbAmount, float partialTicks, float age, float yaw, float pitch, float scale) {
        if (!BetterFPS.shouldCancelLayer(entity, layer)) {
            layer.doRenderLayer(entity, limbSwing, limbAmount, partialTicks, age, yaw, pitch, scale);
        }
    }

    @Inject(
            method = {"doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V"},
            at = {@At("HEAD")}
    )
    private void doRender(T entityLivingBase, double double2, double double3, double double4, float float5, float float6, CallbackInfo callbackInfo) {
        EventManager.call(new RenderLivingEvent(EventType.PRE, entityLivingBase));
    }

    @Inject(
            method = {"doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V"},
            at = {@At("RETURN")}
    )
    private void postRender(T entityLivingBase, double double2, double double3, double double4, float float5, float float6, CallbackInfo callbackInfo) {
        EventManager.call(new RenderLivingEvent(EventType.POST, entityLivingBase));
    }

    @Inject(
            method = {"canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void canRenderName(T entityLivingBase, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (Leader.moduleManager != null) {
            NameTags nameTags = (NameTags) Leader.moduleManager.modules.get(NameTags.class);
            if (nameTags.isEnabled() && nameTags.shouldRenderTags(entityLivingBase)) {
                callbackInfoReturnable.setReturnValue(false);
            } else {
                ESP esp = (ESP) Leader.moduleManager.modules.get(ESP.class);
                if (esp.isEnabled() && !esp.isOutlineEnabled()) {
                    callbackInfoReturnable.setReturnValue(false);
                }
            }
        }
    }
}
