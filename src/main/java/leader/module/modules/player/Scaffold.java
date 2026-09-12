package leader.module.modules.player;

import leader.Leader;
import leader.module.modules.movement.Stuck;
import leader.module.modules.render.FontManager;
import leader.module.modules.render.HUD;
import org.lwjgl.opengl.GL11;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.*;
import leader.management.RotationState;
import leader.module.Module;
import leader.module.modules.movement.LongJump;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375,
            0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875,
            0.78125, 0.84375, 0.90625, 0.96875
    };
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Normal", "Telly", "Snap", "Legit","LegitTelly"});
    public final ModeProperty rotationMode = new ModeProperty("Rotate Mode", 3, new String[]{"None", "Vanilla", "Backwards", "Prediction", "Strict", "GodBridge"});
    public final BooleanProperty noUpdateWhenCanPlace = new BooleanProperty("Do Not Update Rotation When Can Place", false, () -> rotationMode.getValue() == 5);
    public final BooleanProperty edgeLimit = new BooleanProperty("Edge Limit", false, () -> rotationMode.getValue() == 5);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent"});
    public final IntProperty jumpDelay = new IntProperty("Jump Delay", 2, 0, 5, () -> mode.getValue() == 1 || mode.getValue() == 4);
    public final IntProperty placeDelay = new IntProperty("Place Delay", 1, 0, 5);
    public final FloatProperty startRotSpeed = new FloatProperty("Start Rotate Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 1);
    public final FloatProperty normalRotSpeed = new FloatProperty("Normal Rotate Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 1);
    public final FloatProperty normalModeSpeed = new FloatProperty("Normal Mode Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 0);
    public final FloatProperty legitModeSpeed = new FloatProperty("Legit Mode Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 3);
    public final BooleanProperty swing = new BooleanProperty("Swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("Item Spoof", false);
    public final BooleanProperty clutch = new BooleanProperty("Clutch", true);
    public final BooleanProperty onlyInVoid = new BooleanProperty("Only Void", false, this.clutch::getValue);
    public final BooleanProperty bPSRender = new BooleanProperty("Render BPS", true);
    public final BooleanProperty blockCounter = new BooleanProperty("Block Counter", false);
    public final BooleanProperty airRescue = new BooleanProperty("Air Rescue", true);
    public final FloatProperty edgeThreshold = new FloatProperty("Edge Threshold", 0.15F, 0.01F, 0.5F, () -> mode.getValue() == 2);
    public final BooleanProperty ticksLimit = new BooleanProperty("Ticks Limit", false, () -> mode.getValue() == 2);
    public final IntProperty limitTicks = new IntProperty("Limit Ticks", 10, 1, 40, () -> mode.getValue() == 2 && ticksLimit.getValue());
    public final FloatProperty snapForwardSpeed = new FloatProperty("Forward Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 2);
    public final FloatProperty snapBackSpeed = new FloatProperty("Back Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 2);
    public final BooleanProperty snapRotation = new BooleanProperty("Snap Rotation", false, () -> mode.getValue() == 2);
    public final BooleanProperty speedLimit = new BooleanProperty("Speed Limit", false, () -> mode.getValue() == 1);
    public final IntProperty speedLimitTicks = new IntProperty("Speed Limit Ticks", 3, 0, 5, () -> mode.getValue() == 1 && speedLimit.getValue());
    public final IntProperty forwardRotationTicks = new IntProperty("Forward Rotation Ticks", 1, 1, 5, () -> mode.getValue() == 1 && speedLimit.getValue());
    public final IntProperty legitSneakDelay = new IntProperty("Legit Sneak Delay", 4, 1, 5, () -> mode.getValue() == 3);
    public final FloatProperty forwardSpeed = new FloatProperty("ForwardSpeed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 4);
    public final FloatProperty backSpeed = new FloatProperty("BackSpeed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 4);
    public final FloatProperty placeSpeed = new FloatProperty("PlaceSpeed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 4);
    public final IntProperty tellyTicks = new IntProperty("TellyTicks", 3, 1, 6, () -> mode.getValue() == 4);

    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int tellyJumpDelayTimer = 0;
    private int jumpDelayOverride = -1;
    private boolean wasInAir = false;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private boolean clutchActive = false;
    private int clutchTickCounter = 0;
    private EnumFacing targetFacing = null;
    public static int count = 0;
    private int placeDelayCounter = 0;
    private double prevBpsX, prevBpsZ;
    private float currentBps;
    private boolean snapForward = true;
    private int snapForwardTimer = 0;
    private boolean snapLocked = false;
    private int airTicks = 0;
    private boolean pendingSpeedLimitRot = false;
    private int forwardRotateTicksLeft = 0;
    private int legitEdgeState = 0;
    private int legitEdgeTimer = 0;
    private boolean legitWasOnEdge = false;
    private int legitTellyPhase = 0;
    private int legitTellyPhaseTicks = 0;
    private boolean legitTellyWasAirborne = false;
    private boolean legitTellyPlacedFirstBlock = false;
    private float legitTellySilentYaw;
    private float legitTellySilentPitch;
    private BlockData legitTellyLockedBlockData;

    public Scaffold() {
        super("Scaffold", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    private boolean shouldStopSprint() {
        return !this.isTowering() && this.stage <= 0 && this.mode.getValue() != 2;
    }

    private boolean canPlace() {
        BedNuker bedNuker = (BedNuker) Leader.moduleManager.modules.get(BedNuker.class);
        if (bedNuker.isEnabled() && bedNuker.isReady()) return false;
        LongJump longJump = (LongJump) Leader.moduleManager.modules.get(LongJump.class);
        return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private Vec3 applyRescueRotation(BlockData blockData, UpdateEvent event) {
        double[] fx = {0.1, 0.3, 0.5, 0.7, 0.9};
        double[] fy = {0.1, 0.3, 0.5, 0.7, 0.9};
        double[] fz = {0.1, 0.3, 0.5, 0.7, 0.9};
        switch (blockData.facing()) {
            case NORTH: fz = new double[]{0.02}; break;
            case EAST: fx = new double[]{0.98}; break;
            case SOUTH: fz = new double[]{0.98}; break;
            case WEST: fx = new double[]{0.02}; break;
            case DOWN: fy = new double[]{0.02}; break;
            case UP: fy = new double[]{0.98}; break;
        }
        float bestYaw = -180.0F, bestPitch = 0.0F;
        double bestDist = Double.MAX_VALUE;
        Vec3 bestHit = null;
        for (double dx : fx) {
            for (double dy : fy) {
                for (double dz : fz) {
                    double tx = blockData.blockPos().getX() + dx;
                    double ty = blockData.blockPos().getY() + dy;
                    double tz = blockData.blockPos().getZ() + dz;
                    float[] rot = RotationUtil.getRotations(tx, ty, tz);
                    MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance(), 1.0F);
                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                            && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - event.getYaw()));
                        double dist = yawDiff * yawDiff + (rot[1] - event.getPitch()) * (rot[1] - event.getPitch());
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestYaw = rot[0];
                            bestPitch = rot[1];
                            bestHit = mop.hitVec;
                        }
                    }
                }
            }
        }
        if (bestHit == null) return null;
        this.yaw = bestYaw;
        this.pitch = bestPitch;
        this.canRotate = true;
        return bestHit;
    }

    private BlockData getBlockData() {
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(playerY, this.startY) : playerY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        return this.getBlockData(targetPos);
    }

    private BlockData getBlockData(BlockPos targetPos) {
        if (!BlockUtil.isReplaceable(targetPos)) return null;
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!BlockUtil.isReplaceable(pos) && !BlockUtil.isInteractable(pos)
                            && mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5) <= (double) mc.playerController.getBlockReachDistance()) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (facing != EnumFacing.DOWN && BlockUtil.isReplaceable(pos.offset(facing))) {
                                positions.add(pos);
                            }
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) return null;
        positions.sort(Comparator.comparingDouble(o -> o.distanceSqToCenter((double) targetPos.getX() + 0.5, (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)));
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = this.getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private boolean place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (!ItemUtil.isHoldingBlock() || this.blockCount <= 0) return false;
        if (!mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
            return false;
        }
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) this.blockCount--;
        if (this.swing.getValue()) mc.thePlayer.swingItem();
        else PacketUtil.sendPacket(new C0APacketAnimation());
        return true;
    }

    private void selectScaffoldBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        int heldCount = ItemUtil.isBlock(held) ? held.stackSize : 0;
        this.blockCount = Math.min(this.blockCount, heldCount);
        if (this.blockCount > 0) return;

        int slot = mc.thePlayer.inventory.currentItem;
        if (this.blockCount == 0) slot--;
        for (int i = slot; i > slot - 9; i--) {
            int hotbarSlot = (i % 9 + 9) % 9;
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
            if (ItemUtil.isBlock(candidate)) {
                mc.thePlayer.inventory.currentItem = hotbarSlot;
                this.blockCount = candidate.stackSize;
                return;
            }
        }
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(mc.thePlayer.rotationYaw, (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue());
    }

    private boolean isLegitTellyMode() {
        return this.mode.getValue() == 4;
    }

    private float getLegitTellyRotationStep(float speed) {
        return Math.max(1.0F, Math.min(180.0F, speed));
    }

    private float[] smoothLegitTellyRotation(float fromYaw, float fromPitch, float targetYaw, float targetPitch, float speed) {
        float yawStep = this.getLegitTellyRotationStep(speed);
        float pitchStep = Math.max(1.0F, yawStep * 0.55F);
        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - fromYaw);
        float pitchDelta = targetPitch - fromPitch;
        float nextYaw = fromYaw + RotationUtil.clampAngle(yawDelta, yawStep);
        float nextPitch = fromPitch + RotationUtil.clampAngle(pitchDelta, pitchStep);
        return new float[]{RotationUtil.quantizeAngle(nextYaw), RotationUtil.quantizeAngle(MathHelper.clamp_float(nextPitch, -90.0F, 90.0F))};
    }

    private void resetLegitTellyCycle() {
        this.legitTellyPhase = 0;
        this.legitTellyPhaseTicks = 0;
        this.legitTellyPlacedFirstBlock = false;
        this.legitTellyLockedBlockData = null;
    }

    private void updateLegitTelly(UpdateEvent event) {
        if (this.placeDelayCounter > 0) this.placeDelayCounter--;

        boolean onGround = mc.thePlayer.onGround;

        if (onGround && this.legitTellyWasAirborne) this.resetLegitTellyCycle();
        if (!onGround && !this.legitTellyWasAirborne && this.legitTellyPhase == 0) {
            this.legitTellyPhase = 1;
            this.legitTellyPhaseTicks = 0;
        }

        if (this.legitTellyPhase == 1) {
            this.legitTellyPhaseTicks++;
            if (this.legitTellyPhaseTicks >= this.tellyTicks.getValue()) {
                this.legitTellyPhase = 2;
                this.legitTellyPhaseTicks = 0;
            }
        }

        float speed = this.forwardSpeed.getValue();
        if (this.legitTellyPhase == 2) speed = this.backSpeed.getValue();
        else if (this.legitTellyPhase == 3) speed = this.placeSpeed.getValue();

        float targetYaw = mc.thePlayer.rotationYaw;
        float targetPitch = mc.thePlayer.rotationPitch;

        if (this.legitTellyPhase == 1) {
            targetYaw = this.getCurrentYaw();
            targetPitch = 0.0F;
        }

        if (this.legitTellyPhase >= 2) {
            if (this.legitTellyLockedBlockData != null
                    && BlockUtil.isReplaceable(this.legitTellyLockedBlockData.blockPos().offset(this.legitTellyLockedBlockData.facing()))) {
            } else {
                this.legitTellyLockedBlockData = null;
            }
            if (this.legitTellyLockedBlockData == null) {
                BlockPos targetPos = new BlockPos(
                        MathHelper.floor_double(mc.thePlayer.posX),
                        this.startY,
                        MathHelper.floor_double(mc.thePlayer.posZ)
                );
                this.legitTellyLockedBlockData = this.getBlockData(targetPos);
            }

            if (this.legitTellyLockedBlockData != null) {
                BlockData bd = this.legitTellyLockedBlockData;
                double[] offsets = {0.15, 0.35, 0.5, 0.65, 0.85};
                double[] xOff = offsets, yOff = offsets, zOff = offsets;
                switch (bd.facing()) {
                    case NORTH: zOff = new double[]{0.02}; break;
                    case EAST: xOff = new double[]{0.98}; break;
                    case SOUTH: zOff = new double[]{0.98}; break;
                    case WEST: xOff = new double[]{0.02}; break;
                    case DOWN: yOff = new double[]{0.02}; break;
                    case UP: yOff = new double[]{0.98}; break;
                }
                double bestDist = Double.MAX_VALUE;
                for (double dx : xOff) {
                    for (double dy : yOff) {
                        for (double dz : zOff) {
                            float[] rot = RotationUtil.getRotations(
                                    bd.blockPos().getX() + dx,
                                    bd.blockPos().getY() + dy,
                                    bd.blockPos().getZ() + dz);
                            MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1],
                                    mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK
                                    || !mop.getBlockPos().equals(bd.blockPos())
                                    || mop.sideHit != bd.facing()) continue;
                            double dist = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.legitTellySilentYaw))
                                    + Math.abs(rot[1] - this.legitTellySilentPitch);
                            if (dist < bestDist) {
                                bestDist = dist;
                                targetYaw = rot[0];
                                targetPitch = rot[1];
                            }
                        }
                    }
                }
            }
        }

        float[] smoothed = this.smoothLegitTellyRotation(
                this.legitTellySilentYaw, this.legitTellySilentPitch, targetYaw, targetPitch, speed
        );
        this.legitTellySilentYaw = smoothed[0];
        this.legitTellySilentPitch = smoothed[1];
        this.yaw = smoothed[0];
        this.pitch = smoothed[1];
        this.canRotate = true;

        event.setRotation(this.yaw, this.pitch, 3);
        if (this.moveFix.getValue() == 1) event.setPervRotation(this.yaw, 3);

        if (this.legitTellyPhase >= 2 && this.legitTellyLockedBlockData != null && this.placeDelayCounter <= 0) {
            MovingObjectPosition trace = RotationUtil.rayTrace(
                    this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F
            );
            if (trace != null && trace.typeOfHit == MovingObjectType.BLOCK
                    && trace.getBlockPos().equals(this.legitTellyLockedBlockData.blockPos())
                    && trace.sideHit == this.legitTellyLockedBlockData.facing()) {
                if (this.place(this.legitTellyLockedBlockData.blockPos(), this.legitTellyLockedBlockData.facing(), trace.hitVec)) {
                    this.placeDelayCounter = this.placeDelay.getValue();
                    if (this.legitTellyPhase == 2) {
                        this.legitTellyPlacedFirstBlock = true;
                        this.legitTellyPhase = 3;
                    }
                    this.legitTellyLockedBlockData = null;
                }
            }
        }

        this.legitTellyWasAirborne = !onGround;
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (!MoveUtil.isForwardPressed()) return false;
        if (PlayerUtil.isAirAbove()) return false;
        if (mc.thePlayer.onGround) {
            if (this.stage > 0 || mc.gameSettings.keyBindJump.isKeyDown()) return true;
        }
        return this.tellyJumpDelayTimer > 0;
    }

    private boolean isOnEdge() {
        if (!mc.thePlayer.onGround) return true;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        if (BlockUtil.isReplaceable(below)) return true;
        double threshold = edgeThreshold.getValue();
        double xOff = mc.thePlayer.posX - MathHelper.floor_double(mc.thePlayer.posX);
        double zOff = mc.thePlayer.posZ - MathHelper.floor_double(mc.thePlayer.posZ);
        if (xOff < threshold || xOff > 1.0 - threshold || zOff < threshold || zOff > 1.0 - threshold) {
            int checkX = MathHelper.floor_double(mc.thePlayer.posX) + (xOff < threshold ? -1 : (xOff > 1.0 - threshold ? 1 : 0));
            int checkZ = MathHelper.floor_double(mc.thePlayer.posZ) + (zOff < threshold ? -1 : (zOff > 1.0 - threshold ? 1 : 0));
            if (checkX != MathHelper.floor_double(mc.thePlayer.posX) || checkZ != MathHelper.floor_double(mc.thePlayer.posZ)) {
                BlockPos adjacentBelow = new BlockPos(checkX, MathHelper.floor_double(mc.thePlayer.posY) - 1, checkZ);
                if (BlockUtil.isReplaceable(adjacentBelow)) return true;
            }
        }
        return false;
    }

    private boolean isGodBridgeOnEdge() {
        if (!mc.thePlayer.onGround) return true;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        if (BlockUtil.isReplaceable(below)) return true;
        double xOff = mc.thePlayer.posX - MathHelper.floor_double(mc.thePlayer.posX);
        double zOff = mc.thePlayer.posZ - MathHelper.floor_double(mc.thePlayer.posZ);
        if (xOff < 0.15 || xOff > 0.85 || zOff < 0.15 || zOff > 0.85) {
            int checkX = MathHelper.floor_double(mc.thePlayer.posX) + (xOff < 0.15 ? -1 : (xOff > 0.85 ? 1 : 0));
            int checkZ = MathHelper.floor_double(mc.thePlayer.posZ) + (zOff < 0.15 ? -1 : (zOff > 0.85 ? 1 : 0));
            if (checkX != MathHelper.floor_double(mc.thePlayer.posX) || checkZ != MathHelper.floor_double(mc.thePlayer.posZ)) {
                BlockPos adjacentBelow = new BlockPos(checkX, MathHelper.floor_double(mc.thePlayer.posY) - 1, checkZ);
                if (BlockUtil.isReplaceable(adjacentBelow)) return true;
            }
        }
        return false;
    }

    private float quantizeDiagonal(float yaw) {
        float diag = 45.0F + 90.0F * Math.round((yaw - 45.0F) / 90.0F);
        return ((diag % 360.0F) + 360.0F) % 360.0F;
    }

    private void updateClutch() {
        if (!this.clutch.getValue()) { if (this.clutchActive) this.clutchReset(); return; }
        if (mc.thePlayer.onGround) { if (this.clutchActive) this.clutchReset(); return; }
        if (this.bbUnC()) { if (this.clutchActive) this.clutchReset(); return; }
        double fallDistance = mc.thePlayer.fallDistance;
        boolean shouldClutch = fallDistance > 2 && !PlayerUtil.isAirAbove() && !mc.thePlayer.isCollidedHorizontally && (!this.onlyInVoid.getValue() || this.isFallingIntoVoid());
        if (shouldClutch && !this.clutchActive) { this.clutchActive = true; this.clutchTickCounter = 0; }
        if (this.clutchActive) {
            this.clutchTickCounter++;
            Leader.moduleManager.getModule(Stuck.class).setEnabled(this.clutchTickCounter % 10 != 0);
        }
    }

    private void clutchReset() {
        if (this.clutchActive) Leader.moduleManager.getModule(Stuck.class).setEnabled(false);
        this.clutchActive = false; this.clutchTickCounter = 0;
    }

    private boolean isFallingIntoVoid() {
        if (mc.thePlayer == null) return false;
        for (int i = 0; i <= 128; i++) {
            BlockPos checkPos = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY) - i, MathHelper.floor_double(mc.thePlayer.posZ));
            if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isSolid()) return false;
        }
        return true;
    }

    private boolean bbUnC() {
        if (mc.thePlayer == null) return false;
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        for (int i = 1; i <= 2; i++) {
            BlockPos checkPos = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), playerY - i, MathHelper.floor_double(mc.thePlayer.posZ));
            if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isSolid()) return true;
        }
        return false;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            boolean tellyMode = this.mode.getValue() == 1;
            boolean legitTellyMode = this.isLegitTellyMode();
            boolean tellyLikeMode = tellyMode || legitTellyMode;
            boolean legitMode = this.mode.getValue() == 3;

            if (this.rotationTick > 0) this.rotationTick--;
            if (this.forwardRotateTicksLeft > 0) this.forwardRotateTicksLeft--;
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) this.stage--;
                if (this.stage < 0) this.stage++;
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
                if (this.wasInAir) {
                    this.tellyJumpDelayTimer = tellyLikeMode ? (this.jumpDelayOverride >= 0 ? this.jumpDelayOverride : this.jumpDelay.getValue()) : 0;
                    this.wasInAir = false;
                }
                if (this.tellyJumpDelayTimer > 0) this.tellyJumpDelayTimer--;
                if (speedLimit.getValue()) { pendingSpeedLimitRot = false; airTicks = 0; }
            } else {
                if (speedLimit.getValue()) airTicks++;
                this.wasInAir = true;
            }
            if (tellyLikeMode && mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.gameSettings.keyBindJump.isKeyDown() && this.stage == 0) this.stage = 1;
            if (tellyLikeMode) this.jumpDelayOverride = mc.gameSettings.keyBindJump.isKeyDown() ? 2 : -1;
            else { this.jumpDelayOverride = -1; this.tellyJumpDelayTimer = 0; }
            this.updateClutch();

            if (legitTellyMode) {
                this.selectScaffoldBlock();
                this.updateLegitTelly(event);
                return;
            }

            if (this.mode.getValue() == 2) {
                if (ticksLimit.getValue()) {
                    boolean canForward = mc.thePlayer.onGround && !this.isOnEdge();
                    if (!canForward) { snapForward = false; snapForwardTimer = 0; snapLocked = false; }
                    else {
                        if (snapLocked) snapForward = false;
                        else {
                            if (!snapForward) { snapForward = true; snapForwardTimer = 1; }
                            else { snapForwardTimer++; if (snapForwardTimer >= limitTicks.getValue()) { snapForward = false; snapLocked = true; snapForwardTimer = 0; } }
                        }
                    }
                } else snapForward = mc.thePlayer.onGround && !this.isOnEdge();
            }

            if (legitMode) {
                boolean onGround = mc.thePlayer.onGround;
                boolean atEdge = onGround && this.isOnEdge();
                boolean holdingBlock = ItemUtil.isHoldingBlock();
                boolean justReachedEdge = atEdge && !this.legitWasOnEdge;
                if (!onGround) { this.legitEdgeState = 0; this.legitEdgeTimer = 0; }
                else if (atEdge && holdingBlock) {
                    switch (this.legitEdgeState) {
                        case 0: if (justReachedEdge || this.legitEdgeTimer == 0) { this.legitEdgeState = 1; this.legitEdgeTimer = this.legitSneakDelay.getValue(); } break;
                        case 1: this.legitEdgeTimer--; if (this.legitEdgeTimer <= 0) { this.legitEdgeState = 2; this.legitEdgeTimer = 0; } break;
                        case 2: break;
                        default: this.legitEdgeState = 0; this.legitEdgeTimer = 0; break;
                    }
                } else { this.legitEdgeState = 0; this.legitEdgeTimer = 0; }
                this.legitWasOnEdge = atEdge;
            }

            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, count);
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) slot--;
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemUtil.isBlock(candidate)) {
                            mc.thePlayer.inventory.currentItem = hotbarSlot;
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }

                if (this.mode.getValue() == 2) {
                    if (snapForward) { this.yaw = RotationUtil.quantizeAngle(getCurrentYaw()); this.pitch = 80.0F; this.canRotate = true; }
                    else if (!snapRotation.getValue()) { this.yaw = RotationUtil.quantizeAngle(getCurrentYaw() + 180.0F); this.pitch = 85.0F; this.canRotate = true; }
                }

                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                float diagonalYaw = this.isDiagonal(currentYaw) ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());

                if (!this.canRotate) {
                    switch (this.rotationMode.getValue()) {
                        case 1: this.yaw = this.yaw == -180.0F && this.pitch == 0.0F ? RotationUtil.quantizeAngle(diagonalYaw) : RotationUtil.quantizeAngle(diagonalYaw); break;
                        case 2: if (this.yaw == -180.0F && this.pitch == 0.0F) { this.yaw = RotationUtil.quantizeAngle(yawDiffTo180); this.pitch = RotationUtil.quantizeAngle(85.0F); } else this.yaw = RotationUtil.quantizeAngle(yawDiffTo180); break;
                        case 3: if (this.yaw == -180.0F && this.pitch == 0.0F) { this.yaw = RotationUtil.quantizeAngle(diagonalYaw); this.pitch = RotationUtil.quantizeAngle(85.0F); } break;
                        case 5: if (this.yaw == -180.0F && this.pitch == 0.0F) { this.yaw = RotationUtil.quantizeAngle(this.quantizeDiagonal(currentYaw + 180.0F)); this.pitch = RotationUtil.quantizeAngle(85.0F); } break;
                    }
                }

                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                if (this.mode.getValue() == 2 && snapForward) blockData = null;

                if (blockData != null) {
                    if (this.rotationMode.getValue() == 4) {
                        double centerX = blockData.blockPos().getX() + 0.5 + blockData.facing().getDirectionVec().getX() * 0.5;
                        double centerY = blockData.blockPos().getY() + 0.5 + blockData.facing().getDirectionVec().getY() * 0.5;
                        double centerZ = blockData.blockPos().getZ() + 0.5 + blockData.facing().getDirectionVec().getZ() * 0.5;
                        float[] strictRot = RotationUtil.getRotations(centerX, centerY, centerZ);
                        MovingObjectPosition strictMop = RotationUtil.rayTrace(strictRot[0], strictRot[1], mc.playerController.getBlockReachDistance(), 1.0F);
                        if (strictMop != null && strictMop.typeOfHit == MovingObjectType.BLOCK
                                && strictMop.getBlockPos().equals(blockData.blockPos()) && strictMop.sideHit == blockData.facing()) {
                            this.yaw = strictRot[0];
                            this.pitch = strictRot[1];
                            this.canRotate = true;
                            hitVec = strictMop.hitVec;
                        }
                    } else if (this.rotationMode.getValue() == 5) {
                        float diagYaw = this.quantizeDiagonal(this.getCurrentYaw() + 180.0F);
                        double centerX = blockData.blockPos().getX() + 0.5 + blockData.facing().getDirectionVec().getX() * 0.5;
                        double centerY = blockData.blockPos().getY() + 0.5 + blockData.facing().getDirectionVec().getY() * 0.5;
                        double centerZ = blockData.blockPos().getZ() + 0.5 + blockData.facing().getDirectionVec().getZ() * 0.5;
                        float[] centerRot = RotationUtil.getRotations(centerX, centerY, centerZ);
                        float centerPitch = Math.max(-89.0F, Math.min(89.0F, centerRot[1]));
                        float bestPitch = Float.NaN;
                        double bestScore = Double.MAX_VALUE;
                        Vec3 bestHitVec = null;
                        for (float p = centerPitch + 30.0F; p >= centerPitch - 30.0F; p -= 0.5F) {
                            if (p > 89.0F || p < -89.0F) continue;
                            MovingObjectPosition mop = RotationUtil.rayTrace(diagYaw, p, mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                double score = Math.abs(p - centerPitch);
                                if (score < bestScore) { bestScore = score; bestPitch = p; bestHitVec = mop.hitVec; }
                            }
                        }
                        if (bestHitVec != null) {
                            hitVec = bestHitVec;
                            this.canRotate = true;
                            boolean updateRotation = true;
                            if (this.noUpdateWhenCanPlace.getValue()) {
                                MovingObjectPosition curMop = RotationUtil.rayTrace(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.playerController.getBlockReachDistance(), 1.0F);
                                if (curMop != null && curMop.typeOfHit == MovingObjectType.BLOCK
                                        && curMop.getBlockPos().equals(blockData.blockPos()) && curMop.sideHit == blockData.facing()) {
                                    updateRotation = false;
                                }
                            }
                            if (this.edgeLimit.getValue() && !this.isGodBridgeOnEdge()) {
                                updateRotation = false;
                            }
                            if (updateRotation) {
                                float yawDiff = MathHelper.wrapAngleTo180_float(diagYaw - mc.thePlayer.rotationYaw);
                                this.yaw = Math.abs(yawDiff) <= 5.0F ? mc.thePlayer.rotationYaw : diagYaw;
                                this.pitch = bestPitch;
                            } else {
                                this.yaw = mc.thePlayer.rotationYaw;
                                this.pitch = mc.thePlayer.rotationPitch;
                            }
                        } else if (!mc.thePlayer.onGround && this.airRescue.getValue()) {
                            hitVec = this.applyRescueRotation(blockData, event);
                        }
                    } else if (this.rotationMode.getValue() == 3) {
                        double[] offsets = {0.1, 0.3, 0.5, 0.7, 0.9};
                        double[] x = offsets, y = offsets, z = offsets;
                        switch (blockData.facing()) {
                            case NORTH: z = new double[]{0.02}; break;
                            case EAST: x = new double[]{0.98}; break;
                            case SOUTH: z = new double[]{0.98}; break;
                            case WEST: x = new double[]{0.02}; break;
                            case DOWN: y = new double[]{0.02}; break;
                            case UP: y = new double[]{0.98}; break;
                        }
                        float bestYaw = -180.0F, bestPitch = 0.0F;
                        double bestDist = Double.MAX_VALUE;
                        Vec3 bestHitVec = null;
                        for (double dx : x) {
                            for (double dy : y) {
                                for (double dz : z) {
                                    double targetX = blockData.blockPos().getX() + dx;
                                    double targetY = blockData.blockPos().getY() + dy;
                                    double targetZ = blockData.blockPos().getZ() + dz;
                                    float[] rot = RotationUtil.getRotations(targetX, targetY, targetZ);
                                    MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                                            && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.yaw));
                                        float pitchDiff = Math.abs(rot[1] - this.pitch);
                                        double dist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                                        if (dist < bestDist) { bestDist = dist; bestYaw = rot[0]; bestPitch = rot[1]; bestHitVec = mop.hitVec; }
                                    }
                                }
                            }
                        }
                        if (bestYaw != -180.0F || bestPitch != 0.0F) {
                            bestYaw += RandomUtil.nextFloat(-0.5F, 0.5F);
                            bestPitch += RandomUtil.nextFloat(-0.3F, 0.3F);
                            this.yaw = bestYaw; this.pitch = bestPitch; this.canRotate = true; hitVec = bestHitVec;
                        }
                    } else {
                        double[] x = placeOffsets, y = placeOffsets, z = placeOffsets;
                        switch (blockData.facing()) {
                            case NORTH: z = new double[]{0.0}; break;
                            case EAST: x = new double[]{1.0}; break;
                            case SOUTH: z = new double[]{1.0}; break;
                            case WEST: x = new double[]{0.0}; break;
                            case DOWN: y = new double[]{0.0}; break;
                            case UP: y = new double[]{1.0}; break;
                        }
                        float bestYaw = -180.0F, bestPitch = 0.0F, bestDiff = 0.0F;
                        for (double dx : x) {
                            for (double dy : y) {
                                for (double dz : z) {
                                    double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                    double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                    double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                    float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                                    float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                    MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                                            && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                        float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                        if (bestYaw == -180.0F || totalDiff < bestDiff) { bestYaw = rotations[0]; bestPitch = rotations[1]; bestDiff = totalDiff; hitVec = mop.hitVec; }
                                    }
                                }
                            }
                        }
                        if (bestYaw != -180.0F || bestPitch != 0.0F) { this.yaw = bestYaw; this.pitch = bestPitch; this.canRotate = true; }
                    }
                }

                if (blockData != null && hitVec == null && !mc.thePlayer.onGround && this.airRescue.getValue()) {
                    hitVec = this.applyRescueRotation(blockData, event);
                }

                if (this.canRotate && MoveUtil.isForwardPressed() && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    if (this.rotationMode.getValue() == 2) this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                }

                if (!legitMode && this.rotationMode.getValue() != 0 && this.mode.getValue() != 2) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
                    if (!tellyMode) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                        float tolerance = this.normalModeSpeed.getValue();
                        if (Math.abs(yawDiff) > tolerance) {
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + RotationUtil.clampAngle(yawDiff, tolerance));
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    } else {
                        if (tellyMode && speedLimit.getValue() && forwardRotateTicksLeft > 0) {
                            float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                            this.yaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                            this.pitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                            this.rotationTick = 0;
                        } else if (this.towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
                            float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
                            float tolerance = this.rotationTick >= 2 ? startRotSpeed.getValue() : normalRotSpeed.getValue();
                            if (Math.abs(yawDiff) > tolerance) {
                                float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                                targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                                this.rotationTick = Math.max(this.rotationTick, 1);
                            }
                        }
                        if (tellyMode && this.isTowering() && this.tellyJumpDelayTimer <= 0 && forwardRotateTicksLeft <= 0) {
                            if (!speedLimit.getValue()) {
                                float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                                targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                                targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                                this.rotationTick = 3; this.towering = true;
                            } else {
                                pendingSpeedLimitRot = true; airTicks = 0;
                            }
                        } else if (tellyMode && this.tellyJumpDelayTimer > 0) {
                            targetYaw = this.yaw != -180.0F ? this.yaw : RotationUtil.quantizeAngle(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw()) + event.getYaw());
                            targetPitch = Math.abs(this.pitch) > 10 ? this.pitch : 60.0F;
                        }
                        if (speedLimit.getValue() && pendingSpeedLimitRot && !mc.thePlayer.onGround && airTicks >= speedLimitTicks.getValue()) {
                            float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                            this.yaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                            this.pitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                            this.forwardRotateTicksLeft = forwardRotationTicks.getValue(); this.rotationTick = 0; this.towering = true;
                            pendingSpeedLimitRot = false; airTicks = 0;
                        }
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) event.setPervRotation(targetYaw, 3);
                } else if (this.mode.getValue() == 2 && this.rotationMode.getValue() != 0 && this.canRotate) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                    float tolerance = snapForward ? snapForwardSpeed.getValue() : snapBackSpeed.getValue();
                    if (Math.abs(yawDiff) > tolerance) { float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance); targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw); this.rotationTick = Math.max(this.rotationTick, 1); }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) event.setPervRotation(targetYaw, 3);
                } else if (legitMode && this.rotationMode.getValue() != 0 && this.canRotate) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                    float tolerance = this.legitModeSpeed.getValue();
                    if (Math.abs(yawDiff) > tolerance) { float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance); targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw); this.rotationTick = Math.max(this.rotationTick, 1); }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) event.setPervRotation(targetYaw, 3);
                }

                boolean legitCanPlace = !legitMode || !mc.thePlayer.onGround || this.legitEdgeState == 0 || this.legitEdgeState == 2;

                if (blockData != null && hitVec != null && this.rotationTick <= 0 && legitCanPlace) {
                    if (this.placeDelayCounter > 0) {
                        this.placeDelayCounter--;
                    } else {
                        MovingObjectPosition finalCheck = RotationUtil.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
                        if (finalCheck != null && finalCheck.typeOfHit == MovingObjectType.BLOCK
                                && finalCheck.getBlockPos().equals(blockData.blockPos()) && finalCheck.sideHit == blockData.facing()) {
                            this.place(blockData.blockPos(), blockData.facing(), finalCheck.hitVec);
                            this.placeDelayCounter = this.placeDelay.getValue();
                        } else if (this.canRotate) {
                            this.place(blockData.blockPos(), blockData.facing(), hitVec);
                            this.placeDelayCounter = this.placeDelay.getValue();
                        }
                    }
                }

                if (this.targetFacing != null && this.rotationTick <= 0) {
                    int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                    int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                    int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                    BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                    hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                    this.place(belowPlayer, this.targetFacing, hitVec);
                    this.targetFacing = null;
                }
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && this.clutchActive && this.clutchTickCounter % 10 != 0) {
            event.setForward(0.0F); event.setStrafe(0.0F);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.clutchActive && this.clutchTickCounter % 10 != 0) {
                mc.thePlayer.movementInput.moveForward = 0.0F;
                mc.thePlayer.movementInput.moveStrafe = 0.0F;
                mc.thePlayer.movementInput.jump = false;
                mc.thePlayer.movementInput.sneak = false;
                return;
            }
            if (this.moveFix.getValue() == 1 && RotationState.isActived() && RotationState.getPriority() == 3.0F && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (this.mode.getValue() == 1 && mc.thePlayer.onGround
                    && this.stage > 0 && MoveUtil.isForwardPressed() && this.tellyJumpDelayTimer <= 0) {
                mc.thePlayer.movementInput.jump = true;
            }
            if (this.isLegitTellyMode() && mc.thePlayer.onGround
                    && MoveUtil.isForwardPressed()) {
                mc.thePlayer.movementInput.jump = true;
            }
            if (this.mode.getValue() == 3 && mc.currentScreen == null && !this.clutchActive) {
                if (mc.thePlayer.onGround && (this.legitEdgeState == 1 || this.legitEdgeState == 2)) {
                    mc.thePlayer.movementInput.sneak = true;
                    mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                    mc.thePlayer.movementInput.moveForward *= 0.3F;
                    
                }
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            double dx = mc.thePlayer.posX - prevBpsX;
            double dz = mc.thePlayer.posZ - prevBpsZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            this.currentBps = (float) (dist * 20.0);
            this.prevBpsX = mc.thePlayer.posX;
            this.prevBpsZ = mc.thePlayer.posZ;
            if (this.clutchActive && this.clutchTickCounter % 10 != 0) {
                mc.thePlayer.motionX = 0.0; mc.thePlayer.motionY = 0.0; mc.thePlayer.motionZ = 0.0;
                return;
            }
            if (this.shouldStopSprint()) mc.thePlayer.setSprinting(false);
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) return;
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                Item item = stack.getItem();
                if (item instanceof ItemBlock) {
                    Block block = ((ItemBlock) item).getBlock();
                    if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) count += stack.stackSize;
                }
            }
        }
        Scaffold.count = count;

        if (bPSRender.getValue()) {
            ScaledResolution sr = new ScaledResolution(mc);
            int barWidth = 100, barHeight = 4;
            int barX = sr.getScaledWidth() / 2 - barWidth / 2;
            int barY = (int) (sr.getScaledHeight() / 2f);
            float maxDisplayBps = 10.0F;
            float fillWidth = Math.min(barWidth, (currentBps / maxDisplayBps) * barWidth);
            GlStateManager.pushMatrix();
            RenderUtil.enableRenderState();
            RenderUtil.drawRect(barX, barY, barX + barWidth, barY + barHeight, new Color(0, 0, 0, 120).getRGB());
            int fgColor = currentBps > 5.92F ? new Color(255, 50, 50, 200).getRGB() : new Color(0, 200, 255, 200).getRGB();
            RenderUtil.drawRect(barX, barY, barX + fillWidth, barY + barHeight, fgColor);
            float markerX = barX + (5.92F / maxDisplayBps) * barWidth;
            RenderUtil.drawRect(markerX - 0.5F, barY - 2, markerX + 0.5F, barY + barHeight + 2, 0xFFFFFFFF);
            RenderUtil.disableRenderState();
            GlStateManager.disableDepth();
            mc.fontRendererObj.drawStringWithShadow("5.92", (int) (markerX - (float) mc.fontRendererObj.getStringWidth("5.92") / 2), barY - 12, -1);
            String bpsText = String.format("%.2f BPS", currentBps);
            mc.fontRendererObj.drawStringWithShadow(bpsText, barX + barWidth + 2, barY - 2, -1);
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
        if (blockCounter.getValue()) {
            renderBlockCounter();
        }
    }

    private void renderBlockCounter() {
        long now = System.currentTimeMillis();
        String text = Scaffold.count + " Blocks";

        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        Color tc = hud != null ? hud.getColor(now) : new Color(0, 190, 255);

        float textScale = 1.0F;
        float textW = FontManager.getStringWidth(text) * textScale;
        float textH = FontManager.getFontHeight() * textScale;

        float padX = 10.0F;
        float padY = 6.0F;
        float dot = 4.0F;
        float dotGap = 7.0F;
        float cardW = padX + dot + dotGap + textW + padX;
        float cardH = padY + textH + padY;
        float radius = 8.0F;

        ScaledResolution sr = new ScaledResolution(mc);
        float x = sr.getScaledWidth() / 2.0F - cardW / 2.0F;
        float y = sr.getScaledHeight() / 2.0F - cardH - 12.0F;
        float pulse = 0.7F + 0.3F * (float) Math.sin(now * 0.004D);
        int accent = new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 250).getRGB();

        GlStateManager.pushMatrix();
        RenderUtil.drawZenGlass(x, y, x + cardW, y + cardH, radius, 1.0F);
        RenderUtil.drawRoundedRectWithGl(x + padX - 1.0F, y + (cardH - dot) / 2.0F - 1.0F,
                x + padX + dot + 1.0F, y + (cardH + dot) / 2.0F + 1.0F, 3.0F,
                new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), (int) (60.0F * pulse)).getRGB());
        RenderUtil.drawRoundedRectWithGl(x + padX, y + (cardH - dot) / 2.0F, x + padX + dot, y + (cardH + dot) / 2.0F,
                2.0F, accent);

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + padX + dot + dotGap, y + (cardH - textH) / 2.0F + 1.0F, 0.0F);
        GlStateManager.scale(textScale, textScale, 1.0F);
        FontManager.drawString(text, 0.0F, 0.0F, new Color(244, 247, 252, 238).getRGB(), false);
        GlStateManager.popMatrix();

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) event.setCancelled(true);
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) event.setCancelled(true);
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled()) event.setCancelled(true);
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled()) { this.lastSlot = event.setSlot(this.lastSlot); event.setCancelled(true); }
    }

    @Override
    public void onEnabled() {
        this.lastSlot = mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : -1;
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F; this.pitch = 0.0F; this.canRotate = false; this.towering = false;
        this.placeDelayCounter = 0;
        this.prevBpsX = mc.thePlayer.posX; this.prevBpsZ = mc.thePlayer.posZ; this.currentBps = 0.0F;
        this.snapForward = true; this.snapForwardTimer = 0; this.snapLocked = false; this.airTicks = 0;
        this.pendingSpeedLimitRot = false; this.forwardRotateTicksLeft = 0;
        this.legitEdgeState = 0; this.legitEdgeTimer = 0; this.legitWasOnEdge = false;
        this.legitTellyPhase = 0; this.legitTellyPhaseTicks = 0;
        this.legitTellyWasAirborne = false;
        this.legitTellyPlacedFirstBlock = false;
        this.legitTellyLockedBlockData = null;
        this.legitTellySilentYaw = mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0.0F;
        this.legitTellySilentPitch = mc.thePlayer != null ? mc.thePlayer.rotationPitch : 82.0F;
    }

    @Override
    public void onDisabled() {
        this.clutchReset();
        if (mc.thePlayer != null && this.lastSlot != -1) mc.thePlayer.inventory.currentItem = this.lastSlot;
    }

    public int getSlot() { return this.lastSlot; }

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;
        public BlockData(BlockPos blockPos, EnumFacing enumFacing) { this.blockPos = blockPos; this.facing = enumFacing; }
        public BlockPos blockPos() { return this.blockPos; }
        public EnumFacing facing() { return this.facing; }
    }
}
