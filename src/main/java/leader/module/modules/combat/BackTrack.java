package leader.module.modules.combat;

import leader.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import leader.events.LoadWorldEvent;
import org.lwjgl.opengl.GL11;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.*;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.util.PacketUtil;
import leader.util.TimedPacket;

import java.awt.*;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();


    private final IntProperty trackMs = new IntProperty("TrackMS", 200, 1, 1000);
    private final FloatProperty maxDistance = new FloatProperty("Max Track Range", 4.0F, 3.1F, 6.0F);
    private final BooleanProperty rayTrance = new BooleanProperty("Ray Trance",true);
    private final BooleanProperty renderRealPos = new BooleanProperty("Render Real Position", true);
    private final BooleanProperty smart = new BooleanProperty("Smart", true);
    private final BooleanProperty onlyHighSpeed = new BooleanProperty("Only On Target High Speed", false);
    private final FloatProperty highSpeedThreshold = new FloatProperty("HighSpeed Threshold", 0.2F, 0.01F, 1.0F,onlyHighSpeed::getValue);

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final Deque<Vec3> positionHistory = new ConcurrentLinkedDeque<>();
    private final Deque<Vec3> recentPositions = new ConcurrentLinkedDeque<>();

    private Vec3 realTargetPos;
    private Vec3 lastRealTargetPos;
    private EntityPlayer target;
    private boolean replaying;

    public BackTrack() {
        super("BackTrack", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{trackMs.getValue() + "ms"};
    }

    @Override
    public void onEnabled() {
        clearAll();
    }

    @Override
    public void onDisabled() {
        releaseAll();
        clearAll();
    }

    private void clearAll() {
        packetQueue.clear();
        positionHistory.clear();
        recentPositions.clear();
        realTargetPos = null;
        lastRealTargetPos = null;
        target = null;
    }

    @EventTarget
    public void onAttack(AttackEvent e) {
        if (!isEnabled()) return;

        Entity entity = e.getTarget();
        if (!(entity instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) entity;
        if (onlyHighSpeed.getValue()) {
            double dx = player.posX - player.prevPosX;
            double dy = player.posY - player.prevPosY;
            double dz = player.posZ - player.prevPosZ;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed < highSpeedThreshold.getValue()) {
                // A failed filter must also end the previous tracking session.
                if (target != null) resetAndRelease();
                return;
            }
        }
        if (target != null && player.getEntityId() == target.getEntityId()) {
            return;
        }
        // Never carry packets from the previous target into a new target.
        if (target != null) resetAndRelease();
        target = player;
        realTargetPos = player.getPositionVector();
        lastRealTargetPos = realTargetPos;

        positionHistory.clear();
        recentPositions.clear();
        positionHistory.add(realTargetPos);
        recentPositions.add(realTargetPos);
    }

    @EventTarget
    public void onTick(TickEvent e) {
        if (!isEnabled() || e.getType() == EventType.POST) return;
        updateTargetLogic();
        processPacketQueue();
        if (packetQueue.isEmpty() && target != null) {
            Vec3 current = target.getPositionVector();
            lastRealTargetPos = realTargetPos;
            realTargetPos = current;
        }
    }

    private void updateTargetLogic() {
        if (target == null || realTargetPos == null || mc.theWorld == null
                || !mc.theWorld.loadedEntityList.contains(target) || target.isDead) {
            if (target != null) resetAndRelease();
            return;
        }

        try {
            Vec3 currentPos = target.getPositionVector();
            recentPositions.addLast(currentPos);
            if (recentPositions.size() > 5) {
                recentPositions.removeFirst();
            }

            if (recentPositions.size() == 5) {
                Vec3 oldestPos = recentPositions.getFirst();
                if (oldestPos.distanceTo(currentPos) > 5.0) {
                    resetAndRelease();
                    return;
                }
            }

            positionHistory.addLast(currentPos);
            if (positionHistory.size() > 10) {
                positionHistory.removeFirst();
            }

            boolean tooFar = realTargetPos.distanceTo(mc.thePlayer.getPositionVector()) > maxDistance.getValue();
            if (tooFar) {
                resetAndRelease();
                return;
            }

                if (smart.getValue() && !positionHistory.isEmpty()) {
                double distReal = distanceToPositionBox(realTargetPos);
                double distVisible = distanceToPositionBox(target.getPositionVector());
                if (distReal <= distVisible) {
                    resetAndRelease();
                    return;
                }
            }

            lastRealTargetPos = realTargetPos;
        } catch (Exception ex) {
            resetAndRelease();
        }
    }

    private double distanceToPositionBox(Vec3 position) {
        if (mc.thePlayer == null || target == null) return Double.MAX_VALUE;
        double border = target.getCollisionBorderSize();
        double halfWidth = target.width * 0.5D + border;
        double height = target.height + border;
        AxisAlignedBB box = new AxisAlignedBB(
                position.xCoord - halfWidth, position.yCoord, position.zCoord - halfWidth,
                position.xCoord + halfWidth, position.yCoord + height, position.zCoord + halfWidth);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double dx = Math.max(box.minX - eyes.xCoord, Math.max(0.0D, eyes.xCoord - box.maxX));
        double dy = Math.max(box.minY - eyes.yCoord, Math.max(0.0D, eyes.yCoord - box.maxY));
        double dz = Math.max(box.minZ - eyes.zCoord, Math.max(0.0D, eyes.zCoord - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void processPacketQueue() {
        long maxDelay = trackMs.getValue();

        while (!packetQueue.isEmpty()) {
            TimedPacket timedPacket = packetQueue.peek();
            if (timedPacket == null) break;

            if (timedPacket.getCold().getPass(maxDelay)) {
                packetQueue.poll();
                replayPacket(timedPacket.getPacket());
            } else {
                break;
            }
        }
    }

    private void replayPacket(Packet<?> packet) {
        if (packet == null) return;
        replaying = true;
        try {
            PacketUtil.receivePacket(packet);
        } finally {
            replaying = false;
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        // Do not replay packets from the old world into the new world.
        packetQueue.clear();
        positionHistory.clear();
        recentPositions.clear();
        target = null;
        realTargetPos = null;
        lastRealTargetPos = null;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || target == null || realTargetPos == null || lastRealTargetPos == null)
            return;
        if (!renderRealPos.getValue())
            return;

        float size = target.getCollisionBorderSize();
        double width = target.width / 2.0 + size;
        double height = target.height + size;

        Vec3 smoothed = getSmoothedPosition(event.getPartialTicks());
        AxisAlignedBB aabb = new AxisAlignedBB(
                smoothed.xCoord - width, smoothed.yCoord, smoothed.zCoord - width,
                smoothed.xCoord + width, smoothed.yCoord + height, smoothed.zCoord + width
        ).offset(
                -mc.getRenderManager().viewerPosX,
                -mc.getRenderManager().viewerPosY,
                -mc.getRenderManager().viewerPosZ
        );

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        drawFilledBox(aabb, 255, 255, 255);

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private Vec3 getSmoothedPosition(float partialTicks) {
        if (lastRealTargetPos == null || realTargetPos == null) {
            return realTargetPos;
        }
        return new Vec3(
                lastRealTargetPos.xCoord + (realTargetPos.xCoord - lastRealTargetPos.xCoord) * partialTicks,
                lastRealTargetPos.yCoord + (realTargetPos.yCoord - lastRealTargetPos.yCoord) * partialTicks,
                lastRealTargetPos.zCoord + (realTargetPos.zCoord - lastRealTargetPos.zCoord) * partialTicks
        );
    }
    @EventTarget
    public void onUpdate(UpdateEvent event){
        if (!isEnabled() || target == null || realTargetPos == null || event.getType() != EventType.PRE) {
            return;
        }

        float size = target.getCollisionBorderSize();
        double width = target.width / 2.0 + size;
        double height = target.height + size;
        AxisAlignedBB aabb = new AxisAlignedBB(
                realTargetPos.xCoord - width, realTargetPos.yCoord, realTargetPos.zCoord - width,
                realTargetPos.xCoord + width, realTargetPos.yCoord + height, realTargetPos.zCoord + width
        );
        if (rayTrance.getValue()
                && RotationUtil.rayTrace(aabb, event.getNewYaw(), event.getNewPitch(), maxDistance.getValue()) == null) {
            resetAndRelease();
        }
    }

    @EventTarget
    public void onReceivePacket(PacketEvent e) {
        if (!isEnabled() || replaying || e.getType() == EventType.SEND || e.isCancelled()) return;

        Packet<?> packet = e.getPacket();
        if (packet instanceof S08PacketPlayerPosLook) {
            resetAndRelease();
            return;
        }
        if (packet instanceof S12PacketEntityVelocity
                && mc.thePlayer != null
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
            resetAndRelease();
            return;
        }

        if (target == null) return;

        boolean shouldIntercept = false;

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity wrapper = (S14PacketEntity) packet;
            Entity entity = wrapper.getEntity(mc.theWorld);
            if (entity != null && entity.getEntityId() == target.getEntityId()) {
                realTargetPos = realTargetPos.addVector(
                        wrapper.func_149062_c() / 32.0D,
                        wrapper.func_149061_d() / 32.0D,
                        wrapper.func_149064_e() / 32.0D
                );
                shouldIntercept = true;
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport wrapper = (S18PacketEntityTeleport) packet;
            if (wrapper.getEntityId() == target.getEntityId()) {
                realTargetPos = new Vec3(
                        wrapper.getX() / 32.0D,
                        wrapper.getY() / 32.0D,
                        wrapper.getZ() / 32.0D
                );
                shouldIntercept = true;
            }
        } else if (packet instanceof S13PacketDestroyEntities) {
            S13PacketDestroyEntities wrapper = (S13PacketDestroyEntities) packet;
            for (int id : wrapper.getEntityIDs()) {
                if (id == target.getEntityId()) {
                    resetAndRelease();
                    return;
                }
            }
        }

        if (shouldIntercept) {
            packetQueue.add(new TimedPacket(packet));
            e.setCancelled(true);
        }
    }

    private void resetAndRelease() {
        target = null;
        realTargetPos = null;
        lastRealTargetPos = null;
        positionHistory.clear();
        recentPositions.clear();
        releaseAll();
    }

    private void releaseAll() {
        TimedPacket tp;
        while ((tp = packetQueue.poll()) != null) {
            replayPacket(tp.getPacket());
        }
    }

    public static void drawFilledBox(AxisAlignedBB aabb, int red, int green, int blue) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);

        renderer.pos(aabb.minX, aabb.minY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.minX, aabb.minY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.minY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.minY, aabb.minZ).color(red, green, blue, 63).endVertex();

        renderer.pos(aabb.minX, aabb.maxY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.maxY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.maxY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.minX, aabb.maxY, aabb.maxZ).color(red, green, blue, 63).endVertex();

        renderer.pos(aabb.minX, aabb.minY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.minY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.maxY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.minX, aabb.maxY, aabb.minZ).color(red, green, blue, 63).endVertex();

        renderer.pos(aabb.minX, aabb.minY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.minX, aabb.maxY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.maxY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.minY, aabb.maxZ).color(red, green, blue, 63).endVertex();

        renderer.pos(aabb.minX, aabb.minY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.minX, aabb.maxY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.minX, aabb.maxY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.minX, aabb.minY, aabb.maxZ).color(red, green, blue, 63).endVertex();

        renderer.pos(aabb.maxX, aabb.minY, aabb.minZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.minY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.maxY, aabb.maxZ).color(red, green, blue, 63).endVertex();
        renderer.pos(aabb.maxX, aabb.maxY, aabb.minZ).color(red, green, blue, 63).endVertex();

        tessellator.draw();
    }
}