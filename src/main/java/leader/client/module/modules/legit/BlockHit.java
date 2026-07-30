package leader.client.module.modules.legit;

import com.google.common.base.CaseFormat;
import leader.client.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.AttackEvent;
import leader.client.events.TickEvent;
import leader.client.module.Module;
import leader.client.property.properties.*;
import leader.client.util.player.ItemUtil;
import leader.client.util.KeyBindUtil;
import leader.client.util.timer.TimerUtil;

public class BlockHit extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    public BlockHit() {
        super("BlockHit",false, false);
    }
    private final ModeProperty mode = new ModeProperty("Mode",0,new String[]{"Helper","Auto"});

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
    @EventTarget
    public void onTick(TickEvent event) {
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
        if (this.isEnabled() && ItemUtil.isHoldingSword()){
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
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
