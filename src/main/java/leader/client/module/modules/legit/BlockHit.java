package leader.client.module.modules.legit;

import com.google.common.base.CaseFormat;
import leader.client.util.player.RotationUtil;
import net.minecraft.entity.EntityLivingBase;

import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.AttackEvent;
import leader.client.events.TickEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;
import leader.client.util.player.ItemUtil;
import leader.client.util.misc.KeyBindUtil;
import leader.client.util.timer.TimerUtil;

public class BlockHit extends Module {

    public BlockHit() {
        super("BlockHit", false, false);
    }

    private final ListValue mode = new ListValue("Mode", new String[]{"Helper", "Auto"}, "Helper", this);

    private final SliderValue stopTime = new SliderValue("Stop Ticks", 2, 1, 5, () -> this.mode.is("Helper"), Representation.INT, this);
    private final ListValue autoMode = new ListValue("Auto Mode", new String[]{"Spam", "Hold"}, "Spam", () -> this.mode.is("Auto") && this.autoBlockTime.is("Delay"), this);
    private final ListValue autoBlockTime = new ListValue("AutoBlock Time", new String[]{"Delay", "HurtTime", "Sag", "Smart"}, "Delay", () -> this.mode.is("Auto"), this);
    private final BoolValue onFirstHit = new BoolValue("OnFirstHit", true, () -> this.mode.is("Auto") && this.autoBlockTime.is("Smart"), this);
    private final SliderValue smartBlockTick = new SliderValue("Smart Block Ticks", 2, 1, 5, () -> this.mode.is("Auto") && this.autoBlockTime.is("Smart"), Representation.INT, this);
    private final BoolValue releaseAfterHit = new BoolValue("Release After Hit", true, () -> this.mode.is("Auto") && this.autoBlockTime.is("Smart"), this);
    private final SliderValue smartBlockHurtTime = new SliderValue("Smart Block HurtTime", 2, 0, 10, () -> this.mode.is("Auto") && this.autoBlockTime.is("Smart"), Representation.INT, this);
    private final SliderValue blockDelay = new SliderValue("Block Delay", 100, 0, 1000, () -> this.mode.is("Auto") && this.autoBlockTime.is("Delay"), Representation.INT, this);
    private final SliderValue holdTick = new SliderValue("Hold Ticks", 2, 2, 5, () -> this.mode.is("Auto") && this.autoMode.is("Hold") && this.autoBlockTime.is("Delay"), Representation.INT, this);
    private final SliderValue minHurtTime = new SliderValue("Min HurtTime", 10, 1, 10, () -> this.mode.is("Auto") && this.autoBlockTime.is("HurtTime"), Representation.INT, this);
    private final SliderValue maxHurtTime = new SliderValue("Max HurtTime", 10, 1, 10, () -> this.mode.is("Auto") && this.autoBlockTime.is("HurtTime"), Representation.INT, this);
    private final SliderValue chance = new SliderValue("Block Hit Chance", 50, 0, 100, () -> this.mode.is("Auto"), Representation.INT, this);
    private final BoolValue smart = new BoolValue("Smart", true, () -> this.mode.is("Auto"), this);
    private final BoolValue autoBlockRange = new BoolValue("AutoBlock Range", true, () -> this.mode.is("Auto"), this);
    private final SliderValue range = new SliderValue("Range", 3.0, 1.0, 4.0, () -> autoBlockRange.getValue() && mode.is("Auto"), Representation.FLOAT, this);
    private int holdTicks, stopTick;

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
            if (this.mode.is("Helper")) {
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
                if (stopTick > stopTime.getValue().intValue()) {
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    startBlocking = false;
                    stopTick = 0;
                }
            }
            if (this.mode.is("Auto")) {
                if (target == null) return;
                if (attacking) {
                    attackTicks++;
                }
                if (attackTicks > 10) {
                    reset();
                    target = null;
                    return;
                }
                if (Math.random() > chance.getValue()) {
                    reset();
                    return;
                }
                if (autoBlockRange.getValue() && RotationUtil.distanceToBox(target.getCollisionBoundingBox()) >= range.getValue()) {
                    reset();
                    return;
                }
                if (smart.getValue() && target.hurtTime == 0) {
                    reset();
                    return;
                }
                if (attacking && ItemUtil.isHoldingSword()) {
                    if (autoBlockTime.is("Delay")) {
                        if (timer.hasTimeElapsed(blockDelay.getValue().longValue())) {
                            if (this.autoMode.is("Spam")) {
                                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                                timer.reset();
                                reset();
                            }
                            if (this.autoMode.is("Hold")) {
                                startBlocking = true;
                            }
                            if (startBlocking) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                                holdTicks++;
                            }
                            if (holdTicks > holdTick.getValue().intValue()) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                                startBlocking = false;
                                holdTicks = 0;
                                timer.reset();
                            }
                        }
                    }
                    if (autoBlockTime.is("HurtTime")) {
                        if (mc.thePlayer.hurtTime >= minHurtTime.getValue().intValue() && mc.thePlayer.hurtTime <= maxHurtTime.getValue().intValue()) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            startBlocking = true;
                        } else if (startBlocking) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                            startBlocking = false;
                        }
                    }
                    if (autoBlockTime.is("Sag")) {
                        if (sagTicks < 10) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            sagTicks++;
                        }
                        if (sagTicks >= 10) {
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            sagTicks = 0;
                        }
                    }
                    if (autoBlockTime.is("Smart")) {
                        if (mc.thePlayer.hurtTime == smartBlockHurtTime.getValue().intValue()) {
                            canBlock = true;
                        }
                        if (canBlock) {
                            getBlockTicks++;
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                        }
                        if (mc.thePlayer.hurtTime == 9 && releaseAfterHit.getValue()) {
                            canBlock = false;
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            getBlockTicks = 0;
                        }
                        if (getBlockTicks > smartBlockTick.getValue().intValue()) {
                            canBlock = false;
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            getBlockTicks = 0;
                        }
                    }
                }
            }
        }
    }

    private void reset() {
        attacking = canBlock = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        holdTicks = sagTicks = getBlockTicks = 0;
        timer.reset();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isEnabled() && ItemUtil.isHoldingSword()) {
            attacking = true;
            attackTicks = 0;
            target = (EntityLivingBase) event.getTarget();
            if (autoBlockTime.is("Smart")) {
                if (mc.thePlayer.hurtTime == 0 && onFirstHit.getValue()) canBlock = true;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getValue())};
    }
}
