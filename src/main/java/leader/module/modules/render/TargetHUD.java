package leader.module.modules.render;

import leader.Leader;
import leader.enums.ChatColors;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.PacketEvent;
import leader.events.Render2DEvent;
import leader.events.Render3DEvent;
import leader.mixin.IAccessorRenderManager;
import leader.module.Module;
import leader.module.modules.combat.KillAura;
import leader.module.modules.render.HUD;
import leader.util.*;
import leader.util.shader.ShaderElement;
import leader.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormat = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat diffFormat = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target = null;
    private ResourceLocation headTexture = null;
    private float oldHealth = 0.0F;
    private float newHealth = 0.0F;
    private float maxHealth = 0.0F;
    private float lastObservedHealth = Float.NaN;
    private final List<HitParticle> hitParticles = new ArrayList<>();
    private boolean renderingFollow = false;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"DEFAULT", "TRIANGLE", "BACKGROUND", "MODERN", "INK", "AURA", "FROST", "SLATE"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "HUD"});
    public final ModeProperty position = new ModeProperty("position", 0, new String[]{"SCREEN", "FOLLOW"});
    public final ModeProperty posX = new ModeProperty("position-x", 1, new String[]{"LEFT", "MIDDLE", "RIGHT"}, () -> this.position.getValue() == 0);
    public final ModeProperty posY = new ModeProperty("position-y", 1, new String[]{"TOP", "MIDDLE", "BOTTOM"}, () -> this.position.getValue() == 0);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final FloatProperty fontScale = new FloatProperty("font-scale", 1.15F, 0.85F, 1.5F);
    public final IntProperty offX = new IntProperty("offset-x", 0, -255, 255);
    public final IntProperty offY = new IntProperty("offset-y", 40, -255, 255);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty backgroundHUDColor = new BooleanProperty("BackgroundHUDColor",true);
    public final ColorProperty backgroundColor = new ColorProperty("background-color", Color.BLACK.getRGB(),() -> !backgroundHUDColor.getValue());
    public final BooleanProperty head = new BooleanProperty("head", true);
    public final BooleanProperty indicator = new BooleanProperty("indicator", true, () -> this.mode.getValue() != 2);
    public final BooleanProperty outline = new BooleanProperty("outline", false, () -> this.mode.getValue() != 2);
    public final BooleanProperty animations = new BooleanProperty("animations", true, () -> this.mode.getValue() != 2);
    public final BooleanProperty shadow = new BooleanProperty("shadow", false);
    public final BooleanProperty kaOnly = new BooleanProperty("ka-only", true);
    public final BooleanProperty chatPreview = new BooleanProperty("chat-preview", false);
    public final BooleanProperty frostEquip = new BooleanProperty("frost-equip", true, () -> this.mode.getValue() == 6);
    public final BooleanProperty frostLag = new BooleanProperty("frost-lag", true, () -> this.mode.getValue() == 6);
    public final BooleanProperty frostPop = new BooleanProperty("frost-pop", true, () -> this.mode.getValue() == 6);
    public final BooleanProperty frostGlass = new BooleanProperty("frost-glass", true, () -> this.mode.getValue() == 6);
    private float slateLag = 0.0F;
    private long slateLastFrame = 0L;
    private EntityLivingBase slateTarget = null;
    private float frostLagRatio = -1.0F;
    private float frostFade = 0.0F;
    private float frostPopScale = 1.0F;
    private float frostPopVel = 0.0F;
    private int frostLastHurt = 0;
    private long frostLastFrame = 0L;
    private EntityLivingBase frostFadeTarget = null;

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed() && TeamUtil.isEntityLoaded(killAura.getTarget())) {
            return killAura.getTarget();
        } else if (!(Boolean) this.kaOnly.getValue()
                && !this.lastAttackTimer.hasTimeElapsed(1500L)
                && TeamUtil.isEntityLoaded(this.lastTarget)) {
            return this.lastTarget;
        } else {
            return this.chatPreview.getValue() && mc.currentScreen instanceof GuiChat ? mc.thePlayer : null;
        }
    }

    private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
            if (playerInfo != null) {
                return playerInfo.getLocationSkin();
            }
        }
        return null;
    }

    private Color getTargetColor(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                return Leader.friendManager.getColor();
            }
            if (TeamUtil.isTarget((EntityPlayer) entityLivingBase)) {
                return Leader.targetManager.getColor();
            }
        }
        switch (this.color.getValue()) {
            case 0:
                if (!(entityLivingBase instanceof EntityPlayer)) {
                    return new Color(-1);
                }
                return TeamUtil.getTeamColor((EntityPlayer) entityLivingBase, 1.0F);
            case 1:
                int rgb = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                return new Color(rgb);
            default:
                return new Color(-1);
        }
    }

    public TargetHUD() {
        super("TargetHUD", false, true);
    }

    private int getBackgroundColor() {
        HUD hud = (HUD) Leader.moduleManager.getModule(HUD.class);
        if (!backgroundHUDColor.getValue()) {
            return new Color((this.backgroundColor.getValue() >> 16) & 255, (this.backgroundColor.getValue() >> 8) & 255,
                    this.backgroundColor.getValue() & 255, this.getBackgroundAlpha()).getRGB();
        }
        else return new Color(hud.getColor(System.currentTimeMillis()).getRed(),hud.getColor(System.currentTimeMillis()).getGreen(),hud.getColor(System.currentTimeMillis()).getBlue(),this.getBackgroundAlpha()).getRGB();
    }

    private int getBackgroundOverlayColor(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), this.getBackgroundAlpha() / 3).getRGB();
    }

    private int getBackgroundAlpha() {
        return Math.round((float) this.background.getValue() / 100.0F * 255.0F);
    }

    private void drawOutline(float x1, float y1, float x2, float y2, float width, int color) {
        RenderUtil.enableRenderState();
        RenderUtil.drawLine(x1, y1, x2, y1, width, color);
        RenderUtil.drawLine(x2, y1, x2, y2, width, color);
        RenderUtil.drawLine(x2, y2, x1, y2, width, color);
        RenderUtil.drawLine(x1, y2, x1, y1, width, color);
        RenderUtil.disableRenderState();
    }

    private float getTextScale() {
        return Math.max(8.0F, Math.min(32.0F, this.fontScale.getValue() * 12.0F));
    }

    private float getTextWidth(String text) {
        return FontManager.getStringWidth(this.stripFormat(text), this.getTextScale());
    }

    private float getTextHeight() {
        return FontManager.getFontHeight(this.getTextScale());
    }

    private String stripFormat(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private void drawText(String text, float x, float y, int color) {
        FontManager.drawString(this.stripFormat(text), x, y, color, this.shadow.getValue(), this.getTextScale());
    }

    private float getCardHeight() {
        return Math.max(27.0F, this.getTextHeight() * 2.0F + 9.0F);
    }

    private float getTextTop() {
        return 2.0F;
    }

    private float getSecondTextY() {
        return this.getTextTop() + this.getTextHeight() + 2.0F;
    }

    private float getHealthBarY() {
        return this.getCardHeight() - 5.0F;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || this.position.getValue() != 1) {
            return;
        }

        EntityLivingBase targetEntity = this.resolveTarget();
        if (targetEntity == null || targetEntity == mc.thePlayer) {
            return;
        }

        this.updateTargetState(targetEntity);
        this.renderFollow(event.getPartialTicks(), targetEntity);
    }

    private void updateTargetState(EntityLivingBase targetEntity) {
        EntityLivingBase previousTarget = this.target;
        this.target = targetEntity;
        float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
        float abs = targetEntity.getAbsorptionAmount() / 2.0F;
        float heal = targetEntity.getHealth() / 2.0F + abs;
        if (targetEntity != previousTarget) {
            this.headTexture = null;
            this.animTimer.setTime();
            this.oldHealth = heal;
            this.newHealth = heal;
            this.lastObservedHealth = heal;
            this.hitParticles.clear();
        }
        this.maxHealth = Math.max(targetEntity.getMaxHealth() / 2.0F, 1.0F);
        if (!Float.isNaN(this.lastObservedHealth)
                && heal < this.lastObservedHealth - 0.001F
                && this.mode.getValue() == 3) {
            this.spawnHitParticles();
        }
        this.lastObservedHealth = heal;
        if (!this.animations.getValue() || this.animTimer.hasTimeElapsed(150L)) {
            float previousHealth = this.newHealth;
            this.oldHealth = this.newHealth;
            this.newHealth = heal;
            if (this.newHealth < previousHealth - 0.001F && this.mode.getValue() == 3) {
                this.spawnHitParticles();
            }
            if (Math.abs(this.oldHealth - this.newHealth) > 0.001F) {
                this.animTimer.reset();
            }
        }
        ResourceLocation resourceLocation = this.getSkin(targetEntity);
        if (resourceLocation != null) {
            this.headTexture = resourceLocation;
        }
    }

    private void renderFollow(float partialTicks, EntityLivingBase targetEntity) {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        String targetNameText = TeamUtil.stripName(targetEntity);
        float targetNameWidth = this.getTextWidth(targetNameText);
        float abs = targetEntity.getAbsorptionAmount() / 2.0F;
        float heal = targetEntity.getHealth() / 2.0F + abs;
        String healthText = ChatColors.formatColor(String.format("&r&f%s%s❤&r", healthFormat.format(heal), abs > 0.0F ? "&6" : "&c"));
        float healthTextWidth = this.getTextWidth(healthText);
        String statusText = ChatColors.formatColor(String.format("&r&l%s&r", heal == (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F ? "D" : (heal < (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F ? "W" : "L")));
        float statusTextWidth = this.getTextWidth(statusText);
        String healthDiffText = ChatColors.formatColor(String.format("&r%s&r", diffFormat.format((mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F - heal)));
        float healthDiffWidth = this.getTextWidth(healthDiffText);
        Color targetColor = this.getTargetColor(targetEntity);
        float healthRatio = Math.min(Math.max(RenderUtil.lerpFloat(this.newHealth, this.oldHealth, Math.min(Math.max(this.animTimer.getElapsedTime(), 0L), 150L) / 150.0F) / this.maxHealth, 0.0F), 1.0F);
        Color healthBarColor = this.color.getValue() == 0 ? ColorUtil.getHealthBlend(healthRatio) : targetColor;
        Color healthDeltaColor = ColorUtil.getHealthBlend(Math.min(Math.max(((mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F - heal + 1.0F) / 2.0F, 0.0F), 1.0F));

        float cardWidth;
        float cardHeight;
        if (this.mode.getValue() == 3) {
            cardWidth = Math.max(220.0F, targetNameWidth + 118.0F);
            cardHeight = 48.0F;
        } else if (this.mode.getValue() == 4) {
            cardWidth = this.getInkCardWidth(targetNameWidth, statusTextWidth, healthTextWidth, healthDiffWidth);
            cardHeight = this.getInkCardHeight();
        } else if (this.mode.getValue() == 5) {
            cardWidth = this.getAuraCardWidth(targetNameWidth, statusTextWidth, healthTextWidth, healthDiffWidth);
            cardHeight = this.getAuraCardHeight();
        } else if (this.mode.getValue() == 6) {
            String frostHealthStr = heal == Math.floor(heal) ? String.format("%.0f", heal) : healthFormat.format(heal);
            float frostTopRow = targetNameWidth + 6.0F + this.getTextWidth(frostHealthStr)
                    + (this.indicator.getValue() ? 9.0F : 0.0F);
            cardWidth = this.getFrostCardWidth(frostTopRow);
            cardHeight = this.getFrostCardHeight();
        } else if (this.mode.getValue() == 7) {
            cardWidth = this.getSlateCardWidth(targetNameText, heal, abs);
            cardHeight = this.getSlateCardHeight();
        } else if (this.mode.getValue() == 2) {
            cardWidth = 150.0F;
            cardHeight = this.getCardHeight();
        } else if (this.mode.getValue() == 1) {
            cardWidth = 150.0F;
            cardHeight = Math.max(85.0F, 48.0F + this.getTextHeight() * 2.0F);
        } else {
            float barContentWidth = Math.max(targetNameWidth + (this.indicator.getValue() ? 2.0F + statusTextWidth + 2.0F : 0.0F), healthTextWidth + (this.indicator.getValue() ? 2.0F + healthDiffWidth + 2.0F : 0.0F));
            float headSize = this.head.getValue() && this.headTexture != null ? Math.min(23.0F, this.getCardHeight() - 4.0F) + 2.0F : 0.0F;
            cardWidth = Math.max(headSize + 70.0F, headSize + 2.0F + barContentWidth + 2.0F);
            cardHeight = this.getCardHeight();
        }

        double targetX = targetEntity.lastTickPosX + (targetEntity.posX - targetEntity.lastTickPosX) * partialTicks;
        double targetY = targetEntity.lastTickPosY + (targetEntity.posY - targetEntity.lastTickPosY) * partialTicks + targetEntity.height * 0.55D;
        double targetZ = targetEntity.lastTickPosZ + (targetEntity.posZ - targetEntity.lastTickPosZ) * partialTicks;
        double dx = mc.thePlayer.posX - targetX;
        double dz = mc.thePlayer.posZ - targetZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001D) {
            dx = -Math.sin(Math.toRadians(targetEntity.rotationYaw));
            dz = Math.cos(Math.toRadians(targetEntity.rotationYaw));
            distance = 1.0D;
        }
        double sideX = -dz / distance * (targetEntity.width + 0.45D);
        double sideZ = dx / distance * (targetEntity.width + 0.45D);
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();

        double followScale = 0.0075D * this.scale.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.translate(targetX + sideX - renderManager.getRenderPosX(), targetY - renderManager.getRenderPosY(), targetZ + sideZ - renderManager.getRenderPosZ());
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-followScale, -followScale, 1.0D);
        GlStateManager.translate(-cardWidth / 2.0F, -cardHeight / 2.0F, 0.0F);

        GlStateManager.disableDepth();
        this.renderingFollow = true;
        if (this.mode.getValue() == 3) {
            renderModern(scaledResolution, targetNameText, healthText, statusText, healthDiffText, targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth, healthRatio, targetColor, healthBarColor, healthDeltaColor, heal, (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F, abs);
        } else if (this.mode.getValue() == 4) {
            renderInk(scaledResolution, targetNameText, healthText, statusText, healthDiffText, targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth, healthRatio, targetColor, healthBarColor, healthDeltaColor, heal, (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F, abs);
        } else if (this.mode.getValue() == 5) {
            renderAura(scaledResolution, targetNameText, healthText, statusText, healthDiffText, targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth, healthRatio, targetColor, healthBarColor, healthDeltaColor, heal, (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F, abs);
        } else if (this.mode.getValue() == 6) {
            renderFrost(scaledResolution, targetNameText, healthText, statusText, healthDiffText, targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth, healthRatio, targetColor, healthBarColor, healthDeltaColor, heal, (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F, abs);
        } else if (this.mode.getValue() == 7) {
            renderSlate(scaledResolution, targetNameText, healthRatio, heal, abs);
        } else if (this.mode.getValue() == 2) {
            renderBackground(scaledResolution, targetNameText, healthText, targetNameWidth, healthTextWidth, healthRatio, targetColor, healthBarColor, heal, (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F, abs);
        } else if (this.mode.getValue() == 1) {
            renderTriangle(scaledResolution, targetNameText, healthText, statusText, healthDiffText, targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth, healthRatio, targetColor, healthBarColor, healthDeltaColor, heal, (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F, abs);
        } else {
            renderDefaultFollow(cardWidth, cardHeight, targetNameText, healthText, statusText, healthDiffText,
                    targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth, healthRatio,
                    targetColor, healthBarColor, healthDeltaColor);
        }
        this.renderingFollow = false;
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void renderDefaultFollow(float cardWidth, float cardHeight, String targetNameText, String healthText, String statusText, String healthDiffText, float targetNameWidth, float healthTextWidth, float statusTextWidth, float healthDiffWidth, float healthRatio, Color targetColor, Color healthBarColor, Color healthDeltaColor) {
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(0.0F, 0.0F, cardWidth, cardHeight, this.getBackgroundColor());
        if (this.outline.getValue()) {
            this.drawOutline(0.0F, 0.0F, cardWidth, cardHeight, 1.5F, targetColor.getRGB());
        }
        float headSize = this.head.getValue() && this.headTexture != null ? Math.min(23.0F, cardHeight - 4.0F) : 0.0F;
        float headOffset = headSize > 0.0F ? headSize + 2.0F : 0.0F;
        float barY = this.getHealthBarY();
        RenderUtil.drawRect(headOffset + 2.0F, barY, cardWidth - 2.0F, barY + 3.0F, ColorUtil.darker(healthBarColor, 0.2F).getRGB());
        RenderUtil.drawRect(headOffset + 2.0F, barY, headOffset + 2.0F + healthRatio * (cardWidth - headOffset - 4.0F), barY + 3.0F, healthBarColor.getRGB());
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        this.drawText(targetNameText, headOffset + 2.0F, this.getTextTop(), -1);
        this.drawText(healthText, headOffset + 2.0F, this.getSecondTextY(), -1);
        if (this.indicator.getValue()) {
            this.drawText(statusText, cardWidth - 2.0F - statusTextWidth, this.getTextTop(), healthDeltaColor.getRGB());
            this.drawText(healthDiffText, cardWidth - 2.0F - healthDiffWidth, this.getSecondTextY(), ColorUtil.darker(healthDeltaColor, 0.8F).getRGB());
        }
        if (headSize > 0.0F && this.headTexture != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            Gui.drawScaledCustomSizeModalRect(2, (int) ((cardHeight - headSize) / 2.0F), 8.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(2, (int) ((cardHeight - headSize) / 2.0F), 40.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F);
        }
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && mc.thePlayer != null && this.position.getValue() == 0) {
            EntityLivingBase entityLivingBase = this.target;
            this.target = this.resolveTarget();
            if (this.target != null) {
                float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
                float abs = this.target.getAbsorptionAmount() / 2.0F;
                float heal = this.target.getHealth() / 2.0F + abs;
                if (this.target != entityLivingBase) {
                    this.headTexture = null;
                    this.animTimer.setTime();
                    this.oldHealth = heal;
                    this.newHealth = heal;
                    this.lastObservedHealth = heal;
                    this.hitParticles.clear();
                }
                this.maxHealth = Math.max(this.target.getMaxHealth() / 2.0F, 1.0F);
                if (!Float.isNaN(this.lastObservedHealth)
                        && heal < this.lastObservedHealth - 0.001F
                        && this.mode.getValue() == 3) {
                    this.spawnHitParticles();
                }
                this.lastObservedHealth = heal;
                if (!this.animations.getValue() || this.animTimer.hasTimeElapsed(150L)) {
                    float previousHealth = this.newHealth;
                    this.oldHealth = this.newHealth;
                    this.newHealth = heal;
                    if (this.newHealth < previousHealth - 0.001F && this.mode.getValue() == 3) {
                        this.spawnHitParticles();
                    }
                    if (Math.abs(this.oldHealth - this.newHealth) > 0.001F) {
                        this.animTimer.reset();
                    }
                }
                ResourceLocation resourceLocation = this.getSkin(this.target);
                if (resourceLocation != null) {
                    this.headTexture = resourceLocation;
                }
                float elapsedTime = (float) Math.min(Math.max(this.animTimer.getElapsedTime(), 0L), 150L);
                float healthRatio = Math.min(Math.max(RenderUtil.lerpFloat(this.newHealth, this.oldHealth, elapsedTime / 150.0F) / this.maxHealth, 0.0F), 1.0F);
                Color targetColor = this.getTargetColor(this.target);
                Color healthBarColor = this.color.getValue() == 0 ? ColorUtil.getHealthBlend(healthRatio) : targetColor;
                float healthDeltaRatio = Math.min(Math.max((health - heal + 1.0F) / 2.0F, 0.0F), 1.0F);
                Color healthDeltaColor = ColorUtil.getHealthBlend(healthDeltaRatio);
                ScaledResolution scaledResolution = new ScaledResolution(mc);
                String targetNameText = TeamUtil.stripName(this.target);
                float targetNameWidth = this.getTextWidth(targetNameText);
                String healthText = ChatColors.formatColor(
                        String.format("&r&f%s%s❤&r", healthFormat.format(heal), abs > 0.0F ? "&6" : "&c")
                );
                float healthTextWidth = this.getTextWidth(healthText);
                String statusText = ChatColors.formatColor(String.format("&r&l%s&r", heal == health ? "D" : (heal < health ? "W" : "L")));
                float statusTextWidth = this.getTextWidth(statusText);
                String healthDiffText = ChatColors.formatColor(
                        String.format("&r%s&r", heal == health ? "0.0" : diffFormat.format(health - heal))
                );
                float healthDiffWidth = this.getTextWidth(healthDiffText);
                if (this.mode.getValue() == 3) {
                    renderModern(scaledResolution, targetNameText, healthText, statusText, healthDiffText,
                            targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth,
                            healthRatio, targetColor, healthBarColor, healthDeltaColor,
                            heal, health, abs);
                } else if (this.mode.getValue() == 4) {
                    renderInk(scaledResolution, targetNameText, healthText, statusText, healthDiffText,
                            targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth,
                            healthRatio, targetColor, healthBarColor, healthDeltaColor,
                            heal, health, abs);
                } else if (this.mode.getValue() == 5) {
                    renderAura(scaledResolution, targetNameText, healthText, statusText, healthDiffText,
                            targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth,
                            healthRatio, targetColor, healthBarColor, healthDeltaColor,
                            heal, health, abs);
                } else if (this.mode.getValue() == 6) {
                    renderFrost(scaledResolution, targetNameText, healthText, statusText, healthDiffText,
                            targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth,
                            healthRatio, targetColor, healthBarColor, healthDeltaColor,
                            heal, health, abs);
                } else if (this.mode.getValue() == 7) {
                    renderSlate(scaledResolution, targetNameText, healthRatio, heal, abs);
                } else if (this.mode.getValue() == 2) {
                    renderBackground(scaledResolution, targetNameText, healthText,
                            targetNameWidth, healthTextWidth,
                            healthRatio, targetColor, healthBarColor,
                            heal, health, abs);
                } else if (this.mode.getValue() == 1) {
                    renderTriangle(scaledResolution, targetNameText, healthText, statusText, healthDiffText,
                            targetNameWidth, healthTextWidth, statusTextWidth, healthDiffWidth,
                            healthRatio, targetColor, healthBarColor, healthDeltaColor,
                            heal, health, abs);
                } else {
                float barContentWidth = Math.max(
                        targetNameWidth + (this.indicator.getValue() ? 2.0F + statusTextWidth + 2.0F : 0.0F),
                        healthTextWidth + (this.indicator.getValue() ? 2.0F + healthDiffWidth + 2.0F : 0.0F)
                );
                float cardHeight = this.getCardHeight();
                float headSize = Math.min(23.0F, cardHeight - 4.0F);
                float headIconOffset = this.head.getValue() && this.headTexture != null ? headSize + 2.0F : 0.0F;
                float barTotalWidth = Math.max(headIconOffset + 70.0F, headIconOffset + 2.0F + barContentWidth + 2.0F);
                float posX = this.offX.getValue().floatValue() / this.scale.getValue();
                switch (this.posX.getValue()) {
                    case 1:
                        posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - barTotalWidth / 2.0F;
                        break;
                    case 2:
                        posX *= -1.0F;
                        posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - barTotalWidth;
                }
                float posY = this.offY.getValue().floatValue() / this.scale.getValue();
                switch (this.posY.getValue()) {
                    case 1:
                        posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - cardHeight / 2.0F;
                        break;
                    case 2:
                        posY *= -1.0F;
                        posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - cardHeight;
                }
                GlStateManager.pushMatrix();
                GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
                GlStateManager.translate(posX, posY, -450.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(0.0F, 0.0F, barTotalWidth, cardHeight, this.getBackgroundColor());
                if (this.outline.getValue()) {
                    this.drawOutline(0.0F, 0.0F, barTotalWidth, cardHeight, 1.5F, targetColor.getRGB());
                }
                float healthBarY = this.getHealthBarY();
                RenderUtil.drawRect(headIconOffset + 2.0F, healthBarY, barTotalWidth - 2.0F, healthBarY + 3.0F, ColorUtil.darker(healthBarColor, 0.2F).getRGB());
                RenderUtil.drawRect(headIconOffset + 2.0F, healthBarY, headIconOffset + 2.0F + healthRatio * (barTotalWidth - 2.0F - headIconOffset - 2.0F), healthBarY + 3.0F, healthBarColor.getRGB());
                RenderUtil.disableRenderState();
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                this.drawText(targetNameText, headIconOffset + 2.0F, this.getTextTop(), -1);
                this.drawText(healthText, headIconOffset + 2.0F, this.getSecondTextY(), -1);
                if (this.indicator.getValue()) {
                    this.drawText(statusText, barTotalWidth - 2.0F - statusTextWidth, this.getTextTop(), healthDeltaColor.getRGB());
                    this.drawText(healthDiffText, barTotalWidth - 2.0F - healthDiffWidth, this.getSecondTextY(), ColorUtil.darker(healthDeltaColor, 0.8F).getRGB());
                }
                if (this.head.getValue() && this.headTexture != null) {
                    float headY = (cardHeight - headSize) / 2.0F;
                    GlStateManager.color(1.0F, 1.0F, 1.0F);
                    mc.getTextureManager().bindTexture(this.headTexture);
                    Gui.drawScaledCustomSizeModalRect(2, (int) headY, 8.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
                    Gui.drawScaledCustomSizeModalRect(2, (int) headY, 40.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
                    GlStateManager.color(1.0F, 1.0F, 1.0F);
                }
                GlStateManager.disableBlend();
                GlStateManager.enableDepth();
                GlStateManager.popMatrix();
                }
            }
        }
    }

    private void renderBackground(ScaledResolution scaledResolution,
                                   String targetNameText, String healthText,
                                   float targetNameWidth, float healthTextWidth,
                                   float healthRatio, Color targetColor, Color healthBarColor,
                                   float heal, float playerHealth, float abs) {
        final float barWidth = 150.0F;
        final float barHeight = this.getCardHeight();
        final float headSize = Math.min(23.0F, barHeight - 4.0F);
        boolean hasHead = this.head.getValue() && this.headTexture != null;
        float headIconOffset = hasHead ? headSize + 2.0F : 0.0F;

        float posX = this.renderingFollow ? -barWidth / 2.0F : this.offX.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posX.getValue()) {
                case 1:
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - barWidth / 2.0F;
                    break;
                case 2:
                    posX *= -1.0F;
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - barWidth;
            }
        }
        float posY = this.renderingFollow ? -barHeight / 2.0F : this.offY.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posY.getValue()) {
                case 1:
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - barHeight / 2.0F;
                    break;
                case 2:
                    posY *= -1.0F;
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - barHeight;
            }
        }

        if (!this.renderingFollow) {
            final float bx = posX;
            final float by = posY;
            final float bw = barWidth;
            final float bh = barHeight;
            final float sc = this.scale.getValue();
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                GlStateManager.translate(bx, by, -450.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(0.0F, 0.0F, bw, bh, -1);
                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();
            });
        }

        GlStateManager.pushMatrix();
        if (!this.renderingFollow) {
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        }
        GlStateManager.translate(posX, posY, this.renderingFollow ? 0.0F : -450.0F);

        RenderUtil.enableRenderState();
        RenderUtil.drawRect(0.0F, 0.0F, barWidth, barHeight, this.getBackgroundColor());

        float filledWidth = healthRatio * barWidth;
        int bgAlpha = this.getBackgroundAlpha();
        int fillAlpha = Math.min(bgAlpha * 3, 255);
        int fillColor = new Color(healthBarColor.getRed(), healthBarColor.getGreen(), healthBarColor.getBlue(), fillAlpha).getRGB();
        RenderUtil.drawRect(0.0F, 0.0F, filledWidth, barHeight, fillColor);

        if (filledWidth > 1.0F && filledWidth < barWidth - 1.0F) {
            RenderUtil.setColor(healthBarColor.getRGB());
            GL11.glLineWidth(1.5F);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(filledWidth, 0.0F);
            GL11.glVertex2f(filledWidth, barHeight);
            GL11.glEnd();
            GL11.glLineWidth(2.0F);
            GlStateManager.resetColor();
        }

        int borderColor = new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 80).getRGB();
        this.drawOutline(0.0F, 0.0F, barWidth, barHeight, 1.0F, borderColor);
        RenderUtil.disableRenderState();

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float textHeight = this.getTextHeight();
        float lineSpacing = 3.0F;
        float totalHeight = textHeight * 2.0F + lineSpacing;
        float centerTop = (barHeight - totalHeight) / 2.0F;
        float centerSecondY = centerTop + textHeight + lineSpacing;

        this.drawText(targetNameText, headIconOffset + 2.0F, centerTop, -1);

        String displayHealth = ChatColors.formatColor(String.format("&r&f%s&r", healthFormat.format(heal)));
        if (abs > 0.0F) {
            displayHealth = displayHealth + ChatColors.formatColor(String.format(" &6%s&r", healthFormat.format(abs)));
        }
        this.drawText(displayHealth, headIconOffset + 2.0F, centerSecondY, -1);

        if (hasHead) {
            float headY = (barHeight - headSize) / 2.0F;
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            Gui.drawScaledCustomSizeModalRect(2, (int) headY, 8.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(2, (int) headY, 40.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void renderModern(ScaledResolution scaledResolution,
                              String targetNameText, String healthText, String statusText, String healthDiffText,
                              float targetNameWidth, float healthTextWidth, float statusTextWidth, float healthDiffWidth,
                              float healthRatio, Color targetColor, Color healthBarColor, Color healthDeltaColor,
                              float heal, float playerHealth, float abs) {
        final float cardWidth = Math.max(220.0F, targetNameWidth + 118.0F);
        final float cardHeight = 48.0F;
        final float radius = 8.0F;
        final float headSize = 30.0F;
        final float headX = cardWidth - headSize - 10.0F;
        final float headY = 9.0F;
        final float barX = 12.0F;
        final float barY = 31.0F;
        final float barWidth = headX - barX - 10.0F;
        final float barHeight = 4.0F;

        String healthStr = heal == Math.floor(heal) ? String.format("%.0f", heal) : healthFormat.format(heal);
        String modernHealthText = ChatColors.formatColor(String.format("&r&f%s&r", healthStr));
        float modernHealthWidth = this.getTextWidth(modernHealthText);

        float posX = this.renderingFollow ? -cardWidth / 2.0F : this.offX.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posX.getValue()) {
                case 1:
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - cardWidth / 2.0F;
                    break;
                case 2:
                    posX *= -1.0F;
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - cardWidth;
                    break;
            }
        }
        float posY = this.renderingFollow ? -cardHeight / 2.0F : this.offY.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posY.getValue()) {
                case 1:
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - cardHeight / 2.0F;
                    break;
                case 2:
                    posY *= -1.0F;
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - cardHeight;
                    break;
            }
        }

        if (!this.renderingFollow) {
            final float bx = posX;
            final float by = posY;
            final float bw = cardWidth;
            final float bh = cardHeight;
            final float sc = this.scale.getValue();
            final float r = radius;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                GlStateManager.translate(bx, by, -450.0F);
                RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, bw, bh, r, -1);
                GlStateManager.popMatrix();
            });
        }

        GlStateManager.pushMatrix();
        if (!this.renderingFollow) {
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        }
        GlStateManager.translate(posX, posY, this.renderingFollow ? 0.0F : -450.0F);

        float elapsedTime = (float) Math.min(Math.max(this.animTimer.getElapsedTime(), 0L), 180L);
        float hitProgress = this.oldHealth > this.newHealth ? 1.0F - elapsedTime / 180.0F : 0.0F;
        float shake = hitProgress > 0.0F ? (float) Math.sin(System.currentTimeMillis() * 0.08D) * 3.0F * hitProgress : 0.0F;
        float filledWidth = Math.max(2.0F, barWidth * healthRatio);

        int glassAlpha = Math.max(150, Math.min(205, this.getBackgroundAlpha()));
        int rimColor = this.outline.getValue()
                ? new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 90).getRGB()
                : new Color(255, 255, 255, 30).getRGB();
        int glassColor = new Color(14, 16, 22, glassAlpha).getRGB();

        RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, cardWidth, cardHeight, radius, rimColor);
        RenderUtil.drawRoundedRectWithGl(1.0F, 1.0F, cardWidth - 1.0F, cardHeight - 1.0F, radius - 1.0F, glassColor);

        int trackColor = new Color(255, 255, 255, 26).getRGB();
        int fillColor = new Color(healthBarColor.getRed(), healthBarColor.getGreen(), healthBarColor.getBlue(), 235).getRGB();
        RenderUtil.drawRoundedRectWithGl(barX, barY, barX + barWidth, barY + barHeight, barHeight / 2.0F, trackColor);
        RenderUtil.drawRoundedRectWithGl(barX, barY, barX + filledWidth, barY + barHeight, barHeight / 2.0F, fillColor);

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        this.drawText(targetNameText, 12.0F, 9.0F, -1);
        this.drawText(modernHealthText, barX + barWidth - modernHealthWidth, 20.0F, new Color(235, 238, 244, 245).getRGB());

        if (this.head.getValue() && this.headTexture != null) {
            RenderUtil.drawRoundedRectWithGl(headX - 0.5F, headY - 0.5F, headX + headSize + 0.5F, headY + headSize + 0.5F, 6.0F,
                    new Color(20, 22, 30, 160).getRGB());

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);

            drawRoundedHead(headX + shake, headY - shake * 0.4F, headSize, 5.5F, 8.0F, 8.0F, 1.0F);
            drawRoundedHead(headX + shake, headY - shake * 0.4F, headSize, 5.5F, 40.0F, 8.0F, 1.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        this.drawHitParticles(headX + headSize / 2.0F, headY + headSize / 2.0F, healthBarColor);

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private static final float[] HEAD_BOUNDARY = new float[4];

    private static void headBoundaryPoint(int idx, float x, float y, float size, float radius, int steps) {
        int corner = idx / (steps + 1);
        int j = idx - corner * (steps + 1);
        double angle = Math.toRadians(270.0 + corner * 90.0 + 90.0 * j / (double) steps);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float cx = corner <= 1 ? x + size - radius : x + radius;
        float cy = corner == 1 || corner == 2 ? y + size - radius : y + radius;
        HEAD_BOUNDARY[0] = cx + cos * radius;
        HEAD_BOUNDARY[1] = cy + sin * radius;
        HEAD_BOUNDARY[2] = cos;
        HEAD_BOUNDARY[3] = sin;
    }

    private void drawRoundedHead(float x, float y, float size, float radius, float texU, float texV, float alpha) {
        if (size <= 0.05F || alpha <= 0.004F) {
            return;
        }

        float inner = size;
        float ix = x;
        float iy = y;
        float innerRadius = Math.max(0.0F, Math.min(radius, inner / 2.0F));
        int steps = Math.max(8, Math.min(20, (int) Math.ceil(radius * 1.25F) + 3));
        int total = 4 * (steps + 1);

        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cull) {
            GlStateManager.disableCull();
        }
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();

        int oldMinFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int oldMagFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        headVertex(ix + inner / 2.0F, iy + inner / 2.0F, x, y, size, texU, texV, alpha);
        for (int i = 0; i <= total; i++) {
            headBoundaryPoint(i == total ? 0 : i, ix, iy, inner, innerRadius, steps);
            headVertex(HEAD_BOUNDARY[0], HEAD_BOUNDARY[1], x, y, size, texU, texV, alpha);
        }
        GL11.glEnd();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, oldMinFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, oldMagFilter);

        if (cull) {
            GlStateManager.enableCull();
        }
    }

    private void headVertex(float px, float py, float x, float y, float size, float texU, float texV, float alpha) {
        float u = Math.max(0.0F, Math.min(1.0F, (px - x) / size));
        float v = Math.max(0.0F, Math.min(1.0F, (py - y) / size));
        GL11.glTexCoord2d((texU + u * 8.0F) / 64.0D, (texV + v * 8.0F) / 64.0D);

        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        GL11.glVertex2d(px, py);
    }

    private void drawArcRing(float cx, float cy, float radius, float thickness, float startDeg, float sweepDeg, int color) {
        float a = ((color >> 24) & 255) / 255.0F;
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        float inner = radius - thickness;
        int segs = Math.max(8, (int) (Math.abs(sweepDeg) / 6.0F));
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double ang = Math.toRadians(startDeg + sweepDeg * i / (float) segs);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            wr.pos(cx + cos * radius, cy + sin * radius, 0.0D).color(r, g, b, a).endVertex();
            wr.pos(cx + cos * inner, cy + sin * inner, 0.0D).color(r, g, b, a).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private float getAuraCardWidth(float targetNameWidth, float statusTextWidth,
                                   float healthTextWidth, float healthDiffWidth) {
        float headW = this.head.getValue() && this.headTexture != null ? 41.0F : 0.0F;
        float lineW = Math.max(targetNameWidth, healthTextWidth * 1.15F);
        float statusW = this.indicator.getValue() ? 14.0F : 0.0F;
        return Math.max(110.0F, 8.0F + headW + lineW + statusW + 10.0F);
    }

    private float getAuraCardHeight() {
        return Math.max(42.0F, this.getTextHeight() * 2.0F + 22.0F);
    }

    private void renderAura(ScaledResolution scaledResolution,
                            String targetNameText, String healthText, String statusText, String healthDiffText,
                            float targetNameWidth, float healthTextWidth, float statusTextWidth, float healthDiffWidth,
                            float healthRatio, Color targetColor, Color healthBarColor, Color healthDeltaColor,
                            float heal, float playerHealth, float abs) {
        final float cardWidth = this.getAuraCardWidth(targetNameWidth, statusTextWidth, healthTextWidth, healthDiffWidth);
        final float cardHeight = this.getAuraCardHeight();
        final float textHeight = this.getTextHeight();
        final boolean hasHead = this.head.getValue() && this.headTexture != null;
        final float headSize = 26.0F;
        final float radius = 8.0F;
        final float textX = hasHead ? 44.0F : 12.0F;

        float posX = this.renderingFollow ? -cardWidth / 2.0F : this.offX.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posX.getValue()) {
                case 1:
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - cardWidth / 2.0F;
                    break;
                case 2:
                    posX *= -1.0F;
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - cardWidth;
                    break;
            }
        }
        float posY = this.renderingFollow ? -cardHeight / 2.0F : this.offY.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posY.getValue()) {
                case 1:
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - cardHeight / 2.0F;
                    break;
                case 2:
                    posY *= -1.0F;
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - cardHeight;
                    break;
            }
        }

        if (!this.renderingFollow) {
            final float bx = posX;
            final float by = posY;
            final float bw = cardWidth;
            final float bh = cardHeight;
            final float sc = this.scale.getValue();
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                GlStateManager.translate(bx, by, -450.0F);
                RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, bw, bh, 7.0F, -1);
                GlStateManager.popMatrix();
            });
        }

        GlStateManager.pushMatrix();
        if (!this.renderingFollow) {
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        }
        GlStateManager.translate(posX, posY, this.renderingFollow ? 0.0F : -450.0F);

        int cardAlpha = 51;
        int cardColor = new Color(20, 22, 27, cardAlpha).getRGB();
        RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, cardWidth, cardHeight, radius, cardColor);

        float ringCX = 24.0F;
        float ringCY = cardHeight / 2.0F;
        int trackColor = new Color(255, 255, 255, 20).getRGB();
        int glowColor = new Color(healthBarColor.getRed(), healthBarColor.getGreen(), healthBarColor.getBlue(), 42).getRGB();
        int arcColor = new Color(healthBarColor.getRed(), healthBarColor.getGreen(), healthBarColor.getBlue(), 255).getRGB();
        if (hasHead) {
            float sweep = Math.max(healthRatio * 360.0F, 2.0F);
            drawArcRing(ringCX, ringCY, 16.0F, 2.0F, 0.0F, 360.0F, trackColor);
            drawArcRing(ringCX, ringCY, 16.0F, 4.5F, -90.0F, sweep, glowColor);
            drawArcRing(ringCX, ringCY, 16.0F, 2.0F, -90.0F, sweep, arcColor);
        }

        if (hasHead) {
            float headX = 11.0F;
            float headY = (cardHeight - headSize) / 2.0F;
            RenderUtil.drawRoundedRectWithGl(headX, headY, headX + headSize, headY + headSize, 6.0F,
                    new Color(16, 18, 23, 220).getRGB());

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            drawRoundedHead(headX, headY, headSize, 6.0F, 8.0F, 8.0F, 1.0F);
            drawRoundedHead(headX, headY, headSize, 6.0F, 40.0F, 8.0F, 1.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float nameY = (cardHeight - (textHeight + 3.5F + textHeight * 1.15F)) / 2.0F;
        this.drawText(targetNameText, textX, nameY, -1);
        this.drawText(healthFormat.format(heal), textX, nameY + textHeight + 3.5F, arcColor);

        if (this.indicator.getValue()) {

            float dotX = cardWidth - 13.0F;
            float dotY = nameY + textHeight / 2.0F;
            RenderUtil.fillCircle(dotX, dotY, 2.5D, 20,
                    new Color(healthDeltaColor.getRed(), healthDeltaColor.getGreen(), healthDeltaColor.getBlue(), 255).getRGB());
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private float getInkCardWidth(float targetNameWidth, float statusTextWidth,
                                  float healthTextWidth, float healthDiffWidth) {
        float line2 = healthTextWidth + (this.indicator.getValue() ? 6.0F + healthDiffWidth : 0.0F);
        float contentW = Math.max(targetNameWidth, line2);
        float headW = this.head.getValue() && this.headTexture != null ? 30.0F : 0.0F;
        float sealW = this.indicator.getValue() ? 21.0F : 0.0F;
        return Math.max(116.0F, 14.0F + headW + contentW + sealW + 12.0F);
    }

    private float getInkCardHeight() {
        return Math.max(36.0F, this.getTextHeight() * 2.0F + 18.0F);
    }

    private void drawBrushStroke(float x, float y, float length, int color) {
        float a = ((color >> 24) & 255) / 255.0F;
        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float root = 1.8F;
        float tip = 0.5F;
        wr.pos(x, y - root, 0.0D).color(r, g, b, a).endVertex();
        wr.pos(x + length, y - tip, 0.0D).color(r, g, b, a).endVertex();
        wr.pos(x + length, y + tip, 0.0D).color(r, g, b, a).endVertex();
        wr.pos(x, y + root, 0.0D).color(r, g, b, a).endVertex();
        tessellator.draw();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private void renderInk(ScaledResolution scaledResolution,
                           String targetNameText, String healthText, String statusText, String healthDiffText,
                           float targetNameWidth, float healthTextWidth, float statusTextWidth, float healthDiffWidth,
                           float healthRatio, Color targetColor, Color healthBarColor, Color healthDeltaColor,
                           float heal, float playerHealth, float abs) {
        final float cardWidth = this.getInkCardWidth(targetNameWidth, statusTextWidth, healthTextWidth, healthDiffWidth);
        final float cardHeight = this.getInkCardHeight();
        final float textHeight = this.getTextHeight();
        final boolean hasHead = this.head.getValue() && this.headTexture != null;
        final float headSize = 22.0F;
        final float textX = hasHead ? 41.0F : 13.0F;
        final float textTop = 7.0F;
        final float secondY = textTop + textHeight + 3.0F;
        final float brushY = cardHeight - 6.5F;
        final float barX2 = cardWidth - 12.0F;

        float posX = this.renderingFollow ? -cardWidth / 2.0F : this.offX.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posX.getValue()) {
                case 1:
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - cardWidth / 2.0F;
                    break;
                case 2:
                    posX *= -1.0F;
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - cardWidth;
                    break;
            }
        }
        float posY = this.renderingFollow ? -cardHeight / 2.0F : this.offY.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posY.getValue()) {
                case 1:
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - cardHeight / 2.0F;
                    break;
                case 2:
                    posY *= -1.0F;
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - cardHeight;
                    break;
            }
        }

        if (!this.renderingFollow) {
            final float bx = posX;
            final float by = posY;
            final float bw = cardWidth;
            final float bh = cardHeight;
            final float sc = this.scale.getValue();
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                GlStateManager.translate(bx, by, -450.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawRect(0.0F, 0.0F, bw, bh, -1);
                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();
            });
        }

        GlStateManager.pushMatrix();
        if (!this.renderingFollow) {
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        }
        GlStateManager.translate(posX, posY, this.renderingFollow ? 0.0F : -450.0F);

        int paperAlpha = 51;
        int paper = new Color(26, 23, 20, paperAlpha).getRGB();
        int frameOuter = new Color(198, 186, 168, 96).getRGB();
        int frameInner = new Color(198, 186, 168, 42).getRGB();
        int cinnabar = new Color(172, 54, 44, 230).getRGB();

        RenderUtil.enableRenderState();
        RenderUtil.drawRect(0.0F, 0.0F, cardWidth, cardHeight, paper);

        RenderUtil.drawRect(0.0F, 0.0F, cardWidth, 1.0F, frameOuter);
        RenderUtil.drawRect(0.0F, cardHeight - 1.0F, cardWidth, cardHeight, frameOuter);
        RenderUtil.drawRect(0.0F, 0.0F, 1.0F, cardHeight, frameOuter);
        RenderUtil.drawRect(cardWidth - 1.0F, 0.0F, cardWidth, cardHeight, frameOuter);
        RenderUtil.drawRect(2.5F, 2.5F, cardWidth - 2.5F, 3.5F, frameInner);
        RenderUtil.drawRect(2.5F, cardHeight - 3.5F, cardWidth - 2.5F, cardHeight - 2.5F, frameInner);
        RenderUtil.drawRect(2.5F, 2.5F, 3.5F, cardHeight - 2.5F, frameInner);
        RenderUtil.drawRect(cardWidth - 3.5F, 2.5F, cardWidth - 2.5F, cardHeight - 2.5F, frameInner);

        RenderUtil.drawRect(5.5F, 6.0F, 7.0F, cardHeight - 6.0F, cinnabar);

        if (hasHead) {
            float headX = 11.0F;
            float headY = (cardHeight - headSize) / 2.0F;
            RenderUtil.drawRect(headX - 1.5F, headY - 1.5F, headX + headSize + 1.5F, headY + headSize + 1.5F, frameOuter);
            RenderUtil.drawRect(headX - 0.5F, headY - 0.5F, headX + headSize + 0.5F, headY + headSize + 0.5F, new Color(26, 23, 20, 220).getRGB());
        }

        float sealX = cardWidth - 23.0F;
        float sealY = (cardHeight - 13.0F) / 2.0F;
        if (this.indicator.getValue()) {
            RenderUtil.drawRect(sealX, sealY, sealX + 13.0F, sealY + 13.0F, new Color(140, 38, 30, 240).getRGB());
            RenderUtil.drawRect(sealX + 1.0F, sealY + 1.0F, sealX + 12.0F, sealY + 12.0F, cinnabar);
        }
        RenderUtil.disableRenderState();

        drawBrushStroke(textX, brushY, barX2 - textX, new Color(198, 186, 168, 42).getRGB());
        float strokeLen = Math.max(3.0F, (barX2 - textX) * healthRatio);
        drawBrushStroke(textX, brushY, strokeLen, cinnabar);
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(textX + strokeLen - 1.0F, brushY - 1.0F, textX + strokeLen + 1.0F, brushY + 1.0F, new Color(172, 54, 44, 170).getRGB());
        RenderUtil.disableRenderState();

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int inkWhite = new Color(232, 224, 210).getRGB();
        this.drawText(targetNameText, textX, textTop, inkWhite);
        this.drawText(healthText, textX, secondY, -1);
        if (this.indicator.getValue()) {
            this.drawText(healthDiffText, textX + healthTextWidth + 6.0F, secondY, ColorUtil.darker(healthDeltaColor, 0.85F).getRGB());
            this.drawText(statusText, sealX + (13.0F - statusTextWidth) / 2.0F, sealY + (13.0F - textHeight) / 2.0F, inkWhite);
        }

        if (hasHead) {
            float headX = 11.0F;
            float headY = (cardHeight - headSize) / 2.0F;
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            Gui.drawScaledCustomSizeModalRect((int) headX, (int) headY, 8.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect((int) headX, (int) headY, 40.0F, 8.0F, 8, 8, (int) headSize, (int) headSize, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void spawnHitParticles() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D + Math.random() * 0.45D;
            float speed = 0.55F + (float) Math.random() * 0.55F;
            this.hitParticles.add(new HitParticle(
                    (float) Math.cos(angle) * 2.0F,
                    (float) Math.sin(angle) * 2.0F,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    2.0F + (float) Math.random() * 1.6F,
                    now
            ));
        }
    }

    private void drawHitParticles(float centerX, float centerY, Color color) {
        long now = System.currentTimeMillis();
        Iterator<HitParticle> iterator = this.hitParticles.iterator();
        while (iterator.hasNext()) {
            HitParticle particle = iterator.next();
            float age = now - particle.startTime;
            float life = 520.0F;
            if (age >= life) {
                iterator.remove();
                continue;
            }
            float progress = age / life;
            float px = centerX + particle.x + particle.vx * progress * 18.0F;
            float py = centerY + particle.y + particle.vy * progress * 18.0F + progress * progress * 6.0F;
            float size = particle.size * (1.0F - progress * 0.65F);
            int alpha = (int) (185.0F * (1.0F - progress));
            int particleColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha))).getRGB();
            RenderUtil.drawRoundedRectWithGl(px - size / 2.0F, py - size / 2.0F, px + size / 2.0F, py + size / 2.0F, size / 2.0F, particleColor);
        }
    }

    private String getSlateHealthText(float heal) {
        return heal == Math.floor(heal) ? String.format("%.0f", heal) : healthFormat.format(heal);
    }

    private float getSlateCardWidth(String targetNameText, float heal, float abs) {
        float size = this.getTextScale();
        float headW = this.head.getValue() && this.headTexture != null ? 38.0F : 0.0F;
        float nameW = FontManager.getStringWidth(this.stripFormat(targetNameText), size);
        float healthW = FontManager.getStringWidth(this.getSlateHealthText(heal), size * 1.3F)
                + 2.0F + FontManager.getStringWidth("HP", size * 0.72F);
        return Math.max(150.0F, 10.0F + headW + nameW + 14.0F + healthW + 10.0F);
    }

    private float getSlateCardHeight() {
        float size = this.getTextScale();
        float contentH = FontManager.getCapHeight(size) + FontManager.getCapHeight(size * 0.72F) + 15.0F;
        return Math.max(44.0F, contentH + 14.0F);
    }

    private static int slateMix(Color a, Color b, float t, int alpha) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t), alpha).getRGB();
    }

    private void renderSlate(ScaledResolution scaledResolution, String targetNameText,
                             float healthRatio, float heal, float abs) {
        String name = this.stripFormat(targetNameText);
        String healthStr = this.getSlateHealthText(heal);
        float size = this.getTextScale();
        float bigSize = size * 1.3F;
        float subSize = size * 0.72F;
        float nameCap = FontManager.getCapHeight(size);
        float subCap = FontManager.getCapHeight(subSize);
        final float cardWidth = this.getSlateCardWidth(targetNameText, heal, abs);
        final float cardHeight = this.getSlateCardHeight();
        final float radius = 8.0F;
        final float pad = 7.0F;
        final float headSize = cardHeight - pad * 2.0F;
        final boolean hasHead = this.head.getValue() && this.headTexture != null;
        final float contentX = hasHead ? pad + headSize + 8.0F : pad + 3.0F;
        final float contentRight = cardWidth - pad - 3.0F;

        float posX = this.renderingFollow ? 0.0F : this.offX.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posX.getValue()) {
                case 1:
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - cardWidth / 2.0F;
                    break;
                case 2:
                    posX *= -1.0F;
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - cardWidth;
                    break;
            }
        }
        float posY = this.renderingFollow ? 0.0F : this.offY.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posY.getValue()) {
                case 1:
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - cardHeight / 2.0F;
                    break;
                case 2:
                    posY *= -1.0F;
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - cardHeight;
                    break;
            }
        }

        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        long now = System.currentTimeMillis();
        Color accent = hud != null ? hud.getColor(now) : new Color(126, 181, 255);
        Color accentHi = new Color(Math.min(255, accent.getRed() + 70), Math.min(255, accent.getGreen() + 70),
                Math.min(255, accent.getBlue() + 70));

        if (!this.renderingFollow) {
            final float bx = posX;
            final float by = posY;
            final float sc = this.scale.getValue();
            final int mask = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 255).getRGB();
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                GlStateManager.translate(bx, by, -450.0F);
                RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, cardWidth, cardHeight, radius, mask);
                GlStateManager.popMatrix();
            });
        }

        float dt = this.slateLastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.slateLastFrame) / 1000.0F);
        this.slateLastFrame = now;
        if (this.slateTarget != this.target) {
            this.slateTarget = this.target;
            this.slateLag = healthRatio;
        }
        if (this.slateLag < healthRatio) {
            this.slateLag = healthRatio;
        } else {
            this.slateLag += (healthRatio - this.slateLag) * (1.0F - (float) Math.exp(-dt * 3.5F));
        }

        GlStateManager.pushMatrix();
        if (!this.renderingFollow) {
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        }
        GlStateManager.translate(posX, posY, this.renderingFollow ? 0.0F : -450.0F);

        if (this.renderingFollow) {
            RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, cardWidth, cardHeight, radius, new Color(12, 13, 18, 150).getRGB());
        }
        if (this.outline.getValue()) {
            RenderUtil.drawRoundedRectWithGl(0.0F, cardHeight / 2.0F - 7.0F, 2.0F, cardHeight / 2.0F + 7.0F, 1.0F,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 240).getRGB());
        }

        float barH = 3.0F;
        float contentH = nameCap + 6.0F + subCap + 6.0F + barH;
        float top = (cardHeight - contentH) / 2.0F;
        float nameBase = top + nameCap;
        float subBase = nameBase + 6.0F + subCap;
        float barY = top + contentH - barH;

        int segments = 10;
        float gap = 1.5F;
        float barW = contentRight - contentX;
        float segW = (barW - gap * (segments - 1)) / segments;
        float lag = Math.max(0.0F, Math.min(1.0F, this.slateLag));
        int trackColor = new Color(255, 255, 255, 30).getRGB();
        int lagColor = new Color(255, 255, 255, 95).getRGB();
        for (int i = 0; i < segments; i++) {
            float sx = contentX + i * (segW + gap);
            RenderUtil.drawRoundedRectWithGl(sx, barY, sx + segW, barY + barH, 1.0F, trackColor);
            float lagFill = Math.max(0.0F, Math.min(1.0F, lag * segments - i));
            if (lagFill > 0.01F) {
                RenderUtil.drawRoundedRectWithGl(sx, barY, sx + segW * lagFill, barY + barH, 1.0F, lagColor);
            }
            float fill = Math.max(0.0F, Math.min(1.0F, healthRatio * segments - i));
            if (fill > 0.01F) {
                RenderUtil.drawRoundedRectWithGl(sx, barY, sx + segW * fill, barY + barH, 1.0F,
                        slateMix(accent, accentHi, (i + 0.5F) / segments, 255));
            }
        }

        boolean textShadow = this.shadow.getValue();
        float hpW = FontManager.getStringWidth("HP", subSize);
        float healthW = FontManager.getStringWidth(healthStr, bigSize);
        float hpX = contentRight - hpW;
        float healthX = hpX - 2.0F - healthW;
        int muted = new Color(150, 156, 170).getRGB();

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        FontManager.drawString(name, contentX, nameBase - FontManager.getBaseline(size), new Color(246, 248, 252).getRGB(), textShadow, size);
        FontManager.drawString(healthStr, healthX, nameBase - FontManager.getBaseline(bigSize), accentHi.getRGB(), textShadow, bigSize);
        FontManager.drawString("HP", hpX, nameBase - FontManager.getBaseline(subSize),
                abs > 0.0F ? new Color(255, 200, 70).getRGB() : muted, false, subSize);

        float statusX = contentX;
        if (this.indicator.getValue() && mc.thePlayer != null) {
            float self = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
            String state = heal == self ? "EVEN" : (heal < self ? "WINNING" : "LOSING");
            Color stateColor = heal == self ? new Color(170, 176, 188)
                    : (heal < self ? new Color(96, 220, 140) : new Color(255, 96, 96));
            RenderUtil.fillCircle(contentX + 2.0F, subBase - subCap / 2.0F, 2.0D, 16, stateColor.getRGB());
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            statusX += 7.0F;
            FontManager.drawString(state, statusX, subBase - FontManager.getBaseline(subSize), stateColor.getRGB(), false, subSize);
        }
        if (this.target != null && mc.thePlayer != null && this.target != mc.thePlayer) {
            String dist = String.format(Locale.US, "%.1fm", mc.thePlayer.getDistanceToEntity(this.target));
            FontManager.drawString(dist, contentRight - FontManager.getStringWidth(dist, subSize),
                    subBase - FontManager.getBaseline(subSize), muted, false, subSize);
        }

        if (hasHead) {
            RenderUtil.drawRoundedRectWithGl(pad - 1.0F, pad - 1.0F, pad + headSize + 1.0F, pad + headSize + 1.0F, 7.0F,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 210).getRGB());
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            drawRoundedHead(pad, pad, headSize, 6.0F, 8.0F, 8.0F, 1.0F);
            drawRoundedHead(pad, pad, headSize, 6.0F, 40.0F, 8.0F, 1.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            int hurt = this.target != null ? this.target.hurtTime : 0;
            if (hurt > 0) {
                RenderUtil.drawRoundedRectWithGl(pad, pad, pad + headSize, pad + headSize, 6.0F,
                        new Color(255, 60, 60, (int) (hurt / 10.0F * 120.0F)).getRGB());
            }
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private int getFrostArmorCount() {
        if (!this.frostEquip.getValue() || this.target == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 4; slot >= 1; slot--) {
            if (this.target.getEquipmentInSlot(slot) != null) {
                count++;
            }
        }
        return count;
    }

    private float getFrostEquipWidth() {
        int count = this.getFrostArmorCount();
        return count <= 0 ? 0.0F : count * 18.0F - 2.0F;
    }

    private float getFrostContentWidth(float topRowWidth) {
        return Math.max(80.0F, Math.max(topRowWidth, this.getFrostEquipWidth()));
    }

    private float getFrostCardWidth(float topRowWidth) {
        return 46.0F + this.getFrostContentWidth(topRowWidth) + 10.0F;
    }

    private float getFrostNameBlock() {
        return Math.max(11.0F, this.getTextHeight());
    }

    private float getFrostEquipBlock() {
        return this.getFrostArmorCount() > 0 ? 18.0F : 5.0F;
    }

    private float getFrostCardHeight() {
        return Math.max(38.0F, this.getFrostNameBlock() + this.getFrostEquipBlock() + 15.5F);
    }

    private String frostPlainText(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private void renderFrost(ScaledResolution scaledResolution,
                             String targetNameText, String healthText, String statusText, String healthDiffText,
                             float targetNameWidth, float healthTextWidth, float statusTextWidth, float healthDiffWidth,
                             float healthRatio, Color targetColor, Color healthBarColor, Color healthDeltaColor,
                             float heal, float playerHealth, float abs) {
        String name = this.frostPlainText(targetNameText);
        String healthStr = heal == Math.floor(heal) ? String.format("%.0f", heal) : healthFormat.format(heal);
        float nameW = this.getTextWidth(name);
        float healthNumW = this.getTextWidth(healthStr);
        boolean showIndicator = this.indicator.getValue();
        float topRowW = nameW + 6.0F + healthNumW + (showIndicator ? 9.0F : 0.0F);

        float contentW = this.getFrostContentWidth(topRowW);
        float cardWidth = 46.0F + contentW + 10.0F;
        float cardHeight = this.getFrostCardHeight();
        float nameBlock = this.getFrostNameBlock();
        float equipBlock = this.getFrostEquipBlock();
        float contentTop = (cardHeight - (nameBlock + equipBlock + 4.0F)) / 2.0F;
        float nameY = contentTop;
        float equipY = nameY + nameBlock + 0.5F;
        float barY = nameY + nameBlock + equipBlock;
        int armorCount = this.getFrostArmorCount();

        long now = System.currentTimeMillis();
        float frameDelta = this.frostLastFrame == 0L ? 0.0165F : (now - this.frostLastFrame) / 1000.0F;
        this.frostLastFrame = now;
        frameDelta = Math.min(Math.max(frameDelta, 0.001F), 0.05F);

        if (this.target != null && this.target != this.frostFadeTarget) {
            this.frostFadeTarget = this.target;
            this.frostFade = 0.0F;
            this.frostPopScale = 1.0F;
            this.frostPopVel = 0.0F;
            this.frostLagRatio = -1.0F;
            this.frostLastHurt = this.target.hurtTime;
        }
        this.frostFade += (1.0F - this.frostFade) * (1.0F - (float) Math.exp(-frameDelta * 9.0F));
        this.frostFade = Math.min(this.frostFade, 1.0F);

        if (this.target != null && this.animations.getValue() && this.frostPop.getValue()) {
            if (this.target.hurtTime > this.frostLastHurt) {
                this.frostPopScale = 0.74F;
                this.frostPopVel = 0.0F;
            }
            this.frostLastHurt = this.target.hurtTime;
        }
        this.frostPopVel += (1.0F - this.frostPopScale) * 240.0F * frameDelta;
        this.frostPopVel *= 0.72F;
        this.frostPopScale += this.frostPopVel * frameDelta;

        float fade = this.frostFade;
        float lagTarget = healthRatio;
        if (this.frostLagRatio < 0.0F) {
            this.frostLagRatio = lagTarget;
        }
        this.frostLagRatio += (lagTarget - this.frostLagRatio)
                * (lagTarget > this.frostLagRatio ? 1.0F : (1.0F - (float) Math.exp(-frameDelta * 4.5F)));
        if (this.frostLagRatio < lagTarget) {
            this.frostLagRatio = lagTarget;
        }

        float posX = this.renderingFollow ? 0.0F : this.offX.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posX.getValue()) {
                case 1:
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - cardWidth / 2.0F;
                    break;
                case 2:
                    posX *= -1.0F;
                    posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - cardWidth;
                    break;
            }
        }
        float posY = this.renderingFollow ? 0.0F : this.offY.getValue().floatValue() / this.scale.getValue();
        if (!this.renderingFollow) {
            switch (this.posY.getValue()) {
                case 1:
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - cardHeight / 2.0F;
                    break;
                case 2:
                    posY *= -1.0F;
                    posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - cardHeight;
                    break;
            }
        }

        if (!this.renderingFollow) {
            final float bx = posX;
            final float by = posY;
            final float bw = cardWidth;
            final float bh = cardHeight;
            final float sc = this.scale.getValue();
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                GlStateManager.translate(bx, by, -450.0F);
                RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, bw, bh, 5.0F, -1);
                GlStateManager.popMatrix();
            });
        }

        GlStateManager.pushMatrix();
        if (!this.renderingFollow) {
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        }
        GlStateManager.translate(posX, posY, this.renderingFollow ? 0.0F : -450.0F);

        boolean glassBg = this.frostGlass.getValue();
        int textColor = new Color(255, 255, 255, (int) (250.0F * fade)).getRGB();
        int textShadow = new Color(0, 0, 0, (int) (110.0F * fade)).getRGB();
        int trackColor = new Color(0, 0, 0, (int) (100.0F * fade)).getRGB();
        int lagColor = 0;

        if (glassBg) {
            RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, cardWidth, cardHeight, 5.0F,
                    new Color(0, 0, 0, (int) (80.0F * fade)).getRGB());
        }

        float headSize = 30.0F * this.frostPopScale * Math.max(0.7F, fade);
        float headX = 8.0F + (30.0F - headSize) / 2.0F;
        float headY = (cardHeight - headSize) / 2.0F;

        float lagW = Math.max(2.0F, contentW * this.frostLagRatio);
        float fillW = Math.max(2.0F, contentW * healthRatio * fade);
        RenderUtil.drawRoundedRectWithGl(46.0F, barY, 46.0F + contentW, barY + 4.0F,
                2.0F, trackColor);
        int frostBlue = new Color(0, 140, 255, (int) (255.0F * fade)).getRGB();
        RenderUtil.drawRoundedRectWithGl(46.0F, barY, 46.0F + fillW, barY + 4.0F,
                Math.min(2.0F, fillW / 2.0F), frostBlue);

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        this.drawText(name, 46.0F + 0.6F, nameY + (1.0F - fade) * 5.0F + 0.6F, textShadow);
        this.drawText(name, 46.0F, nameY + (1.0F - fade) * 5.0F, textColor);
        this.drawText(healthStr, 46.0F + contentW - healthNumW + 0.6F, nameY + (1.0F - fade) * 5.0F + 0.6F, textShadow);
        this.drawText(healthStr, 46.0F + contentW - healthNumW, nameY + (1.0F - fade) * 5.0F, textColor);

        if (armorCount > 0) {
            float itemX = 46.0F;
            for (int slot = 4; slot >= 1; slot--) {
                ItemStack itemStack = this.target.getEquipmentInSlot(slot);
                if (itemStack == null) {
                    continue;
                }
                RenderUtil.renderItemInGUI(itemStack, (int) itemX, (int) equipY, false);
                itemX += 18.0F;
            }
        }

        if (showIndicator) {
            float dotX = 46.0F + contentW - healthNumW - 6.5F;
            float dotY = nameY + this.getTextHeight() / 2.0F;
            Color dotFill = healthDeltaColor;
            RenderUtil.fillCircle(dotX, dotY, 2.4D, 20,
                    new Color(dotFill.getRed(), dotFill.getGreen(), dotFill.getBlue(),
                            (int) (255.0F * fade)).getRGB());
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
        }

        if (this.head.getValue() && this.headTexture != null && headSize > 1.0F) {
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, fade);
            mc.getTextureManager().bindTexture(this.headTexture);
            drawRoundedHead(headX, headY, headSize, 6.0F * this.frostPopScale, 8.0F, 8.0F, fade);
            drawRoundedHead(headX, headY, headSize, 6.0F * this.frostPopScale, 40.0F, 8.0F, fade);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void renderTriangle(ScaledResolution scaledResolution,
                                String targetNameText, String healthText, String statusText, String healthDiffText,
                                float targetNameWidth, float healthTextWidth, float statusTextWidth, float healthDiffWidth,
                                float healthRatio, Color targetColor, Color healthBarColor, Color healthDeltaColor,
                                float heal, float playerHealth, float abs) {
        final float baseWidth = 150.0F;
        final float textHeight = this.getTextHeight();
        final float height = Math.max(85.0F, 48.0F + textHeight * 2.0F);
        final float halfBase = baseWidth / 2.0F;
        final float headSize = 20.0F;
        final float barLineWidth = 3.5F;

        float triPosX = this.offX.getValue().floatValue() / this.scale.getValue();
        switch (this.posX.getValue()) {
            case 1:
                triPosX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - halfBase;
                break;
            case 2:
                triPosX *= -1.0F;
                triPosX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - baseWidth;
                break;
        }
        float triPosY = this.offY.getValue().floatValue() / this.scale.getValue();
        switch (this.posY.getValue()) {
            case 1:
                triPosY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - height / 2.0F;
                break;
            case 2:
                triPosY *= -1.0F;
                triPosY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - height;
                break;
        }
        final float tipX  = triPosX + halfBase;
        final float tipY  = triPosY;
        final float leftX = triPosX;
        final float rightX = triPosX + baseWidth;
        final float baseY = triPosY + height;

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        GlStateManager.translate(0.0F, 0.0F, -450.0F);

        if (this.shadow.getValue()) {
            RenderUtil.enableRenderState();
            RenderUtil.drawFilledTriangle(tipX, tipY + 2.0F, leftX + 2.0F, baseY + 3.0F, rightX - 2.0F, baseY + 3.0F,
                    new Color(0, 0, 0, 20).getRGB());
            RenderUtil.disableRenderState();
        }

        RenderUtil.enableRenderState();
        int bgBaseColor = this.getBackgroundColor();
        int tipAccentColor = this.getBackgroundOverlayColor(targetColor);
        RenderUtil.drawGradientTriangle(tipX, tipY, leftX, baseY, rightX, baseY, tipAccentColor, bgBaseColor);
        RenderUtil.disableRenderState();

        int trackColor = new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 45).getRGB();
        RenderUtil.enableRenderState();
        RenderUtil.drawTriangleOutline(tipX, tipY, leftX, baseY, rightX, baseY, barLineWidth, trackColor);
        int barEmptyColor = ColorUtil.darker(healthBarColor, 0.3F).getRGB();
        RenderUtil.drawTriangleProgressBorder(leftX, baseY, tipX, tipY, rightX, baseY,
                healthRatio, barLineWidth, healthBarColor.getRGB(), barEmptyColor);
        RenderUtil.disableRenderState();

        if (this.outline.getValue()) {
            RenderUtil.enableRenderState();
            RenderUtil.drawTriangleOutline(tipX, tipY, leftX, baseY, rightX, baseY, 2.0F, targetColor.getRGB());
            RenderUtil.drawTriangleOutline(tipX, tipY, leftX - 0.5F, baseY + 0.5F, rightX + 0.5F, baseY + 0.5F, 4.0F,
                    new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 50).getRGB());
            RenderUtil.disableRenderState();
        }

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        final float nameY = tipY + 38.0F;
        final float infoY = nameY + textHeight + 2.0F;
        final float hwName = halfBase * (nameY - tipY) / height;
        final float hwInfo = halfBase * (infoY - tipY) / height;
        final float textMargin = 5.0F;

        this.drawText(targetNameText,
                tipX - hwName + textMargin, nameY, -1);
        if (this.indicator.getValue()) {
            this.drawText(statusText,
                    tipX + hwName - textMargin - statusTextWidth, nameY,
                    healthDeltaColor.getRGB());
        }

        String finalHealthText = ChatColors.formatColor(
                String.format("&r&f%s%s❤&r",
                        new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US)).format(heal),
                        abs > 0.0F ? "&6" : "&c")
        );
        this.drawText(finalHealthText,
                tipX - hwInfo + textMargin, infoY, -1);
        if (this.indicator.getValue()) {
            String diffStr = ChatColors.formatColor(
                    String.format("&r%s&r", heal == playerHealth ? "0.0"
                            : new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US))
                                    .format(playerHealth - heal))
            );
            float diffW = this.getTextWidth(diffStr);
            this.drawText(diffStr,
                    tipX + hwInfo - textMargin - diffW, infoY,
                    ColorUtil.darker(healthDeltaColor, 0.8F).getRGB());
        }

        if (this.head.getValue() && this.headTexture != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            final float headX = tipX - headSize / 2.0F;
            final float headY = tipY + 14.0F;
            Gui.drawScaledCustomSizeModalRect((int) headX, (int) headY, 8.0F, 8.0F, 8, 8,
                    (int) headSize, (int) headSize, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect((int) headX, (int) headY, 40.0F, 8.0F, 8, 8,
                    (int) headSize, (int) headSize, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() != Action.ATTACK) {
                return;
            }
            Entity entity = packet.getEntityFromWorld(mc.theWorld);
            if (entity instanceof EntityLivingBase) {
                if (entity instanceof EntityArmorStand) {
                    return;
                }
                this.lastAttackTimer.reset();
                this.lastTarget = (EntityLivingBase) entity;
            }
        }
    }

    private static class HitParticle {
        final float x;
        final float y;
        final float vx;
        final float vy;
        final float size;
        final long startTime;

        HitParticle(float x, float y, float vx, float vy, float size, long startTime) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.startTime = startTime;
        }
    }
}
