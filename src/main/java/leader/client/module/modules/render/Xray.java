package leader.client.module.modules.render;

import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.LoadWorldEvent;
import leader.client.events.PacketEvent;
import leader.client.events.Render3DEvent;
import leader.mixin.accessor.IAccessorMinecraft;
import leader.client.module.Module;
import leader.client.util.render.RenderUtil;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockMobSpawner;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S22PacketMultiBlockChange.BlockUpdateData;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3i;
import net.minecraftforge.common.ForgeModContainer;

import java.awt.*;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArraySet;

public class Xray extends Module {

    private static final LinkedHashSet<Integer> xrayBlocks;
    private static final LinkedHashSet<Vec3i> caveOffsetsSmall;
    private static final LinkedHashSet<Vec3i> caveOffsetsLarge;
    public final CopyOnWriteArraySet<BlockPos> trackedBlocks = new CopyOnWriteArraySet<>();
    public final CopyOnWriteArraySet<BlockPos> pendingBlocks = new CopyOnWriteArraySet<>();

    public final ListValue mode = new ListValue("mode", new String[]{"SOFT", "FULL"}, "SOFT", this);
    public final SliderValue opacity = new SliderValue("opacity", 50, 0, 100, Representation.INT, this);
    public final SliderValue range = new SliderValue("range", 64, 16, 512, Representation.INT, this);
    public final BoolValue cavesOnly = new BoolValue("caves-only", true, this);
    public final SliderValue caveRadius = new SliderValue("caves-radius", 2, 1, 2, Representation.INT, this);
    public final BoolValue diamonds = new BoolValue("diamonds", true, this);
    public final BoolValue diamondTracers = new BoolValue("diamonds-tracers", true, this);
    public final BoolValue gold = new BoolValue("gold", true, this);
    public final BoolValue goldTracers = new BoolValue("gold-tracers", true, this);
    public final BoolValue iron = new BoolValue("iron", false, this);
    public final BoolValue ironTracers = new BoolValue("iron-tracers", false, this);
    public final BoolValue coal = new BoolValue("coal", false, this);
    public final BoolValue coalTracers = new BoolValue("coal-tracers", false, this);
    public final BoolValue redstone = new BoolValue("redstone", false, this);
    public final BoolValue redStoneTracers = new BoolValue("redstone-tracers", false, this);
    public final BoolValue lapis = new BoolValue("lapis", false, this);
    public final BoolValue lapisTracers = new BoolValue("lapis-tracers", false, this);
    public final BoolValue emeralds = new BoolValue("emeralds", false, this);
    public final BoolValue emeraldsTracers = new BoolValue("emeralds-tracers", false, this);
    public final BoolValue spawners = new BoolValue("spawners", false, this);
    public final BoolValue spawnerTracers = new BoolValue("spawners-tracers", false, this);
    public final BoolValue canes = new BoolValue("canes", false, this);
    public final BoolValue canesTracers = new BoolValue("canes-tracers", false, this);
    public final BoolValue warts = new BoolValue("warts", false, this);
    public final BoolValue wartsTracers = new BoolValue("warts-tracers", false, this);

