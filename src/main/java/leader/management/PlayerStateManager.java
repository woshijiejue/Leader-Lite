package leader.management;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

public class PlayerStateManager {
    public boolean attacking = false;
    public boolean interacting = false;
    public boolean digging = false;
    public boolean placing = false;
    public boolean swapping = false;
    public boolean swinging = false;
    private long movementSequence;
    private boolean sprintInput;
    private boolean sneakInput;

    public long getMovementSequence() {
        return movementSequence;
    }

    public boolean hasInputAction() {
        return sprintInput || sneakInput;
    }

    public void handlePacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            this.attacking = true;
            if (((C02PacketUseEntity) packet).getAction() != C02PacketUseEntity.Action.ATTACK) {
                this.interacting = true;
            }
        }
        if (packet instanceof C07PacketPlayerDigging) {
            this.digging = true;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            this.placing = true;
        }
        if (packet instanceof C09PacketHeldItemChange) {
            this.swapping = true;
        }
        if (packet instanceof C0APacketAnimation) {
            this.swinging = true;
        }
        if (packet instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction.Action action = ((C0BPacketEntityAction) packet).getAction();
            if (action == C0BPacketEntityAction.Action.START_SPRINTING
                    || action == C0BPacketEntityAction.Action.STOP_SPRINTING) sprintInput = true;
            if (action == C0BPacketEntityAction.Action.START_SNEAKING
                    || action == C0BPacketEntityAction.Action.STOP_SNEAKING) sneakInput = true;
        }
        if (packet instanceof C03PacketPlayer) {
            movementSequence++;
            this.attacking = false;
            this.interacting = false;
            this.digging = false;
            this.placing = false;
            this.swapping = false;
            this.swinging = false;
            this.sprintInput = false;
            this.sneakInput = false;
        }
    }
}
