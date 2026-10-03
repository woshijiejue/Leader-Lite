package leader.mixin;

import leader.Leader;
import leader.event.EventManager;
import leader.events.Render2DEvent;
import leader.module.modules.render.NickHider;
import leader.module.modules.render.Shaders;
import leader.util.shader.ShaderElement;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {GuiIngameForge.class}, priority = 9999)
public abstract class MixinGuiIngameForge {
    @Inject(
            method = {"renderGameOverlay"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/GuiIngameForge;renderTitle(IIF)V",
                    shift = At.Shift.AFTER,
                    remap = false
            )}
    )
    private void renderGameOverlay(float float1, CallbackInfo callbackInfo) {
        if (Leader.moduleManager != null) {
            Shaders shaders = (Shaders) Leader.moduleManager.modules.get(Shaders.class);
            if (shaders != null && shaders.isEnabled()) {
                shaders.renderShaders();
            } else {
                ShaderElement.getTasks().clear();
            }
        }
        EventManager.call(new Render2DEvent(float1));
    }

    @Redirect(
            method = {"renderExperience"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;experience:F"
            )
    )
    private float renderExperience(EntityPlayerSP entityPlayerSP) {
        if (Leader.moduleManager == null) {
            return entityPlayerSP.experience;
        } else {
            NickHider event = (NickHider) Leader.moduleManager.modules.get(NickHider.class);
            return event.isEnabled() && event.level.getValue() ? 0.0F : entityPlayerSP.experience;
        }
    }

    @Redirect(
            method = {"renderExperience"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;experienceLevel:I"
            )
    )
    private int renderExperienceLevel(EntityPlayerSP entityPlayerSP) {
        if (Leader.moduleManager == null) {
            return entityPlayerSP.experienceLevel;
        } else {
            NickHider event = (NickHider) Leader.moduleManager.modules.get(NickHider.class);
            return event.isEnabled() && event.level.getValue() ? 0 : entityPlayerSP.experienceLevel;
        }
    }
}
