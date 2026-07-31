package leader.client.module.modules.player;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.event.types.Priority;
import leader.client.events.HitBlockEvent;
import leader.client.events.LeftClickMouseEvent;
import leader.client.events.MoveInputEvent;
import leader.client.events.PacketEvent;
import leader.client.events.RightClickMouseEvent;
import leader.client.events.Render2DEvent;
import leader.client.events.Render3DEvent;
import leader.client.events.SafeWalkEvent;
import leader.client.events.UpdateEvent;
import leader.mixin.accessor.IAccessorRenderManager;
import leader.client.module.Module;
import leader.client.module.values.impl.BoolValue;
import leader.client.util.render.RenderUtil;
import leader.client.util.player.BlockUtil;
import leader.client.util.player.ItemUtil;
import leader.client.util.misc.KeyBindUtil;
import leader.client.util.server.PacketUtil;
import leader.client.util.player.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Leader Lite port of the TellyBridge state machine.
 * The bridge controller owns both the visible player rotation and movement curve,
 * while placement is verified against the same ray trace before the click is sent.
 */
public final class LegitTelly extends Module {
    public final BoolValue autoSwap = new BoolValue("Auto Swap", true, this);
    public final BoolValue disableSafeWalk = new BoolValue("Disable SafeWalk", true, this);
    public final BoolValue showActivationHitbox = new BoolValue("Show Activation Hitbox", false, this);

    private static final float[] YAW_CURVE = {91.68F, 98.88F, 78.94F, 37.45F, 1.61F, -21.69F, -33.98F, -35.80F, -34.64F, -33.85F, -33.06F, -31.55F, -29.26F, -26.65F, -24.19F, -21.07F, -18.84F, -17.06F, -8.87F, 2.61F, 41.94F};
    private static final float[] PITCH_CURVE = {64.31F, 59.95F, 60.57F, 61.46F, 60.64F, 58.89F, 56.91F, 56.63F, 58.65F, 61.63F, 64.20F, 66.74F, 68.69F, 70.64F, 73.01F, 75.37F, 77.46F, 78.56F, 78.90F, 77.22F, 72.25F};
    private static final float[] FORWARD_CURVE = {1, 1, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 1};
    private static final float[] STRAFE_CURVE = {-1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1};
    private static final int[] YAW_NUDGE = {0, 1, -1, 2, -2};
    private static final double[] HIT_OFFSETS = {.5D, .25D, .75D, .15D, .85D, .35D, .65D, .05D, .95D};
    private static final float QUANTUM = .03404715F;
    private static final long PROMPT_READY_MS = 1000L;
    private static final long SUPPRESS_USE_MS = 850L;
    private static final long BREAK_WINDOW_MS = 300L;
    private static final long ROTATION_MS = 50L;
    private static final float ACTIVATION_TOLERANCE = 2.0F;

    private boolean armed;
    private boolean running;
    private long promptStartedAt;
    private long promptBrokenAt;
    private float promptAlpha;
    private long promptFadeAt;
    private int promptColor = 0xFF5555;
    private BlockPos activationPos;
    private EnumFacing activationFace;
    private boolean activationKeysHeld;

    private int setupTick;
    private int cyclePhase;
    private float baseYaw;
    private int travelX;
    private int travelZ;
    private int bridgeLane;
    private int bridgeStartProgress;
    private double antiSwayLane;
    private float antiSwayYaw;
    private boolean antiSwayTapUsed;
    private int savedSlot = -1;

    private float desiredForward;
    private float desiredStrafe;
    private boolean desiredJump;
    private boolean desiredSprint;
    private boolean useWindow;
    private boolean firstPlacementPending;
    private float adaptiveYaw;
    private float adaptivePitch;
    private long adaptiveAt;
    private BlockPos latestPlaced;
    private BlockPos lastSupport;
    private EnumFacing lastSupportFace;

    private float scriptedYaw;
    private float scriptedPitch;
    private float rotationStartYaw;
    private float rotationStartPitch;
    private float rotationTargetYaw;
    private float rotationTargetPitch;
    private long rotationStartedAt;
    private boolean rotationActive;
    private int rotationStep;

    private long lastUpdateAt;
    private long takeoverAllowedAt;
    private long takeoverLastAt;
    private float takeoverAmount;
    private boolean ignoreForward;
    private boolean ignoreBack;
    private boolean ignoreLeft;
    private boolean ignoreRight;
    private boolean ignoreJump;
    private boolean ignoreSneak;
    private boolean ignoreSprint;
    private boolean safeWalkCaptured;
    private boolean safeWalkWasEnabled;

    private int candidateTick = Integer.MIN_VALUE;
    private float candidateYaw = Float.NaN;
    private float candidatePitch = Float.NaN;
    private PlacementCandidate cachedCandidate;
    private int lastAttemptTick = Integer.MIN_VALUE;
    private final Map<BlockPos, Integer> rejectedTargets = new HashMap<>();
    private double lastServerX;
    private double lastServerY;
    private double lastServerZ;
    private boolean hasServerPosition;

    public LegitTelly() {
        super("LegitTelly", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.running ? "Running" : this.armed ? "Armed" : "Idle"};
    }

