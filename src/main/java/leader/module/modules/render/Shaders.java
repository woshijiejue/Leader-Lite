package leader.module.modules.render;

import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import leader.util.shader.Bloom;
import leader.util.shader.KawaseBlur;
import leader.util.shader.ShaderElement;
import leader.util.shader.Shadow;
import net.minecraft.client.shader.Framebuffer;

public class Shaders extends Module {

    public final BooleanProperty blur = new BooleanProperty("blur", true);
    public final IntProperty blurRadius = new IntProperty("blur-radius", 2, 1, 8, blur::getValue);
    public final IntProperty blurOffset = new IntProperty("blur-offset", 3, 1, 10, blur::getValue);
    public final BooleanProperty shadow = new BooleanProperty("shadow", false);
    public final IntProperty shadowRadius = new IntProperty("shadow-radius", 8, 1, 20, shadow::getValue);
    public final IntProperty shadowOffset = new IntProperty("shadow-offset", 1, 1, 10, shadow::getValue);
    public final BooleanProperty bloom = new BooleanProperty("bloom", false);
    public final IntProperty bloomRadius = new IntProperty("bloom-radius", 3, 1, 10, bloom::getValue);
    public final IntProperty bloomOffset = new IntProperty("bloom-offset", 1, 1, 10, bloom::getValue);

    private Framebuffer maskStencil;

    public Shaders() {
        super("Shaders", true);
    }

    private void runMaskTasks(Framebuffer stencil) {
        stencil.framebufferClear();
        stencil.bindFramebuffer(false);
        for (Runnable runnable : ShaderElement.getTasks()) {
            runnable.run();
        }
        stencil.unbindFramebuffer();
    }

    public void renderShaders() {
        // A fullscreen shader chain is unnecessary when no HUD supplied a mask.
        if (ShaderElement.getTasks().isEmpty()) return;
        boolean extras = !BetterFPS.skipExtraPostFX();
        if (!(blur.getValue() || extras && (shadow.getValue() || bloom.getValue()))) {
            ShaderElement.getTasks().clear();
            return;
        }
        maskStencil = ShaderElement.createFrameBuffer(maskStencil);
        // All three effects consume the same mask. Build it once, not once per effect.
        runMaskTasks(maskStencil);
        if (this.blur.getValue()) {
            KawaseBlur.renderBlur(maskStencil.framebufferTexture, BetterFPS.limitShaderPasses(this.blurRadius.getValue()), this.blurOffset.getValue());
        }
        if (extras && this.shadow.getValue()) {
            Shadow.renderShadow(maskStencil.framebufferTexture, BetterFPS.limitShaderPasses(this.shadowRadius.getValue()), this.shadowOffset.getValue());
        }
        if (extras && this.bloom.getValue()) {
            Bloom.renderBloom(maskStencil.framebufferTexture, BetterFPS.limitShaderPasses(this.bloomRadius.getValue()), this.bloomOffset.getValue());
        }
        ShaderElement.getTasks().clear();
    }
}
