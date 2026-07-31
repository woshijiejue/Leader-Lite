package leader.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leader.client.command.CommandManager;
import leader.client.command.commands.*;
import leader.client.component.impl.*;
import leader.client.component.impl.floater.FloatComponent;
import leader.client.component.impl.network.blink.BlinkComponent;
import leader.client.component.impl.network.delay.DelayComponent;
import leader.client.component.impl.network.lag.LagComponent;
import leader.client.component.impl.rotaion.RotationManager;
import leader.client.config.ConfigManager;
import leader.client.event.EventManager;
import leader.client.module.Module;
import leader.client.module.ModuleManager;
import leader.client.module.modules.combat.*;
import leader.client.module.modules.legit.*;
import leader.client.module.modules.misc.*;
import leader.client.module.modules.movement.*;
import leader.client.module.modules.player.*;
import leader.client.module.modules.render.*;
import leader.client.util.InstanceAccess;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

@Getter
public class Leader implements InstanceAccess {
    public static final Logger LOGGER = LogManager.getLogger(Leader.class);

    public static String folderName = "Leader";
    public static String clientName = "&7[&bLea&3der &9Li&1te&7]&r ";
    public static String version;

    public static File mainDir = new File(mc.mcDataDir, folderName);

    private Path dataFolder;

    public static RotationManager rotationManager;
    public static FloatComponent floatComponent;
    public static BlinkComponent blinkComponent;
    public static DelayComponent delayComponent;
    public static LagComponent lagComponent;
    public static PlayerStateComponent playerStateComponent;
    public static FriendComponent friendComponent;
    public static TargetComponent targetComponent;
    public static ModuleManager moduleManager;
    public static CommandManager commandManager;
    public static ConfigManager configManager;

    public Leader() {
        this.init();
    }

