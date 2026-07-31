package leader.client.module.modules.player;

import com.google.common.base.CaseFormat;
import leader.client.Leader;
import leader.client.component.impl.network.blink.BlinkType;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.PacketEvent;
import leader.client.events.TickEvent;
import leader.client.util.DebugUtil;
import leader.client.util.server.PacketUtil;
import leader.client.util.server.ServerUtil;
import leader.mixin.accessor.IAccessorC03PacketPlayer;
import leader.mixin.accessor.IAccessorMinecraft;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;
import leader.client.util.player.PlayerUtil;
import leader.client.util.timer.TimerUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;

public class NoFall extends Module {
    public final ListValue mode = (ListValue) new ListValue("mode", new String[]{"PACKET", "BLINK", "NO_GROUND", "SPOOF"}, "PACKET", this)
            .onChanged(() -> { if (isEnabled()) onDisabled(); });
    public final SliderValue distance = (SliderValue) new SliderValue("distance", 3.0, 0.0, 20.0, Representation.FLOAT, this)
            .onChanged(() -> { if (isEnabled()) onDisabled(); });
    public final SliderValue delay = (SliderValue) new SliderValue("delay", 0, 0, 10000, Representation.INT, this)
            .onChanged(() -> { if (isEnabled()) onDisabled(); });

    private final TimerUtil packetDelayTimer = new TimerUtil();
    private final TimerUtil scoreboardResetTimer = new TimerUtil();
    private boolean slowFalling = false;
    private boolean lastOnGround = false;

    private boolean canTrigger() {
        return this.scoreboardResetTimer.hasTimeElapsed(3000) && this.packetDelayTimer.hasTimeElapsed(this.delay.getValue().longValue());
    }

    public NoFall() {
        super("NoFall", false);
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S08PacketPlayerPosLook) {
            this.onDisabled();
        } else if (this.isEnabled() && event.getType() == EventType.SEND && !event.isCancelled()) {
            if (event.getPacket() instanceof C03PacketPlayer) {
                C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
                switch (this.mode.getValue()) {
                    case "PACKET":
                        if (this.slowFalling) {
                            this.slowFalling = false;
                            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
                        } else if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                this.slowFalling = true;
                                ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.5F;
                            }
                        }
                        break;
                    case "BLINK":
                        boolean allowed = !mc.thePlayer.isOnLadder() && !mc.thePlayer.capabilities.allowFlying && mc.thePlayer.hurtTime == 0;
                        if (Leader.blinkComponent.getBlinkingModule() != BlinkType.NO_FALL) {
                            if (this.lastOnGround
                                    && !packet.isOnGround()
                                    && allowed
                                    && PlayerUtil.canFly(this.distance.getValue().intValue())
                                    && mc.thePlayer.motionY < 0.0) {
                                Leader.blinkComponent.setBlinkState(false, Leader.blinkComponent.getBlinkingModule());
                                Leader.blinkComponent.setBlinkState(true, BlinkType.NO_FALL);
                            }
                        } else if (!allowed) {
                            Leader.blinkComponent.setBlinkState(false, BlinkType.NO_FALL);
                            DebugUtil.sendFormatted(String.format("%s%s: &cFailed player check!&r", Leader.clientName, this.getName()));
                        } else if (PlayerUtil.checkInWater(mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
                            Leader.blinkComponent.setBlinkState(false, BlinkType.NO_FALL);
                            DebugUtil.sendFormatted(String.format("%s%s: &cFailed void check!&r", Leader.clientName, this.getName()));
                        } else if (packet.isOnGround()) {
                            for (Packet<?> blinkedPacket : Leader.blinkComponent.blinkedPackets) {
                                if (blinkedPacket instanceof C03PacketPlayer) {
                                    ((IAccessorC03PacketPlayer) blinkedPacket).setOnGround(true);
                                }
                            }
                            Leader.blinkComponent.setBlinkState(false, BlinkType.NO_FALL);
                            this.packetDelayTimer.reset();
                        }
                        this.lastOnGround = packet.isOnGround() && allowed && this.canTrigger();
                        break;
                    case "NO_GROUND":
                        ((IAccessorC03PacketPlayer) packet).setOnGround(false);
                        break;
                    case "SPOOF":
                        if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                ((IAccessorC03PacketPlayer) packet).setOnGround(true);
                                mc.thePlayer.fallDistance = 0.0F;
                            }
                        }
                }
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (ServerUtil.hasPlayerCountInfo()) {
                this.scoreboardResetTimer.reset();
            }
            if (this.mode.is("PACKET") && this.slowFalling) {
                PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
                mc.thePlayer.fallDistance = 0.0F;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.lastOnGround = false;
        Leader.blinkComponent.setBlinkState(false, BlinkType.NO_FALL);
        if (this.slowFalling) {
            this.slowFalling = false;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getValue())};
    }
}
