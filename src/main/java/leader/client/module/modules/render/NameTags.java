package leader.client.module.modules.render;

import leader.client.Leader;
import leader.client.util.misc.ChatColors;
import leader.client.event.EventTarget;
import leader.client.events.Render3DEvent;
import leader.mixin.accessor.IAccessorRenderManager;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.util.render.ColorUtil;
import leader.client.util.render.RenderUtil;
import leader.client.util.player.TeamUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NameTags extends Module {

    private static final DecimalFormat healthFormatter = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    public final SliderValue scale = new SliderValue("scale", 1.0, 0.5, 2.0, Representation.FLOAT, this);
    public final BoolValue autoScale = new BoolValue("auto-scale", true, this);
    public final SliderValue backgroundOpacity = new SliderValue("background", 25, 0, 100, Representation.INT, this);
    public final BoolValue shadow = new BoolValue("shadow", true, this);
    public final ListValue distanceMode = new ListValue("distance", new String[]{"NONE", "DEFAULT", "VAPE"}, "NONE", this);
    public final ListValue healthMode = new ListValue("health", new String[]{"NONE", "HP", "HEARTS", "TAB"}, "HEARTS", this);
    public final BoolValue armor = new BoolValue("armor", true, this);
    public final BoolValue effects = new BoolValue("effects", true, this);
    public final BoolValue players = new BoolValue("players", true, this);
    public final BoolValue friends = new BoolValue("friends", true, this);
    public final BoolValue enemies = new BoolValue("enemies", true, this);
    public final BoolValue bossees = new BoolValue("bosses", false, this);
    public final BoolValue mobs = new BoolValue("mobs", false, this);
    public final BoolValue creepers = new BoolValue("creepers", false, this);
    public final BoolValue endermans = new BoolValue("endermen", false, this);
    public final BoolValue blazes = new BoolValue("blazes", false, this);
    public final BoolValue animals = new BoolValue("animals", false, this);
    public final BoolValue self = new BoolValue("self", false, this);
    public final BoolValue bots = new BoolValue("bots", false, this);

    public NameTags() {
        super("NameTags", false);
    }

    public boolean shouldRenderTags(EntityLivingBase entityLivingBase) {
        if (entityLivingBase.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityLivingBase) > 512.0F) {
            return false;
        } else if (entityLivingBase instanceof EntityPlayer) {
            if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.getRenderViewEntity()) {
                if (TeamUtil.isBot((EntityPlayer) entityLivingBase)) {
                    return this.bots.getValue();
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return this.friends.getValue();
                } else {
                    return TeamUtil.isTarget((EntityPlayer) entityLivingBase) ? this.enemies.getValue() : this.players.getValue();
                }
            } else {
                return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
        } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
            return !entityLivingBase.isInvisible() && this.bossees.getValue();
        } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
            return (entityLivingBase instanceof EntityAnimal
                    || entityLivingBase instanceof EntityBat
                    || entityLivingBase instanceof EntitySquid
                    || entityLivingBase instanceof EntityVillager) && this.animals.getValue();
        } else if (entityLivingBase instanceof EntityCreeper) {
            return this.creepers.getValue();
        } else if (entityLivingBase instanceof EntityEnderman) {
            return this.endermans.getValue();
        } else {
            return entityLivingBase instanceof EntityBlaze ? this.blazes.getValue() : this.mobs.getValue();
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled()) {
            for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
                if (entity instanceof EntityLivingBase
                        && this.shouldRenderTags((EntityLivingBase) entity)
                        && (entity.ignoreFrustumCheck || RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 10.0))) {
                    String teamName = TeamUtil.stripName(entity);
                    if (!StringUtils.isBlank(EnumChatFormatting.getTextWithoutFormattingCodes(teamName))) {
                        double x = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
                        double y = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()
                                + (double) entity.getEyeHeight();
                        double z = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
                        double distance = mc.getRenderViewEntity().getDistanceToEntity(entity);
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(x, y + (entity.isSneaking() ? 0.225 : 0.4), z);
                        GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                        float view = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
                        GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0.0F, 0.0F);
                        double scaleFactor = Math.pow(Math.min(Math.max(this.autoScale.getValue() ? distance : 0.0, 6.0), 128.0), 0.75) * 0.0075;
                        GlStateManager.scale(-scaleFactor * (double) this.scale.getValue(), -scaleFactor * (double) this.scale.getValue(), 1.0);
                        String distanceText = "";
                        if (this.distanceMode.is("DEFAULT")) {
                            distanceText = String.format("&7%dm&r ", (int) distance);
                        } else if (this.distanceMode.is("VAPE")) {
                            distanceText = String.format("&a[&f%d&a]&r ", (int) distance);
                        }
                        float health = ((EntityLivingBase) entity).getHealth();
                        float absorption = ((EntityLivingBase) entity).getAbsorptionAmount();
                        float max = ((EntityLivingBase) entity).getMaxHealth();
                        float percent = Math.min(Math.max((health + absorption) / max, 0.0F), 1.0F);
                        String healText = "";
                        if (this.healthMode.is("HP")) {
                            healText = String.format(" %d%s", (int) health, absorption > 0.0F ? String.format(" &6%d&r", (int) absorption) : "&r");
                        } else if (this.healthMode.is("HEARTS")) {
                            healText = String.format(
                                    " %s%s",
                                    healthFormatter.format((double) health / 2.0),
                                    absorption > 0.0F ? String.format(" &6%s&r", healthFormatter.format((double) absorption / 2.0)) : "&r"
                            );
                        } else if (this.healthMode.is("TAB")) {
                            if (entity instanceof EntityPlayer) {
                                Scoreboard scoreboard = mc.theWorld.getScoreboard();
                                if (scoreboard != null) {
                                    ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
                                    if (objective != null) {
                                        Score score = scoreboard.getValueFromObjective(entity.getName(), objective);
                                        if (score != null) {
                                            healText = String.format(" &e%d&r", score.getScorePoints());
                                        }
                                    }
                                }
                            }
                        }
                        String color = ChatColors.formatColor(String.format("%s&f%s&r%s", distanceText, teamName, healText));
                        int width = FontManager.getStringWidth(color);
                        if (this.backgroundOpacity.getValue() > 0) {
                            Color textColor = !entity.isSneaking() && !entity.isInvisible()
                                    ? new Color(0.0F, 0.0F, 0.0F, this.backgroundOpacity.getValue().floatValue() / 100.0F)
                                    : new Color(0.33F, 0.0F, 0.33F, this.backgroundOpacity.getValue().floatValue() / 100.0F);
                            RenderUtil.enableRenderState();
                            RenderUtil.drawRect(
                                    (float) (-width) / 2.0F - 1.0F,
                                    (float) (-FontManager.getFontHeight()) - 1.0F,
                                    (float) width / 2.0F + (this.shadow.getValue() ? 1.0F : 0.0F),
                                    this.shadow.getValue() ? 0.0F : -1.0F,
                                    textColor.getRGB()
                            );
                            RenderUtil.disableRenderState();
                        }
                        GlStateManager.disableDepth();
                        FontManager.drawString(
                                        color,
                                        (float) (-width) / 2.0F,
                                        (float) (-FontManager.getFontHeight()),
                                        ColorUtil.getHealthBlend(percent).getRGB(),
                                        this.shadow.getValue()
                                );
                        GlStateManager.enableDepth();
                        if (entity instanceof EntityPlayer) {
                            int height = FontManager.getFontHeight() + 2;
                            if (this.armor.getValue()) {
                                ArrayList<ItemStack> renderingItems = new ArrayList<>();
                                for (int i = 4; i >= 0; i--) {
                                    ItemStack itemStack;
                                    if (i == 0) {
                                        itemStack = ((EntityPlayer) entity).getHeldItem();
                                    } else {
                                        itemStack = ((EntityPlayer) entity).inventory.armorInventory[i - 1];
                                    }
                                    if (itemStack != null) {
                                        renderingItems.add(itemStack);
                                    }
                                }
                                if (!renderingItems.isEmpty()) {
                                    int offset = renderingItems.size() * -8;
                                    for (int i = 0; i < renderingItems.size(); i++) {
                                        RenderUtil.renderItemInGUI(renderingItems.get(i), offset + i * 16, -height - 16);
                                    }
                                    height += 16;
                                }
                            }
                            if (this.effects.getValue()) {
                                List<PotionEffect> effectsList = ((EntityPlayer) entity)
                                        .getActivePotionEffects()
                                        .stream()
                                        .filter(potionEffect -> Potion.potionTypes[potionEffect.getPotionID()].hasStatusIcon())
                                        .collect(Collectors.toList());
                                if (!effectsList.isEmpty()) {
                                    GlStateManager.pushMatrix();
                                    GlStateManager.scale(0.5F, 0.5F, 1.0F);
                                    int offset = effectsList.size() * -9;
                                    for (int i = 0; i < effectsList.size(); i++) {
                                        RenderUtil.renderPotionEffect(effectsList.get(i), offset + i * 18, -(height * 2) - 18);
                                    }
                                    GlStateManager.popMatrix();
                                }
                            }
                            if (TeamUtil.isFriend((EntityPlayer) entity)) {
                                RenderUtil.enableRenderState();
                                float x1 = (float) (-width) / 2.0F - 1.0F;
                                view = (float) (-FontManager.getFontHeight()) - 1.0F;
                                float y1 = (float) width / 2.0F + 1.0F;
                                float offset = this.shadow.getValue() ? 0.0F : -1.0F;
                                int friendColor = Leader.friendComponent.getColor().getRGB();
                                RenderUtil.drawOutlineRect(x1, view, y1, offset, 1.5F, 0, friendColor);
                                RenderUtil.disableRenderState();
                            } else if (TeamUtil.isTarget((EntityPlayer) entity)) {
                                RenderUtil.enableRenderState();
                                float x1 = (float) (-width) / 2.0F - 1.0F;
                                view = (float) (-FontManager.getFontHeight()) - 1.0F;
                                float y1 = (float) width / 2.0F + 1.0F;
                                float offset = this.shadow.getValue() ? 0.0F : -1.0F;
                                int targetColor = Leader.targetComponent.getColor().getRGB();
                                RenderUtil.drawOutlineRect(x1, view, y1, offset, 1.5F, 0, targetColor);
                                RenderUtil.disableRenderState();
                            }
                        }
                        GlStateManager.popMatrix();
                    }
                }
            }
        }
    }
}
