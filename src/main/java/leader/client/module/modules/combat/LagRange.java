package leader.client.module.modules.combat;

import leader.client.Leader;
import leader.client.enums.BlinkModules;
import leader.client.event.EventTarget;
import leader.client.event.types.Priority;
import leader.client.events.PacketEvent;
import leader.client.events.Render3DEvent;
import leader.client.events.TickEvent;
import leader.mixin.IAccessorPlayerControllerMP;
import leader.mixin.IAccessorRenderManager;
import leader.client.module.Module;
import leader.client.module.modules.player.BedNuker;
import leader.client.module.modules.render.HUD;
import leader.client.util.player.ItemUtil;
import leader.client.util.render.RenderUtil;
import leader.client.util.player.RotationUtil;
import leader.client.util.player.TeamUtil;
import leader.client.property.properties.*;
import leader.client.property.properties.BooleanProperty;
import leader.client.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
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
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Delay Blink", "Lag"});
    public final IntProperty blinkTick = new IntProperty("Blink Tick", 3, 0, 10, () -> mode.getValue() == 0);
    public final IntProperty delay = new IntProperty("Delay", 150, 0, 1000, () -> mode.getValue() == 1);
    public final FloatProperty range = new FloatProperty("Range", 10.0F, 3.0F, 100.0F);
    public final BooleanProperty weaponsOnly = new BooleanProperty("Weapons Only", true);
    public final BooleanProperty allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty botCheck = new BooleanProperty("Bot Check", true);
    public final BooleanProperty teams = new BooleanProperty("Teams", true);
    public final ModeProperty showPosition = new ModeProperty("Show Position", 0, new String[]{"None", "Default", "Hud"});
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
                    if ((killAura.shouldAutoBlock() || killAura.isBlocking() && killAura.isEnabled() && killAura.getTarget() != null) && mode.getValue() == 0){
                        Leader.blinkManager.setBlinkState(false, BlinkModules.LAG_RANGE);
                        return;
                    }
                    Leader.lagManager.setDelay(0);
                    this.hasTarget = false;
                    BedNuker bedNuker = (BedNuker) Leader.moduleManager.modules.get(BedNuker.class);
                    if ((!bedNuker.isEnabled() || !bedNuker.isReady())
                            && !((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                            && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking())
                            && (
                            !(Boolean) this.weaponsOnly.getValue()
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
                            Leader.blinkManager.setBlinkState(false, BlinkModules.LAG_RANGE);
                            this.tickIndex = -1;
                        } else {
                            double height = mc.thePlayer.getEyeHeight();
                            Vec3 eyePosition = Leader.lagManager.getLastPosition().addVector(0.0, height, 0.0);
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
                                            for (this.delayCounter = this.delayCounter + (long) this.delay.getValue();
                                                 this.delayCounter > 0L;
                                                 this.delayCounter = this.delayCounter - 50
                                            ) {
                                                this.tickIndex++;
                                            }
                                        }
                                        if (mode.getValue() == 1) {
                                            Leader.lagManager.setDelay(this.tickIndex);
                                        }
                                        if (mode.getValue() == 0){
                                            Leader.blinkManager.setBlinkState(true, BlinkModules.LAG_RANGE);
                                            if (Leader.blinkManager.countMovement() > blinkTick.getValue().longValue()) {
                                                Leader.blinkManager.setBlinkState(false, BlinkModules.LAG_RANGE);
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
                        Leader.blinkManager.setBlinkState(false, BlinkModules.LAG_RANGE);
                    }
                    if (!hasTarget){
                        Leader.blinkManager.setBlinkState(false, BlinkModules.LAG_RANGE);
                    }
                    break;
                case POST:
                    Vec3 savedPosition = Leader.lagManager.getLastPosition();
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
                Leader.lagManager.setDelay(0);
                this.tickIndex = -1;
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            if (this.showPosition.getValue() != 0
                    && mc.gameSettings.thirdPersonView != 0
                    && this.hasTarget
                    && this.lastPosition != null
                    && this.currentPosition != null) {
                Color color = new Color(-1);
                switch (this.showPosition.getValue()) {
                    case 1:
                        color = TeamUtil.getTeamColor(mc.thePlayer, 1.0F);
                        break;
                    case 2:
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
        Leader.lagManager.setDelay(0);
        this.tickIndex = -1;
        this.delayCounter = 0L;
        this.hasTarget = false;
        this.lastPosition = null;
        this.currentPosition = null;
    }

    @Override
    public String[] getSuffix() {
        if (this.mode.getValue() == 1) {
            return new String[]{String.format("%dms", this.delay.getValue())};
        }
        else return new String[]{blinkTick.getValue().toString()};
    }
}
