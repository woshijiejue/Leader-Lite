package leader.module.modules.player;

import com.google.common.base.CaseFormat;
import leader.Leader;
import leader.enums.BlinkModules;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.PacketEvent;
import leader.events.MoveInputEvent;
import leader.events.TickEvent;
import leader.events.UpdateEvent;
import leader.mixin.IAccessorC03PacketPlayer;
import leader.mixin.IAccessorMinecraft;
import leader.management.RotationState;
import leader.module.Module;
import leader.util.*;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.ModeProperty;
import leader.property.properties.IntProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

public class NoFall extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil packetDelayTimer = new TimerUtil();
    private final TimerUtil scoreboardResetTimer = new TimerUtil();
    private boolean slowFalling = false;
    private boolean lastOnGround = false;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"PACKET", "BLINK", "NO_GROUND", "SPOOF", "MLG"});
    public final FloatProperty distance = new FloatProperty("distance", 3.0F, 0.0F, 20.0F);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 10000);
    public final IntProperty retrieveTick = new IntProperty("RetrieveTick", 0, 0, 20);
    public final ModeProperty distanceMode = new ModeProperty("FallDistanceMode", 0, new String[]{"SafeDistance", "Custom"}, () -> mode.getValue() == 4);
    public final BooleanProperty stopMove = new BooleanProperty("StopMove", false, () -> mode.getValue() == 4);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent", "Strict"}, () -> mode.getValue() == 4);

    private boolean mlgShouldReceive = false;
    private boolean mlgHandleStopMove = false;
    private int mlgOldSlot = -1;
    private int mlgTicksExisted = -1;
    private BlockPos mlgTarget = null;
    private boolean mlgPendingPlace = false;
    private int mlgPlaceDelay = 0;

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
                    case 0:
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
                    case 1:
                        boolean allowed = !mc.thePlayer.isOnLadder() && !mc.thePlayer.capabilities.allowFlying && mc.thePlayer.hurtTime == 0;
                        if (Leader.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
                            if (this.lastOnGround
                                    && !packet.isOnGround()
                                    && allowed
                                    && PlayerUtil.canFly(this.distance.getValue().intValue())
                                    && mc.thePlayer.motionY < 0.0) {
                                Leader.blinkManager.setBlinkState(false, Leader.blinkManager.getBlinkingModule());
                                Leader.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
                            }
                        } else if (!allowed) {
                            Leader.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed player check!&r", Leader.clientName, this.getName()));
                        } else if (PlayerUtil.checkInWater(mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
                            Leader.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed void check!&r", Leader.clientName, this.getName()));
                        } else if (packet.isOnGround()) {
                            for (Packet<?> blinkedPacket : Leader.blinkManager.blinkedPackets) {
                                if (blinkedPacket instanceof C03PacketPlayer) {
                                    ((IAccessorC03PacketPlayer) blinkedPacket).setOnGround(true);
                                }
                            }
                            Leader.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            this.packetDelayTimer.reset();
                        }
                        this.lastOnGround = packet.isOnGround() && allowed && this.canTrigger();
                        break;
                    case 2:
                        ((IAccessorC03PacketPlayer) packet).setOnGround(false);
                        break;
                    case 3:
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
                        break;
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
            if (this.mode.getValue() == 0 && this.slowFalling) {
                PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
                mc.thePlayer.fallDistance = 0.0F;
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 4) return;
        if (event.getType() == EventType.PRE) {
            this.mlgTick(event);
            if (this.mlgPendingPlace) {
                if (this.mlgPlaceDelay > 0) {
                    this.mlgPlaceDelay--;
                } else {
                    this.mlgPlace();
                    this.mlgPendingPlace = false;
                }
            }
        }
    }

    private void mlgTick(UpdateEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.thePlayer.capabilities.isCreativeMode) return;
        if (mc.thePlayer.isUsingItem() || mc.currentScreen != null) return;

        boolean shouldMLG = shouldMLG();

        if (this.mlgTicksExisted >= 0) {
            this.mlgTicksExisted--;
        }

        if (shouldMLG) {
            int waterBucketSlot = findSlot(Items.water_bucket);
            if (waterBucketSlot == -1) {
                shouldMLG = false;
            } else {
                this.mlgTicksExisted = this.retrieveTick.getValue();
                this.mlgHandleStopMove = true;
                this.mlgOldSlot = mc.thePlayer.inventory.currentItem;
                if (mc.thePlayer.inventory.currentItem != waterBucketSlot) {
                    mc.thePlayer.inventory.currentItem = waterBucketSlot;
                    mc.playerController.updateController();
                }
                BlockPos best = findBestPlacePos();
                if (best != null) {
                    float[] rot = RotationUtil.getRotations(best);
                    event.setRotation(rot[0], rot[1], 1);
                    event.setPervRotation(rot[0], 1);
                    this.mlgTarget = best;
                    this.mlgShouldReceive = true;
                    if (!this.mlgPendingPlace) {
                        this.mlgPendingPlace = true;
                        this.mlgPlaceDelay = 1;
                    }
                }
            }
        }
        if (!shouldMLG && hasEmptyBucket() && this.mlgShouldReceive && this.mlgTicksExisted <= 0) {
            int emptyBucketSlot = findSlot(Items.bucket);
            BlockPos pos = findScoopableWaterBlock();
            if (emptyBucketSlot != -1 && pos != null) {
                if (mc.thePlayer.getHeldItem() == null || mc.thePlayer.getHeldItem().getItem() != Items.bucket) {
                    mc.thePlayer.inventory.currentItem = emptyBucketSlot;
                    mc.playerController.updateController();
                }
                float[] rot = RotationUtil.getRotations(pos);
                event.setRotation(rot[0], rot[1], 1);
                event.setPervRotation(rot[0], 1);
                this.mlgTarget = pos;
                this.mlgShouldReceive = false;
                if (!this.mlgPendingPlace) {
                    this.mlgPendingPlace = true;
                    this.mlgPlaceDelay = 1;
                }
                return;
            }
        }

        if (this.mlgHandleStopMove) {
            if (!mc.thePlayer.isInWater() && mc.thePlayer.onGround) {
                this.mlgHandleStopMove = false;
            }
        }
    }

    private void mlgPlace() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;
        if (this.mlgTarget == null) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(held));
        }
        if (this.mlgOldSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.mlgOldSlot;
            mc.playerController.updateController();
            this.mlgOldSlot = -1;
        }
        this.mlgTarget = null;
    }

    private boolean shouldMLG() {
        boolean safeDist = this.distanceMode.getValue() == 0
                ? mc.thePlayer.fallDistance > 3.0F
                : mc.thePlayer.fallDistance > this.distance.getValue();
        return safeDist && !mc.thePlayer.isInWater() && nextTickWillLanding();
    }

    private BlockPos findBestPlacePos() {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        int reach = (int) Math.ceil(mc.playerController.getBlockReachDistance());
        BlockPos playerPos = mc.thePlayer.getPosition();
        for (int dy = 1; dy <= reach; dy++) {
            BlockPos pos = new BlockPos(playerPos.getX(), playerPos.getY() - dy, playerPos.getZ());
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block != null && !block.isAir(mc.theWorld, pos) && !(block instanceof BlockLiquid)) {
                return pos;
            }
        }
        return null;
    }

    private BlockPos findScoopableWaterBlock() {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        int reach = (int) Math.ceil(mc.playerController.getBlockReachDistance());
        BlockPos playerPos = mc.thePlayer.getPosition();
        List<BlockPos> possible = new ArrayList<>();
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block instanceof BlockLiquid
                            && block.getMaterial() == Material.water
                            && mc.theWorld.getBlockState(pos).getValue(BlockLiquid.LEVEL) == 0) {
                        possible.add(pos);
                    }
                }
            }
        }
        if (possible.isEmpty()) return null;
        possible.sort((a, b) -> Double.compare(
                a.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                b.distanceSqToCenter(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)));
        return possible.get(0);
    }

    private boolean nextTickWillLanding() {

        return !isAirBlocksBelow(getYMotion() + 2);
    }

    private boolean isAirBlocksBelow(int high) {
        if (mc.thePlayer == null || mc.theWorld == null) return true;
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ);
        int minY = MathHelper.floor_double(box.minY);
        for (int y = 1; y <= high; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, minY - y, z);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block != null && !block.isAir(mc.theWorld, pos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private int getYMotion() {
        if (mc.thePlayer == null) return 1;
        return Math.max(1, (int) Math.ceil(mc.thePlayer.motionY * mc.thePlayer.motionY));
    }

    private boolean hasEmptyBucket() {
        if (mc.thePlayer == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.bucket) {
                return true;
            }
        }
        return false;
    }

    private int findSlot(Item item) {
        if (mc.thePlayer == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 4) return;
        if (this.mlgHandleStopMove && this.stopMove.getValue()) {
            mc.thePlayer.movementInput.moveForward = 0.0F;
            mc.thePlayer.movementInput.moveStrafe = 0.0F;
            mc.thePlayer.movementInput.jump = false;
        }
        if (this.moveFix.getValue() == 1
                && RotationState.isActived()
                && RotationState.getPriority() == 1.0F
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @Override
    public void onDisabled() {
        this.lastOnGround = false;
        this.mlgShouldReceive = false;
        this.mlgHandleStopMove = false;
        this.mlgOldSlot = -1;
        this.mlgTicksExisted = -1;
        this.mlgTarget = null;
        this.mlgPendingPlace = false;
        this.mlgPlaceDelay = 0;
        Leader.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        if (this.slowFalling) {
            this.slowFalling = false;
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
        }
    }

    @Override
    public void verifyValue(String mode) {
        if (this.isEnabled()) {
            this.onDisabled();
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
