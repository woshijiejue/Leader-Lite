package leader.module.modules.render;

import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.block.Block;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockDeadBush;
import net.minecraft.block.BlockMushroom;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.client.renderer.entity.layers.LayerArrow;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;

public class BetterFPS extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public BetterFPS() {
        super("BetterFPS", false);
    }

    public static BooleanProperty fastLoad = new BooleanProperty("FastLoad", true);
    public static BooleanProperty entityOptimize = new BooleanProperty("EntityOptimize", false);
    public static ModeProperty entityLevel = new ModeProperty("EntityLevel", 0, new String[]{"Normal", "Fast", "Extreme"}, () -> entityOptimize.getValue());
    public static final IntProperty entityDistance = new IntProperty("EntityDistance", 96, 32, 192, entityOptimize::getValue);
    public static final BooleanProperty keepPlayers = new BooleanProperty("KeepPlayers", true, entityOptimize::getValue);
    public static final BooleanProperty cosmeticCull = new BooleanProperty("CosmeticCull", true);
    public static final IntProperty cosmeticDistance = new IntProperty("CosmeticDistance", 32, 8, 96, cosmeticCull::getValue);
    public static final BooleanProperty particleOptimize = new BooleanProperty("ParticleOptimize", true);
    public static final IntProperty particleDistance = new IntProperty("ParticleDistance", 32, 8, 96, particleOptimize::getValue);
    public static final IntProperty particleBudget = new IntProperty("ParticleBudget", 128, 16, 512, particleOptimize::getValue);
    public static final BooleanProperty tileEntityOptimize = new BooleanProperty("TileEntityOptimize", true);
    public static final IntProperty tileEntityDistance = new IntProperty("TileEntityDistance", 64, 16, 128, tileEntityOptimize::getValue);
    public static final BooleanProperty noEntityShadows = new BooleanProperty("NoEntityShadows", true);
    public static final BooleanProperty noWeather = new BooleanProperty("NoWeather", false);
    public static final BooleanProperty noClouds = new BooleanProperty("NoClouds", false);
    public static final BooleanProperty hudOptimize = new BooleanProperty("HUDOptimize", true);
    public static final BooleanProperty shaderBudget = new BooleanProperty("ShaderBudget", true);
    public static final IntProperty shaderPasses = new IntProperty("ShaderPasses", 2, 1, 4, shaderBudget::getValue);
    public static final BooleanProperty cacheRenderLists = new BooleanProperty("CacheRenderLists", true);
    public static final ModeProperty clientMode = new ModeProperty("ClientMode", 0, new String[]{"Balanced", "Aggressive"});
    public static final BooleanProperty temporalBlur = new BooleanProperty("TemporalBlur", true);
    public static final IntProperty blurRefreshRate = new IntProperty("BlurRefreshRate", 30, 10, 120, temporalBlur::getValue);
    public static final BooleanProperty reducedGlow = new BooleanProperty("ReducedGlow", true);
    public static final BooleanProperty skipExtraPostFX = new BooleanProperty("SkipExtraPostFX", true, () -> clientMode.getValue() == 1);
    public static final BooleanProperty chunkOptimize = new BooleanProperty("ChunkOptimize", true);
    public static final IntProperty chunkUpdateBudget = new IntProperty("ChunkUpdateBudget", 4, 1, 12, chunkOptimize::getValue);
    public static final BooleanProperty fastBlockLighting = new BooleanProperty("FastBlockLighting", false);
    public static final BooleanProperty asyncChunkBuild = new BooleanProperty("AsyncChunkBuild", false, chunkOptimize::getValue);
    public static final BooleanProperty decorationCull = new BooleanProperty("DecorationCull", false);
    public static final BooleanProperty hideGrass = new BooleanProperty("HideGrass", true, decorationCull::getValue);
    public static final BooleanProperty hideFlowers = new BooleanProperty("HideFlowers", true, decorationCull::getValue);
    public static final BooleanProperty hideMushrooms = new BooleanProperty("HideMushrooms", false, decorationCull::getValue);
    public static final BooleanProperty hideItems = new BooleanProperty("HideDroppedItems", false);
    public static final BooleanProperty hideXP = new BooleanProperty("HideXPOrbs", false);
    public static final BooleanProperty hideArmorStands = new BooleanProperty("HideArmorStands", false);
    public static final BooleanProperty modelLOD = new BooleanProperty("ModelLOD", false);
    public static final IntProperty modelLODDistance = new IntProperty("ModelLODDistance", 24, 8, 96, modelLOD::getValue);
    public static final BooleanProperty skipDistantArmor = new BooleanProperty("SkipDistantArmor", false, modelLOD::getValue);
    public static final BooleanProperty skipDistantHeldItems = new BooleanProperty("SkipDistantHeldItems", false, modelLOD::getValue);
    public static final BooleanProperty singleItemModel = new BooleanProperty("SingleItemModel", true, modelLOD::getValue);
    public static final BooleanProperty textLayoutCache = new BooleanProperty("TextLayoutCache", true);
    public static final ModeProperty blurResolution = new ModeProperty("BlurResolution", 1,
            new String[]{"Full", "Half", "Quarter"}, shaderBudget::getValue);
    public static volatile boolean using = false;
    private static volatile int decorationMask;
    private int appliedDecorationMask;
    private static long particleWindow;
    private static int acceptedParticles;
    private boolean appliedBlockLighting;
    private boolean reloadLighting;

    public static boolean shouldCancelEntity(Entity entity) {
        if (!using) return false;
        Entity camera = mc.getRenderViewEntity();
        if (mc.theWorld == null || camera == null || entity == camera || entity == mc.thePlayer
                || entity.isRiding() || entity.riddenByEntity != null) {
            return false;
        }
        double distanceSq = entity.getDistanceSqToEntity(camera);
        // These switches hide the category entirely. They affect drawing only, never entity ticks or interaction.
        if (hideItems.getValue() && entity instanceof EntityItem) return true;
        if (hideXP.getValue() && entity instanceof EntityXPOrb) return true;
        if (hideArmorStands.getValue() && entity instanceof EntityArmorStand) return true;
        if (cosmeticCull.getValue() && (entity instanceof EntityItem || entity instanceof EntityXPOrb
                || entity instanceof EntityArmorStand)) {
            int range = cosmeticDistance.getValue();
            if (distanceSq > range * range) return true;
        }
        // Never hide arrows/fireballs/pearls: the optional distance filter is for living models only.
        if (!entityOptimize.getValue() || !(entity instanceof net.minecraft.entity.EntityLivingBase)
                || (keepPlayers.getValue() && entity instanceof EntityPlayer)) return false;
        int range = aggressive() ? Math.min(entityDistance.getValue(), 32) : entityDistance.getValue();
        if (entityLevel.getValue() == 1) range = Math.min(range, 64);
        else if (entityLevel.getValue() == 2) range = Math.min(range, 32);
        return distanceSq > range * range;
    }

    public static boolean shouldCancelParticle(Entity particle) {
        if (!using || !particleOptimize.getValue() || mc.theWorld == null || mc.getRenderViewEntity() == null) return false;
        int range = aggressive() ? Math.min(particleDistance.getValue(), 16) : particleDistance.getValue();
        if (particle.getDistanceSqToEntity(mc.getRenderViewEntity()) > range * range) return true;
        // Admission budget, not simulation skipping: accepted particles keep vanilla lifetimes and motion.
        long window = System.nanoTime() / 50000000L;
        if (particleWindow != window) {
            particleWindow = window;
            acceptedParticles = 0;
        }
        return acceptedParticles++ >= (aggressive() ? Math.min(64, particleBudget.getValue()) : particleBudget.getValue());
    }

    public static boolean shouldCancelTileEntity(TileEntity tile, int destroyStage) {
        if (!using || !tileEntityOptimize.getValue() || destroyStage >= 0 || mc.theWorld == null
                || mc.getRenderViewEntity() == null || tile.getWorld() != mc.theWorld) return false;
        int range = aggressive() ? Math.min(tileEntityDistance.getValue(), 32) : tileEntityDistance.getValue();
        if (tile instanceof TileEntitySign) range = Math.min(range, 32);
        Entity camera = mc.getRenderViewEntity();
        return tile.getDistanceSq(camera.posX, camera.posY, camera.posZ) > range * range;
    }

    public static boolean optimizedHUD() {
        return using && hudOptimize.getValue();
    }

    public static boolean cachedTextLayout() {
        return optimizedHUD() && textLayoutCache.getValue();
    }

    public static int blurResolutionDivisor() {
        return using && shaderBudget.getValue() ? 1 << blurResolution.getValue() : 1;
    }

    public static int limitShaderPasses(int requested) {
        return using && shaderBudget.getValue() ? Math.min(requested, shaderPasses.getValue()) : requested;
    }

    public static boolean cachedRenderLists() {
        return using && cacheRenderLists.getValue();
    }

    public static boolean aggressive() {
        return using && clientMode.getValue() == 1;
    }

    public static int blurRefreshRate() {
        if (!using || !temporalBlur.getValue()) return 0;
        return aggressive() ? Math.min(20, blurRefreshRate.getValue()) : blurRefreshRate.getValue();
    }

    public static int glowPasses(int requested) {
        return using && reducedGlow.getValue() ? Math.min(aggressive() ? 1 : 2, requested) : requested;
    }

    public static boolean skipExtraPostFX() {
        return aggressive() && skipExtraPostFX.getValue();
    }

    public static long chunkUpdateDeadline(long vanillaDeadline) {
        if (!using || !chunkOptimize.getValue()) return vanillaDeadline;
        long budget = (long) chunkUpdateBudget.getValue() * 1000000L;
        if (aggressive()) budget = Math.min(budget, 2000000L);
        return Math.min(vanillaDeadline, System.nanoTime() + budget);
    }

    public static boolean useFastBlockLighting() {
        return using && fastBlockLighting.getValue();
    }

    private static int requestedDecorationMask() {
        if (!using || !decorationCull.getValue()) return 0;
        return (hideGrass.getValue() ? 1 : 0) | (hideFlowers.getValue() ? 2 : 0) | (hideMushrooms.getValue() ? 4 : 0);
    }

    public static boolean shouldCancelBlock(IBlockState state) {
        // Worker threads see a consistent published category mask; no distance-based holes in cached chunk meshes.
        int mask = using ? decorationMask : 0;
        if (mask == 0) return false;
        Block block = state.getBlock();
        if (block instanceof BlockTallGrass || block instanceof BlockDeadBush) return (mask & 1) != 0;
        if (block instanceof BlockDoublePlant) {
            // Upper-half metadata doesn't identify grass vs flowers; cull all double plants only
            // when both categories are selected, keeping two-block plants visually consistent.
            return (mask & 3) == 3;
        }
        if (block instanceof BlockFlower) return (mask & 2) != 0;
        return block instanceof BlockMushroom && (mask & 4) != 0;
    }

    public static boolean distantModel(Entity entity) {
        if (!using || !modelLOD.getValue() || mc.theWorld == null || mc.getRenderViewEntity() == null
                || entity == mc.thePlayer || entity == mc.getRenderViewEntity() || entity.isRiding()
                || entity.riddenByEntity != null || (mc.objectMouseOver != null && mc.objectMouseOver.entityHit == entity)) return false;
        int range = modelLODDistance.getValue();
        return entity.getDistanceSqToEntity(mc.getRenderViewEntity()) > range * range;
    }

    public static boolean shouldCancelLayer(EntityLivingBase entity, LayerRenderer<?> layer) {
        if (!distantModel(entity)) return false;
        // Never skip the base model, damage tint or unknown mod layers.
        if (layer instanceof LayerCape || layer instanceof LayerArrow) return true;
        return (skipDistantArmor.getValue() && layer instanceof LayerArmorBase)
                || (skipDistantHeldItems.getValue() && layer instanceof LayerHeldItem);
    }


    @Override
    public void verifyValue(String name) {
        if ("FastBlockLighting".equals(name)) reloadLighting = true;
        decorationMask = requestedDecorationMask();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.POST) return;
        boolean lighting = useFastBlockLighting();
        if (lighting != appliedBlockLighting) reloadLighting = true;
        decorationMask = requestedDecorationMask();
        if (decorationMask != appliedDecorationMask) reloadLighting = true;
        if (reloadLighting && mc.theWorld != null && mc.renderGlobal != null) {
            // Rebuild existing meshes once when toggled so old AO geometry doesn't remain cached.
            // Do not clear queues or resubmit the entire world on every frame.
            reloadLighting = false;
            appliedBlockLighting = lighting;
            appliedDecorationMask = decorationMask;
            mc.renderGlobal.loadRenderers();
        }
    }

    @Override
    public void onEnabled() {
        using = true;
        decorationMask = requestedDecorationMask();
        acceptedParticles = 0;
        particleWindow = 0;
    }

    @Override
    public void onDisabled() {
        using = false;
        decorationMask = 0;
    }
}
