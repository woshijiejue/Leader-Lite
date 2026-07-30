package leader.client.module.modules.player;

import leader.client.Leader;
import leader.client.module.modules.movement.Stuck;
import leader.client.util.player.BlockUtil;
import leader.client.util.player.ItemUtil;
import leader.client.util.player.PlayerUtil;
import leader.client.util.player.RotationUtil;
import leader.client.util.render.RenderUtil;
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
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.*;
import leader.client.management.RotationState;
import leader.client.module.Module;
import leader.client.module.modules.movement.LongJump;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.FloatProperty;
import leader.client.property.properties.IntProperty;
import leader.client.property.properties.ModeProperty;
import leader.client.util.*;
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
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Normal", "Telly", "Snap", "Legit"});
    public final ModeProperty rotationMode = new ModeProperty("Rotate Mode", 3, new String[]{"None", "Vanilla", "Backwards", "Prediction"}, () -> mode.getValue() != 3);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent"});
    public final IntProperty jumpDelay = new IntProperty("Jump Delay", 2, 0, 5, () -> mode.getValue() == 1);
    public final IntProperty placeDelay = new IntProperty("Place Delay", 1, 0, 5);
    public final FloatProperty startRotSpeed = new FloatProperty("Start Rotate Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 1);
    public final FloatProperty normalRotSpeed = new FloatProperty("Normal Rotate Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 1);
    public final BooleanProperty swing = new BooleanProperty("Swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("Item Spoof", false);
    public final BooleanProperty clutch = new BooleanProperty("Clutch", true);
    public final BooleanProperty onlyInVoid = new BooleanProperty("Only Void", false, this.clutch::getValue);
    public final BooleanProperty bPSRender = new BooleanProperty("Render BPS", true);
    public final FloatProperty edgeThreshold = new FloatProperty("Edge Threshold", 0.15F, 0.01F, 0.5F, () -> mode.getValue() == 2);
    public final BooleanProperty ticksLimit = new BooleanProperty("Ticks Limit", false, () -> mode.getValue() == 2);
    public final IntProperty limitTicks = new IntProperty("Limit Ticks", 10, 1, 40, () -> mode.getValue() == 2 && ticksLimit.getValue());
    public final FloatProperty forwardSpeed = new FloatProperty("Forward Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 2);
    public final FloatProperty backSpeed = new FloatProperty("Back Speed", 180.0F, 1.0F, 180.0F, () -> mode.getValue() == 2);
    public final BooleanProperty snapRotation = new BooleanProperty("Snap Rotation", false, () -> mode.getValue() == 2);
    public final BooleanProperty speedLimit = new BooleanProperty("Speed Limit", false, () -> mode.getValue() == 1);
    public final IntProperty speedLimitTicks = new IntProperty("Speed Limit Ticks", 3, 0, 5, () -> mode.getValue() == 1 && speedLimit.getValue());
    public final IntProperty forwardRotationTicks = new IntProperty("Forward Rotation Ticks", 1, 1, 5, () -> mode.getValue() == 1 && speedLimit.getValue());
    public final IntProperty legitSneakDelay = new IntProperty("Legit Sneak Delay", 4, 1, 5, () -> mode.getValue() == 3);
    public final IntProperty legitPlaceDuration = new IntProperty("Legit Place Time", 4, 2, 5, () -> mode.getValue() == 3);


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
    private boolean legitLastKeyFwd, legitLastKeyBack, legitLastKeyLeft, legitLastKeyRight;
    private boolean pendingSpeedLimitRot = false;
    private int forwardRotateTicksLeft = 0;
    private int legitEdgeState = 0;
    private int legitEdgeTimer = 0;
    private boolean legitWasOnEdge = false;

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

    private BlockData getBlockData() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (!BlockUtil.isReplaceable(targetPos)) return null;
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!BlockUtil.isReplaceable(pos) && !BlockUtil.isInteractable(pos)
                            && mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5) <= (double) mc.playerController.getBlockReachDistance()
                            && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
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

    private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (ItemUtil.isHoldingBlock() && this.blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) this.blockCount--;
                if (this.swing.getValue()) mc.thePlayer.swingItem();
                else PacketUtil.sendPacket(new C0APacketAnimation());
            }
        }
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(mc.thePlayer.rotationYaw, (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue());
    }

    private boolean rawKeysChanged() {
        boolean fwd = mc.gameSettings.keyBindForward.isKeyDown();
        boolean back = mc.gameSettings.keyBindBack.isKeyDown();
        boolean left = mc.gameSettings.keyBindLeft.isKeyDown();
        boolean right = mc.gameSettings.keyBindRight.isKeyDown();
        boolean changed = (fwd != legitLastKeyFwd) || (back != legitLastKeyBack) || (left != legitLastKeyLeft) || (right != legitLastKeyRight);
        legitLastKeyFwd = fwd; legitLastKeyBack = back; legitLastKeyLeft = left; legitLastKeyRight = right;
        return changed;
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
            boolean legitMode = this.mode.getValue() == 3;
            boolean bridgeMode = tellyMode;

            if (this.rotationTick > 0) this.rotationTick--;
            if (this.forwardRotateTicksLeft > 0) this.forwardRotateTicksLeft--;
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) this.stage--;
                if (this.stage < 0) this.stage++;
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
                if (this.wasInAir) {
                    this.tellyJumpDelayTimer = tellyMode ? (this.jumpDelayOverride >= 0 ? this.jumpDelayOverride : this.jumpDelay.getValue()) : 0;
                    this.wasInAir = false;
                }
                if (this.tellyJumpDelayTimer > 0) this.tellyJumpDelayTimer--;
                if (speedLimit.getValue()) { pendingSpeedLimitRot = false; airTicks = 0; }
            } else {
                if (speedLimit.getValue()) airTicks++;
                this.wasInAir = true;
            }
            if (bridgeMode && mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.gameSettings.keyBindJump.isKeyDown() && this.stage == 0) this.stage = 1;
            if (bridgeMode) this.jumpDelayOverride = mc.gameSettings.keyBindJump.isKeyDown() ? 2 : -1;
            else { this.jumpDelayOverride = -1; this.tellyJumpDelayTimer = 0; }
            this.updateClutch();

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
                        case 1: this.legitEdgeTimer--; if (this.legitEdgeTimer <= 0) { this.legitEdgeState = 2; this.legitEdgeTimer = this.legitPlaceDuration.getValue(); } break;
                        case 2: this.legitEdgeTimer--; if (this.legitEdgeTimer <= 0) { this.legitEdgeState = 3; this.legitEdgeTimer = 3 + (int)(Math.random() * 4); } break;
                        case 3: this.legitEdgeTimer--; if (this.legitEdgeTimer <= 0) { this.legitEdgeState = 0; this.legitEdgeTimer = 0; } break;
                    }
                } else { this.legitEdgeState = 0; this.legitEdgeTimer = 0; }
                this.legitWasOnEdge = atEdge;
                if (!mc.thePlayer.onGround) {
                    float currentYaw = this.getCurrentYaw();
                    float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                    this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                    this.pitch = RotationUtil.quantizeAngle(85.0F);
                    this.canRotate = true;
                } else {
                    this.yaw = this.getCurrentYaw() - 180;
                    this.pitch = RotationUtil.quantizeAngle(85.0F);
                    this.canRotate = true;
                }
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

                if (!this.canRotate && !legitMode) {
                    switch (this.rotationMode.getValue()) {
                        case 1: this.yaw = this.yaw == -180.0F && this.pitch == 0.0F ? RotationUtil.quantizeAngle(diagonalYaw) : RotationUtil.quantizeAngle(diagonalYaw); break;
                        case 2: if (this.yaw == -180.0F && this.pitch == 0.0F) { this.yaw = RotationUtil.quantizeAngle(yawDiffTo180); this.pitch = RotationUtil.quantizeAngle(85.0F); } else this.yaw = RotationUtil.quantizeAngle(yawDiffTo180); break;
                        case 3: if (this.yaw == -180.0F && this.pitch == 0.0F) { this.yaw = RotationUtil.quantizeAngle(diagonalYaw); this.pitch = RotationUtil.quantizeAngle(85.0F); } break;
                    }
                }

                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                if (this.mode.getValue() == 2 && snapForward) blockData = null;

                if (blockData != null) {
                    if (blockData != null && this.rotationMode.getValue() == 3 && !legitMode) {
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

                if (this.canRotate && MoveUtil.isForwardPressed() && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    if (this.rotationMode.getValue() == 2 && !legitMode) this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                }

                if (!legitMode && this.rotationMode.getValue() != 0 && this.mode.getValue() != 2) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
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
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) event.setPervRotation(targetYaw, 3);
                } else if (this.mode.getValue() == 2 && this.rotationMode.getValue() != 0 && this.canRotate) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                    float tolerance = snapForward ? forwardSpeed.getValue() : backSpeed.getValue();
                    if (Math.abs(yawDiff) > tolerance) { float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance); targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw); this.rotationTick = Math.max(this.rotationTick, 1); }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) event.setPervRotation(targetYaw, 3);
                } else if (legitMode && this.canRotate) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                    float tolerance = 180.0F;
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
            if (this.mode.getValue() == 1 && mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed() && this.tellyJumpDelayTimer <= 0) {
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
        if (this.isEnabled() && bPSRender.getValue()) {
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
    }

    @Override
    public void onDisabled() {
        this.clutchReset();
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),false);
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
