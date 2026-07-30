package leader.mixin;

import leader.client.ui.alt.GuiAccountManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    @Inject(method = "addSingleplayerMultiplayerButtons", at = @At("TAIL"))
    private void addCustomButton(int p_73969_1_, int p_73969_2_, CallbackInfo ci) {
        this.buttonList.add(new GuiButton(3, this.width / 2 - 100, p_73969_1_ + p_73969_2_ * 2, "Alt Manager"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == 3) {
            this.mc.displayGuiScreen(new GuiAccountManager(this));
        }
    }
}