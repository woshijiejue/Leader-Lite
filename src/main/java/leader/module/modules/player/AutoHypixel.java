package leader.module.modules.player;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.PacketEvent;
import leader.module.Module;
import leader.module.modules.render.notification.NoticeMode;
import leader.module.modules.render.notification.Notification;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.util.ChatUtil;
import leader.util.TimerUtil;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;

import java.util.Iterator;

import static leader.config.Config.mc;
/**
 * @Description: Kiss my ass
 * @Author: Eremin12
 * @Date: 2026/9/9 09:49
 */
public class AutoHypixel extends Module {
    private final FloatProperty autoPlayDelay = new FloatProperty("Delay", 3.0f, 0.0f, 5.0f);

    public AutoHypixel() {
        super("AutoHypixel", false);
    }
    public String knownMode, knownType;
    public TimerUtil delay = new TimerUtil();
    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() == EventType.SEND) {
            return;
        }

        if (event.getPacket() instanceof S3EPacketTeams) {
            S3EPacketTeams packet = (S3EPacketTeams) event.getPacket();
            String message = StringUtils.stripControlCodes(packet.getSuffix());
            if (message.equals("Mode: Normal")) {
                knownMode = "normal";
            }
            if (message.equals("Mode: Insane")) {
                knownMode = "insane";
            }
            if (message.equals("Mode: Mega")) {
                knownType = "mega";
                knownMode = "normal";
            }
            if (message.equals("Teams left")) {
                knownType = "teams";
            }
        }

        if (event.getPacket() instanceof S45PacketTitle) {
            S45PacketTitle packet = (S45PacketTitle) event.getPacket();
            if (packet.getMessage() == null) {
                return;
            }

            String message = packet.getMessage().getUnformattedText();
            if (message.equals("YOU DIED!") || message.equals("GAME END") || message.equals("VICTORY!") || message.equals("You are now a spectator!")) {
                if (knownType != null && knownMode != null) {
                    if (delay.hasTimeElapsed(2000, true)) {
                        mc.thePlayer.sendChatMessage("/play " + knownType + "_" + knownMode);
                        Notification.addNotification("AutoPlay Running...", NoticeMode.Info);
                    }
                }
            }
        }
        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.getChatComponent().getFormattedText().contains("play again?")) {
            Iterator<IChatComponent> iterator = packet.getChatComponent().getSiblings().iterator();
            while (iterator.hasNext()) {
                for (String command : iterator.next().toString().split("'")) {
                    if (command.startsWith("/play") && !command.contains(".")) {
                        join(command);
                        break;
                    }
                }
            }
        }
    }

    private void join(String command) {
        float delay = autoPlayDelay.getValue();
        if (delay == 0) {
            if (mc.thePlayer != null) {
                ChatUtil.sendMessage(command);
            }
            Notification.addNotification("AutoPlay", "Running...", NoticeMode.Info);
            return;
        }

        new Thread(() -> {
            Notification.addNotification("AutoPlay", "Play again in " + delay + " seconds.", NoticeMode.Info);
            try {
                Thread.sleep((long) (delay * 1000L));
            } catch (InterruptedException ignored) {
            }
            if (mc.thePlayer != null) {
                ChatUtil.sendMessage(command);
            }
        }, "Leader-AutoHypixel").start();
    }
}
