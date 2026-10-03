package leader.util;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KeyBindUtil {
    public static final int NONE = 0;
    public static final int CHAR_OFFSET = 256;

    private static final Map<Integer, String> DISPLAY_NAMES = new HashMap<>();
    private static final Map<String, Integer> ALIASES = new HashMap<>();
    private static final Set<Integer> extendedDown = new HashSet<>();

    static {
        display(Keyboard.KEY_LCONTROL, "LCTRL");
        display(Keyboard.KEY_RCONTROL, "RCTRL");
        display(Keyboard.KEY_LMENU, "LALT");
        display(Keyboard.KEY_RMENU, "RALT");
        display(Keyboard.KEY_LMETA, "LWIN");
        display(Keyboard.KEY_RMETA, "RWIN");
        display(Keyboard.KEY_RETURN, "ENTER");
        display(Keyboard.KEY_NUMPADENTER, "NUMENTER");
        display(Keyboard.KEY_BACK, "BACKSPACE");
        display(Keyboard.KEY_DELETE, "DELETE");
        display(Keyboard.KEY_INSERT, "INSERT");
        display(Keyboard.KEY_PRIOR, "PGUP");
        display(Keyboard.KEY_NEXT, "PGDN");
        display(Keyboard.KEY_CAPITAL, "CAPSLOCK");
        display(Keyboard.KEY_SCROLL, "SCROLLLOCK");
        display(Keyboard.KEY_SYSRQ, "PRINTSCREEN");
        display(Keyboard.KEY_ESCAPE, "ESC");
        display(Keyboard.KEY_GRAVE, "`");
        display(Keyboard.KEY_MINUS, "-");
        display(Keyboard.KEY_EQUALS, "=");
        display(Keyboard.KEY_LBRACKET, "[");
        display(Keyboard.KEY_RBRACKET, "]");
        display(Keyboard.KEY_SEMICOLON, ";");
        display(Keyboard.KEY_APOSTROPHE, "'");
        display(Keyboard.KEY_BACKSLASH, "\\");
        display(Keyboard.KEY_COMMA, ",");
        display(Keyboard.KEY_PERIOD, ".");
        display(Keyboard.KEY_SLASH, "/");
        display(Keyboard.KEY_ADD, "NUM+");
        display(Keyboard.KEY_SUBTRACT, "NUM-");
        display(Keyboard.KEY_MULTIPLY, "NUM*");
        display(Keyboard.KEY_DIVIDE, "NUM/");
        display(Keyboard.KEY_DECIMAL, "NUM.");
        for (int i = 0; i <= 9; i++) {
            display(Keyboard.getKeyIndex("NUMPAD" + i), "NUM" + i);
        }

        alias(Keyboard.KEY_LCONTROL, "CTRL", "CONTROL", "LCTRL", "LEFTCTRL");
        alias(Keyboard.KEY_RCONTROL, "RCTRL", "RIGHTCTRL");
        alias(Keyboard.KEY_LMENU, "ALT", "LALT", "LEFTALT");
        alias(Keyboard.KEY_RMENU, "RALT", "RIGHTALT", "ALTGR");
        alias(Keyboard.KEY_LSHIFT, "SHIFT", "LEFTSHIFT");
        alias(Keyboard.KEY_RSHIFT, "RIGHTSHIFT");
        alias(Keyboard.KEY_LMETA, "WIN", "LWIN", "META", "SUPER", "CMD");
        alias(Keyboard.KEY_RMETA, "RWIN");
        alias(Keyboard.KEY_RETURN, "ENTER");
        alias(Keyboard.KEY_NUMPADENTER, "NUMENTER", "KPENTER");
        alias(Keyboard.KEY_BACK, "BACKSPACE", "BKSP", "BS");
        alias(Keyboard.KEY_DELETE, "DEL");
        alias(Keyboard.KEY_INSERT, "INS");
        alias(Keyboard.KEY_PRIOR, "PGUP", "PAGEUP");
        alias(Keyboard.KEY_NEXT, "PGDN", "PAGEDOWN");
        alias(Keyboard.KEY_CAPITAL, "CAPS", "CAPSLOCK");
        alias(Keyboard.KEY_SCROLL, "SCROLLLOCK");
        alias(Keyboard.KEY_SYSRQ, "PRINTSCREEN", "PRTSC");
        alias(Keyboard.KEY_ESCAPE, "ESC");
        alias(Keyboard.KEY_SPACE, "SPACEBAR");
        alias(Keyboard.KEY_GRAVE, "`", "~", "TILDE", "BACKTICK");
        alias(Keyboard.KEY_MINUS, "-", "_");
        alias(Keyboard.KEY_EQUALS, "=", "+");
        alias(Keyboard.KEY_LBRACKET, "[", "{");
        alias(Keyboard.KEY_RBRACKET, "]", "}");
        alias(Keyboard.KEY_SEMICOLON, ";", ":");
        alias(Keyboard.KEY_APOSTROPHE, "'", "\"", "QUOTE");
        alias(Keyboard.KEY_BACKSLASH, "\\", "|");
        alias(Keyboard.KEY_COMMA, ",", "<");
        alias(Keyboard.KEY_PERIOD, ".", ">");
        alias(Keyboard.KEY_SLASH, "/", "?");
        alias(Keyboard.KEY_ADD, "NUM+", "NUMPLUS");
        alias(Keyboard.KEY_SUBTRACT, "NUM-", "NUMMINUS");
        alias(Keyboard.KEY_MULTIPLY, "NUM*");
        alias(Keyboard.KEY_DIVIDE, "NUM/");
        alias(Keyboard.KEY_DECIMAL, "NUM.");
        for (int i = 0; i <= 9; i++) {
            alias(Keyboard.getKeyIndex("NUMPAD" + i), "NUM" + i, "KP" + i);
        }
    }

    private static void display(int key, String name) {
        DISPLAY_NAMES.put(key, name);
    }

    private static void alias(int key, String... names) {
        for (String name : names) ALIASES.put(name, key);
    }

    public static String getKeyName(int keyCode) {
        if (keyCode == NONE) {
            return "NONE";
        }
        if (keyCode < 0) {
            int mouseButton = keyCode + 100;
            switch (mouseButton) {
                case 0:
                    return "LMB";
                case 1:
                    return "RMB";
                case 2:
                    return "MMB";
                default:
                    return "MOUSE" + mouseButton;
            }
        }
        if (keyCode >= CHAR_OFFSET) {
            return String.valueOf((char) (keyCode - CHAR_OFFSET)).toUpperCase(Locale.ROOT);
        }
        String name = DISPLAY_NAMES.get(keyCode);
        if (name != null) return name;
        name = Keyboard.getKeyName(keyCode);
        return name != null ? name : "KEY" + keyCode;
    }

    public static int getKeyIndex(String input) {
        if (input == null) return NONE;
        String name = input.trim();
        if (name.isEmpty()) return NONE;
        String upper = name.toUpperCase(Locale.ROOT);
        Integer alias = ALIASES.get(upper);
        if (alias != null) return alias;
        int index = Keyboard.getKeyIndex(upper);
        if (index != Keyboard.KEY_NONE) return index;
        if (upper.startsWith("KEY") && upper.length() > 3) {
            try {
                int code = Integer.parseInt(upper.substring(3));
                if (code > 0 && code < CHAR_OFFSET) return code;
            } catch (NumberFormatException ignored) {
            }
        }
        return NONE;
    }

    public static int fromTyped(char typedChar, int keyCode) {
        if (keyCode != Keyboard.KEY_NONE) return keyCode;
        return typedChar == 0 ? NONE : typedChar + CHAR_OFFSET;
    }

    public static void onKeyState(int keyCode, boolean pressed) {
        if (keyCode < CHAR_OFFSET) return;
        if (pressed) extendedDown.add(keyCode);
        else extendedDown.remove(keyCode);
    }

    public static boolean isKeyDown(int keyCode) {
        if (keyCode == NONE) return false;
        if (keyCode < 0) return Mouse.isButtonDown(keyCode + 100);
        if (keyCode >= CHAR_OFFSET) return extendedDown.contains(keyCode);
        return Keyboard.isKeyDown(keyCode);
    }

    public static void updateKeyState(int keyCode) {
        KeyBindUtil.setKeyBindState(keyCode, isKeyDown(keyCode));
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        KeyBinding.setKeyBindState(keyCode, pressed);
    }

    public static void pressKeyOnce(int keyCode) {
        KeyBinding.onTick(keyCode);
    }
}
