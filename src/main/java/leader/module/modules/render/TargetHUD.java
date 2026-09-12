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
import leader.util.ColorUtil;
import leader.util.RenderUtil;
import leader.util.TeamUtil;
import leader.util.TimerUtil;
import leader.util.shader.KawaseBlur;
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
import net.minecraft.client.shader.Framebuffer;
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
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"DEFAULT", "TRIANGLE", "BACKGROUND", "MODERN", "INK", "AURA", "FROST"});
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
    public final BooleanProperty blur = new BooleanProperty("blur", false, () -> this.mode.getValue() >= 2);
    public final BooleanProperty frostEquip = new BooleanProperty("frost-equip", true, () -> this.mode.getValue() == 6);
    public final BooleanProperty frostLag = new BooleanProperty("frost-lag", true, () -> this.mode.getValue() == 6);
    public final BooleanProperty frostPop = new BooleanProperty("frost-pop", true, () -> this.mode.getValue() == 6);
    public final BooleanProperty frostGlass = new BooleanProperty("frost-glass", true, () -> this.mode.getValue() == 6);
    private float frostLagRatio = -1.0F;
    private float frostFade = 0.0F;
    private float frostPopScale = 1.0F;
    private float frostPopVel = 0.0F;
    private int frostLastHurt = 0;
    private long frostLastFrame = 0L;
    private EntityLivingBase frostFadeTarget = null;
    public final IntProperty blurIterations = new IntProperty("blur-iterations", 2, 1, 8, blur::getValue);
    public final IntProperty blurOffset = new IntProperty("blur-offset", 3, 1, 10, blur::getValue);
    private Framebuffer blurStencil;

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

    public void drawBlur() {
        if (!this.blur.getValue()) {
            return;
        }

        blurStencil = ShaderElement.createFrameBuffer(blurStencil);
        blurStencil.framebufferClear();
        blurStencil.bindFramebuffer(false);
        for (Runnable runnable : ShaderElement.getTasks()) {
            runnable.run();
        }
        ShaderElement.getTasks().clear();
        blurStencil.unbindFramebuffer();
        KawaseBlur.renderBlur(blurStencil.framebufferTexture, blurIterations.getValue(), blurOffset.getValue());
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
        RenderUtil.drawLine(x1, y1, x2, y1, width, color);
        RenderUtil.drawLine(x2, y1, x2, y2, width, color);
        RenderUtil.drawLine(x2, y2, x1, y2, width, color);
        RenderUtil.drawLine(x1, y2, x1, y1, width, color);
    }

    private float getTextScale() {
        return this.fontScale.getValue();
    }

    private float getTextWidth(String text) {
        return FontManager.getStringWidth(text) * this.getTextScale();
    }

    private float getTextHeight() {
        return FontManager.getFontHeight() * this.getTextScale();
    }

    private void drawText(String text, float x, float y, int color) {
        float textScale = this.getTextScale();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(textScale, textScale, 1.0F);
        FontManager.drawString(text, 0.0F, 0.0F, color, this.shadow.getValue());
        GlStateManager.popMatrix();
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
        String targetNameText = ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(targetEntity)));
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

        // Follow uses a fixed world-space size. Do not scale the HUD by view distance.
        double followScale = 0.0075D * this.scale.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.translate(targetX + sideX - renderManager.getRenderPosX(), targetY - renderManager.getRenderPosY(), targetZ + sideZ - renderManager.getRenderPosZ());
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-followScale, -followScale, 1.0D);
        GlStateManager.translate(-cardWidth / 2.0F, -cardHeight / 2.0F, 0.0F);

        // The SCREEN renderers calculate their own 2D coordinates. Reusing them
        // in a world-space matrix puts the card far outside the target. Follow
        // therefore uses a local, centered card renderer.
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
                String targetNameText = ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(this.target)));
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

        if (this.blur.getValue() && !this.renderingFollow) {
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

        if (this.blur.getValue() && !this.renderingFollow) {
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

        // Flat card: thin rim + single translucent pane. No tint, no shine.
        int glassAlpha = Math.max(150, Math.min(205, this.getBackgroundAlpha()));
        int rimColor = this.outline.getValue()
                ? new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 90).getRGB()
                : new Color(255, 255, 255, 30).getRGB();
        int glassColor = new Color(14, 16, 22, glassAlpha).getRGB();

        RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, cardWidth, cardHeight, radius, rimColor);
        RenderUtil.drawRoundedRectWithGl(1.0F, 1.0F, cardWidth - 1.0F, cardHeight - 1.0F, radius - 1.0F, glassColor);

        // Health bar: dim track + solid fill, no glow.
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
            // drawRoundedRectWithGl re-enables depth and disables blend; restore
            // the state the head texture was originally rendered with.
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            // Face + hat overlay, both clipped to the rounded plate shape.
            drawRoundedHead(headX + shake, headY - shake * 0.4F, headSize, 5.5F, 8.0F, 8.0F);
            drawRoundedHead(headX + shake, headY - shake * 0.4F, headSize, 5.5F, 40.0F, 8.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        // Draw particles after the textured head so the hit burst stays on top.
        this.drawHitParticles(headX + headSize / 2.0F, headY + headSize / 2.0F, healthBarColor);

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    /**
     * Draws an 8x8 region of a 64x64 skin texture clipped to a rounded rect by
     * tessellating the rounded outline as a triangle fan with mapped UVs.
     */
    private void drawRoundedHead(float x, float y, float size, float radius, float texU, float texV) {
        float x2 = x + size;
        float y2 = y + size;
        float cx = x + size / 2.0F;
        float cy = y + size / 2.0F;
        float[][] corners = {
                {x2 - radius, y + radius, 270.0F},
                {x2 - radius, y2 - radius, 0.0F},
                {x + radius, y2 - radius, 90.0F},
                {x + radius, y + radius, 180.0F}
        };
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        // The fan winding ends up clockwise in window space (back face), and
        // surrounding rendering re-enables culling — disable it while drawing.
        GlStateManager.disableCull();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_TEX);
        wr.pos(cx, cy, 0.0D).tex((texU + 4.0F) / 64.0D, (texV + 4.0F) / 64.0D).endVertex();
        int steps = 4;
        float firstX = 0.0F;
        float firstY = 0.0F;
        double firstU = 0.0D;
        double firstV = 0.0D;
        boolean first = true;
        for (float[] corner : corners) {
            for (int i = 0; i <= steps; i++) {
                double rad = Math.toRadians(corner[2] + i * 90.0F / steps);
                float px = corner[0] + (float) Math.cos(rad) * radius;
                float py = corner[1] + (float) Math.sin(rad) * radius;
                double tu = (texU + (px - x) / size * 8.0F) / 64.0D;
                double tv = (texV + (py - y) / size * 8.0F) / 64.0D;
                wr.pos(px, py, 0.0D).tex(tu, tv).endVertex();
                if (first) {
                    first = false;
                    firstX = px;
                    firstY = py;
                    firstU = tu;
                    firstV = tv;
                }
            }
        }
        wr.pos(firstX, firstY, 0.0D).tex(firstU, firstV).endVertex();
        tessellator.draw();
        GlStateManager.enableCull();
    }

    /** Draws a ring segment (TRIANGLE_STRIP arc band). Angles in degrees, 0 = east, 90 = south. */
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

    /**
     * AURA mode: minimal-text modern card. A rounded-corner head wrapped by a
     * glowing health ring replaces the health bar; the only text is the target
     * name and one oversized health number.
     */
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

        if (this.blur.getValue() && !this.renderingFollow) {
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

        // Flat, solid modern card with a soft offset shadow. No glass layers.
        int shadowColor = new Color(0, 0, 0, 55).getRGB();
        int cardAlpha = Math.max(215, Math.min(245, this.getBackgroundAlpha() + 60));
        int cardColor = new Color(20, 22, 27, cardAlpha).getRGB();
        RenderUtil.drawRoundedRectWithGl(0.0F, 2.0F, cardWidth, cardHeight + 2.0F, radius, shadowColor);
        RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, cardWidth, cardHeight, radius, cardColor);

        // Health ring around the head: dim track, glowing progress arc. This
        // ring replaces the health bar entirely.
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

        // Rounded-corner head inside the ring, no backing plate.
        if (hasHead) {
            float headX = 11.0F;
            float headY = (cardHeight - headSize) / 2.0F;
            RenderUtil.drawRoundedRectWithGl(headX, headY, headX + headSize, headY + headSize, 6.0F,
                    new Color(16, 18, 23, 220).getRGB());
            // drawRoundedRectWithGl re-enables depth and disables blend; restore
            // the state the head texture was originally rendered with.
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            drawRoundedHead(headX, headY, headSize, 6.0F, 8.0F, 8.0F);
            drawRoundedHead(headX, headY, headSize, 6.0F, 40.0F, 8.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Only two pieces of text: the name and one oversized health number.
        float nameY = (cardHeight - (textHeight + 3.5F + textHeight * 1.15F)) / 2.0F;
        this.drawText(targetNameText, textX, nameY, -1);
        float numScale = this.getTextScale() * 1.15F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(textX, nameY + textHeight + 3.5F, 0.0F);
        GlStateManager.scale(numScale, numScale, 1.0F);
        FontManager.drawString(healthFormat.format(heal), 0.0F, 0.0F, arcColor, this.shadow.getValue());
        GlStateManager.popMatrix();

        if (this.indicator.getValue()) {
            // W/L/D shown as a small status dot beside the name line.
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

    /** Tapered calligraphy brush stroke: thick at the root, thin at the tip. */
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

    /**
     * INK mode (古风简洁): flat ink-dark card with a double hairline mounting
     * frame, a cinnabar binding thread, a vermilion W/L/D seal and a tapered
     * calligraphy brush stroke as the health bar.
     */
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

        if (this.blur.getValue() && !this.renderingFollow) {
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

        // Paper: flat warm ink-dark, no rounding, no glow.
        int paperAlpha = Math.max(140, Math.min(195, this.getBackgroundAlpha()));
        int paper = new Color(26, 23, 20, paperAlpha).getRGB();
        int frameOuter = new Color(198, 186, 168, 96).getRGB();
        int frameInner = new Color(198, 186, 168, 42).getRGB();
        int cinnabar = new Color(172, 54, 44, 230).getRGB();

        RenderUtil.enableRenderState();
        RenderUtil.drawRect(0.0F, 0.0F, cardWidth, cardHeight, paper);
        // Double hairline mounting frame (装裱).
        RenderUtil.drawRect(0.0F, 0.0F, cardWidth, 1.0F, frameOuter);
        RenderUtil.drawRect(0.0F, cardHeight - 1.0F, cardWidth, cardHeight, frameOuter);
        RenderUtil.drawRect(0.0F, 0.0F, 1.0F, cardHeight, frameOuter);
        RenderUtil.drawRect(cardWidth - 1.0F, 0.0F, cardWidth, cardHeight, frameOuter);
        RenderUtil.drawRect(2.5F, 2.5F, cardWidth - 2.5F, 3.5F, frameInner);
        RenderUtil.drawRect(2.5F, cardHeight - 3.5F, cardWidth - 2.5F, cardHeight - 2.5F, frameInner);
        RenderUtil.drawRect(2.5F, 2.5F, 3.5F, cardHeight - 2.5F, frameInner);
        RenderUtil.drawRect(cardWidth - 3.5F, 2.5F, cardWidth - 2.5F, cardHeight - 2.5F, frameInner);
        // Cinnabar binding thread (书签绳).
        RenderUtil.drawRect(5.5F, 6.0F, 7.0F, cardHeight - 6.0F, cinnabar);

        // Head: square portrait with a thin ink frame (画像).
        if (hasHead) {
            float headX = 11.0F;
            float headY = (cardHeight - headSize) / 2.0F;
            RenderUtil.drawRect(headX - 1.5F, headY - 1.5F, headX + headSize + 1.5F, headY + headSize + 1.5F, frameOuter);
            RenderUtil.drawRect(headX - 0.5F, headY - 0.5F, headX + headSize + 0.5F, headY + headSize + 0.5F, new Color(26, 23, 20, 220).getRGB());
        }

        // Vermilion seal (印章) for the W/L/D indicator.
        float sealX = cardWidth - 23.0F;
        float sealY = (cardHeight - 13.0F) / 2.0F;
        if (this.indicator.getValue()) {
            RenderUtil.drawRect(sealX, sealY, sealX + 13.0F, sealY + 13.0F, new Color(140, 38, 30, 240).getRGB());
            RenderUtil.drawRect(sealX + 1.0F, sealY + 1.0F, sealX + 12.0F, sealY + 12.0F, cinnabar);
        }
        RenderUtil.disableRenderState();

        // Calligraphy brush stroke health bar: faint track + cinnabar stroke.
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

        if (this.blur.getValue() && !this.renderingFollow) {
            final float bx = posX;
            final float by = posY;
            final float bw = cardWidth;
            final float bh = cardHeight;
            final float sc = this.scale.getValue();
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                GlStateManager.translate(bx, by, -450.0F);
                RenderUtil.drawRoundedRectWithGl(0.0F, 0.0F, bw, bh, 9.0F, -1);
                GlStateManager.popMatrix();
            });
        }

        GlStateManager.pushMatrix();
        if (!this.renderingFollow) {
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
        }
        GlStateManager.translate(posX, posY, this.renderingFollow ? 0.0F : -450.0F);

        boolean glassBg = this.frostGlass.getValue();
        int textColor = glassBg ? new Color(20, 24, 34, (int) (232.0F * fade)).getRGB()
                : new Color(255, 255, 255, (int) (240.0F * fade)).getRGB();
        int trackColor = glassBg ? new Color(20, 24, 34, (int) (22.0F * fade)).getRGB()
                : new Color(0, 0, 0, (int) (95.0F * fade)).getRGB();
        int lagColor = glassBg ? new Color(20, 24, 34, (int) (58.0F * fade)).getRGB()
                : new Color(0, 0, 0, (int) (140.0F * fade)).getRGB();
        Color healthFill = ColorUtil.darker(healthBarColor, glassBg ? 0.82F : 1.0F);

        if (glassBg) {
            RenderUtil.drawGlass(0.0F, 0.0F, cardWidth, cardHeight, 9.0F, fade);
        }

        float headSize = 30.0F * this.frostPopScale * Math.max(0.7F, fade);
        float headX = 8.0F + (30.0F - headSize) / 2.0F;
        float headY = (cardHeight - headSize) / 2.0F;
        if (glassBg) {
            RenderUtil.drawRoundedRectWithGl(headX - 0.6F, headY - 0.6F, headX + headSize + 0.6F, headY + headSize + 0.6F,
                    6.6F, new Color(20, 24, 34, (int) (26.0F * fade)).getRGB());
        }

        float lagW = Math.max(2.0F, contentW * this.frostLagRatio);
        float fillW = Math.max(2.0F, contentW * healthRatio * fade);
        RenderUtil.drawRoundedRectWithGl(46.0F, barY, 46.0F + contentW, barY + 4.0F,
                2.0F, trackColor);
        if (this.frostLag.getValue()) {
            RenderUtil.drawRoundedRectWithGl(46.0F, barY, 46.0F + lagW, barY + 4.0F,
                    Math.min(2.0F, lagW / 2.0F), lagColor);
        }
        RenderUtil.drawRoundedRectWithGl(46.0F, barY, 46.0F + fillW, barY + 4.0F,
                Math.min(2.0F, fillW / 2.0F), new Color(healthFill.getRed(), healthFill.getGreen(),
                        healthFill.getBlue(), (int) (245.0F * fade)).getRGB());

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        this.drawText(name, 46.0F, nameY + (1.0F - fade) * 5.0F, textColor);
        Color numberFill = glassBg ? ColorUtil.darker(healthFill, 0.78F) : healthFill;
        int numberColor = new Color(numberFill.getRed(), numberFill.getGreen(), numberFill.getBlue(),
                (int) (240.0F * fade)).getRGB();
        this.drawText(healthStr, 46.0F + contentW - healthNumW, nameY + (1.0F - fade) * 5.0F, numberColor);

        if (armorCount > 0) {
            float itemX = 46.0F;
            for (int slot = 4; slot >= 1; slot--) {
                ItemStack itemStack = this.target.getEquipmentInSlot(slot);
                if (itemStack == null) {
                    continue;
                }
                RenderUtil.renderItemInGUI(itemStack, (int) itemX, (int) equipY);
                itemX += 18.0F;
            }
        }

        if (showIndicator) {
            float dotX = 46.0F + contentW - healthNumW - 6.5F;
            float dotY = nameY + this.getTextHeight() / 2.0F;
            Color dotFill = glassBg ? ColorUtil.darker(healthDeltaColor, 0.82F) : healthDeltaColor;
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
            drawRoundedHead(headX, headY, headSize, 6.0F * this.frostPopScale, 8.0F, 8.0F);
            drawRoundedHead(headX, headY, headSize, 6.0F * this.frostPopScale, 40.0F, 8.0F);
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
