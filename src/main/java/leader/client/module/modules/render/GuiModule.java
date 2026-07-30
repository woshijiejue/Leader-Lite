package leader.client.module.modules.render;

import leader.client.module.Module;
import leader.client.property.properties.ModeProperty;
import leader.client.ui.BookClickGuiScreen;
import leader.client.ui.ClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private ClickGui clickGui;
    private BookClickGuiScreen bookGui;
    private ModeProperty mode = new ModeProperty("Mode",1,new String[]{"Normal","Book"});

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (mode.getValue() == 1) {
            if (bookGui == null) bookGui = new BookClickGuiScreen();
            mc.displayGuiScreen(bookGui);
        } else {
            if (clickGui == null) clickGui = new ClickGui();
            mc.displayGuiScreen(clickGui);
        }
    }
}
