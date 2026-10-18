package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.MicrosoftAuth;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.auth.TokenAuth;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.*;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiAddToken extends GuiScreen {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private final GuiScreen previousScreen;

    private GuiButton openButton = null;
    private boolean openButtonEnabled = true;
    private String status = null;
    private String cause = null;
    private ExecutorService executor = null;
    private CompletableFuture<Void> task = null;
    private boolean success = false;
    private boolean authenticating;
    private GuiTextField tokenField;

    public GuiAddToken(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        ScaledResolution sr = new ScaledResolution(mc);
        String previousInput = tokenField == null ? "" : tokenField.getText();
        tokenField = new GuiTextField(1, mc.fontRendererObj,sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2, 200, 20);
        tokenField.setMaxStringLength(32767);
        tokenField.setText(previousInput);
        tokenField.setFocused(true);

        buttonList.add(openButton = new GuiButton(998, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 30, 200, 20, "Add"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (task != null && !task.isDone()) task.cancel(true);
        if (executor != null) executor.shutdownNow();
    }

    @Override
    public void updateScreen() {
        tokenField.updateCursorCounter();
        if (success) {
            mc.displayGuiScreen(new GuiAccountManager(
                    previousScreen,
                    new Notification(
                            TextFormatting.translate(String.format(
                                    "&aSuccessful login! (%s)&r",
                                    SessionManager.get().getUsername()
                            )),
                            5000L
                    )
            ));
            success = false;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        tokenField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (openButton != null) {
            openButton.enabled = openButtonEnabled && !authenticating;
        }
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        drawCenteredString(
                fontRendererObj, "Add Token",
                width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - fontRendererObj.FONT_HEIGHT * 2 - 14, 11184810
        );
        tokenField.drawTextBox();

        if (status != null) {
            drawCenteredString(
                    fontRendererObj, TextFormatting.translate(status),
                    width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - 14, -1
            );
        }

        if (cause != null) {
            String causeText = TextFormatting.translate(cause);
            Gui.drawRect(
                    0, height - 2 - fontRendererObj.FONT_HEIGHT - 3,
                    3 + mc.fontRendererObj.getStringWidth(causeText) + 3, height,
                    0x64000000
            );
            drawString(
                    fontRendererObj, TextFormatting.translate(cause),
                    3, height - 2 - fontRendererObj.FONT_HEIGHT, -1
            );
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        tokenField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(previousScreen);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }

        if (button.enabled) {
            if (button.id == 998) {
                if (!authenticating) {
                    final String input;
                    try {
                        input = TokenAuth.normalize(tokenField.getText());
                    } catch (IllegalArgumentException exception) {
                        status = "&c" + TokenAuth.errorMessage(exception) + "&r";
                        return;
                    }
                    if (executor == null || executor.isShutdown()) {
                        executor = Executors.newSingleThreadExecutor(runnable -> {
                            Thread thread = new Thread(runnable, "Leader-TokenLogin");
                            thread.setDaemon(true);
                            return thread;
                        });
                    }
                    authenticating = true;
                    AtomicReference<String> refreshToken = new AtomicReference<>("");
                    AtomicReference<String> accessToken = new AtomicReference<>("");
                    accessToken.set(input);
                    status = "&7Checking token...&r";
                    cause = null;
                    task = MicrosoftAuth.login(input, executor)
                            .handle((session, error) -> {
                                if (session != null) return CompletableFuture.completedFuture(session);
                                if (!TokenAuth.canRefresh(input, error)) {
                                    CompletableFuture<Session> failed = new CompletableFuture<>();
                                    failed.completeExceptionally(TokenAuth.rootCause(error));
                                    return failed;
                                }
                                return MicrosoftAuth.refreshMSAccessTokens(input, CLIENT_ID, SCOPE, executor)
                                        .thenCompose(msAccessTokens -> {
                                            refreshToken.set(msAccessTokens.get("refresh_token"));
                                            return MicrosoftAuth.acquireXboxAccessToken(msAccessTokens.get("access_token"), CLIENT_ID, executor);
                                        })
                                        .thenCompose(xboxAccessToken -> MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, executor))
                                        .thenCompose(xboxXstsData -> MicrosoftAuth.acquireMCAccessToken(
                                                xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor))
                                        .thenCompose(mcToken -> {
                                            accessToken.set(mcToken);
                                            return MicrosoftAuth.login(mcToken, executor);
                                        });
                            })
                            .thenCompose(future -> future)
                            .thenAccept(session -> mc.addScheduledTask(() -> {
                                if (mc.currentScreen == this) {
                                    try {
                                        addAccount(session, refreshToken.get(), accessToken.get());
                                    } catch (RuntimeException exception) {
                                        status = "&c" + TokenAuth.errorMessage(exception) + "&r";
                                    } finally {
                                        authenticating = false;
                                    }
                                }
                            }))
                            .exceptionally(error -> {
                                mc.addScheduledTask(() -> {
                                    if (mc.currentScreen == this) {
                                        authenticating = false;
                                        status = "&c" + TokenAuth.errorMessage(error) + "&r";
                                        cause = null;
                                    }
                                });
                                return null;
                            });
                }
            }
        }
    }

    private void addAccount(Session session, String refresh, String access) {
        SessionManager.set(session);
        status = null;
        Account acc = new Account(refresh, access, session.getUsername(), CLIENT_ID, SCOPE);
        for (Account account : AccountManager.accounts) {
            if (acc.getUsername().equals(account.getUsername())) {
                acc.setUnban(account.getUnban());
                break;
            }
        }
        AccountManager.accounts.add(acc);
        AccountManager.save();
        success = true;
    }
}
