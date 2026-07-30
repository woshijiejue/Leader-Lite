package leader.client.util.timer;

import net.minecraft.network.Packet;

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


    public Packet<?> getPacket() {
        return packet;
    }

    public TimerUtil getCold() {
        return getTime();
    }

    public TimerUtil getTime() {
        return time;
    }

    public long getMillis() {
        return millis;
    }

}