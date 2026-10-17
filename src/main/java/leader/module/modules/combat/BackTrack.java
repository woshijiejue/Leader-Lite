package leader.module.modules.combat;

import leader.Leader;
import leader.enums.DelayModules;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.AttackEvent;
import leader.events.LoadWorldEvent;
import leader.events.Render3DEvent;
import leader.events.TickEvent;
import leader.module.Module;
import leader.module.modules.movement.Stuck;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.util.PacketUtil;
import leader.util.RenderUtil;
import leader.util.RotationUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

public class BackTrack extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MAX_BUFFERED_PACKETS = 512;

    public final FloatProperty minRange = new FloatProperty("Min Range", 3.0F, 1.0F, 6.0F);
    public final FloatProperty maxRange = new FloatProperty("Max Range", 6.0F, 1.0F, 6.0F);
    public final IntProperty delay = new IntProperty("Delay", 200, 0, 1000);
    public final IntProperty chance = new IntProperty("Chance", 100, 5, 100);
    public final BooleanProperty resetOnVelocity = new BooleanProperty("Reset On Velocity", true);
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private final Deque<DelayedMovement> packetQueue = new ArrayDeque<>();

    private EntityPlayer trackedPlayer;
    private EntityPlayerSP trackedLocalPlayer;
    private WorldClient trackedWorld;
    private NetHandlerPlayClient trackedHandler;
    private Vec3 truePos;
    private Vec3 lastTruePos;
    private boolean backtracking;
    private Packet<?> processingPacket;

    public BackTrack() {
        super("BackTrack", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[] {delay.getValue().toString()};
    }

    @Override
    public void onEnabled() {
        runOnClientThread(
                () -> {
                    if (isEnabled()) resetAndRelease();
                });
    }

    @Override
    public void onDisabled() {
        runOnClientThread(
                () -> {
                    if (!isEnabled()) resetAndRelease();
                });
    }

    private static void runOnClientThread(Runnable action) {
        if (mc.isCallingFromMinecraftThread()) action.run();
        else mc.addScheduledTask(action);
    }

    private void clearAll() {
        this.packetQueue.clear();
        this.trackedPlayer = null;
        this.trackedLocalPlayer = null;
        this.trackedWorld = null;
        this.trackedHandler = null;
        this.truePos = null;
        this.lastTruePos = null;
        this.backtracking = false;
    }

    private void resetAndRelease() {
        this.releaseAll();
        this.clearAll();
    }

    private boolean isSessionValid() {
        return trackedWorld != null
                && trackedHandler != null
                && mc.theWorld == trackedWorld
                && mc.thePlayer == trackedLocalPlayer
                && mc.getNetHandler() == trackedHandler
                && trackedHandler.getNetworkManager().isChannelOpen();
    }

    private boolean canReplay() {
        return isSessionValid()
                && trackedPlayer != null
                && trackedWorld.getEntityByID(trackedPlayer.getEntityId()) == trackedPlayer;
    }

    private void replay(Packet<?> packet) {
        Packet<?> previous = processingPacket;
        processingPacket = packet;
        try {
            PacketUtil.receivePacket(packet);
        } finally {
            processingPacket = previous;
        }
    }

    private void releaseAll() {
        this.backtracking = false;
        while (!this.packetQueue.isEmpty()) {
            if (!canReplay()) {
                this.packetQueue.clear();
                return;
            }
            replay(this.packetQueue.removeFirst().packet);
        }
    }

    private boolean isTrackedValid() {
        if (!canReplay() || mc.thePlayer.isDead) return false;
        if (this.trackedPlayer.isDead || this.trackedPlayer.deathTime > 0) return false;
        return mc.thePlayer.getDistanceToEntity(this.trackedPlayer)
                <= this.maxRange.getValue() + 3.0F;
    }

    private AxisAlignedBB buildBox(Vec3 pos) {
        double border = this.trackedPlayer.getCollisionBorderSize();
        return this.trackedPlayer
                .getEntityBoundingBox()
                .offset(
                        pos.xCoord - trackedPlayer.posX,
                        pos.yCoord - trackedPlayer.posY,
                        pos.zCoord - trackedPlayer.posZ)
                .expand(border, border, border);
    }

    private void checkBacktrackRange() {
        if (!this.backtracking || this.trackedPlayer == null || this.truePos == null) return;
        double visibleDist =
                RotationUtil.distanceToBox(this.buildBox(this.trackedPlayer.getPositionVector()));
        double trueDist = RotationUtil.distanceToBox(this.buildBox(this.truePos));
        if (trueDist < this.minRange.getValue()
                || trueDist > this.maxRange.getValue()
                || trueDist <= visibleDist + 1.0E-6) {
            this.releaseAll();
        }
    }

    private boolean canHoldPackets() {
        if (delay.getValue() <= 0) return false;
        if (Leader.delayManager != null
                && (Leader.delayManager.getDelayModule() != DelayModules.NONE
                        || Leader.delayManager.isReplaying())) return false;
        Stuck stuck =
                Leader.moduleManager == null
                        ? null
                        : (Stuck) Leader.moduleManager.getModule(Stuck.class);
        return stuck == null || !stuck.isStuckActive();
    }

    private void processQueue() {
        long now = System.nanoTime();
        long delayNanos = this.delay.getValue() * 1_000_000L;
        while (!this.packetQueue.isEmpty()) {
            if (!canReplay()) {
                clearAll();
                return;
            }
            DelayedMovement movement = this.packetQueue.peekFirst();
            if (now - movement.receivedAt < delayNanos) break;
            replay(this.packetQueue.removeFirst().packet);
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        Entity entity = event.getTarget();
        if (!(entity instanceof EntityPlayer)) return;
        runOnClientThread(() -> selectTarget((EntityPlayer) entity));
    }

    private void selectTarget(EntityPlayer player) {
        if (!isEnabled()
                || mc.thePlayer == null
                || mc.theWorld == null
                || mc.getNetHandler() == null
                || mc.thePlayer.isDead
                || player == mc.thePlayer
                || player.isDead
                || player.deathTime > 0
                || mc.theWorld.getEntityByID(player.getEntityId()) != player) return;
        if (this.trackedPlayer == player && this.isTrackedValid()) return;
        this.resetAndRelease();
        if (Math.random() > this.chance.getValue() / 100.0) {
            return;
        }
        this.trackedPlayer = player;
        this.trackedLocalPlayer = mc.thePlayer;
        this.trackedWorld = mc.theWorld;
        this.trackedHandler = mc.getNetHandler();
        this.truePos =
                new Vec3(
                        player.serverPosX / 32.0D,
                        player.serverPosY / 32.0D,
                        player.serverPosZ / 32.0D);
        this.lastTruePos = this.truePos;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() == EventType.POST) return;
        if (!isSessionValid()) {
            this.clearAll();
            return;
        }
        if (!this.isTrackedValid() || !canHoldPackets()) {
            this.resetAndRelease();
            return;
        }
        this.lastTruePos = this.truePos;
        this.checkBacktrackRange();
        this.processQueue();
    }

    public static boolean handleMainThreadPacket(Packet<?> packet) {
        if (!mc.isCallingFromMinecraftThread() || Leader.moduleManager == null) return false;
        BackTrack module = (BackTrack) Leader.moduleManager.getModule(BackTrack.class);
        return module != null && module.receiveOnMainThread(packet);
    }

    public static void handleNetworkControl(Packet<?> packet, NetworkManager connection) {
        if (!(packet instanceof S08PacketPlayerPosLook) && !(packet instanceof S40PacketDisconnect))
            return;
        if (Leader.moduleManager == null) return;
        BackTrack module = (BackTrack) Leader.moduleManager.getModule(BackTrack.class);
        if (module == null) return;
        runOnClientThread(
                () -> {
                    if (module.trackedHandler == null
                            || module.trackedHandler.getNetworkManager() != connection) return;
                    if (packet instanceof S40PacketDisconnect) module.clearAll();
                    else module.resetAndRelease();
                });
    }

    private boolean receiveOnMainThread(Packet<?> packet) {
        if (packet == processingPacket) return false;
        if (packet instanceof S01PacketJoinGame
                || packet instanceof S07PacketRespawn
                || packet instanceof S40PacketDisconnect) {
            clearAll();
            return false;
        }
        if (!isEnabled()) {
            resetAndRelease();
            return false;
        }
        if (!isSessionValid()) {
            clearAll();
            return false;
        }
        if (packet instanceof S08PacketPlayerPosLook) {
            resetAndRelease();
            return false;
        }
        boolean localVelocity =
                packet instanceof S12PacketEntityVelocity
                        && ((S12PacketEntityVelocity) packet).getEntityID()
                                == mc.thePlayer.getEntityId();
        if (packet instanceof S27PacketExplosion) {
            S27PacketExplosion explosion = (S27PacketExplosion) packet;
            localVelocity =
                    explosion.func_149149_c() != 0
                            || explosion.func_149144_d() != 0
                            || explosion.func_149147_e() != 0;
        }
        if (localVelocity && resetOnVelocity.getValue()) {
            resetAndRelease();
            return false;
        }
        if (!isTrackedValid() || !canHoldPackets()) {
            resetAndRelease();
            return false;
        }
        if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (id == trackedPlayer.getEntityId()) {
                    resetAndRelease();
                    break;
                }
            }
            return false;
        }
        Vec3 previous = truePos;
        Vec3 next;
        boolean teleport = packet instanceof S18PacketEntityTeleport;
        if (packet instanceof S14PacketEntity.S15PacketEntityRelMove
                || packet instanceof S14PacketEntity.S17PacketEntityLookMove) {
            S14PacketEntity move = (S14PacketEntity) packet;
            if (move.getEntity(trackedWorld) != trackedPlayer) return false;
            next =
                    previous.addVector(
                            move.func_149062_c() / 32.0D,
                            move.func_149061_d() / 32.0D,
                            move.func_149064_e() / 32.0D);
        } else if (teleport) {
            S18PacketEntityTeleport move = (S18PacketEntityTeleport) packet;
            if (move.getEntityId() != trackedPlayer.getEntityId()) return false;
            next = new Vec3(move.getX() / 32.0D, move.getY() / 32.0D, move.getZ() / 32.0D);
        } else {
            return false;
        }
        processQueue();
        if (!isTrackedValid()) {
            clearAll();
            return false;
        }
        truePos = next;
        double oldDistance = RotationUtil.distanceToBox(buildBox(previous));
        double newDistance = RotationUtil.distanceToBox(buildBox(next));
        boolean movingAway = newDistance > oldDistance + 1.0E-6;
        boolean inRange = newDistance >= minRange.getValue() && newDistance <= maxRange.getValue();
        if (movingAway
                && inRange
                && (!teleport || previous.squareDistanceTo(next) < 64.0)
                && packetQueue.size() < MAX_BUFFERED_PACKETS) {
            backtracking = true;
            packetQueue.addLast(new DelayedMovement(packet, System.nanoTime()));
            return true;
        }
        releaseAll();
        return false;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        runOnClientThread(this::clearAll);
    }

    @Override
    public void verifyValue(String name) {
        if (minRange.getValue() > maxRange.getValue()) {
            if ("Min Range".equals(name)) maxRange.setValue(minRange.getValue());
            else if ("Max Range".equals(name)) minRange.setValue(maxRange.getValue());
        }
        if ("Delay".equals(name) && delay.getValue() == 0) runOnClientThread(this::resetAndRelease);
    }

    private static final class DelayedMovement {
        private final Packet<?> packet;
        private final long receivedAt;

        private DelayedMovement(Packet<?> packet, long receivedAt) {
            this.packet = packet;
            this.receivedAt = receivedAt;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.render.getValue()) return;
        if (!canReplay() || !this.backtracking || this.truePos == null) return;

        float partialTicks = event.getPartialTicks();
        Vec3 from = this.lastTruePos != null ? this.lastTruePos : this.truePos;
        Vec3 smoothed =
                new Vec3(
                        from.xCoord + (this.truePos.xCoord - from.xCoord) * partialTicks,
                        from.yCoord + (this.truePos.yCoord - from.yCoord) * partialTicks,
                        from.zCoord + (this.truePos.zCoord - from.zCoord) * partialTicks);

        AxisAlignedBB box =
                this.buildBox(smoothed)
                        .offset(
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
