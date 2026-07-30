package leader.mixin;

import leader.client.Leader;
import leader.client.module.modules.render.Animations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {ItemRenderer.class}, priority = 9999)
public abstract class MixinItemRenderer {
    @Shadow
    private float prevEquippedProgress;

    @Shadow
    private float equippedProgress;

    @Shadow
    @Final
    private Minecraft mc;

    @Shadow
    private ItemStack itemToRender;

    @Shadow
    protected abstract void rotateArroundXAndY(float angle, float angleY);

    @Shadow
    protected abstract void setLightMapFromPlayer(AbstractClientPlayer clientPlayer);

    @Shadow
    protected abstract void rotateWithPlayerRotations(EntityPlayerSP entityplayerspIn, float partialTicks);

    @Shadow
    protected abstract void renderItemMap(AbstractClientPlayer clientPlayer, float pitch, float equipmentProgress, float swingProgress);

    @Shadow
    protected abstract void performDrinking(AbstractClientPlayer clientPlayer, float partialTicks);

    @Shadow
    protected abstract void doBlockTransformations();

    @Shadow
    protected abstract void doBowTransformations(float partialTicks, AbstractClientPlayer clientPlayer);

    @Shadow
    public abstract void renderItem(EntityLivingBase entityIn, ItemStack heldStack, ItemCameraTransforms.TransformType transform);

    @Shadow
    protected abstract void renderPlayerArm(AbstractClientPlayer clientPlayer, float equipProgress, float swingProgress);

