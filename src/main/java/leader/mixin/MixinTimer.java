package leader.mixin;

import leader.util.TimerSpeedLease;

import net.minecraft.util.Timer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Timer.class)
public abstract class MixinTimer {
    @Inject(method = "updateTimer", at = @At("RETURN"))
    private void leader$limitTickBurst(CallbackInfo ci) {
        TimerSpeedLease.limitFrame((Timer) (Object) this);
    }
}
