package leader.mixin;

import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyBinding.class)
public interface IAccessorKeyBinding {
    @Accessor("pressed")
    void setPressed(boolean boolean1);

    @Invoker("unpressKey")
    void callUnpressKey();
}