    private void clearAndReload() {
        this.trackedBlocks.clear();
        this.pendingBlocks.clear();
        if (this.isEnabled() && mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    private void renderOreHighlight(BlockPos blockPos, int blockId, Vec3 viewVector) {
        if (mc.thePlayer.getDistance(blockPos.getX(), blockPos.getY(), blockPos.getZ()) <= this.range.getValue().doubleValue()) {
            Color color = this.getOreColor(blockId);
            RenderUtil.drawBlockBoundingBox(blockPos, 1.0, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), 1.5F);
            if (this.shouldDrawTracer(blockId)) {
                RenderUtil.drawLine3D(
                        viewVector,
                        (double) blockPos.getX() + 0.5,
                        (double) blockPos.getY() + 0.5,
                        (double) blockPos.getZ() + 0.5,
                        (float) color.getRed() / 255.0F,
                        (float) color.getGreen() / 255.0F,
                        (float) color.getBlue() / 255.0F,
                        1.0F,
                        1.5F
                );
            }
        }
    }

    private Color getOreColor(int blockId) {
        switch (blockId) {
            case 14:
                return new Color(16777045);
            case 15:
                return new Color(16777215);
            case 16:
                return new Color(0);
            case 21:
                return new Color(5592575);
            case 52:
                return new Color(16733695);
            case 56:
                return new Color(5636095);
            case 73:
            case 74:
                return new Color(16733525);
            case 83:
                return new Color(11206570);
            case 115:
                return new Color(11141120);
            case 129:
                return new Color(5635925);
            default:
                return new Color(-1);
        }
    }

    private boolean shouldDrawTracer(int blockId) {
        switch (blockId) {
            case 14:
                return this.goldTracers.getValue();
            case 15:
                return this.ironTracers.getValue();
            case 16:
                return this.coalTracers.getValue();
            case 21:
                return this.lapisTracers.getValue();
            case 52:
                return this.spawnerTracers.getValue();
            case 56:
                return this.diamondTracers.getValue();
            case 73:
            case 74:
                return this.redStoneTracers.getValue();
            case 83:
                return this.canesTracers.getValue();
            case 115:
                return this.wartsTracers.getValue();
            case 129:
                return this.emeraldsTracers.getValue();
            default:
                return false;
        }
    }

    private boolean isValidCaveBlock(BlockPos pos) {
        if (mc.theWorld.isBlockLoaded(pos, false)) {
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            return block instanceof BlockMobSpawner || !block.isFullBlock() || !block.getMaterial().isOpaque() || block.canProvidePower();
        } else {
            return false;
        }
    }

    public Xray() {
        super("Xray", false);
        // Reload renderers when any ore-toggle or relevant setting changes
        Runnable reload = this::clearAndReload;
        this.mode.onChanged(reload);
        this.cavesOnly.onChanged(reload);
        this.caveRadius.onChanged(reload);
        this.diamonds.onChanged(reload);
        this.gold.onChanged(reload);
        this.iron.onChanged(reload);
        this.coal.onChanged(reload);
        this.redstone.onChanged(reload);
        this.lapis.onChanged(reload);
        this.emeralds.onChanged(reload);
        this.spawners.onChanged(reload);
        this.canes.onChanged(reload);
        this.warts.onChanged(reload);
    }

    public boolean shouldRenderSide(int blockId) {
        return xrayBlocks.contains(blockId);
    }

    public boolean isXrayBlock(int blockId) {
        switch (blockId) {
            case 14:
                return this.gold.getValue();
            case 15:
                return this.iron.getValue();
            case 16:
                return this.coal.getValue();
            case 21:
                return this.lapis.getValue();
            case 52:
                return this.spawners.getValue();
            case 56:
                return this.diamonds.getValue();
            case 73:
            case 74:
                return this.redstone.getValue();
            case 83:
                return this.canes.getValue();
            case 115:
                return this.warts.getValue();
            case 129:
                return this.emeralds.getValue();
            default:
                return false;
        }
    }

    public boolean checkBlock(BlockPos blockPos) {
        if (!this.cavesOnly.getValue()) {
            return true;
        } else {
            if (this.caveRadius.getValue().intValue() >= 2) {
                for (Vec3i vec3i : caveOffsetsLarge) {
                    if (this.isValidCaveBlock(blockPos.add(vec3i))) {
                        return true;
                    }
                }
            } else {
                for (Vec3i vec3i : caveOffsetsSmall) {
                    if (this.isValidCaveBlock(blockPos.add(vec3i))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            Vec3 vec3;
            if (mc.gameSettings.thirdPersonView == 0) {
                vec3 = new Vec3(0.0, 0.0, 1.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationPitch,
                                                        mc.getRenderViewEntity().prevRotationPitch,
                                                        ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationYaw,
                                                        mc.getRenderViewEntity().prevRotationYaw,
                                                        ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        );
            } else {
                vec3 = new Vec3(0.0, 0.0, 0.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                                        )
                                )
                        );
            }
            vec3 = new Vec3(vec3.xCoord, vec3.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), vec3.zCoord);
            RenderUtil.enableRenderState();
            for (BlockPos blockPos : this.trackedBlocks) {
                if (this.pendingBlocks.contains(blockPos)) {
                    this.trackedBlocks.remove(blockPos);
                } else {
                    int id = Block.getIdFromBlock(mc.theWorld.getBlockState(blockPos).getBlock());
                    if (this.isXrayBlock(id)) {
                        this.renderOreHighlight(blockPos, id, vec3);
                    } else {
                        this.trackedBlocks.remove(blockPos);
                    }
                }
            }
            for (BlockPos blockPos : this.pendingBlocks) {
                int id = Block.getIdFromBlock(mc.theWorld.getBlockState(blockPos).getBlock());
                if (this.isXrayBlock(id)) {
                    this.renderOreHighlight(blockPos, id, vec3);
                } else {
                    this.pendingBlocks.remove(blockPos);
                }
            }
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S22PacketMultiBlockChange) {
                for (BlockUpdateData blockUpdateData : ((S22PacketMultiBlockChange) event.getPacket()).getChangedBlocks()) {
                    if (this.isXrayBlock(Block.getIdFromBlock(blockUpdateData.getBlockState().getBlock()))) {
                        this.pendingBlocks.add(new BlockPos(blockUpdateData.getPos()));
                    }
                }
            } else if (event.getPacket() instanceof S23PacketBlockChange) {
                S23PacketBlockChange packet = (S23PacketBlockChange) event.getPacket();
                if (this.isXrayBlock(Block.getIdFromBlock(packet.getBlockState().getBlock()))) {
                    this.pendingBlocks.add(new BlockPos(packet.getBlockPosition()));
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.trackedBlocks.clear();
        this.pendingBlocks.clear();
    }

    @Override
    public void onEnabled() {
        ForgeModContainer.forgeLightPipelineEnabled = false;
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    @Override
    public void onDisabled() {
        ForgeModContainer.forgeLightPipelineEnabled = true;
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    static {
        xrayBlocks = new LinkedHashSet<>(Arrays.asList(56, 14, 15, 16, 73, 74, 21, 129, 52, 83, 115));
        caveOffsetsSmall = new LinkedHashSet<>(
                Arrays.asList(new Vec3i(0, -1, 0), new Vec3i(1, 0, 0), new Vec3i(0, 0, -1), new Vec3i(0, 0, 1), new Vec3i(-1, 0, 0), new Vec3i(0, 1, 0))
        );
        caveOffsetsLarge = new LinkedHashSet<>(
                Arrays.asList(
                        new Vec3i(0, -2, 0),
                        new Vec3i(1, -1, 0),
                        new Vec3i(0, -1, -1),
                        new Vec3i(0, -1, 0),
                        new Vec3i(0, -1, 1),
                        new Vec3i(-1, -1, 0),
                        new Vec3i(2, 0, 0),
                        new Vec3i(0, 0, 2),
                        new Vec3i(0, 0, -2),
                        new Vec3i(-2, 0, 0),
                        new Vec3i(1, 0, -1),
                        new Vec3i(1, 0, 0),
                        new Vec3i(1, 0, 1),
                        new Vec3i(0, 0, -1),
                        new Vec3i(0, 0, 1),
                        new Vec3i(-1, 0, -1),
                        new Vec3i(-1, 0, 0),
                        new Vec3i(-1, 0, 1),
                        new Vec3i(1, 1, 0),
                        new Vec3i(0, 1, -1),
                        new Vec3i(0, 1, 0),
                        new Vec3i(0, 1, 1),
                        new Vec3i(-1, 1, 0),
                        new Vec3i(0, 2, 0)
                )
        );
    }
}
