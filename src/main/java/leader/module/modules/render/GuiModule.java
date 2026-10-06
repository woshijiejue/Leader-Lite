package leader.module.modules.render;

import leader.module.Module;
import leader.property.properties.ModeProperty;
import leader.property.properties.BooleanProperty;
import leader.ui.ClickGui;
import leader.ui.ListClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty style = new ModeProperty("Style", 0, new String[]{"Window", "List", "Xylitol", "Xylitol List"});
    // Shared decoration controls; they never select or overwrite a module's visual mode.
    public final BooleanProperty visualMotion = new BooleanProperty("Visual Motion", true);
    private ClickGui clickGui;
    private ListClickGui listClickGui;

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (this.style.getValue() == 1 || this.style.getValue() == 3) {
            if (listClickGui == null) listClickGui = new ListClickGui();
            mc.displayGuiScreen(listClickGui);
        } else {
            if (clickGui == null) clickGui = new ClickGui();
            mc.displayGuiScreen(clickGui);
        }
    }
}
