package leader.module.modules.combat;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.AttackEvent;
import leader.events.PacketEvent;
import leader.events.Render3DEvent;
import leader.events.TickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.util.PacketUtil;
import leader.util.RenderUtil;
import leader.util.RotationUtil;
import leader.util.TimedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty minRange = new FloatProperty("Min Range", 3.0F, 1.0F, 6.0F);
    public final FloatProperty maxRange = new FloatProperty("Max Range", 6.0F, 1.0F, 6.0F);
    public final IntProperty delay = new IntProperty("Delay", 200, 0, 1000);
    public final IntProperty chance = new IntProperty("Chance", 100, 5, 100);
    public final BooleanProperty resetOnVelocity = new BooleanProperty("Reset On Velocity", true);
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();

    private EntityPlayer trackedPlayer;
    private Vec3 truePos;
    private Vec3 lastTruePos;
    private boolean backtracking;
    private boolean replaying;

    public BackTrack() {
        super("BackTrack", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{delay.getValue().toString()};
    }

    @Override
    public void onEnabled() {
        this.clearAll();
    }

    @Override
    public void onDisabled() {
        this.releaseAll();
        this.clearAll();
    }

    private void clearAll() {
        this.packetQueue.clear();
        this.trackedPlayer = null;
        this.truePos = null;
        this.lastTruePos = null;
        this.backtracking = false;
    }

    private void resetAndRelease() {
        this.trackedPlayer = null;
        this.truePos = null;
        this.lastTruePos = null;
        this.releaseAll();
    }

    private void releaseAll() {
        this.backtracking = false;
        this.replaying = true;
        try {
            while (!this.packetQueue.isEmpty()) {
                TimedPacket timedPacket = this.packetQueue.poll();
                if (timedPacket == null) continue;
                PacketUtil.receivePacket(timedPacket.getPacket());
            }
        } finally {
            this.replaying = false;
        }
    }

    private boolean isTrackedValid() {
        if (this.trackedPlayer == null) return false;
        if (this.trackedPlayer.isDead || this.trackedPlayer.deathTime > 0) return false;
        if (!mc.theWorld.playerEntities.contains(this.trackedPlayer)) return false;
        return mc.thePlayer.getDistanceToEntity(this.trackedPlayer) <= this.maxRange.getValue() + 3.0F;
    }

    private AxisAlignedBB buildBox(Vec3 pos) {
        double width = this.trackedPlayer.width / 2.0 + this.trackedPlayer.getCollisionBorderSize();
        double height = this.trackedPlayer.height + this.trackedPlayer.getCollisionBorderSize();
        return new AxisAlignedBB(
                pos.xCoord - width, pos.yCoord, pos.zCoord - width,
                pos.xCoord + width, pos.yCoord + height, pos.zCoord + width);
    }

    private void checkBacktrackRange() {
        if (this.trackedPlayer == null || this.truePos == null) return;
        double visibleDist = RotationUtil.distanceToBox(this.trackedPlayer.getEntityBoundingBox());
        double trueDist = RotationUtil.distanceToBox(this.buildBox(this.truePos));
        if (visibleDist <= 3.0 && trueDist >= this.minRange.getValue() && trueDist < this.maxRange.getValue()) {
            this.backtracking = true;
        } else {
            this.backtracking = false;
        }
    }

    private void processQueue() {
        long delayMs = this.delay.getValue();
        this.replaying = true;
        try {
            while (!this.packetQueue.isEmpty()) {
                TimedPacket timedPacket = this.packetQueue.peek();
                if (timedPacket == null) break;
                if (!timedPacket.getCold().getPass(delayMs)) break;
                this.packetQueue.poll();
                PacketUtil.receivePacket(timedPacket.getPacket());
            }
        } finally {
            this.replaying = false;
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        Entity entity = event.getTarget();
        if (!(entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) entity;
        if (player == mc.thePlayer) return;
        if (this.trackedPlayer != null && this.trackedPlayer.getEntityId() == player.getEntityId()) return;
        if (Math.random() > this.chance.getValue() / 100.0) {
            this.resetAndRelease();
            return;
        }
        this.releaseAll();
        this.trackedPlayer = player;
        this.truePos = player.getPositionVector();
        this.lastTruePos = this.truePos;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() == EventType.POST) return;
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetAndRelease();
            return;
        }
        if (this.trackedPlayer == null) {
            this.releaseAll();
            return;
        }
        if (!this.isTrackedValid()) {
            this.resetAndRelease();
            return;
        }
        this.lastTruePos = this.truePos;
        if (this.backtracking) {
            this.checkBacktrackRange();
        }
        this.processQueue();
    }

    @EventTarget
    public void onReceivePacket(PacketEvent event) {
        if (!this.isEnabled() || this.replaying) return;
        if (event.getType() == EventType.SEND || event.isCancelled()) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof S08PacketPlayerPosLook) {
            this.resetAndRelease();
            return;
        }

        if (mc.thePlayer != null && packet instanceof S12PacketEntityVelocity
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
            if (this.resetOnVelocity.getValue()) {
                this.resetAndRelease();
            }
            return;
        }

        if (this.trackedPlayer == null || this.truePos == null) return;

        boolean trackedMove = false;
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity wrapper = (S14PacketEntity) packet;
            Entity entity = wrapper.getEntity(mc.theWorld);
            if (entity != null && entity.getEntityId() == this.trackedPlayer.getEntityId()) {
                this.truePos = this.truePos.addVector(
                        wrapper.func_149062_c() / 32.0D,
                        wrapper.func_149061_d() / 32.0D,
                        wrapper.func_149064_e() / 32.0D);
                this.checkBacktrackRange();
                trackedMove = true;
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport wrapper = (S18PacketEntityTeleport) packet;
            if (wrapper.getEntityId() == this.trackedPlayer.getEntityId()) {
                this.truePos = new Vec3(
                        wrapper.getX() / 32.0D,
                        wrapper.getY() / 32.0D,
                        wrapper.getZ() / 32.0D);
                this.checkBacktrackRange();
                trackedMove = true;
            }
        } else if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (id == this.trackedPlayer.getEntityId()) {
                    this.resetAndRelease();
                    return;
                }
            }
        }

        if (trackedMove && this.backtracking) {
            this.packetQueue.add(new TimedPacket(packet));
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.render.getValue()) return;
        if (!this.backtracking || this.truePos == null) return;

        float partialTicks = event.getPartialTicks();
        Vec3 from = this.lastTruePos != null ? this.lastTruePos : this.truePos;
        Vec3 smoothed = new Vec3(
                from.xCoord + (this.truePos.xCoord - from.xCoord) * partialTicks,
                from.yCoord + (this.truePos.yCoord - from.yCoord) * partialTicks,
                from.zCoord + (this.truePos.zCoord - from.zCoord) * partialTicks);

        AxisAlignedBB box = this.buildBox(smoothed).offset(
                -mc.getRenderManager().viewerPosX,
                -mc.getRenderManager().viewerPosY,
                -mc.getRenderManager().viewerPosZ);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        RenderUtil.drawFilledBox(box, 255, 255, 255);
        RenderUtil.drawBoundingBox(box, 255, 255, 255, 204, 1.5F);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
