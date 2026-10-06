package leader.module.modules.legit;

import com.google.common.base.CaseFormat;
import leader.Leader;
import leader.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import leader.enums.BlinkModules;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.AttackEvent;
import leader.events.TickEvent;
import leader.events.UpdateEvent;
import leader.events.PacketEvent;
import leader.events.LoadWorldEvent;
import leader.events.RightClickMouseEvent;
import leader.events.HitBlockEvent;
import leader.module.modules.legit.blockhit.AmunixBlockHit;
import leader.module.Module;
import leader.property.properties.*;
import leader.util.ItemUtil;
import leader.util.KeyBindUtil;
import leader.util.TimerUtil;

public class BlockHit extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    public BlockHit() {
        super("BlockHit",false, false);
    }
    public final ModeProperty mode = new ModeProperty("Mode",0,new String[]{"Helper","Auto","Lag","Predict"});
    public final BooleanProperty smartUnblock = new BooleanProperty("Smart Unblock", true, () -> mode.getValue() == 2);
    public final IntProperty lagDuration = new IntProperty("Lag Duration", 200, 50, 1000, () -> mode.getValue() == 2);
    public final IntProperty minLagDuration = new IntProperty("Minimum Lag Duration", 100, 0, 1000, () -> mode.getValue() == 2);
    public final PercentProperty lagChance = new PercentProperty("Lag Chance", 100, () -> mode.getValue() == 2);
    public final IntProperty lagHoldTime = new IntProperty("Lag Hold Ticks", 1, 1, 10, () -> mode.getValue() == 2);
    public final FloatProperty predictRange = new FloatProperty("Predict Range", 4.0F, 1.0F, 6.0F, () -> mode.getValue() == 3);
    public final BooleanProperty lobbyCheck = new BooleanProperty("Lobby Check", true, () -> mode.getValue() == 3);
    public final IntProperty maxHold = new IntProperty("Maximum Block ms", 200, 150, 500, () -> mode.getValue() == 3);
    public final IntProperty unblockTime = new IntProperty("Unblock ms", 200, 50, 350, () -> mode.getValue() == 3);
    public final IntProperty unblockDelay = new IntProperty("Unblock Delay ms", 0, 0, 100, () -> mode.getValue() == 3);
    public final FloatProperty firstMeleeRange = new FloatProperty("First Melee Range", 3.5F, 2.0F, 5.0F, () -> mode.getValue() == 3);
    private final AmunixBlockHit amunix = new AmunixBlockHit(this);

    private final IntProperty stopTime = new IntProperty("Stop Ticks",2,1,5, () -> this.mode.getValue() == 0);
    private final ModeProperty autoMode = new ModeProperty("Auto Mode",0,new String[]{"Spam","Hold"},() -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final ModeProperty autoBlockTime = new ModeProperty("AutoBlock Time",0, new String[]{"Delay","HurtTime","Sag","Smart"},() -> this.mode.getValue() == 1);
    private final BooleanProperty onFirstHit = new BooleanProperty("OnFirstHit",true, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 3);
    private final IntProperty smartBlockTick = new IntProperty("Smart Block Ticks",2,1,5, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 3);
    private final BooleanProperty releaseAfterHit = new BooleanProperty("Release After Hit",true, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 3);
    private final IntProperty smartBlockHurtTime = new IntProperty("Smart Block HurtTime",2,0,10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 3);
    private final IntProperty blockDelay = new IntProperty("Block Delay",100,0,1000, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final IntProperty holdTick = new IntProperty("Hold Ticks",2,2,5, () -> this.mode.getValue() == 1 && this.autoMode.getValue() == 1  && this.autoBlockTime.getValue() == 0);
    private final IntProperty minHurtTime = new IntProperty("Min HurtTime",10,1,10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 1);
    private final IntProperty maxHurtTime = new IntProperty("Max HurtTime",10,1,10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 1);
    private final PercentProperty chance = new PercentProperty("Block Hit Chance",50,()-> this.mode.getValue() == 1);
    private final BooleanProperty smart = new BooleanProperty("Smart",true,() -> this.mode.getValue() == 1);
    private final BooleanProperty autoBlockRange = new BooleanProperty("AutoBlock Range",true,() -> this.mode.getValue() == 1);
    private final FloatProperty range = new FloatProperty("Range",3.0f,1f,4f,() -> autoBlockRange.getValue() && mode.getValue() == 1);
    private int holdTicks,stopTick;

    private boolean startBlocking;
    private boolean attacking;
    private int attackTicks;
    private int sagTicks = 0;
    private boolean canBlock = false;
    private int getBlockTicks = 0;
    private EntityLivingBase target;
    private TimerUtil timer = new TimerUtil();
    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (mode.getValue() >= 2) {
            if (event.getType() == EventType.PRE) amunix.tick();
            return;
        }
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() == EventType.PRE) {
            if (this.mode.getValue() == 0) {
                if (mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (mc.thePlayer.isBlocking()) {
                        startBlocking = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    }
                }
                if (startBlocking) stopTick++;
                if (stopTick == 2) {
                    KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (stopTick > stopTime.getValue()) {
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    startBlocking = false;
                    stopTick = 0;
                }
            }
            if (this.mode.getValue() == 1) {
                if (target == null) return;
                if (attacking) {
                    attackTicks++;
                }
                if (attackTicks > 10) {
                    reset();
                    target = null;
                    return;
                }
                if (Math.random() > chance.getValue()){
                    reset();
                    return;
                }
                if (autoBlockRange.getValue() && RotationUtil.distanceToBox(target.getCollisionBoundingBox()) >= range.getValue()){
                    reset();
                    return;
                }
                if (smart.getValue() && target.hurtTime == 0){
                    reset();
                    return;
                }
                if (attacking && ItemUtil.isHoldingSword()) {
                    if (autoBlockTime.getValue() == 0) {
                        if (timer.hasTimeElapsed(blockDelay.getValue().longValue())) {
                            if (this.autoMode.getValue() == 0) {
                                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                                timer.reset();
                                reset();
                            }
                            if (this.autoMode.getValue() == 1) {
                                startBlocking = true;
                            }
                            if (startBlocking) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                                holdTicks++;
                            }
                            if (holdTicks > holdTick.getValue()) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                                startBlocking = false;
                                holdTicks = 0;
                                timer.reset();
                            }
                        }
                    }
                    if (autoBlockTime.getValue() == 1) {
                        if (mc.thePlayer.hurtTime >= minHurtTime.getValue() && mc.thePlayer.hurtTime <= maxHurtTime.getValue()) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            startBlocking = true;
                        } else if (startBlocking) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                            startBlocking = false;
                        }
                    }
                    if (autoBlockTime.getValue() == 2){
                        if (sagTicks < 10) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            sagTicks++;
                        }
                        if (sagTicks >= 10){
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            sagTicks = 0;
                        }
                    }
                    if (autoBlockTime.getValue() == 3){
                        if(mc.thePlayer.hurtTime == smartBlockHurtTime.getValue()){
                            canBlock = true;
                        }
                        if (canBlock){
                            getBlockTicks++;
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                        }
                        if (mc.thePlayer.hurtTime == 9 && releaseAfterHit.getValue()){
                            canBlock = false;
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            getBlockTicks = 0;
                        }
                        if (getBlockTicks > smartBlockTick.getValue()){
                            canBlock = false;
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            getBlockTicks = 0;
                        }
                    }
                }
            }
        }
    }
    private void reset(){
        attacking = canBlock = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        holdTicks = sagTicks = getBlockTicks = 0;
        timer.reset();
    }

    @EventTarget
    public void onAttack(AttackEvent event){
        if (mode.getValue() >= 2) return;
        if (this.isEnabled() && ItemUtil.isHoldingSword() && event.getTarget() instanceof EntityLivingBase){
            attacking = true;
            attackTicks = 0;
            target = (EntityLivingBase) event.getTarget();
            if (autoBlockTime.getValue() == 3){
                if (mc.thePlayer.hurtTime == 0 && onFirstHit.getValue())canBlock = true;
            }
        }
    }
    @Override
    public String[] getSuffix() {
        return new String[]{mode.getValue() == 2 ? "Lag " + lagDuration.getValue() + "ms" : mode.getModeString()};
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE && mode.getValue() >= 2) amunix.update();
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (mode.getValue() >= 2 && event.getType() == EventType.SEND && !event.isCancelled()) amunix.packet(event.getPacket());
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (amunix.suppressUse()) event.setCancelled(true);
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (mode.getValue() == 2 && amunix.ownsBlink()) event.setCancelled(true);
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) { cleanup(); }

    private void cleanup() {
        amunix.reset();
        if (startBlocking || attacking || stopTick > 0) reset();
        startBlocking = false; stopTick = attackTicks = 0; target = null;
    }

    @Override
    public void onEnabled() { cleanup(); }

    @Override
    public void onDisabled() { cleanup(); }

    @Override
    public void verifyValue(String name) {
        if ("Mode".equals(name)) cleanup();
        if ("Lag Duration".equals(name) && minLagDuration.getValue() > lagDuration.getValue()) minLagDuration.setValue(lagDuration.getValue());
        if ("Minimum Lag Duration".equals(name) && minLagDuration.getValue() > lagDuration.getValue()) lagDuration.setValue(minLagDuration.getValue());
    }

    public boolean controlsUseInput() { return amunix.controlsInput(); }
}
