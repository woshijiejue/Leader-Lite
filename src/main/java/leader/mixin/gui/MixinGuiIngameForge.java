package leader.mixin.gui;

import leader.client.Leader;
import leader.client.event.EventManager;
import leader.client.events.Render2DEvent;
import leader.client.module.modules.render.HUD;
import leader.client.module.modules.render.NickHider;
import leader.client.module.modules.render.Notification;
import leader.client.module.modules.render.Potion;
import leader.client.module.modules.render.TargetHUD;
import leader.client.util.render.shader.ShaderElement;
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
            HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
            TargetHUD targetHud = (TargetHUD) Leader.moduleManager.modules.get(TargetHUD.class);
            Notification notification = (Notification) Leader.moduleManager.modules.get(Notification.class);
            Potion potion = (Potion) Leader.moduleManager.modules.get(Potion.class);
            boolean hudBlur = hud != null && hud.isEnabled() && hud.blur.getValue();
            boolean targetBlur = targetHud != null && targetHud.isEnabled() && targetHud.blur.getValue();
            boolean notificationBlur = notification != null && notification.isEnabled() && notification.blur.getValue();
            boolean potionBlur = potion != null && potion.isEnabled() && potion.blur.getValue();

            if (hudBlur || targetBlur) {
                if (hud != null) hud.drawBlur();
            } else if (notificationBlur && notification != null) {
                notification.drawBlur();
            } else if (potionBlur && potion != null) {
                potion.drawBlur();
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