    @Unique
    private void myau$transformFirstPersonItem(float equipProgress, float swingProgress) {
        GlStateManager.translate(0.56F, -0.52F, -0.72F);
        GlStateManager.translate(0.0F, equipProgress * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        GlStateManager.rotate(f * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
    }

    @Unique
    private void myau$func_178105_d(final float swingProgress) {
        final float f = -0.4F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        final float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI * 2.0F);
        final float f2 = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translate(f, f1, f2);
    }

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void myau$renderItemInFirstPerson(float partialTicks, CallbackInfo ci) {
        if (Leader.moduleManager == null) {
            return;
        }

        Animations animations = (Animations) Leader.moduleManager.modules.get(Animations.class);
        if (animations == null || !animations.isEnabled()) {
            return;
        }

        final float equipProgress = 1.0F - (this.prevEquippedProgress + (this.equippedProgress - this.prevEquippedProgress) * partialTicks);
        final EntityPlayerSP player = this.mc.thePlayer;
        if (player == null) {
            return;
        }

        final float swingProgress = player.getSwingProgress(partialTicks);
        final float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
        final float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;

        if (animations.mode.getValue() == 3
                && this.itemToRender != null
                && this.itemToRender.getItem() instanceof net.minecraft.item.ItemSword) {

            this.rotateArroundXAndY(pitch, yaw);
            this.setLightMapFromPlayer(player);
            this.rotateWithPlayerRotations(player, partialTicks);

            GlStateManager.pushMatrix();
            GlStateManager.enableRescaleNormal();
            float fovScale = Math.max(
                    0.1F,
                    1.0F - animations.itemFov.getValue() * 0.1F
            );

            GlStateManager.scale(
                    1.0F,
                    1.0F,
                    fovScale
            );

            Animations.renderDragonClaws(
                    partialTicks,
                    equipProgress,
                    swingProgress
            );

            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();

            GlStateManager.color(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            RenderHelper.disableStandardItemLighting();
            ci.cancel();
            return;
        }

        GL11.glTranslated(animations.itemPosX.getValue().doubleValue(), animations.itemPosY.getValue().doubleValue(), animations.itemPosZ.getValue().doubleValue());
        this.rotateArroundXAndY(pitch, yaw);
        this.setLightMapFromPlayer(player);
        this.rotateWithPlayerRotations(player, partialTicks);
        GlStateManager.scale(1.0F, 1.0F, -animations.itemFov.getValue() + 1.0F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();

        GL11.glTranslated(animations.itemPosX.getValue().doubleValue(), animations.itemPosY.getValue().doubleValue(), animations.itemPosZ.getValue().doubleValue());

        if (this.itemToRender != null) {
            if (this.itemToRender.getItem() instanceof ItemMap) {
                this.renderItemMap(player, pitch, equipProgress, swingProgress);
            } else {
                boolean isUsingItem = player.getItemInUseCount() > 0;
                EnumAction action = isUsingItem ? this.itemToRender.getItemUseAction() : EnumAction.NONE;

                boolean isBlocking = isUsingItem && action == EnumAction.BLOCK;
                boolean cancelEquip = animations.cancelEquip.getValue() && (!animations.cancelEquipBlockingOnly.getValue() || isBlocking);
                float equip = cancelEquip ? 0.0F : equipProgress;

                if (action == EnumAction.NONE) {
                    this.myau$func_178105_d(swingProgress);
                    this.myau$transformFirstPersonItem(equip, swingProgress);
                    GlStateManager.scale(animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F);
                } else if (action == EnumAction.EAT || action == EnumAction.DRINK) {
                    this.performDrinking(player, partialTicks);
                    this.myau$transformFirstPersonItem(equip, 0.0F);
                    GlStateManager.scale(animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F);
                } else if (action == EnumAction.BLOCK) {
                    String z = animations.mode.getModeString();
                    switch (z) {
                        case "1.8": {
                            GL11.glTranslated(animations.blockPosX.getValue().doubleValue(), animations.blockPosY.getValue().doubleValue(), animations.blockPosZ.getValue().doubleValue());
                            this.myau$transformFirstPersonItem(cancelEquip ? 0.0F : equipProgress, 0.0f);
                            doBlockTransformations();
                            break;
                        }
                        case "Swing": {
                            GL11.glTranslated(animations.blockPosX.getValue().doubleValue(), animations.blockPosY.getValue().doubleValue(), animations.blockPosZ.getValue().doubleValue());
                            this.myau$transformFirstPersonItem(cancelEquip ? 0.0F : equipProgress, swingProgress);
                            this.doBlockTransformations();
                            break;
                        }
                        case "Push": {
                            GL11.glTranslated(animations.blockPosX.getValue().doubleValue(), animations.blockPosY.getValue().doubleValue(), animations.blockPosZ.getValue().doubleValue());
                            final float var9 = MathHelper.sin(MathHelper.sqrt_float(this.mc.thePlayer.getSwingProgress(partialTicks)) * (float) Math.PI);
                            this.myau$transformFirstPersonItem(cancelEquip ? 0.0F : (equipProgress / 2.5f), 0.0f);
                            GlStateManager.rotate(-var9 * 40.0F / 2.0F, var9 / 2.0F, 1.0F, 4.0F);
                            GlStateManager.rotate(-var9 * 30.0F, 1.0F, var9 / 3.0F, -0.0F);
                            doBlockTransformations();
                            break;
                        }
                    }

                    GlStateManager.scale(animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F);
                } else if (action == EnumAction.BOW) {
                    this.myau$transformFirstPersonItem(equip, 0.0F);
                    this.doBowTransformations(partialTicks, player);
                    GlStateManager.scale(animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F);
                } else {
                    this.myau$transformFirstPersonItem(equip, swingProgress);
                    GlStateManager.scale(animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F, animations.itemSize.getValue() + 1.0F);
                }
                this.renderItem(player, this.itemToRender, ItemCameraTransforms.TransformType.FIRST_PERSON);
            }
        } else if (!player.isInvisible()) {
            this.renderPlayerArm(player, equipProgress, swingProgress);
        }

        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GL11.glTranslated(-animations.itemPosX.getValue().doubleValue(), -animations.itemPosY.getValue().doubleValue(), -animations.itemPosZ.getValue().doubleValue());

        ci.cancel();
    }
}