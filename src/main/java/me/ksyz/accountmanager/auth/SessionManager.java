package me.ksyz.accountmanager.auth;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import leader.mixin.IAccessorMinecraft;
import java.util.concurrent.ExecutionException;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class SessionManager {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static Session get() {
        return mc.getSession();
    }

    public static void set(Session session) {
        if (session == null) throw new IllegalArgumentException("Login did not return a session.");
        if (mc.isCallingFromMinecraftThread()) {
            apply(session);
            return;
        }
        try {
            mc.addScheduledTask(() -> apply(session)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Login was cancelled before the session could be applied.");
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to apply the login session.", exception.getCause());
        }
    }

    private static void apply(Session session) {
        ((IAccessorMinecraft) mc).setSession(session);
        if (mc.getSession() != session) throw new IllegalStateException("The login session was not updated.");
    }
}
