package leader.client.module.modules.render;

import leader.client.Leader;
import leader.client.util.misc.ChatColors;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.LoadWorldEvent;
import leader.client.events.PacketEvent;
import leader.client.events.Render2DEvent;
import leader.client.events.TickEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.StringValue;
import leader.client.util.DebugUtil;
import leader.client.util.render.ColorUtil;
import leader.client.util.render.SoundUtil;
import leader.client.util.player.TeamUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BedTracker extends Module {

    private final ScheduledExecutorService executor;
    private final LinkedHashMap<String, Long> alertCooldowns;
    private final LinkedHashSet<EntityEnderPearl> trackedPearls;
    private final LinkedHashSet<String> whitelistedPlayers;
    private final Color wBed;
    private final Color rBed;
    private final Color yBed;
    private final Color gBed;
    private BlockPos bedPos;
    private long lastMarcoTime;
    private boolean waiting;
    public final BoolValue alerts;
    public final SliderValue alertRange;
    public final BoolValue alertOnPearl;
    public final ListValue alertSound;
    public final SliderValue alertFrequency;
    public final BoolValue marco;
    public final SliderValue marcoRange;
    public final BoolValue marcoOnPreal;
    public final StringValue marcoText;
    public final SliderValue marcoDelay;
    public final BoolValue hud;
    public final ListValue hudPosX;
    public final ListValue hudPosY;
    public final SliderValue hudOffX;
    public final SliderValue hudOffY;
    public final SliderValue hudScale;
    public final BoolValue hudShadow;

    private void playAlertSound() {
        if (this.alertSound.is("MEOW")) {
            SoundUtil.playSound("mob.cat.meow");
        } else if (this.alertSound.is("ANVIL")) {
            SoundUtil.playSound("random.anvil_land");
        }
    }

    private Color getHudColor(int distance) {
        if (distance < 0) {
            return this.wBed;
        } else if (distance <= 100) {
            return this.gBed;
        } else if (distance <= 114) {
            return ColorUtil.interpolate((float) (114 - distance) / 14.0F, this.yBed, this.gBed);
        } else {
            return distance <= 128 ? ColorUtil.interpolate((float) (128 - distance) / 14.0F, this.rBed, this.yBed) : this.rBed;
        }
    }

    private boolean isBed(BlockPos blockPos) {
        return blockPos != null && mc.theWorld.getBlockState(blockPos).getBlock() == Blocks.bed;
    }

    public BedTracker() {
        super("BedTracker", false, true);
        this.executor = Executors.newScheduledThreadPool(1);
        this.alertCooldowns = new LinkedHashMap<>();
        this.trackedPearls = new LinkedHashSet<>();
        this.whitelistedPlayers = new LinkedHashSet<>();
        this.wBed = new Color(ChatColors.WHITE.toAwtColor());
        this.rBed = new Color(ChatColors.RED.toAwtColor());
        this.yBed = new Color(ChatColors.YELLOW.toAwtColor());
        this.gBed = new Color(ChatColors.GREEN.toAwtColor());
        this.bedPos = null;
        this.lastMarcoTime = -1L;
        this.waiting = false;
        this.alerts = new BoolValue("alerts", true, this);
        this.alertRange = new SliderValue("alerts-range", 48, 8, 128, () -> this.alerts.getValue(), Representation.INT, this);
        this.alertOnPearl = new BoolValue("alerts-on-pearl", true, this);
        this.alertSound = new ListValue("alerts-sound", new String[]{"NONE", "MEOW", "ANVIL"}, "MEOW", () -> this.alerts.getValue() || this.alertOnPearl.getValue(), this);
        this.alertFrequency = new SliderValue("alerts-frequency", 5, 1, 30, () -> this.alerts.getValue() || this.alertOnPearl.getValue(), Representation.INT, this);
        this.marco = new BoolValue("macro", false, this);
        this.marcoRange = new SliderValue("macro-range", 24, 8, 128, () -> this.marco.getValue(), Representation.INT, this);
        this.marcoOnPreal = new BoolValue("macro-on-pearl", false, this);
        this.marcoText = new StringValue("macro-text", "/lobby", () -> this.marco.getValue() || this.marcoOnPreal.getValue(), this);
        this.marcoDelay = new SliderValue("macro-delay", 1, 1, 10, () -> this.marco.getValue() || this.marcoOnPreal.getValue(), Representation.INT, this);
        this.hud = new BoolValue("hud", true, this);
        this.hudPosX = new ListValue("hud-position-x", new String[]{"LEFT", "MIDDLE", "RIGHT"}, "LEFT", () -> this.hud.getValue(), this);
        this.hudPosY = new ListValue("hud-position-y", new String[]{"TOP", "MIDDLE", "BOTTOM"}, "TOP", () -> this.hud.getValue(), this);
        this.hudOffX = new SliderValue("hud-offset-x", 2, 0, 255, () -> this.hud.getValue(), Representation.INT, this);
        this.hudOffY = new SliderValue("hud-offset-y", 2, 0, 255, () -> this.hud.getValue(), Representation.INT, this);
        this.hudScale = new SliderValue("hud-scale", 1.0, 0.5, 1.5, () -> this.hud.getValue(), Representation.FLOAT, this);
        this.hudShadow = new BoolValue("hud-shadow", true, () -> this.hud.getValue(), this);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST && this.isBed(this.bedPos)) {
            long millis = System.currentTimeMillis();
            boolean pearl = false;
            boolean marco = false;
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity instanceof EntityEnderPearl) {
                    EntityEnderPearl enderPearl = (EntityEnderPearl) entity;
                    if (!this.trackedPearls.contains(enderPearl)) {
                        this.trackedPearls.add(enderPearl);
                        if (this.alertOnPearl.getValue()) {
                            DebugUtil.sendFormatted(String.format("%s%s: &fDetected &5Ender Pearl&r &e&l⚠&r", Leader.clientName, this.getName()));
                            pearl = true;
                        }
                        if (this.marcoOnPreal.getValue() && this.lastMarcoTime + (long) this.marcoDelay.getValue().intValue() * 1000L <= millis) {
                            this.lastMarcoTime = millis;
                            marco = true;
                        }
                    }
                }
            }
            for (EntityPlayer player : mc.theWorld
                    .loadedEntityList
                    .stream()
                    .filter(entity -> entity instanceof EntityPlayer)
                    .map(entity -> (EntityPlayer) entity)
                    .filter(entityPlayer -> !TeamUtil.isBot(entityPlayer) && !this.whitelistedPlayers.contains(entityPlayer.getName()))
                    .collect(Collectors.toList())) {
                if (TeamUtil.isSameTeam(player)) {
                    this.whitelistedPlayers.add(player.getName());
                } else {
                    double distance = player.getDistance((double) this.bedPos.getX() + 0.5, (double) this.bedPos.getY() + 0.5, (double) this.bedPos.getZ() + 0.5);
                    String name = player.getName();
                    String text = player.getDisplayName().getFormattedText();
                    ItemStack item = player.getHeldItem();
                    boolean isPearl = item != null && item.getItem() instanceof ItemEnderPearl;
                    if (this.alerts.getValue() && distance < (double) this.alertRange.getValue().intValue()) {
                        Long cooldown = this.alertCooldowns.get(name);
                        if (cooldown == null || cooldown + (long) this.alertFrequency.getValue().intValue() * 1000L <= millis) {
                            this.alertCooldowns.put(name, millis);
                            DebugUtil.sendFormatted(
                                    String.format("%s%s: %s&r &fis %d blocks away from your bed &e&l⚠&r", Leader.clientName, this.getName(), text, (int) distance + 1)
                            );
                            pearl = true;
                        }
                    }
                    if (this.alertOnPearl.getValue() && isPearl) {
                        Long cooldown = this.alertCooldowns.get(name);
                        if (cooldown == null || cooldown + (long) this.alertFrequency.getValue().intValue() * 1000L <= millis) {
                            this.alertCooldowns.put(name, millis);
                            DebugUtil.sendFormatted(
                                    String.format("%s%s: %s&r &fhas &5Ender Pearl&r &e&l⚠&r", Leader.clientName, this.getName(), text)
                            );
                            pearl = true;
                        }
                    }
                    if ((
                            this.marco.getValue() && distance < (double) this.marcoRange.getValue().intValue()
                                    || this.marcoOnPreal.getValue() && isPearl
                    )
                            && this.lastMarcoTime + (long) this.marcoDelay.getValue().intValue() * 1000L <= millis) {
                        this.lastMarcoTime = millis;
                        marco = true;
                    }
                }
            }
            if (pearl) {
                this.playAlertSound();
            }
            if (marco) {
                DebugUtil.sendRaw(
                        String.format(
                                ChatColors.formatColor("%s%s: &fRunning &6%s&r"),
                                ChatColors.formatColor(Leader.clientName),
                                this.getName(),
                                this.marcoText.getValue()
                        )
                );
                DebugUtil.sendMessage(this.marcoText.getValue());
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && this.hud.getValue()) {
            if (mc.theWorld != null && mc.thePlayer != null && !mc.gameSettings.showDebugInfo) {
                GuiScreen currentScreen = mc.currentScreen;
                if (currentScreen == null || currentScreen instanceof GuiChat) {
                    int distanceSq = 0;
                    boolean hasBed = this.isBed(this.bedPos);
                    if (hasBed) {
                        double xDiff = mc.thePlayer.posX - (double) this.bedPos.getX();
                        double zDiff = mc.thePlayer.posZ - (double) this.bedPos.getZ();
                        distanceSq = (int) Math.sqrt(xDiff * xDiff + zDiff * zDiff) + 1;
                    }
                    String text = ChatColors.formatColor(
                            String.format(
                                    "&fBed: %s%s",
                                    !hasBed ? "&cfalse&r" : "&atrue&r",
                                    !hasBed ? "" : String.format(" &7| &fDistance: &r%d%s", distanceSq, distanceSq >= 128 ? " &c&l⚠&r" : "")
                            )
                    );
                    ScaledResolution scaledResolution = new ScaledResolution(mc);
                    float width = (float) FontManager.getStringWidth(text);
                    float height = (float) FontManager.getFontHeight() - 1.0F;
                    float scale = (float) this.hudOffX.getValue().intValue() / this.hudScale.getValue();
                    if (this.hudPosX.is("LEFT")) {
                        scale++;
                    } else if (this.hudPosX.is("MIDDLE")) {
                        scale += (float) scaledResolution.getScaledWidth() / this.hudScale.getValue() / 2.0F - width / 2.0F;
                    } else if (this.hudPosX.is("RIGHT")) {
                        scale = (scale + 1.0F) * -1.0F;
                        scale += (float) scaledResolution.getScaledWidth() / this.hudScale.getValue() - width;
                    }
                    float offset = (float) this.hudOffY.getValue().intValue() / this.hudScale.getValue();
                    if (this.hudPosY.is("TOP")) {
                        offset++;
                    } else if (this.hudPosY.is("MIDDLE")) {
                        offset += (float) scaledResolution.getScaledHeight() / this.hudScale.getValue() / 2.0F - height / 2.0F;
                    } else if (this.hudPosY.is("BOTTOM")) {
                        offset = (offset + 1.0F) * -1.0F;
                        offset += (float) scaledResolution.getScaledHeight() / this.hudScale.getValue() - height;
                    }
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(this.hudScale.getValue(), this.hudScale.getValue(), 1.0F);
                    GlStateManager.translate(scale, offset, 0.0F);
                    GlStateManager.disableDepth();
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    FontManager.drawString(text, 0.0F, 0.0F, this.getHudColor(distanceSq).getRGB(), this.hudShadow.getValue());
                    GlStateManager.disableBlend();
                    GlStateManager.enableDepth();
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.waiting = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            if (event.getPacket() instanceof S02PacketChat) {
                String msg = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
                if (msg.contains("§e§lProtect your bed and destroy the enemy bed") || msg.contains("§e§lDestroy the enemy bed and then eliminate them")) {
                    this.alertCooldowns.clear();
                    this.trackedPearls.clear();
                    this.whitelistedPlayers.clear();
                    this.bedPos = null;
                    this.waiting = true;
                }
            }
            if (event.getPacket() instanceof S08PacketPlayerPosLook && this.waiting) {
                this.waiting = false;
                this.executor
                        .schedule(
                                () -> {
                                    int x = MathHelper.floor_double(mc.thePlayer.posX);
                                    int y = MathHelper.floor_double(mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
                                    int z = MathHelper.floor_double(mc.thePlayer.posZ);
                                    for (int i = x - 25; i <= x + 25; i++) {
                                        for (int j = y - 25; j <= y + 25; j++) {
                                            for (int k = z - 25; k <= z + 25; k++) {
                                                BlockPos blockPos = new BlockPos(i, j, k);
                                                if (this.isBed(blockPos)) {
                                                    this.bedPos = blockPos;
                                                    DebugUtil.sendFormatted(
                                                            String.format(
                                                                    "%s%s: &fWhitelisted your bed at (%d, %d, %d) &a&l✔&r",
                                                                    Leader.clientName,
                                                                    this.getName(),
                                                                    this.bedPos.getX(),
                                                                    this.bedPos.getY(),
                                                                    this.bedPos.getZ()
                                                            )
                                                    );
                                                    SoundUtil.playSound("note.pling");
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                },
                                3000L,
                                TimeUnit.MILLISECONDS
                        );
            }
        }
    }

    @Override
    public void onDisabled() {
        this.alertCooldowns.clear();
        this.trackedPearls.clear();
        this.whitelistedPlayers.clear();
        this.bedPos = null;
    }
}
