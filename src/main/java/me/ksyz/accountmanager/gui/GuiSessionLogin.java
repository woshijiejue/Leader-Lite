package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.auth.MicrosoftAuth;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.auth.TokenAuth;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiSessionLogin extends GuiScreen {
    private GuiScreen previousScreen;

    private String status = "Session Login";
    private GuiTextField sessionField;
    private ScaledResolution sr;
    private GuiButton loginButton;
    private ExecutorService executor;
    private CompletableFuture<Void> task;
    private boolean authenticating;

    public GuiSessionLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        sr = new ScaledResolution(mc);

        String previousInput = sessionField == null ? "" : sessionField.getText();
        sessionField = new GuiTextField(1, mc.fontRendererObj,sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2, 200, 20);
        sessionField.setMaxStringLength(32767);
        sessionField.setText(previousInput);
        sessionField.setFocused(true);

        buttonList.add(loginButton = new GuiButton(998, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 30, 200, 20, "Login"));

        super.initGui();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (task != null && !task.isDone()) task.cancel(true);
        if (executor != null) executor.shutdownNow();

        super.onGuiClosed();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (loginButton != null) loginButton.enabled = !authenticating;
        drawDefaultBackground();

        mc.fontRendererObj.drawString(status, sr.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(status) / 2, sr.getScaledHeight() / 2 - 30, Color.WHITE.getRGB());
        sessionField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button != null && button.enabled && button.id == 998 && !authenticating) {
            final String input;
            try {
                input = TokenAuth.normalize(sessionField.getText());
            } catch (IllegalArgumentException exception) {
                status = "§c" + TokenAuth.errorMessage(exception);
                return;
            }
            if (executor == null || executor.isShutdown()) {
                executor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "Leader-SessionLogin");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            authenticating = true;
            status = "Checking Minecraft token...";
            task = MicrosoftAuth.login(input, executor)
                    .thenAccept(session -> mc.addScheduledTask(() -> {
                        if (mc.currentScreen != this) return;
                        try {
                            SessionManager.set(session);
                            mc.displayGuiScreen(previousScreen);
                        } catch (RuntimeException exception) {
                            status = "§c" + TokenAuth.errorMessage(exception);
                        } finally {
                            authenticating = false;
                        }
                    }))
                    .exceptionally(error -> {
                        mc.addScheduledTask(() -> {
                            if (mc.currentScreen == this) {
                                authenticating = false;
                                status = "§c" + TokenAuth.errorMessage(error);
                            }
                        });
                        return null;
                    });
        }

        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        sessionField.textboxKeyTyped(typedChar, keyCode);

        if (Keyboard.KEY_ESCAPE == keyCode) mc.displayGuiScreen(previousScreen);
        else super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        sessionField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        sessionField.updateCursorCounter();
    }
}
