package leader.client.util.timer;

import lombok.Getter;
import net.minecraft.network.Packet;

@Getter
public class TimedPacket {

    private final Packet<?> packet;
    private final TimerUtil time;
    private final long millis;

    public TimedPacket(Packet<?> packet) {
        this.packet = packet;
        this.time = new TimerUtil();
        this.millis = System.currentTimeMillis();
    }

    public TimedPacket(final Packet<?> packet, final long millis) {
        this.packet = packet;
        this.millis = millis;
        this.time = new TimerUtil();
    }


    public TimerUtil getCold() {
        return getTime();
    }
}