package leader.client.module.modules.combat;

import leader.client.Leader;
import leader.client.component.impl.network.blink.BlinkType;
import leader.client.event.EventTarget;
import leader.client.event.types.Priority;
import leader.client.events.PacketEvent;
import leader.client.events.Render3DEvent;
import leader.client.events.TickEvent;
import leader.mixin.accessor.IAccessorPlayerControllerMP;
import leader.mixin.accessor.IAccessorRenderManager;
import leader.client.module.Module;
import leader.client.module.modules.player.BedNuker;
import leader.client.module.modules.render.HUD;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.module.values.impl.ListValue;
import leader.client.util.player.ItemUtil;
import leader.client.util.render.RenderUtil;
import leader.client.util.player.RotationUtil;
import leader.client.util.player.TeamUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class LagRange extends Module {
    public final ListValue mode = new ListValue("Mode", new String[]{"Delay Blink", "Lag"}, "Delay Blink", this);
    public final SliderValue blinkTick = new SliderValue("Blink Tick", 3, 0, 10, () -> mode.is("Delay Blink"), Representation.INT, this);
    public final SliderValue delay = new SliderValue("Delay", 150, 0, 1000, () -> mode.is("Lag"), Representation.INT, this);
    public final SliderValue range = new SliderValue("Range", 10.0, 3.0, 100.0, Representation.FLOAT, this);
    public final BoolValue weaponsOnly = new BoolValue("Weapons Only", true, this);
    public final BoolValue allowTools = new BoolValue("Allow Tools", false, this.weaponsOnly::getValue, this);
    public final BoolValue botCheck = new BoolValue("Bot Check", true, this);
    public final BoolValue teams = new BoolValue("Teams", true, this);
    public final ListValue showPosition = new ListValue("Show Position", new String[]{"None", "Default", "Hud"}, "None", this);
    private int tickIndex = -1;
    private long delayCounter = 0L;
    private boolean hasTarget = false;
    private Vec3 lastPosition = null;
    private Vec3 currentPosition = null;

    public LagRange() {
        super("LagRange", false);
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.teams.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean shouldResetOnPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            return true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
            return item == null || !(item.getItem() instanceof ItemSword);
        } else {
            return false;
        }
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    KillAura killAura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
                    if ((killAura.shouldAutoBlock() || killAura.isBlocking() && killAura.isEnabled() && killAura.getTarget() != null) && mode.is("Delay Blink")) {
                        Leader.blinkComponent.setBlinkState(false, BlinkType.LAG_RANGE);
                        return;
                    }
                    Leader.lagComponent.setDelay(0);
                    this.hasTarget = false;
                    BedNuker bedNuker = (BedNuker) Leader.moduleManager.modules.get(BedNuker.class);
                    if ((!bedNuker.isEnabled() || !bedNuker.isReady())
                            && !((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                            && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking())
                            && (
                            !this.weaponsOnly.getValue()
                                    || ItemUtil.hasRawUnbreakingEnchant()
                                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()
                    )) {
                        List<EntityPlayer> players = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityPlayer)
                                .map(entity -> (EntityPlayer) entity)
                                .filter(this::isValidTarget)
                                .collect(Collectors.toList());
                        if (players.isEmpty()) {
                            Leader.blinkComponent.setBlinkState(false, BlinkType.LAG_RANGE);
                            this.tickIndex = -1;
                        } else {
                            double height = mc.thePlayer.getEyeHeight();
                            Vec3 eyePosition = Leader.lagComponent.getLastPosition().addVector(0.0, height, 0.0);
                            Vec3 targetEyePosition = new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + height, mc.thePlayer.lastTickPosZ);
                            Vec3 playerEyePosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + height, mc.thePlayer.posZ);
                            for (EntityPlayer player : players) {
                                double distance = RotationUtil.distanceToBox(player, playerEyePosition);
                                if (!(distance > (double) this.range.getValue())) {
                                    double targetDist = RotationUtil.distanceToBox(player, targetEyePosition);
                                    double eyeDist = RotationUtil.distanceToBox(player, eyePosition);
                                    if (distance < targetDist || distance < eyeDist) {
                                        if (this.tickIndex < 0) {
                                            this.tickIndex = 0;
                                            for (this.delayCounter = this.delayCounter + (long) this.delay.getValue().intValue();
                                                 this.delayCounter > 0L;
                                                 this.delayCounter = this.delayCounter - 50
                                            ) {
                                                this.tickIndex++;
                                            }
                                        }
                                        if (mode.is("Lag")) {
                                            Leader.lagComponent.setDelay(this.tickIndex);
                                        }
                                        if (mode.is("Delay Blink")) {
                                            Leader.blinkComponent.setBlinkState(true, BlinkType.LAG_RANGE);
                                            if (Leader.blinkComponent.countMovement() > blinkTick.getValue().longValue()) {
                                                Leader.blinkComponent.setBlinkState(false, BlinkType.LAG_RANGE);
                                            }
                                        }
                                        this.hasTarget = true;
                                        return;
                                    }
                                }
                            }
                        }
                    } else {
                        this.tickIndex = -1;
                        Leader.blinkComponent.setBlinkState(false, BlinkType.LAG_RANGE);
                    }
                    if (!hasTarget) {
                        Leader.blinkComponent.setBlinkState(false, BlinkType.LAG_RANGE);
                    }
                    break;
                case POST:
                    Vec3 savedPosition = Leader.lagComponent.getLastPosition();
                    if (this.currentPosition == null) {
                        this.lastPosition = savedPosition;
                    } else {
                        this.lastPosition = this.currentPosition;
                    }
                    this.currentPosition = savedPosition;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            if (this.shouldResetOnPacket(event.getPacket())) {
                Leader.lagComponent.setDelay(0);
                this.tickIndex = -1;
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            if (!this.showPosition.is("None")
                    && mc.gameSettings.thirdPersonView != 0
                    && this.hasTarget
                    && this.lastPosition != null
                    && this.currentPosition != null) {
                Color color = new Color(-1);
                if (this.showPosition.is("Default")) {
                    color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
                } else if (this.showPosition.is("Hud")) {
                    color = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                double x = RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
                double y = RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
                double z = RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());
                float size = mc.thePlayer.getCollisionBorderSize();
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) mc.thePlayer.width / 2.0,
                        y,
                        z - (double) mc.thePlayer.width / 2.0,
                        x + (double) mc.thePlayer.width / 2.0,
                        y + (double) mc.thePlayer.height,
                        z + (double) mc.thePlayer.width / 2.0
                )
                        .expand(size, size, size)
                        .offset(
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                        );
                RenderUtil.enableRenderState();
                RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onDisabled() {
        Leader.lagComponent.setDelay(0);
        this.tickIndex = -1;
        this.delayCounter = 0L;
        this.hasTarget = false;
        this.lastPosition = null;
        this.currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        if (this.mode.is("Lag")) {
            return new String[]{String.format("%dms", this.delay.getValue().intValue())};
        } else {
            return new String[]{blinkTick.getValue().toString()};
        }
    }
}
