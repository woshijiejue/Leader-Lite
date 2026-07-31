package leader.client.module.modules.player;

import com.google.common.base.CaseFormat;
import leader.client.Leader;
import leader.client.component.impl.network.blink.BlinkType;
import leader.client.event.EventTarget;
import leader.client.event.types.Priority;
import leader.client.events.KeyEvent;
import leader.client.events.PlayerUpdateEvent;
import leader.client.module.Module;
import leader.client.module.modules.movement.LongJump;
import leader.client.util.player.PlayerUtil;
import leader.client.util.math.RandomUtil;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.AxisAlignedBB;

public class AntiVoid extends Module {
    public final ListValue mode = (ListValue) new ListValue("mode", new String[]{"BLINK"}, "BLINK", this)
            .onChanged(() -> { if (isEnabled()) onDisabled(); });
    public final SliderValue distance = (SliderValue) new SliderValue("distance", 5.0, 0.0, 16.0, Representation.FLOAT, this)
            .onChanged(() -> { if (isEnabled()) onDisabled(); });
    private boolean isInVoid = false;
    private boolean wasInVoid = false;
    private double[] lastSafePosition = null;

    private void resetBlink() {
        Leader.blinkComponent.setBlinkState(false, BlinkType.ANTI_VOID);
        this.lastSafePosition = null;
    }

    private boolean canUseAntiVoid() {
        LongJump longJump = (LongJump) Leader.moduleManager.modules.get(LongJump.class);
        return !longJump.isJumping();
    }

    public AntiVoid() {
        super("AntiVoid", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled()) {
            this.isInVoid = !mc.thePlayer.capabilities.allowFlying && PlayerUtil.isInWater();
            if (this.mode.is("BLINK")) {
                if (!this.isInVoid) {
                    this.resetBlink();
                }
                if (this.lastSafePosition != null) {
                    float subWidth = mc.thePlayer.width / 2.0F;
                    float height = mc.thePlayer.height;
                    if (PlayerUtil.checkInWater(
                            new AxisAlignedBB(
                                    this.lastSafePosition[0] - (double) subWidth,
                                    this.lastSafePosition[1],
                                    this.lastSafePosition[2] - (double) subWidth,
                                    this.lastSafePosition[0] + (double) subWidth,
                                    this.lastSafePosition[1] + (double) height,
                                    this.lastSafePosition[2] + (double) subWidth
                            )
                    )) {
                        this.resetBlink();
                    }
                }
                if (!this.wasInVoid && this.isInVoid && this.canUseAntiVoid()) {
                    Leader.blinkComponent.setBlinkState(false, BlinkType.AUTO_BLOCK);
                    if (Leader.blinkComponent.setBlinkState(true, BlinkType.ANTI_VOID)) {
                        this.lastSafePosition = new double[]{mc.thePlayer.prevPosX, mc.thePlayer.prevPosY, mc.thePlayer.prevPosZ};
                    }
                }
                if (Leader.blinkComponent.getBlinkingModule() == BlinkType.ANTI_VOID
                        && this.lastSafePosition != null
                        && this.lastSafePosition[1] - (double) this.distance.getValue().floatValue() > mc.thePlayer.posY) {
                    Leader.blinkComponent
                            .blinkedPackets
                            .offerFirst(
                                    new C04PacketPlayerPosition(
                                            this.lastSafePosition[0], this.lastSafePosition[1] - RandomUtil.nextDouble(10.0, 20.0), this.lastSafePosition[2], false
                                    )
                            );
                    this.resetBlink();
                }
            }
            this.wasInVoid = this.isInVoid;
        }
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            ItemStack currentItem = mc.thePlayer.inventory.getCurrentItem();
            if (currentItem != null && currentItem.getItem() instanceof ItemEnderPearl) {
                this.resetBlink();
            }
        }
    }

    @Override
    public void onEnabled() {
        this.isInVoid = false;
        this.wasInVoid = false;
        this.resetBlink();
    }

    @Override
    public void onDisabled() {
        Leader.blinkComponent.setBlinkState(false, BlinkType.ANTI_VOID);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getValue())};
    }
}
