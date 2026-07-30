package leader.client.command.commands;

import leader.client.Leader;
import leader.client.command.Command;
import leader.client.config.impl.ModuleConfig;
import leader.client.enums.ChatColors;
import leader.client.util.ChatUtil;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.ClickEvent.Action;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import org.apache.commons.io.FilenameUtils;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class ConfigCommand extends Command {

    public ConfigCommand() {
        super(new ArrayList<>(Arrays.asList("config", "cfg", "c")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            String command = args.get(0).toLowerCase(Locale.ROOT);
            ChatUtil.sendFormatted(
                    String.format("%sUsage: .%s &oload&r/&osave&r <&oname&r> | .%s &olist&r | .%s &ofolder&r", Leader.clientName, command, command, command)
            );
            return;
        }

        String subCommand = args.get(1);
        if (subCommand.equalsIgnoreCase("l")) {
            subCommand = args.size() < 3 ? "list" : "load";
        }
        String sub = subCommand.toLowerCase(Locale.ROOT);

        File mainDir = Leader.getMainDir();

        switch (sub) {
            case "load":
            case "reload": {
                if (args.size() < 3) {
                    ChatUtil.sendFormatted(
                            String.format("%sMissing config name (use '&odefault&r' to load default config)&r", Leader.clientName)
                    );
                    return;
                }
                String configName = args.get(2);
                ModuleConfig config = new ModuleConfig(configName);
                Leader.configManager.loadConfig(config);
                ChatUtil.sendFormatted(String.format("%sLoaded config: &a%s&r", Leader.clientName, configName));
                return;
            }

            case "s":
            case "save": {
                if (args.size() < 3) {
                    ChatUtil.sendFormatted(
                            String.format("%sMissing config name to save.&r", Leader.clientName)
                    );
                    return;
                }
                String configName = args.get(2);
                ModuleConfig config = new ModuleConfig(configName);
                Leader.configManager.saveConfig(config);
                ChatUtil.sendFormatted(String.format("%sSaved config: &a%s&r", Leader.clientName, configName));
                return;
            }

            case "list": {
                try {
                    File[] configs = mainDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                    if (configs == null || configs.length == 0) {
                        ChatUtil.sendFormatted(String.format("%sNo configs found in &o%s&r", Leader.clientName, mainDir.getPath()));
                        return;
                    }

                    Arrays.sort(configs, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

                    ChatUtil.sendFormatted(String.format("%sConfigs:&r", Leader.clientName));
                    for (File file : configs) {
                        String nameWithoutExt = FilenameUtils.removeExtension(file.getName());
                        String formatted = ChatColors.formatColor(String.format("&7»&r &o%s&r", nameWithoutExt));
                        String clickCommand = String.format(".config load %s", nameWithoutExt);

                        ChatUtil.send(
                                new ChatComponentText(formatted)
                                        .setChatStyle(
                                                new ChatStyle()
                                                        .setChatClickEvent(new ClickEvent(Action.RUN_COMMAND, clickCommand))
                                                        .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("Click to load " + nameWithoutExt)))
                                        )
                        );
                    }
                } catch (Exception e) {
                    ChatUtil.sendFormatted(String.format("%sFailed to read configs (&o%s&r)&r", Leader.clientName, mainDir.getPath()));
                }
                return;
            }

            case "f":
            case "folder":
            case "dir":
            case "directory": {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(mainDir);
                        ChatUtil.sendFormatted(String.format("%sOpened config folder.&r", Leader.clientName));
                    } else {
                        ChatUtil.sendFormatted(String.format("%sDesktop operation is not supported on this OS.&r", Leader.clientName));
                    }
                } catch (Exception e) {
                    ChatUtil.sendFormatted(String.format("%sFailed to open (&o%s&r)&r", Leader.clientName, mainDir.getPath()));
                }
                return;
            }

            default:
                ChatUtil.sendFormatted(String.format("%sInvalid argument (&o%s&r)&r", Leader.clientName, args.get(1)));
        }
    }
}