    public void init() {
        setupMainDirectory();

        rotationManager = new RotationManager();
        floatComponent = new FloatComponent();
        blinkComponent = new BlinkComponent();
        delayComponent = new DelayComponent();
        lagComponent = new LagComponent();
        playerStateComponent = new PlayerStateComponent();
        friendComponent = new FriendComponent();
        targetComponent = new TargetComponent();
        moduleManager = new ModuleManager();
        commandManager = new CommandManager();

        EventManager.register(rotationManager);
        EventManager.register(floatComponent);
        EventManager.register(blinkComponent);
        EventManager.register(delayComponent);
        EventManager.register(lagComponent);
        EventManager.register(moduleManager);
        EventManager.register(commandManager);

        moduleManager.modules.put(Animations.class, new Animations());
        moduleManager.modules.put(AimAssist.class, new AimAssist());
        moduleManager.modules.put(AntiAFK.class, new AntiAFK());
        moduleManager.modules.put(AntiDebuff.class, new AntiDebuff());
        moduleManager.modules.put(AntiFireball.class, new AntiFireball());
        moduleManager.modules.put(AntiObbyTrap.class, new AntiObbyTrap());
        moduleManager.modules.put(AntiObfuscate.class, new AntiObfuscate());
        moduleManager.modules.put(AntiVoid.class, new AntiVoid());
        moduleManager.modules.put(AutoClicker.class, new AutoClicker());
        moduleManager.modules.put(AutoHeal.class, new AutoHeal());
        moduleManager.modules.put(AutoTool.class, new AutoTool());
        moduleManager.modules.put(BetterFPS.class, new BetterFPS());
        moduleManager.modules.put(BlockHit.class, new BlockHit());
        moduleManager.modules.put(BackTrack.class, new BackTrack());
        moduleManager.modules.put(BedESP.class, new BedESP());
        moduleManager.modules.put(BedTracker.class, new BedTracker());
        moduleManager.modules.put(Blink.class, new Blink());
        moduleManager.modules.put(BlinkSettings.class, new BlinkSettings());
        moduleManager.modules.put(Chams.class, new Chams());
        moduleManager.modules.put(ChestESP.class, new ChestESP());
        moduleManager.modules.put(ChestAura.class, new ChestAura());
        moduleManager.modules.put(ChestStealer.class, new ChestStealer());
        moduleManager.modules.put(Disabler.class, new Disabler());
        moduleManager.modules.put(Eagle.class, new Eagle());
        moduleManager.modules.put(ESP.class, new ESP());
        moduleManager.modules.put(FastPlace.class, new FastPlace());
        moduleManager.modules.put(Stuck.class, new Stuck());
        moduleManager.modules.put(Fly.class, new Fly());
        moduleManager.modules.put(FontManager.class, new FontManager());
        moduleManager.modules.put(FullBright.class, new FullBright());
        moduleManager.modules.put(GhostHand.class, new GhostHand());
        moduleManager.modules.put(GifDisplay.class, new GifDisplay());
        moduleManager.modules.put(GuiModule.class, new GuiModule());
        moduleManager.modules.put(HUD.class, new HUD());
        moduleManager.modules.put(MoreKB.class, new MoreKB());
        moduleManager.modules.put(Indicators.class, new Indicators());
        moduleManager.modules.put(InventoryClicker.class, new InventoryClicker());
        moduleManager.modules.put(BedNuker.class, new BedNuker());
        moduleManager.modules.put(InvManager.class, new InvManager());
        moduleManager.modules.put(InvWalk.class, new InvWalk());
        moduleManager.modules.put(ItemESP.class, new ItemESP());
        moduleManager.modules.put(Jesus.class, new Jesus());
        moduleManager.modules.put(KeepSprint.class, new KeepSprint());
        moduleManager.modules.put(HitBox.class, new HitBox());
        moduleManager.modules.put(KillAura.class, new KillAura());
        moduleManager.modules.put(LagRange.class, new LagRange());
        moduleManager.modules.put(LightningTracker.class, new LightningTracker());
        moduleManager.modules.put(LongJump.class, new LongJump());
        moduleManager.modules.put(MCF.class, new MCF());
        moduleManager.modules.put(NameTags.class, new NameTags());
        moduleManager.modules.put(Notification.class, new Notification());
        moduleManager.modules.put(Watermark.class, new Watermark());
        moduleManager.modules.put(Potion.class, new Potion());
        moduleManager.modules.put(NickHider.class, new NickHider());
        moduleManager.modules.put(NoFall.class, new NoFall());
        moduleManager.modules.put(NoHitDelay.class, new NoHitDelay());
        moduleManager.modules.put(NoHurtCam.class, new NoHurtCam());
        moduleManager.modules.put(NoJumpDelay.class, new NoJumpDelay());
        moduleManager.modules.put(NoRotate.class, new NoRotate());
        moduleManager.modules.put(NoSlow.class, new NoSlow());
        moduleManager.modules.put(Radar.class, new Radar());
        moduleManager.modules.put(Reach.class, new Reach());
        moduleManager.modules.put(Refill.class, new Refill());
        moduleManager.modules.put(SafeWalk.class, new SafeWalk());
        moduleManager.modules.put(Scaffold.class, new Scaffold());
        moduleManager.modules.put(LegitTelly.class, new LegitTelly());
        moduleManager.modules.put(AutoBlockIn.class, new AutoBlockIn());
        moduleManager.modules.put(Spammer.class, new Spammer());
        moduleManager.modules.put(Speed.class, new Speed());
        moduleManager.modules.put(SpeedMine.class, new SpeedMine());
        moduleManager.modules.put(Sprint.class, new Sprint());
        moduleManager.modules.put(SmartAttack.class, new SmartAttack());
        moduleManager.modules.put(TargetHUD.class, new TargetHUD());
        moduleManager.modules.put(TargetESP.class, new TargetESP());
        moduleManager.modules.put(TargetStrafe.class, new TargetStrafe());
        moduleManager.modules.put(Tracers.class, new Tracers());
        moduleManager.modules.put(Trajectories.class, new Trajectories());
        moduleManager.modules.put(Velocity.class, new Velocity());
        moduleManager.modules.put(ViewClip.class, new ViewClip());
        moduleManager.modules.put(Wtap.class, new Wtap());
        moduleManager.modules.put(Xray.class, new Xray());

        commandManager.commands.add(new BindCommand());
        commandManager.commands.add(new ConfigCommand());
        commandManager.commands.add(new DenickCommand());
        commandManager.commands.add(new FriendCommand());
        commandManager.commands.add(new HelpCommand());
        commandManager.commands.add(new HideCommand());
        commandManager.commands.add(new IgnCommand());
        commandManager.commands.add(new ItemCommand());
        commandManager.commands.add(new ListCommand());
        commandManager.commands.add(new ModuleCommand());
        commandManager.commands.add(new PlayerCommand());
        commandManager.commands.add(new ShowCommand());
        commandManager.commands.add(new TargetCommand());
        commandManager.commands.add(new ToggleCommand());
        commandManager.commands.add(new VclipCommand());

        for (Module module : moduleManager.modules.values()) {
            EventManager.register(module);
        }

        configManager = new ConfigManager();
        dataFolder = Paths.get(mc.mcDataDir.getAbsolutePath()).resolve(folderName);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> configManager.saveConfigs()));

        try {
            InputStream stream = Leader.class.getResourceAsStream("/version.json");
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject modInfo = new JsonParser().parse(reader).getAsJsonObject();
                    version = modInfo.get("version").getAsString();
                }
            } else {
                version = "dev";
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load version.json, falling back to dev version", e);
            version = "dev";
        }
    }

    private void setupMainDirectory() {
        if (!mainDir.exists()) {
            boolean dirCreated = mainDir.mkdir();
            if (dirCreated) {
                LOGGER.info("Created main directory at {}", mainDir.getAbsolutePath());
            } else {
                LOGGER.warn("Failed to create main directory at {}", mainDir.getAbsolutePath());
            }
            Minecraft.getMinecraft().gameSettings.setSoundLevel(SoundCategory.MUSIC, 0);
        } else {
            LOGGER.info("Main directory already exists at {}", mainDir.getAbsolutePath());
        }

        this.dataFolder = Paths.get(Minecraft.getMinecraft().mcDataDir.getAbsolutePath()).resolve(folderName);
    }
}