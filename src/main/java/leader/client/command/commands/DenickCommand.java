package leader.client.command.commands;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import leader.client.Leader;
import leader.client.command.Command;
import leader.client.util.misc.ChatColors;
import leader.client.util.DebugUtil;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;

public class DenickCommand extends Command {

    public DenickCommand() {
        super(new ArrayList<>(Collections.singletonList("denick")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            DebugUtil.sendFormatted(String.format("%sUsage: .%s <&oname&r>&r", Leader.clientName, args.get(0).toLowerCase(Locale.ROOT)));
        } else {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(ChatColors.formatColor(args.get(1)));
            if (playerInfo != null) {
                GameProfile gameProfile = playerInfo.getGameProfile();
                Property property = Iterables.getFirst(gameProfile.getProperties().get("textures"), null);
                if (property != null) {
                    String code = new String(Base64.getDecoder().decode(property.getValue().getBytes(StandardCharsets.UTF_8)));
                    String name = code.contains("profileName\" : \"") ? code.split("profileName\" : \"")[1].split("\"")[0] : "?";
                    String uuid = code.contains("profileId\" : \"") ? code.split("profileId\" : \"")[1].split("\"")[0] : "?";
                    DebugUtil.sendRaw(
                            String.format(
                                    ChatColors.formatColor("%s%s&r -> %s (&o%s&r)&r"),
                                    ChatColors.formatColor(Leader.clientName),
                                    gameProfile.getName().replace("§", "&"),
                                    name,
                                    uuid
                            )
                    );
                    if (!uuid.isEmpty() && !uuid.equals("?")) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(uuid), null);
                    }
                } else {
                    DebugUtil.sendRaw(
                            String.format(
                                    ChatColors.formatColor("%sNo textures for entity with name &o%s&r"),
                                    ChatColors.formatColor(Leader.clientName),
                                    args.get(1)
                            )
                    );
                }
            } else {
                DebugUtil.sendRaw(
                        String.format(
                                ChatColors.formatColor("%sNo entity with name &o%s&r"),
                                ChatColors.formatColor(Leader.clientName),
                                args.get(1)
                        )
                );
            }
        }
    }
}