    @Override
    public void onEnabled() {
        this.arm();
    }

    @Override
    public void onDisabled() {
        this.stopAutomation(false);
        this.armed = false;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) return;
        long now = System.currentTimeMillis();
        this.updatePromptFade(now);
        if (!this.running) {
            this.updateActivation(now);
            return;
        }
        if (this.lastUpdateAt != 0L && now - this.lastUpdateAt > 300L) {
            this.stopAutomation(true);
            return;
        }
        this.lastUpdateAt = now;
        this.enforceSafeWalkDisabled();
        if (mc.currentScreen != null || mc.thePlayer.isDead || mc.thePlayer.fallDistance > 7.0F || !ItemUtil.isHoldingBlock() || this.scaffoldEnabled()) {
            this.stopAutomation(true);
            return;
        }
        this.autoSwapBlocks();
        if (this.detectCameraTakeover(now)) return;
        this.pruneRejectedTargets();
        if (this.firstPlacementPending) this.updateAdaptiveAim();
        this.applySmoothedRotation(now);
        this.updateController(now);
        this.applySmoothedRotation(now);
        event.setRotation(this.scriptedYaw, this.scriptedPitch, 4);
        event.setPervRotation(this.scriptedYaw, 4);
        if (this.useWindow) this.attemptPlacement(event);
    }

    private void arm() {
        this.armed = true;
        this.running = false;
        this.promptStartedAt = 0L;
        this.promptBrokenAt = 0L;
        this.setupTick = 0;
        this.cyclePhase = 19;
        this.rotationActive = false;
        this.releaseControlledKeys();
    }

    private void updateActivation(long now) {
        if (!this.armed || mc.currentScreen != null || this.scaffoldEnabled()) {
            this.clearPrompt();
            return;
        }
        boolean valid = mc.gameSettings.keyBindSneak.isKeyDown() && this.isValidActivationEdge();
        if (valid) {
            if (this.promptStartedAt == 0L) this.promptStartedAt = now;
            this.promptBrokenAt = 0L;
            this.rememberPromptColor(now);
            boolean ready = this.promptReady(now);
            if (now - this.promptStartedAt >= SUPPRESS_USE_MS) KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            this.setActivationHold(ready && Mouse.isButtonDown(1));
            if (ready && Mouse.isButtonDown(1)) this.disableSafeWalkForRun();
            else if (this.safeWalkCaptured) this.restoreSafeWalk();
            return;
        }
        if (this.promptStartedAt == 0L) return;
        if (!this.promptReady(now)) {
            this.clearPrompt();
            return;
        }
        if (this.promptBrokenAt == 0L) {
            this.rememberPromptColor(now);
            this.promptBrokenAt = now;
        }
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        if (!mc.gameSettings.keyBindSneak.isKeyDown() && Mouse.isButtonDown(1) && isActivationYawAligned(mc.thePlayer.rotationYaw)) {
            this.beginAutomation(now);
            return;
        }
        if (now - this.promptBrokenAt > BREAK_WINDOW_MS) this.clearPrompt();
    }

    private boolean isValidActivationEdge() {
        if (!isActivationYawAligned(mc.thePlayer.rotationYaw) || mc.thePlayer.rotationPitch < 75.0F) return false;
        MovingObjectPosition hit = RotationUtil.rayTrace(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 4.5D, 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.sideHit == null || hit.sideHit.getAxis().isVertical()) return false;
        int[] direction = travelDirection(mc.thePlayer.rotationYaw);
        if (hit.sideHit != facingFor(direction[0], direction[1]) || !isCenterHit(hit.hitVec, hit.getBlockPos(), hit.sideHit)) return false;
        BlockPos base = hit.getBlockPos();
        if (MathHelper.floor_double(mc.thePlayer.posY - .01D) != base.getY()
                || Math.abs(mc.thePlayer.posX - base.getX() - .5D) > .85D
                || Math.abs(mc.thePlayer.posZ - base.getZ() - .5D) > .85D
                || !BlockUtil.isReplaceable(base.add(direction[0], 1, direction[1]))) return false;
        double lip = hit.sideHit == EnumFacing.EAST ? base.getX() + 1D - mc.thePlayer.posX
                : hit.sideHit == EnumFacing.WEST ? mc.thePlayer.posX - base.getX()
                : hit.sideHit == EnumFacing.SOUTH ? base.getZ() + 1D - mc.thePlayer.posZ : mc.thePlayer.posZ - base.getZ();
        if (lip > .65D) return false;
        this.activationPos = base;
        this.activationFace = hit.sideHit;
        return true;
    }

    private static boolean isCenterHit(Vec3 hit, BlockPos pos, EnumFacing face) {
        double across = face.getAxis() == EnumFacing.Axis.X ? hit.zCoord - pos.getZ() : hit.xCoord - pos.getX();
        if (face == EnumFacing.WEST || face == EnumFacing.SOUTH) across = 1D - across;
        double height = hit.yCoord - pos.getY();
        return across >= .38D && across <= .65D && height >= .25D && height <= .75D;
    }

    private void beginAutomation(long now) {
        if (!ItemUtil.isHoldingBlock() || !isActivationYawAligned(mc.thePlayer.rotationYaw)) return;
        this.disableSafeWalkForRun();
        this.baseYaw = mc.thePlayer.rotationYaw;
        int[] direction = travelDirection(this.baseYaw);
        this.travelX = direction[0];
        this.travelZ = direction[1];
        this.bridgeLane = this.travelX != 0 ? MathHelper.floor_double(mc.thePlayer.posZ) : MathHelper.floor_double(mc.thePlayer.posX);
        this.bridgeStartProgress = this.progress(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        this.latestPlaced = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        this.antiSwayLane = this.travelX != 0 ? mc.thePlayer.posZ : mc.thePlayer.posX;
        this.antiSwayYaw = 0F;
        this.antiSwayTapUsed = false;
        this.savedSlot = mc.thePlayer.inventory.currentItem;
        this.armed = false;
        this.running = true;
        this.promptStartedAt = 0L;
        this.promptBrokenAt = 0L;
        this.setupTick = 0;
        this.cyclePhase = 19;
        this.scriptedYaw = mc.thePlayer.rotationYaw;
        this.scriptedPitch = mc.thePlayer.rotationPitch;
        this.lastUpdateAt = now;
        this.takeoverAmount = 0F;
        this.takeoverLastAt = now;
        this.takeoverAllowedAt = 0L;
        this.firstPlacementPending = false;
        this.clearCandidate();
        this.setActivationHold(false);
        this.setRotationTarget(this.baseYaw, 74.52F, now);
        this.setInput(-1F, -1F, false, false, true);
    }

    private void updateController(long now) {
        if (this.setupTick >= 0) {
            boolean jump = this.setupTick >= 6;
            this.setInput(-1F, -1F, jump, false, true);
            this.setRotationTarget(this.setupTick == 11 ? this.baseYaw + YAW_CURVE[19] : this.baseYaw, this.setupTick == 11 ? PITCH_CURVE[19] : 74.52F, now);
            if (++this.setupTick == 12) {
                this.setupTick = -1;
                this.cyclePhase = 19;
                this.takeoverAllowedAt = now + 125L;
                this.captureInitialKeys();
                this.firstPlacementPending = true;
                this.updateAdaptiveAim();
            }
            return;
        }
        int phase = this.cyclePhase;
        this.setInput(FORWARD_CURVE[phase], this.applyAntiSway(FORWARD_CURVE[phase], STRAFE_CURVE[phase]), phase >= 1 && phase <= 19, phase == 0 || phase == 1, phase >= 7);
        this.cyclePhase = (phase + 1) % YAW_CURVE.length;
        this.setRotationTarget(this.baseYaw + YAW_CURVE[this.cyclePhase], PITCH_CURVE[this.cyclePhase], now);
    }

    private float applyAntiSway(float forward, float strafe) {
        double lanePosition = this.travelX != 0 ? mc.thePlayer.posZ : mc.thePlayer.posX;
        double laneVelocity = this.travelX != 0 ? mc.thePlayer.motionZ : mc.thePlayer.motionX;
        double error = this.antiSwayLane - lanePosition;
        if (Math.abs(error) < .015D && Math.abs(laneVelocity) < .008D) {
            this.antiSwayTapUsed = false;
            this.antiSwayYaw *= .65F;
            return strafe;
        }
        double correction = Math.max(-.16D, Math.min(.16D, error * .42D - laneVelocity * .78D)) - laneVelocity;
        double radians = Math.toRadians(mc.thePlayer.rotationYaw);
        double derivative = this.travelX != 0 ? -forward * Math.sin(radians) + strafe * Math.cos(radians) : -forward * Math.cos(radians) - strafe * Math.sin(radians);
        double yaw = Math.abs(derivative) < .12D ? 0D : Math.toDegrees(correction * .55D / derivative);
        yaw = Math.max(-2.25D, Math.min(2.25D, yaw));
        this.antiSwayYaw = this.antiSwayYaw * .60F + (float) yaw * .40F;
        double strafeAxis = this.travelX != 0 ? Math.sin(radians) : Math.cos(radians);
        if (!this.antiSwayTapUsed && Math.abs(correction) >= .03D && strafe < .5F && correction * strafeAxis > 0D) {
            this.antiSwayTapUsed = true;
            return strafe + 1F;
        }
        return strafe;
    }

    private void setInput(float forward, float strafe, boolean jump, boolean sprint, boolean use) {
        this.desiredForward = forward;
        this.desiredStrafe = strafe;
        this.desiredJump = jump;
        this.desiredSprint = sprint;
        this.useWindow = use;
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), use);
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }

    private void setRotationTarget(float yaw, float pitch, long now) {
        this.applySmoothedRotation(now);
        this.rotationStartYaw = this.scriptedYaw;
        this.rotationStartPitch = this.scriptedPitch;
        boolean adaptive = this.running && this.useWindow && this.firstPlacementPending && now - this.adaptiveAt <= 125L;
        if (adaptive) {
            yaw = this.adaptiveYaw;
            pitch = this.adaptivePitch;
        } else if (this.running) {
            yaw += this.antiSwayYaw;
        }
        yaw += QUANTUM * YAW_NUDGE[++this.rotationStep % YAW_NUDGE.length];
        this.rotationTargetYaw = this.rotationStartYaw + MathHelper.wrapAngleTo180_float(yaw - this.rotationStartYaw);
        this.rotationTargetPitch = MathHelper.clamp_float(pitch, -90F, 90F);
        this.rotationStartedAt = now;
        this.rotationActive = true;
    }

    private void applySmoothedRotation(long now) {
        if (!this.rotationActive || mc.thePlayer == null) return;
        float fraction = MathHelper.clamp_float((now - this.rotationStartedAt) / (float) ROTATION_MS, 0F, 1F);
        this.scriptedYaw = quantize(this.rotationStartYaw, this.rotationStartYaw + (this.rotationTargetYaw - this.rotationStartYaw) * fraction);
        this.scriptedPitch = MathHelper.clamp_float(quantize(this.rotationStartPitch, this.rotationStartPitch + (this.rotationTargetPitch - this.rotationStartPitch) * fraction), -90F, 90F);
        mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;
        mc.thePlayer.prevRotationPitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationYaw = this.scriptedYaw;
        mc.thePlayer.rotationPitch = this.scriptedPitch;
        mc.thePlayer.rotationYawHead = this.scriptedYaw;
        if (fraction >= 1F) this.rotationActive = false;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || !this.running || mc.thePlayer == null || mc.currentScreen != null) return;
        mc.thePlayer.movementInput.moveForward = this.desiredForward;
        mc.thePlayer.movementInput.moveStrafe = this.desiredStrafe;
        mc.thePlayer.movementInput.jump = this.desiredJump;
        mc.thePlayer.movementInput.sneak = false;
        mc.thePlayer.setSprinting(this.desiredSprint);
        setMovementKey(mc.gameSettings.keyBindForward, this.desiredForward > .03F);
        setMovementKey(mc.gameSettings.keyBindBack, this.desiredForward < -.03F);
        setMovementKey(mc.gameSettings.keyBindLeft, this.desiredStrafe > .5F);
        setMovementKey(mc.gameSettings.keyBindRight, this.desiredStrafe < -.5F);
        setMovementKey(mc.gameSettings.keyBindJump, this.desiredJump);
        setMovementKey(mc.gameSettings.keyBindSprint, this.desiredSprint);
    }

    private void attemptPlacement(UpdateEvent event) {
        int tick = mc.thePlayer.ticksExisted;
        if (this.lastAttemptTick == tick) return;
        PlacementCandidate candidate = this.resolveCandidate(event.getNewYaw(), event.getNewPitch());
        if (candidate == null) return;
        this.lastAttemptTick = tick;
        if (!this.isValidCandidate(candidate) || !BlockUtil.isReplaceable(candidate.target) || this.intersectsPlayer(candidate.target)) {
            this.reject(candidate.target, tick);
            return;
        }
        MovingObjectPosition hit = RotationUtil.rayTrace(candidate.yaw, candidate.pitch, mc.playerController.getBlockReachDistance(), 1F);
        if (!this.matches(hit, candidate)) {
            this.reject(candidate.target, tick);
            return;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null || !ItemUtil.isBlock(stack)) return;
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack, candidate.support, candidate.face, hit.hitVec)) {
            mc.thePlayer.swingItem();
            this.latestPlaced = candidate.target;
            this.lastSupport = candidate.support;
            this.lastSupportFace = candidate.face;
            this.firstPlacementPending = false;
            this.clearCandidate();
        } else {
            this.reject(candidate.target, tick);
        }
    }

    private PlacementCandidate resolveCandidate(float yaw, float pitch) {
        int tick = mc.thePlayer.ticksExisted;
        if (this.cachedCandidate != null && this.candidateTick == tick
                && Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.candidateYaw)) <= .75F
                && Math.abs(pitch - this.candidatePitch) <= .75F) return this.cachedCandidate;
        PlacementCandidate candidate = this.directCandidate(yaw, pitch);
        if (candidate == null) candidate = this.continuationCandidate(yaw, pitch);
        if (candidate == null) candidate = this.belowPlayerCandidate(yaw, pitch);
        if (candidate == null) candidate = this.nearbyCandidate(yaw, pitch);
        this.cachedCandidate = candidate;
        this.candidateTick = tick;
        this.candidateYaw = yaw;
        this.candidatePitch = pitch;
        return candidate;
    }

    private PlacementCandidate directCandidate(float yaw, float pitch) {
        MovingObjectPosition hit = RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.sideHit == EnumFacing.DOWN) return null;
        BlockPos target = hit.getBlockPos().offset(hit.sideHit);
        if (!this.isStraightTarget(target) || !this.validSupport(hit.getBlockPos()) || !BlockUtil.isReplaceable(target) || this.rejected(target)) return null;
        return new PlacementCandidate(hit.getBlockPos(), hit.sideHit, target, hit.hitVec, yaw, pitch);
    }

    private PlacementCandidate continuationCandidate(float yaw, float pitch) {
        BlockPos support = this.latestPlaced != null ? this.latestPlaced : this.lastSupport;
        if (support == null) return null;
        EnumFacing face = this.travelX > 0 ? EnumFacing.EAST : this.travelX < 0 ? EnumFacing.WEST : this.travelZ > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
        BlockPos target = support.offset(face);
        return this.findCandidateForTarget(target, support, face, yaw, pitch);
    }

    private PlacementCandidate belowPlayerCandidate(float yaw, float pitch) {
        int y = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        BlockPos target = new BlockPos(mc.thePlayer.posX + mc.thePlayer.motionX * 1.25D, y, mc.thePlayer.posZ + mc.thePlayer.motionZ * 1.25D);
        if (!this.isStraightTarget(target)) target = new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ);
        return this.findCandidateForTarget(target, null, null, yaw, pitch);
    }

    private PlacementCandidate nearbyCandidate(float yaw, float pitch) {
        int y = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        for (int radius = 0; radius <= 2; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                    PlacementCandidate candidate = this.findCandidateForTarget(new BlockPos(mc.thePlayer.posX + x, y, mc.thePlayer.posZ + z), null, null, yaw, pitch);
                    if (candidate != null) return candidate;
                }
            }
        }
        return null;
    }

    private PlacementCandidate findCandidateForTarget(BlockPos target, BlockPos preferredSupport, EnumFacing preferredFace, float baseYaw, float basePitch) {
        if (target == null || !this.isStraightTarget(target) || !BlockUtil.isReplaceable(target) || this.rejected(target) || this.intersectsPlayer(target)) return null;
        PlacementCandidate best = null;
        double score = Double.MAX_VALUE;
        EnumFacing[] faces = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP};
        for (EnumFacing face : faces) {
            if (preferredFace != null && face != preferredFace) continue;
            BlockPos support = target.offset(face.getOpposite());
            if (preferredSupport != null && !preferredSupport.equals(support)) continue;
            if (!this.validSupport(support) || !this.withinReach(support)) continue;
            for (double a : HIT_OFFSETS) {
                for (double b : HIT_OFFSETS) {
                    Vec3 point = facePoint(support, face, a, b);
                    float[] rotations = RotationUtil.getRotationsTo(point.xCoord - mc.thePlayer.getPositionEyes(1F).xCoord, point.yCoord - mc.thePlayer.getPositionEyes(1F).yCoord, point.zCoord - mc.thePlayer.getPositionEyes(1F).zCoord, this.scriptedYaw, this.scriptedPitch);
                    MovingObjectPosition ray = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1F);
                    if (ray == null || !ray.getBlockPos().equals(support) || ray.sideHit != face || !target.equals(support.offset(face))) continue;
                    double candidateScore = Math.abs(rotations[1] - basePitch) + 2D * (Math.abs(a - .5D) + Math.abs(b - .5D)) + (face == EnumFacing.UP ? 0D : .35D);
                    if (candidateScore < score) {
                        score = candidateScore;
                        best = new PlacementCandidate(support, face, target, ray.hitVec, rotations[0], rotations[1]);
                    }
                }
            }
        }
        return best;
    }

    private void updateAdaptiveAim() {
        PlacementCandidate candidate = this.resolveCandidate(this.scriptedYaw, this.scriptedPitch);
        if (candidate == null) return;
        this.adaptiveYaw = candidate.yaw;
        this.adaptivePitch = candidate.pitch;
        this.adaptiveAt = System.currentTimeMillis();
    }

    private boolean isValidCandidate(PlacementCandidate candidate) {
        return candidate != null && this.isStraightTarget(candidate.target) && this.validSupport(candidate.support) && this.withinReach(candidate.support) && !this.rejected(candidate.target);
    }

    private boolean matches(MovingObjectPosition hit, PlacementCandidate candidate) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.getBlockPos().equals(candidate.support) && hit.sideHit == candidate.face && candidate.target.equals(candidate.support.offset(candidate.face));
    }

    private boolean validSupport(BlockPos pos) {
        if (pos == null || mc.theWorld == null) return false;
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return !BlockUtil.isReplaceable(block) && BlockUtil.isSolid(block) && !BlockUtil.isInteractable(block);
    }

    private boolean withinReach(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1F);
        AxisAlignedBB box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1D, pos.getY() + 1D, pos.getZ() + 1D);
        Vec3 closest = RotationUtil.getClosestPointOnBox(eyes, box);
        return eyes.squareDistanceTo(closest) <= mc.playerController.getBlockReachDistance() * mc.playerController.getBlockReachDistance();
    }

    private boolean intersectsPlayer(BlockPos pos) {
        AxisAlignedBB block = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1D, pos.getY() + 1D, pos.getZ() + 1D);
        AxisAlignedBB player = mc.thePlayer.getEntityBoundingBox();
        if (player.intersectsWith(block)) return true;
        if (mc.thePlayer.onGround && this.hasServerPosition) {
            AxisAlignedBB serverPlayer = new AxisAlignedBB(this.lastServerX - .3D, this.lastServerY, this.lastServerZ - .3D, this.lastServerX + .3D, this.lastServerY + 1.8D, this.lastServerZ + .3D);
            return serverPlayer.intersectsWith(block);
        }
        return false;
    }

    private static Vec3 facePoint(BlockPos pos, EnumFacing face, double a, double b) {
        a = Math.max(.001D, Math.min(.999D, a));
        b = Math.max(.001D, Math.min(.999D, b));
        switch (face) {
            case NORTH: return new Vec3(pos.getX() + a, pos.getY() + b, pos.getZ() + .001D);
            case SOUTH: return new Vec3(pos.getX() + a, pos.getY() + b, pos.getZ() + .999D);
            case WEST: return new Vec3(pos.getX() + .001D, pos.getY() + a, pos.getZ() + b);
            case EAST: return new Vec3(pos.getX() + .999D, pos.getY() + a, pos.getZ() + b);
            default: return new Vec3(pos.getX() + a, pos.getY() + .999D, pos.getZ() + b);
        }
    }

    private boolean isStraightTarget(BlockPos pos) {
        int lane = this.travelX != 0 ? pos.getZ() : pos.getX();
        return lane == this.bridgeLane && this.progress(pos) >= this.bridgeStartProgress;
    }

    private int progress(BlockPos pos) {
        return pos.getX() * this.travelX + pos.getZ() * this.travelZ;
    }

    private void autoSwapBlocks() {
        if (!this.autoSwap.getValue() || !ItemUtil.isHoldingBlock() || mc.thePlayer.getHeldItem().stackSize > 5) return;
        int best = mc.thePlayer.inventory.currentItem;
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (ItemUtil.isBlock(stack) && stack.stackSize > count) {
                best = slot;
                count = stack.stackSize;
            }
        }
        if (best != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = best;
            PacketUtil.sendPacket(new net.minecraft.network.play.client.C09PacketHeldItemChange(best));
        }
    }

    private boolean detectCameraTakeover(long now) {
        if (this.setupTick >= 0 || now < this.takeoverAllowedAt) return false;
        long elapsed = Math.max(0L, now - this.takeoverLastAt);
        this.takeoverLastAt = now;
        this.takeoverAmount = Math.max(0F, this.takeoverAmount - elapsed * .045F);
        float yaw = Math.abs(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - this.scriptedYaw));
        float pitch = Math.abs(mc.thePlayer.rotationPitch - this.scriptedPitch);
        if (yaw > QUANTUM * .45F || pitch > QUANTUM * .45F) this.takeoverAmount += yaw + pitch;
        if (this.takeoverAmount < 25F) return false;
        this.stopAutomation(true);
        return true;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.promptAlpha < .05F) return;
        String text = "Activate?";
        int alpha = Math.max(16, (int) (this.promptAlpha * 255F));
        int color = alpha << 24 | this.promptColor;
        ScaledResolution resolution = new ScaledResolution(mc);
        mc.fontRendererObj.drawStringWithShadow(text,
                resolution.getScaledWidth() / 2F - mc.fontRendererObj.getStringWidth(text) / 2F,
                resolution.getScaledHeight() / 2F + 10F,
                color);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.showActivationHitbox.getValue() || !this.armed || this.running
                || this.promptAlpha < .05F || this.activationPos == null || this.activationFace == null) return;
        this.renderActivationFace();
    }

    private void renderActivationFace() {
        double y1 = this.activationPos.getY() + .25D;
        double y2 = this.activationPos.getY() + .75D;
        double x1;
        double x2;
        double z1;
        double z2;
        switch (this.activationFace) {
            case EAST:
                x1 = x2 = this.activationPos.getX() + 1.005D;
                z1 = this.activationPos.getZ() + .38D;
                z2 = this.activationPos.getZ() + .65D;
                break;
            case WEST:
                x1 = x2 = this.activationPos.getX() - .005D;
                z1 = this.activationPos.getZ() + .35D;
                z2 = this.activationPos.getZ() + .62D;
                break;
            case SOUTH:
                z1 = z2 = this.activationPos.getZ() + 1.005D;
                x1 = this.activationPos.getX() + .35D;
                x2 = this.activationPos.getX() + .62D;
                break;
            default:
                z1 = z2 = this.activationPos.getZ() - .005D;
                x1 = this.activationPos.getX() + .38D;
                x2 = this.activationPos.getX() + .65D;
                break;
        }
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        x1 -= renderManager.getRenderPosX();
        x2 -= renderManager.getRenderPosX();
        y1 -= renderManager.getRenderPosY();
        y2 -= renderManager.getRenderPosY();
        z1 -= renderManager.getRenderPosZ();
        z2 -= renderManager.getRenderPosZ();

        int red = this.promptColor >> 16 & 255;
        int green = this.promptColor >> 8 & 255;
        int blue = this.promptColor & 255;
        int fillAlpha = Math.max(4, (int) (60F * this.promptAlpha));
        int lineAlpha = Math.max(16, (int) (220F * this.promptAlpha));

        RenderUtil.enableRenderState();
        GlStateManager.depthMask(false);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(x1, y1, z1).color(red, green, blue, fillAlpha).endVertex();
        worldRenderer.pos(x2, y1, z2).color(red, green, blue, fillAlpha).endVertex();
        worldRenderer.pos(x2, y2, z2).color(red, green, blue, fillAlpha).endVertex();
        worldRenderer.pos(x1, y2, z1).color(red, green, blue, fillAlpha).endVertex();
        tessellator.draw();

        GL11.glLineWidth(2F);
        worldRenderer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(x1, y1, z1).color(red, green, blue, lineAlpha).endVertex();
        worldRenderer.pos(x2, y1, z2).color(red, green, blue, lineAlpha).endVertex();
        worldRenderer.pos(x2, y2, z2).color(red, green, blue, lineAlpha).endVertex();
        worldRenderer.pos(x1, y2, z1).color(red, green, blue, lineAlpha).endVertex();
        tessellator.draw();
        GL11.glLineWidth(1F);
        GlStateManager.depthMask(true);
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (this.running && this.disableSafeWalk.getValue()) event.setSafeWalk(false);
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.running) event.setCancelled(true);
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.running || this.promptStartedAt != 0L) event.setCancelled(true);
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.running) event.setCancelled(true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE) {
            if (this.running && event.getPacket() instanceof S08PacketPlayerPosLook) this.stopAutomation(true);
            return;
        }
        if (this.isDropProtected() && event.getPacket() instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging.Action action = ((C07PacketPlayerDigging) event.getPacket()).getStatus();
            if (action == C07PacketPlayerDigging.Action.DROP_ITEM || action == C07PacketPlayerDigging.Action.DROP_ALL_ITEMS) event.setCancelled(true);
        }
        if (!this.running) return;
        if (event.getPacket() instanceof C02PacketUseEntity && ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK) event.setCancelled(true);
        if (event.getPacket() instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging.Action action = ((C07PacketPlayerDigging) event.getPacket()).getStatus();
            if (action == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK || action == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK || action == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK) event.setCancelled(true);
        }
        if (event.getPacket() instanceof C0BPacketEntityAction && ((C0BPacketEntityAction) event.getPacket()).getAction() == C0BPacketEntityAction.Action.START_SNEAKING) event.setCancelled(true);
        if (event.getPacket() instanceof C03PacketPlayer) {
            C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
            if (packet.isMoving()) {
                this.lastServerX = packet.getPositionX();
                this.lastServerY = packet.getPositionY();
                this.lastServerZ = packet.getPositionZ();
                this.hasServerPosition = true;
            }
        }
        if (event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
            if (packet.getPlacedBlockDirection() != 255 && packet.getPosition() != null) {
                BlockPos target = packet.getPosition().offset(EnumFacing.getFront(packet.getPlacedBlockDirection()));
                if (!this.isStraightTarget(target)) event.setCancelled(true);
            }
        }
    }

    private void clearPrompt() {
        this.rememberPromptColor(System.currentTimeMillis());
        this.promptStartedAt = 0L;
        this.promptBrokenAt = 0L;
        this.activationPos = null;
        this.activationFace = null;
        this.setActivationHold(false);
        if (!this.running) this.restoreSafeWalk();
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(1));
    }

    private void setActivationHold(boolean held) {
        if (this.activationKeysHeld == held) return;
        this.activationKeysHeld = held;
        setMovementKey(mc.gameSettings.keyBindBack, held ? true : mc.gameSettings.keyBindBack.isKeyDown());
        setMovementKey(mc.gameSettings.keyBindRight, held ? true : mc.gameSettings.keyBindRight.isKeyDown());
    }

    private boolean promptReady(long now) { return this.promptStartedAt != 0L && now - this.promptStartedAt >= PROMPT_READY_MS; }
    private boolean isDropProtected() { return this.running || this.promptStartedAt != 0L; }

    private void rememberPromptColor(long now) {
        if (this.promptStartedAt != 0L) this.promptColor = this.promptReady(now) ? 0x55FF55 : 0xFF5555;
    }

    private void updatePromptFade(long now) {
        boolean display = this.armed && !this.running && this.promptStartedAt != 0L;
        if (display) this.rememberPromptColor(now);
        long elapsed = this.promptFadeAt == 0L ? 0L : Math.min(100L, now - this.promptFadeAt);
        this.promptFadeAt = now;
        this.promptAlpha = Math.max(0F, Math.min(1F, this.promptAlpha + (display ? 1F : -1F) * elapsed / 200F));
    }

    private void disableSafeWalkForRun() {
        if (this.safeWalkCaptured || !this.disableSafeWalk.getValue()) return;
        Module safeWalk = Leader.moduleManager.getModule("SafeWalk");
        if (safeWalk == null) return;
        this.safeWalkCaptured = true;
        this.safeWalkWasEnabled = safeWalk.isEnabled();
        if (this.safeWalkWasEnabled) safeWalk.setEnabled(false);
    }

    private void enforceSafeWalkDisabled() {
        if (!this.safeWalkCaptured) return;
        Module safeWalk = Leader.moduleManager.getModule("SafeWalk");
        if (safeWalk != null && safeWalk.isEnabled()) safeWalk.setEnabled(false);
    }

    private void restoreSafeWalk() {
        if (!this.safeWalkCaptured) return;
        Module safeWalk = Leader.moduleManager.getModule("SafeWalk");
        if (safeWalk != null && this.safeWalkWasEnabled && !safeWalk.isEnabled()) safeWalk.setEnabled(true);
        this.safeWalkCaptured = false;
        this.safeWalkWasEnabled = false;
    }

    private void stopAutomation(boolean rearm) {
        this.running = false;
        this.rotationActive = false;
        this.useWindow = false;
        this.firstPlacementPending = false;
        this.antiSwayYaw = 0F;
        this.antiSwayTapUsed = false;
        this.takeoverAmount = 0F;
        this.lastUpdateAt = 0L;
        this.clearCandidate();
        this.rejectedTargets.clear();
        this.latestPlaced = null;
        this.lastSupport = null;
        this.lastSupportFace = null;
        this.hasServerPosition = false;
        this.clearIgnoredKeys();
        if (mc.thePlayer != null && this.savedSlot >= 0) {
            mc.thePlayer.inventory.currentItem = this.savedSlot;
            PacketUtil.sendPacket(new net.minecraft.network.play.client.C09PacketHeldItemChange(this.savedSlot));
        }
        this.savedSlot = -1;
        this.releaseControlledKeys();
        this.restoreSafeWalk();
        if (rearm && this.isEnabled()) this.arm();
    }

    private boolean scaffoldEnabled() {
        Scaffold scaffold = (Scaffold) Leader.moduleManager.getModule(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private void captureInitialKeys() {
        this.ignoreForward = mc.gameSettings.keyBindForward.isKeyDown();
        this.ignoreBack = mc.gameSettings.keyBindBack.isKeyDown();
        this.ignoreLeft = mc.gameSettings.keyBindLeft.isKeyDown();
        this.ignoreRight = mc.gameSettings.keyBindRight.isKeyDown();
        this.ignoreJump = mc.gameSettings.keyBindJump.isKeyDown();
        this.ignoreSneak = mc.gameSettings.keyBindSneak.isKeyDown();
        this.ignoreSprint = mc.gameSettings.keyBindSprint.isKeyDown();
    }

    private void clearIgnoredKeys() { this.ignoreForward = this.ignoreBack = this.ignoreLeft = this.ignoreRight = this.ignoreJump = this.ignoreSneak = this.ignoreSprint = false; }
    private void clearCandidate() { this.cachedCandidate = null; this.candidateTick = Integer.MIN_VALUE; this.candidateYaw = Float.NaN; this.candidatePitch = Float.NaN; }
    private void reject(BlockPos target, int tick) { if (target != null) this.rejectedTargets.put(target, tick); }
    private boolean rejected(BlockPos target) { Integer tick = this.rejectedTargets.get(target); return tick != null && mc.thePlayer.ticksExisted - tick <= 4; }

    private void pruneRejectedTargets() {
        Iterator<Map.Entry<BlockPos, Integer>> it = this.rejectedTargets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            if (mc.thePlayer.ticksExisted - entry.getValue() > 4 || !BlockUtil.isReplaceable(entry.getKey())) it.remove();
        }
    }

    private static float quantize(float origin, float value) { return origin + Math.round((value - origin) / QUANTUM) * QUANTUM; }
    private static boolean isActivationYawAligned(float yaw) { float diagonal = Math.round((yaw - 45F) / 90F) * 90F + 45F; return Math.abs(MathHelper.wrapAngleTo180_float(yaw - diagonal)) <= ACTIVATION_TOLERANCE; }
    private static int[] travelDirection(float yaw) { double radians = Math.toRadians(yaw); double x = Math.sin(radians) - Math.cos(radians); double z = -Math.cos(radians) - Math.sin(radians); return Math.abs(x) >= Math.abs(z) ? new int[]{x >= 0D ? 1 : -1, 0} : new int[]{0, z >= 0D ? 1 : -1}; }
    private static EnumFacing facingFor(int x, int z) { return x > 0 ? EnumFacing.EAST : x < 0 ? EnumFacing.WEST : z > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH; }
    private static void setMovementKey(KeyBinding key, boolean down) { KeyBindUtil.setKeyBindState(key.getKeyCode(), down); }

    private void releaseControlledKeys() {
        if (mc.gameSettings != null) {
            setMovementKey(mc.gameSettings.keyBindForward, false);
            setMovementKey(mc.gameSettings.keyBindBack, false);
            setMovementKey(mc.gameSettings.keyBindLeft, false);
            setMovementKey(mc.gameSettings.keyBindRight, false);
            setMovementKey(mc.gameSettings.keyBindJump, false);
            setMovementKey(mc.gameSettings.keyBindSneak, false);
            setMovementKey(mc.gameSettings.keyBindSprint, false);
            setMovementKey(mc.gameSettings.keyBindUseItem, false);
            setMovementKey(mc.gameSettings.keyBindAttack, false);
        }
        if (mc.thePlayer != null) {
            mc.thePlayer.movementInput.moveForward = 0F;
            mc.thePlayer.movementInput.moveStrafe = 0F;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
            mc.thePlayer.setSprinting(false);
        }
        this.useWindow = false;
        this.activationKeysHeld = false;
    }

    private static final class PlacementCandidate {
        private final BlockPos support;
        private final EnumFacing face;
        private final BlockPos target;
        private final Vec3 hit;
        private final float yaw;
        private final float pitch;

        private PlacementCandidate(BlockPos support, EnumFacing face, BlockPos target, Vec3 hit, float yaw, float pitch) {
            this.support = support;
            this.face = face;
            this.target = target;
            this.hit = hit;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
