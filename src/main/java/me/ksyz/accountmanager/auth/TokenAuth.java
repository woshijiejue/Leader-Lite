package me.ksyz.accountmanager.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ksyz.accountmanager.utils.SSLUtils;
import net.minecraft.util.Session;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class TokenAuth {
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private TokenAuth() { }

    public static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("Please enter a Minecraft access token.");
        String token = input.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
        String[] session = token.split(":", 3);
        if (session.length == 3 && session[1].replace("-", "").matches("[0-9a-fA-F]{32}")) token = session[2].trim();
        if (token.length() >= 2 && ((token.startsWith("\"") && token.endsWith("\""))
                || (token.startsWith("'") && token.endsWith("'")))) token = token.substring(1, token.length() - 1).trim();
        if (token.isEmpty()) throw new IllegalArgumentException("Please enter a Minecraft access token.");
        if (token.length() > 32767 || token.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("Invalid token format. Paste the complete token without spaces or line breaks.");
        }
        return token;
    }

    public static boolean isAccessToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    }

    public static Session login(String input) throws IOException {
        String token = normalize(input);
        HttpsURLConnection connection = (HttpsURLConnection) new URL(PROFILE_URL).openConnection();
        connection.setSSLSocketFactory(SSLUtils.getSSLContext().getSocketFactory());
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new ProfileException(status);
            try (InputStream stream = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parseProfile(new JsonParser().parse(reader), token);
            } catch (RuntimeException exception) {
                throw new IOException("Minecraft returned an invalid profile response. Please try again.");
            }
        } finally {
            connection.disconnect();
        }
    }

    static Session parseProfile(JsonElement response, String token) throws IOException {
        if (response == null || !response.isJsonObject()) throw new IOException("Minecraft returned an invalid profile response.");
        JsonObject profile = response.getAsJsonObject();
        JsonElement id = profile.get("id");
        JsonElement name = profile.get("name");
        if (id == null || name == null || !id.isJsonPrimitive() || !name.isJsonPrimitive()
                || !id.getAsJsonPrimitive().isString() || !name.getAsJsonPrimitive().isString()) {
            throw new IOException("Minecraft returned an incomplete profile response.");
        }
        String uuid = id.getAsString().replace("-", "");
        String username = name.getAsString();
        if (!uuid.matches("[0-9a-fA-F]{32}") || !username.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IOException("Minecraft returned an invalid profile identity.");
        }
        return new Session(username, uuid, token, "mojang");
    }

    public static Throwable rootCause(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    public static boolean canRefresh(String input, Throwable error) {
        Throwable cause = rootCause(error);
        return !isAccessToken(normalize(input)) && cause instanceof ProfileException
                && ((ProfileException) cause).getStatus() == 401;
    }

    public static String errorMessage(Throwable error) {
        Throwable cause = rootCause(error);
        if (cause instanceof ProfileException || cause instanceof IllegalArgumentException) return cause.getMessage();
        if (cause instanceof SocketTimeoutException) return "Login timed out. Check your network or proxy and retry.";
        if (cause instanceof SSLException) return "TLS verification failed. Update Java and check the system clock or HTTPS proxy.";
        if (cause instanceof IOException) return "Unable to reach Minecraft authentication. Check your network or proxy and retry.";
        return "Unable to apply the login session. Please retry or sign in with Microsoft.";
    }

    public static final class ProfileException extends IOException {
        private final int status;

        private ProfileException(int status) {
            super(message(status));
            this.status = status;
        }

        public int getStatus() { return status; }

        private static String message(int status) {
            switch (status) {
                case 401: return "Minecraft token is invalid or expired. Paste a new access token or sign in with Microsoft.";
                case 403: return "Minecraft rejected profile access (HTTP 403). Check account permissions and your network.";
                case 404: return "No Minecraft Java profile was found for this token.";
                case 429: return "Minecraft is rate limiting logins. Wait before retrying.";
                default: return "Minecraft profile request failed (HTTP " + status + "). Please retry later.";
            }
        }
    }
}
