package leader.mixin;

import leader.Leader;
import leader.event.EventManager;
import leader.events.HitSlowDownEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityPlayer.class}, priority = 9999)
public abstract class MixinEntityPlayer extends MixinEntityLivingBase {

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"
            )
    )
    private void setSprinting(EntityPlayer entityPlayer, boolean sprinting) {
        if (Leader.moduleManager == null) {
            entityPlayer.setSprinting(sprinting);
            return;
        }
        HitSlowDownEvent event = (HitSlowDownEvent) EventManager.call(new HitSlowDownEvent());
        if (Math.abs(event.getSlowDown() - 0.6) > 1.0E-9) {
            entityPlayer.motionX = entityPlayer.motionX / 0.6 * event.getSlowDown();
            entityPlayer.motionZ = entityPlayer.motionZ / 0.6 * event.getSlowDown();
        }
        if (!event.getSprint()) {
            entityPlayer.setSprinting(sprinting);
        }
    }
}
