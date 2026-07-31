package leader.client.module.modules.render;

import leader.client.module.Module;
import leader.client.module.values.impl.ListValue;
import leader.client.ui.BookClickGuiScreen;
import leader.client.ui.ClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {

    private ClickGui clickGui;
    private BookClickGuiScreen bookGui;
    private ListValue mode = new ListValue("Mode", new String[]{"Normal", "Book"}, "Book", this);

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (mode.is("Book")) {
            if (bookGui == null) bookGui = new BookClickGuiScreen();
            mc.displayGuiScreen(bookGui);
        } else {
            if (clickGui == null) clickGui = new ClickGui();
            mc.displayGuiScreen(clickGui);
        }
    }
}
