package leader.module.modules.player;

import leader.event.EventTarget;
import leader.events.AttackEvent;
import leader.events.HitSlowDownEvent;
import leader.events.LivingUpdateEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.ModeProperty;
import leader.property.properties.PercentProperty;
import leader.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla", "Legit", "Grim", "Buffer"});

    public final BooleanProperty onHurt = new BooleanProperty("OnHurt", false, () -> mode.getValue() == 1 || mode.getValue() == 3);

    public final PercentProperty slowdown = new PercentProperty("Slowdown", 0, () -> mode.getValue() == 0);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", false, () -> mode.getValue() == 0);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach Only", false, () -> mode.getValue() == 0);

    public final BooleanProperty autoFactor = new BooleanProperty("Auto Factor", true, () -> mode.getValue() == 2);
    public final PercentProperty offsetBudget = new PercentProperty("Offset Budget", 50, () -> mode.getValue() == 2 && autoFactor.getValue());
    public final PercentProperty factor = new PercentProperty("Factor", 65, () -> mode.getValue() == 2 && !autoFactor.getValue());
    public final BooleanProperty grimGroundOnly = new BooleanProperty("Ground Only", true, () -> mode.getValue() == 2);

    private int disSprintTicks = 0;

    public KeepSprint() {
        super("KeepSprint", false);
    }
    public boolean isBufferMode() {
        return mode.getValue() == 3;
    }

    public boolean shouldKeepSprint() {
        switch (mode.getValue()) {
            case 1:
                return false;
            case 2:
                if (grimGroundOnly.getValue() && !mc.thePlayer.onGround) return false;
                return true;
            case 3:
                return true;
            default:
                if (groundOnly.getValue() && !mc.thePlayer.onGround) return false;
                return !reachOnly.getValue() || mc.objectMouseOver != null
                        && mc.objectMouseOver.hitVec != null
                        && mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0;
        }
    }

    public boolean isAttackNoSlow() {
        return isEnabled() && shouldKeepSprint();
    }

    public double getSlowFactor() {
        switch (mode.getValue()) {
            case 1:
                return 0.6;
            case 2:
                if (autoFactor.getValue()) {
                    double speed = Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
                    if (speed <= 0.0) return 1.0;
                    double budget = 0.001 * offsetBudget.getValue() / 100.0;
                    double maxFactor = speed * 0.6 < 0.005 ? budget / speed : 0.6 + budget / speed;
                    return Math.min(1.0, maxFactor);
                }
                return factor.getValue().doubleValue() / 100.0;
            case 3:
                return 1.0;
            default:
                return 0.6 + 0.4 * (1.0 - slowdown.getValue().doubleValue() / 100.0);
        }
    }

    @EventTarget
    public void onHitSlowDown(HitSlowDownEvent event) {
        if (!this.isEnabled()) return;
        switch (mode.getValue()) {
            case 0:
            case 2:
                if (shouldKeepSprint()) {
                    event.setSlowDown(getSlowFactor());
                    event.setSprint(true);
                }
                break;
            case 1:
            case 3:
                break;
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isEnabled() && mode.getValue() == 1) {
            this.disSprintTicks = 3;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && mode.getValue() == 1) {
            if (disSprintTicks >= 0) {
                if (onHurt.getValue() || mc.thePlayer.hurtTime == 0) {
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                    mc.thePlayer.setSprinting(false);
                }
                disSprintTicks--;
            }
        }
    }

    @Override
    public void onEnabled() {
        disSprintTicks = 0;
    }

    @Override
    public void onDisabled() {
        if (mode.getValue() == 1) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
        }
    }

    @Override
    public String[] getSuffix() {
        switch (mode.getValue()) {
            case 2:
                if (autoFactor.getValue()) {
                    return new String[]{"Grim", String.format("%.0f%%", getSlowFactor() * 100)};
                }
                return new String[]{"Grim", factor.getValue() + "%"};
            default:
                return new String[]{this.mode.getModeString()};
        }
    }
}
