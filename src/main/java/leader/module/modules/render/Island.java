package leader.module.modules.render;

import leader.Leader;
import leader.management.PingTracker;
import leader.event.EventTarget;
import leader.events.Render2DEvent;
import leader.events.LoadWorldEvent;
import leader.module.Module;
import leader.module.modules.combat.KillAura;
import leader.module.modules.player.Scaffold;
import leader.module.modules.render.notification.Notification;
import leader.module.modules.render.notification.NoticeMode;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.util.BlockUtil;
import leader.util.Icon;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Locale;

public class Island extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final FloatProperty fontScale = new FloatProperty("font-scale", 1.0F, 0.7F, 1.5F);
    public final IntProperty holdTime = new IntProperty("hold-time", 1100, 0, 5000);
    public final BooleanProperty blur = new BooleanProperty("blur", true);
    public final FloatProperty cornerRadius = new FloatProperty("corner-radius", 12.0F, 4.0F, 18.0F);
    public final FloatProperty animationSpeed = new FloatProperty("animation-speed", 1.0F, 0.5F, 2.0F);
    public final BooleanProperty showMetrics = new BooleanProperty("show-metrics", true);
    public final BooleanProperty healthWarning = new BooleanProperty("health-warning", true);
    public final BooleanProperty fontGlow = new BooleanProperty("font-glow", false);
    public final FloatProperty glowStrength = new FloatProperty("glow-strength", 0.65F, 0.1F, 1.0F, fontGlow::getValue);

    private static final int IDLE = 0;
    private static final int BLOCKS = 1;
    private static final int TARGET = 2;
    private static final int DANGER = 3;
    private static final int ALERT = 4;

    private int shown = IDLE;
    private int pending = IDLE;
    private long lastActive;
    private long lastFrame;
    private float animW;
    private float animH;
    private float velW;
    private float velH;
    private float contentFade = 1.0F;
    private EntityLivingBase lastTarget;
    private float hpAnim;
    private float hpGhost;
    private float blocksAnim;
    private long alertStart;
    private String alertTitle = "";
    private String alertDescription = "";
    private NoticeMode alertMode = NoticeMode.Info;
    private float alertDuration = 1500;
    private NoticeMode previewNoticeMode = NoticeMode.Enable;
    private static final Color SECONDARY = new Color(149, 158, 177);
    private static final Color FOREGROUND = new Color(238, 242, 250);

    public Island() {
        super("Island", false);
    }

    @Override
    public void onEnabled() {
        resetAnimation();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        resetAnimation();
    }

    private void resetAnimation() {
        shown = pending = IDLE;
        lastActive = lastFrame = 0L;
        animW = animH = velW = velH = hpAnim = hpGhost = blocksAnim = 0.0F;
        contentFade = 1.0F;
        lastTarget = null;
        alertStart = 0;
        alertTitle = alertDescription = "";
    }

    private EntityLivingBase getTarget() {
        if (!Leader.hudElementManager.isSuppressed("TargetHUD")) return null;
        TargetHUD hud = (TargetHUD) Leader.moduleManager.modules.get(TargetHUD.class);
        if (hud == null || !hud.isEnabled()) return null;
        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return null;
        EntityLivingBase target = killAura.getTarget();
        return target != null && target.isEntityAlive() ? target : null;
    }

    private boolean isLowHealth() {
        return this.healthWarning.getValue() && mc.thePlayer != null
                && mc.thePlayer.getHealth() > 0.0F && mc.thePlayer.getHealth() <= 6.0F;
    }

    private boolean isScaffolding() {
        if (!Leader.hudElementManager.isSuppressed("ScaffoldCounter")) return false;
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        if (scaffold == null || !scaffold.isEnabled() || !scaffold.blockCounter.getValue()) return false;
        return true;
    }

    private boolean hasAlert() {
        if (!Leader.hudElementManager.isSuppressed("Notification")) return false;
        Notification notification = (Notification) Leader.moduleManager.modules.get(Notification.class);
        return notification != null && notification.isEnabled()
                && Notification.hasLatest() && Notification.latestProgress() > 0.0F;
    }

    private int resolveState(EntityLivingBase target) {
        if (target != null) return TARGET;
        if (this.isLowHealth()) return DANGER;
        if (this.isScaffolding()) return BLOCKS;
        if (this.hasAlert()) return ALERT;
        return IDLE;
    }


    private static int alpha(Color c, float a) {
        int v = Math.max(0, Math.min(255, (int) a));
        return (v << 24) | (c.getRGB() & 0x00FFFFFF);
    }

    private static float approach(float current, float target, float k) {
        return current + (target - current) * k;
    }

    private static String fit(String text, float maxW, float size) {
        if (maxW <= 0.0F) return "";
        if (FontManager.getStringWidth(text, size) <= maxW) return text;
        String out = text;
        while (!out.isEmpty() && FontManager.getStringWidth(out + "..", size) > maxW) {
            out = out.substring(0, out.offsetByCodePoints(out.length(), -1));
        }
        return out.isEmpty() ? "" : out + "..";
    }

    private static ResourceLocation getSkin(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer && mc.getNetHandler() != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(entity.getName());
            if (info != null) return info.getLocationSkin();
        }
        return null;
    }

    private Color accent(long now) {
        HUD hud = (HUD) Leader.moduleManager.modules.get(HUD.class);
        return hud != null ? hud.getColor(now) : new Color(120, 170, 255);
    }

    private void text(String s, float x, float baseline, float size, int color) {
        if ((color >>> 24) < 4) return;
        if (fontGlow.getValue()) FontManager.drawStringWithGlow(s, x, baseline - FontManager.getBaseline(size), color, size, glowStrength.getValue());
        else FontManager.drawString(s, x, baseline - FontManager.getBaseline(size), color, false, size);
    }

    public float contentWidth(int state, float nameSize, float metaSize, EntityLivingBase target) {
        switch (state) {
            case TARGET: {
                float nameW = Math.min(110.0F, FontManager.getStringWidth(target != null ? target.getName() : "", nameSize));
                return Math.max(168.0F, 12.0F + 26.0F + 9.0F + nameW + 46.0F + 12.0F);
            }
            case BLOCKS:
                return Math.max(170, 60 + FontManager.getStringWidth("999", nameSize + 4)
                        + FontManager.getStringWidth("blocks", metaSize) + FontManager.getStringWidth("9.99 b/s", metaSize));
            case DANGER:
                return Math.max(154, 70 + FontManager.getStringWidth("Low health", nameSize)
                        + FontManager.getStringWidth("6.0", nameSize + 2));
            case ALERT:
                return alertWidth(Notification.latestText(), Notification.latestDescription(),
                        Notification.latestMode(), nameSize, metaSize);
            default: {
                return idleWidth(nameSize, metaSize, false);
            }
        }
    }

    public float contentHeight(int state, float nameSize) {
        float cap = FontManager.getCapHeight(nameSize);
        switch (state) {
            case TARGET:
                return Math.max(34.0F, cap + 22.0F);
            case BLOCKS:
                return Math.max(26, cap + 17);
            case DANGER:
                return Math.max(26, cap + 17);
            case ALERT:
                return Math.max(26, cap + 17);
            default:
                return Math.max(21, cap + 13);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.currentScreen instanceof leader.ui.GuiHUDDesigner) return;

        long now = System.currentTimeMillis();
        float dt = this.lastFrame == 0L ? 0.016F : Math.max(0.0F, Math.min(0.05F, (now - this.lastFrame) / 1000.0F));
        this.lastFrame = now;

        EntityLivingBase liveTarget = this.getTarget();
        if (liveTarget != null && liveTarget != this.lastTarget) {
            this.lastTarget = liveTarget;
            this.hpAnim = this.hpGhost = Math.max(0.0F, Math.min(1.0F,
                    liveTarget.getHealth() / Math.max(1.0F, liveTarget.getMaxHealth())));
        }
        int resolved = this.resolveState(liveTarget);
        if (resolved != IDLE) {
            this.lastActive = now;
            this.pending = resolved;
        } else if (this.pending == ALERT || now - this.lastActive > this.holdTime.getValue()) {
            this.pending = IDLE;
        }

        boolean newAlert = pending == ALERT && shown == ALERT && Notification.latestStartTime() != alertStart;
        if (this.pending != this.shown || newAlert) {
            this.contentFade = Math.max(0.0F, this.contentFade - dt * 9.0F * animationSpeed.getValue());
            if (this.contentFade <= 0.0F) {
                this.shown = this.pending;
                if (shown == ALERT) captureAlert();
            }
        } else {
            this.contentFade = Math.min(1.0F, this.contentFade + dt * 6.0F * animationSpeed.getValue());
        }

        float nameSize = 14.0F * this.fontScale.getValue();
        float metaSize = 11.0F * this.fontScale.getValue();
        float targetW = this.contentWidth(this.pending, nameSize, metaSize, this.lastTarget);
        float baseH = this.contentHeight(this.pending, nameSize);
        float targetH = baseH;
        if (this.animW <= 0.0F) {
            this.animW = targetW;
            this.animH = targetH;
        }
        // Substeps keep the spring stable even at low FPS or high animation speed.
        float remaining = dt * animationSpeed.getValue();
        while (remaining > 0.0F) {
            float step = Math.min(remaining, 1.0F / 120.0F);
            this.velW += ((targetW - this.animW) * 260.0F - this.velW * 24.0F) * step;
            this.velH += ((targetH - this.animH) * 260.0F - this.velH * 24.0F) * step;
            this.animW += this.velW * step;
            this.animH += this.velH * step;
            remaining -= step;
        }

        float sc = this.scale.getValue();
        ScaledResolution sr = new ScaledResolution(mc);
        float w = this.animW;
        float h = this.animH;
        float r = Math.min(h / 2.0F, cornerRadius.getValue());
        float defaultX = sr.getScaledWidth() / 2.0F - w * sc / 2.0F;
        float x = Leader.hudElementManager.x("Island", defaultX, 8.0F) / sc;
        float y = Leader.hudElementManager.y("Island", defaultX, 8.0F) / sc;

        GlStateManager.pushMatrix();
        GlStateManager.scale(sc, sc, 1.0F);

        if (this.blur.getValue()) {
            final float bx = x;
            final float by = y;
            final float bw = w;
            final float bh = h;
            final float br = r;
            ShaderElement.addBlurTask(() -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(sc, sc, 1.0F);
                RenderUtil.drawRoundedRectWithGl(bx, by, bx + bw, by + bh, br, -1);
                GlStateManager.popMatrix();
            });
        }

        Color accent = this.accent(now);
        Color edge = this.shown == DANGER ? new Color(255, 96, 92) : accent;
        drawSurface(x, y, w, h, r, edge, 1.0F, shown == TARGET);

        float ease = this.contentFade * this.contentFade * (3.0F - 2.0F * this.contentFade);
        if (ease > 0.01F) {
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float slide = (1.0F - ease) * 3.0F;
            switch (this.shown) {
                case TARGET:
                    this.drawTarget(x, y + slide, w, h, ease, dt, nameSize, metaSize, accent);
                    break;
                case BLOCKS:
                    this.drawBlocks(x, y + slide, w, h, ease, dt, nameSize, metaSize, accent, false);
                    break;
                case DANGER:
                    this.drawDanger(x, y + slide, w, h, ease, now, nameSize, metaSize, false);
                    break;
                case ALERT:
                    this.drawAlert(x, y + slide, w, h, ease, nameSize, metaSize, false);
                    break;
                default:
                    this.drawIdle(x, y + slide, w, h, ease, nameSize, metaSize, false);
                    break;
            }
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
        }

        GlStateManager.popMatrix();
    }

    private static int fade(Color c, float a, float ease) {
        return alpha(c, a * ease);
    }

    private void captureAlert() {
        alertStart = Notification.latestStartTime();
        alertTitle = Notification.latestText();
        alertDescription = Notification.latestDescription();
        alertMode = Notification.latestMode();
        Notification notification = (Notification) Leader.moduleManager.modules.get(Notification.class);
        alertDuration = notification != null ? notification.duration.getValue() : 1500;
    }

    private Color noticeTint(NoticeMode mode) {
        switch (mode) {
            case Enable: return new Color(137, 213, 175);
            case Disable: return new Color(225, 144, 156);
            default: return new Color(222, 192, 139);
        }
    }

    private String noticeDetail(String description, NoticeMode mode) {
        if (!description.isEmpty()) return description;
        return mode == NoticeMode.Enable ? "Enabled" : mode == NoticeMode.Disable ? "Disabled" : "Client notice";
    }

    private float alertWidth(String title, String description, NoticeMode mode, float nameSize, float metaSize) {
        float textWidth = Math.max(FontManager.getStringWidth(title, nameSize),
                FontManager.getStringWidth(noticeDetail(description, mode), metaSize));
        return Math.max(146, Math.min(260, 65 + textWidth));
    }

    private float titleBaseline(float y, float h, float nameSize, float metaSize) {
        float titleCap = FontManager.getCapHeight(nameSize), metaCap = FontManager.getCapHeight(metaSize);
        return y + (h - titleCap - metaCap - 3) / 2 + titleCap;
    }

    private void iconTile(float x, float y, Icon icon, Color tint, float opacity) {
        RenderUtil.drawRoundedRectWithGl(x, y, x + 18, y + 18, 4, fade(tint, 13, opacity));
        icon.drawCentered(x + 9, y + 9, 11, tint.getRGB(), opacity);
    }

    private void noticeGlyph(float x, float y, NoticeMode mode, Color tint, float opacity) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.75F, 0.75F, 1);
        x = y = 0;
        RenderUtil.drawRoundedRectWithGl(x, y, x + 24, y + 24, 6, fade(tint, 18, opacity));
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        int ink = fade(tint, 255, opacity);
        if (mode == NoticeMode.Enable) {
            RenderUtil.drawLine(x + 7, y + 12, x + 10.5F, y + 15.5F, 1.6F, ink);
            RenderUtil.drawLine(x + 10.5F, y + 15.5F, x + 17, y + 8.5F, 1.6F, ink);
        } else if (mode == NoticeMode.Disable) {
            RenderUtil.drawLine(x + 8, y + 8, x + 16, y + 16, 1.6F, ink);
            RenderUtil.drawLine(x + 16, y + 8, x + 8, y + 16, 1.6F, ink);
        } else {
            RenderUtil.drawRoundedRectWithGl(x + 11.2F, y + 7, x + 12.8F, y + 13, 0.8F, ink);
            RenderUtil.fillCircle(x + 12, y + 16, 0.9F, 12, ink);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private void lifetimeRing(float cx, float cy, float progress, Color tint, float opacity) {
        progress = Math.max(0, Math.min(1, progress));
        if (BetterFPS.optimizedHUD()) {
            // Previously 32 glBegin/glEnd calls plus repeated GL state changes for a five-pixel ring.
            RenderUtil.drawArcRing(cx, cy, 4.5F, 1, -90, 360, fade(tint, 24, opacity));
            if (progress > 0) RenderUtil.drawArcRing(cx, cy, 4.5F, 1, -90, progress * 360, fade(tint, 145, opacity));
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32 - Math.PI / 2;
            double b = Math.PI * 2 * (i + 1) / 32 - Math.PI / 2;
            RenderUtil.drawLine(cx + (float) Math.cos(a) * 4.5F, cy + (float) Math.sin(a) * 4.5F,
                    cx + (float) Math.cos(b) * 4.5F, cy + (float) Math.sin(b) * 4.5F, 1,
                    fade(tint, i / 32.0F < progress ? 145 : 24, opacity));
        }
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawSurface(float x, float y, float w, float h, float radius, Color accent, float opacity, boolean target) {
        Color bg = Leader.hudElementManager.backgroundColor("Island", 40.0F, 40.0F);
        if (!target) {
            // One restrained graphite surface: readable over the world without a stacked card effect.
            RenderUtil.drawRoundedRectWithGl(x, y, x + w, y + h, radius,
                    alpha(new Color(bg.getRed() / 2, bg.getGreen() / 2, bg.getBlue() / 2), bg.getAlpha() * opacity * 0.5F));
            return;
        }
        RenderUtil.drawRoundedRectWithGl(x, y + 2.0F, x + w, y + h + 2.0F, radius, alpha(Color.BLACK, 38 * opacity));
        RenderUtil.drawRoundedRectWithGl(x - 0.6F, y - 0.6F, x + w + 0.6F, y + h + 0.6F,
                radius + 0.6F, alpha(new Color(170, 182, 208), 35 * opacity));
        RenderUtil.drawRoundedRectGradient(x, y, x + w, y + h, radius,
                alpha(bg, bg.getAlpha() * opacity),
                alpha(new Color(Math.max(0, bg.getRed() - 10), Math.max(0, bg.getGreen() - 10),
                        Math.max(0, bg.getBlue() - 10)), bg.getAlpha() * opacity));
        RenderUtil.drawRoundedRectGradientH(x, y, x + w, y + h, radius,
                alpha(accent, 10 * opacity), alpha(accent, 0));
        RenderUtil.drawRoundedRectWithGl(x + radius, y + 0.5F, x + w - radius, y + 1.0F,
                0.25F, alpha(Color.WHITE, 24 * opacity));
    }

    private void drawTarget(float x, float y, float w, float h, float ease, float dt, float nameSize, float metaSize, Color accent) {
        EntityLivingBase target = this.lastTarget;
        if (target == null) return;

        float max = Math.max(1.0F, target.getMaxHealth());
        float hp = Math.max(0.0F, Math.min(max, target.getHealth()));
        float ratio = hp / max;
        float k = 1.0F - (float) Math.exp(-dt * 12.0F);
        this.hpAnim = approach(this.hpAnim, ratio, k);
        if (this.hpGhost < this.hpAnim) this.hpGhost = this.hpAnim;
        else this.hpGhost = approach(this.hpGhost, this.hpAnim, 1.0F - (float) Math.exp(-dt * 3.0F));

        float head = 28.0F;
        float hx = x + 10.0F;
        float hy = y + (h - head) / 2.0F;

        RenderUtil.drawRoundedRectWithGl(hx, hy, hx + head, hy + head, 5, fade(new Color(22, 24, 30), 255, ease));
        RenderUtil.drawRoundedRectWithGl(hx, hy, hx + head, hy + head, 5, fade(accent, 25, ease));

        ResourceLocation skin = getSkin(target);
        if (skin != null) {
            float hurt = target.hurtTime > 0 ? target.hurtTime / 10.0F : 0.0F;
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F - hurt * 0.5F, 1.0F - hurt * 0.5F, ease);
            mc.getTextureManager().bindTexture(skin);
            drawRoundedSkin(hx, hy, head, 5, 8, 8);
            drawRoundedSkin(hx, hy, head, 5, 40, 8);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            String initial = target.getName().isEmpty() ? "?" : target.getName().substring(0, 1).toUpperCase();
            float iw = FontManager.getStringWidth(initial, nameSize);
            this.text(initial, hx + (head - iw) / 2.0F, hy + (head + FontManager.getCapHeight(nameSize)) / 2.0F, nameSize, fade(accent, 255, ease));
        }

        float left = hx + head + 9.0F;
        float right = x + w - 12.0F;
        float cap = FontManager.getCapHeight(nameSize);
        float nameBase = y + (h - cap) / 2.0F + cap - 1.0F;

        Color hpColor = new Color(Color.HSBtoRGB(0.33F * this.hpAnim, 0.7F, 0.95F));
        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        float hpW = FontManager.getStringWidth(hpText, nameSize);
        String name = fit(target.getName(), Math.max(10.0F, right - hpW - 8.0F - left), nameSize);
        this.text(name, left, nameBase, nameSize, fade(Color.WHITE, 255, ease));
        this.text(hpText, right - hpW, nameBase, nameSize, fade(hpColor, 255, ease));

        float barY = y + h - 7.0F;
        float barH = 3.5F;
        float barW = right - left;
        RenderUtil.drawRoundedRectWithGl(left, barY, right, barY + barH, barH / 2.0F, fade(Color.WHITE, 16, ease));
        if (this.hpGhost > this.hpAnim + 0.002F) {
            RenderUtil.drawRoundedRectWithGl(left, barY, left + barW * this.hpGhost, barY + barH, barH / 2.0F, fade(new Color(255, 200, 140), 90, ease));
        }
        if (this.hpAnim > 0.01F) {
            RenderUtil.drawRoundedRectGradientH(left, barY, left + Math.max(barH, barW * this.hpAnim), barY + barH, barH / 2.0F,
                    fade(accent, 255, ease), fade(hpColor, 255, ease));
        }
    }

    private void drawBlocks(float x, float y, float w, float h, float ease, float dt, float nameSize, float metaSize, Color accent, boolean preview) {
        int count = preview ? 128 : Math.max(0, Scaffold.count);
        float k = 1.0F - (float) Math.exp(-dt * 12.0F);
        this.blocksAnim = approach(this.blocksAnim, Math.min(1.0F, count / 64.0F), k);

        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        ItemStack iconStack = null;
        if (!preview && scaffold != null && mc.thePlayer != null) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock) {
                    Block block = ((ItemBlock) stack.getItem()).getBlock();
                    if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                        if (iconStack == null || i == mc.thePlayer.inventory.currentItem) {
                            iconStack = stack;
                        }
                    }
                }
            }
        }

        float iconSize = 18.0F;
        float ix = x + 10.0F;
        float iy = y + (h - iconSize) / 2.0F;

        if (iconStack == null) {
            RenderUtil.drawRoundedRectGradient(ix + 2, iy + 2, ix + 16, iy + 16, 2,
                    fade(new Color(191, 191, 200), 230, ease), fade(new Color(112, 112, 126), 230, ease));
            RenderUtil.drawRect(ix + 3, iy + 3, ix + 15, iy + 5, fade(Color.WHITE, 65, ease));
        }

        if (iconStack != null && ease > 0.6F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(ix + 1.0F, iy + 1.0F, 0.0F);
            RenderUtil.renderItemInGUI(iconStack, 0, 0, false);
            GlStateManager.popMatrix();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        String countText = Integer.toString(count);
        double bps = preview || mc.thePlayer == null ? 4.32 : Math.hypot(mc.thePlayer.posX - mc.thePlayer.prevPosX,
                mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * 20;
        String speedText = String.format(Locale.ROOT, "%.2f b/s", bps);
        float numberSize = nameSize + 4;
        float textX = x + 32;
        float numberW = FontManager.getStringWidth(countText, numberSize);
        float valueBase = y + (h + FontManager.getCapHeight(numberSize)) / 2 - 1;
        Color countColor = count <= 16 ? new Color(225, 144, 156) : count <= 48 ? new Color(222, 192, 139) : new Color(174, 181, 230);
        text(countText, textX, valueBase, numberSize, fade(FOREGROUND, 255, ease));
        text("blocks", textX + numberW + 5, valueBase, metaSize, fade(SECONDARY, 255, ease));
        float barLeft = textX + numberW + 5 + FontManager.getStringWidth("blocks", metaSize) + 12;
        float barRight = x + w - 12;
        float barY = y + h / 2 + 5;
        float barH = 2;
        text(speedText, barLeft, barY - 5, metaSize, fade(SECONDARY, 255, ease));
        RenderUtil.drawRoundedRectWithGl(barLeft, barY, barRight, barY + barH, 1,
                fade(Color.WHITE, 22, ease));
        float ratio = preview ? 0.75F : this.blocksAnim;
        if (ratio > 0.01F) {
            float fill = Math.max(barH, (barRight - barLeft) * ratio);
            RenderUtil.drawRoundedRectWithGl(barLeft, barY, barLeft + fill, barY + barH, 1, fade(countColor, 220, ease));
        }
    }

    private void drawDanger(float x, float y, float w, float h, float ease, long now, float nameSize, float metaSize, boolean preview) {
        Color red = new Color(225, 144, 156);
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 360.0);
        iconTile(x + 8, y + (h - 18) / 2, Icon.HEAL, red, ease * (0.8F + pulse * 0.2F));
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float base = titleBaseline(y, h, nameSize, metaSize);
        float health = preview || mc.thePlayer == null ? 4.0F : mc.thePlayer.getHealth();
        String hp = String.format(Locale.ROOT, "%.1f", health);
        float valueSize = nameSize + 2;
        float pillW = FontManager.getStringWidth(hp, valueSize) + 16;
        float pillX = x + w - 10 - pillW;
        text(fit("Low health", pillX - x - 39, nameSize), x + 32, base, nameSize, fade(FOREGROUND, 255, ease));
        text("Take cover", x + 32, base + FontManager.getCapHeight(metaSize) + 3,
                metaSize, fade(SECONDARY, 255, ease));
        RenderUtil.drawRoundedRectWithGl(pillX, y + h / 2 - 10, pillX + pillW, y + h / 2 + 10,
                5, fade(red, 14, ease));
        text(hp, pillX + 8, y + (h + FontManager.getCapHeight(valueSize)) / 2,
                valueSize, fade(red, 255, ease));
    }

    private void drawAlert(float x, float y, float w, float h, float ease, float nameSize, float metaSize, boolean preview) {
        NoticeMode mode = preview ? previewNoticeMode : alertMode;
        Color tint = noticeTint(mode);
        String title = preview ? "Scaffold" : alertTitle;
        String description = preview ? "" : alertDescription;
        noticeGlyph(x + 8, y + (h - 18) / 2, mode, tint, ease);
        float left = x + 32, available = Math.max(0, w - 65);
        float base = titleBaseline(y, h, nameSize, metaSize);
        text(fit(title, available, nameSize), left, base, nameSize, fade(FOREGROUND, 255, ease));
        text(fit(noticeDetail(description, mode), available, metaSize), left,
                base + 3 + FontManager.getCapHeight(metaSize), metaSize, fade(tint, 205, ease));
        float progress = preview ? 0.65F : 1 - (System.currentTimeMillis() - alertStart) / Math.max(1, alertDuration);
        lifetimeRing(x + w - 16, y + h / 2, progress, tint, ease);
    }

    private String idleMetrics(boolean preview) {
        return (preview ? 60 : Minecraft.getDebugFPS()) + " fps / " + (preview ? "20" : PingTracker.INSTANCE.display()) + " ms";
    }

    private float idleWidth(float nameSize, float metaSize, boolean preview) {
        return 29 + FontManager.getStringWidth("Leader", nameSize) + 9
                + (showMetrics.getValue() ? 18 + FontManager.getStringWidth(idleMetrics(preview), metaSize) : 0);
    }

    public NoticeMode getPreviewNoticeMode() { return previewNoticeMode; }

    public void setPreviewNoticeMode(NoticeMode mode) { previewNoticeMode = mode; }

    private void drawRoundedSkin(float x, float y, float size, float radius, float skinU, float skinV) {
        // Textured rounded mesh clips both skin layers without modifying the global stencil buffer.
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x + size / 2, y + size / 2, 0).tex((skinU + 4) / 64, (skinV + 4) / 64).endVertex();
        for (int corner = 0; corner <= 4; corner++) {
            int c = corner % 4;
            float cx = x + (c == 0 || c == 3 ? radius : size - radius);
            float cy = y + (c < 2 ? radius : size - radius);
            for (int step = 0; step <= (corner == 4 ? 0 : 10); step++) {
                double angle = Math.toRadians(180 + c * 90 + step * 9);
                float px = cx + (float) Math.cos(angle) * radius;
                float py = cy + (float) Math.sin(angle) * radius;
                wr.pos(px, py, 0).tex((skinU + (px - x) / size * 8) / 64,
                        (skinV + (py - y) / size * 8) / 64).endVertex();
            }
        }
        tessellator.draw();
        if (cull) GlStateManager.enableCull();
    }

    private void drawIdle(float x, float y, float w, float h, float ease, float nameSize, float metaSize, boolean preview) {
        iconTile(x + 6, y + (h - 18) / 2, Icon.CROWN, new Color(174, 181, 230), ease);
        float brandX = x + 29;
        text("Leader", brandX, y + (h + FontManager.getCapHeight(nameSize)) / 2,
                nameSize, fade(FOREGROUND, 255, ease));
        if (!showMetrics.getValue()) return;
        String right = idleMetrics(preview);
        float brandW = FontManager.getStringWidth("Leader", nameSize);
        float sep = brandX + brandW + 7;
        float infoX = sep + 9;
        // Width is animated and may temporarily lag behind a longer FPS/ping value.
        // Fade the complete metrics in as space becomes available, never truncate its units.
        float available = w - (29 + brandW + 16) - 9;
        float metricsEase = ease * Math.max(0, Math.min(1,
                (available - FontManager.getStringWidth(right, metaSize) + 2) / 2));
        RenderUtil.drawRect(sep, y + 7, sep + 0.6F, y + h - 7, fade(SECONDARY, 45, metricsEase));
        text(right, infoX, y + (h + FontManager.getCapHeight(metaSize)) / 2,
                metaSize, fade(SECONDARY, 255, metricsEase));
    }

    public void renderPreview(float x, float y, float alpha) {
        renderPreview(x, y, alpha, IDLE);
    }

    public float[] previewSize(int state) {
        float nameSize = 14 * fontScale.getValue();
        float w = contentWidth(state, nameSize, 11 * fontScale.getValue(), null);
        if (state == IDLE && showMetrics.getValue()) {
            w = idleWidth(nameSize, 11 * fontScale.getValue(), true);
        } else if (state == ALERT) {
            w = alertWidth("Scaffold", "", previewNoticeMode, nameSize, 11 * fontScale.getValue());
        }
        return new float[]{w * scale.getValue(), contentHeight(state, nameSize) * scale.getValue()};
    }

    public void renderPreview(float x, float y, float opacity, int state) {
        float sc = scale.getValue();
        float[] size = previewSize(state);
        float w = size[0] / sc, h = size[1] / sc;
        float nameSize = 14 * fontScale.getValue(), metaSize = 11 * fontScale.getValue();
        Color accent = accent(System.currentTimeMillis());
        GlStateManager.pushMatrix();
        GlStateManager.scale(sc, sc, 1);
        x /= sc;
        y /= sc;
        drawSurface(x, y, w, h, Math.min(h / 2, cornerRadius.getValue()),
                state == DANGER ? new Color(255, 125, 145) : accent, opacity, state == TARGET);
        if (state == BLOCKS) drawBlocks(x, y, w, h, opacity, 0, nameSize, metaSize, accent, true);
        else if (state == DANGER) drawDanger(x, y, w, h, opacity, System.currentTimeMillis(), nameSize, metaSize, true);
        else if (state == ALERT) drawAlert(x, y, w, h, opacity, nameSize, metaSize, true);
        else if (state == TARGET) {
            // Keep the accepted target layout without mutating live combat animation state.
            RenderUtil.drawRoundedRectWithGl(x + 10, y + (h - 28) / 2, x + 38, y + (h + 28) / 2,
                    5, fade(accent, 28, opacity));
            text("T", x + 19, y + (h + FontManager.getCapHeight(nameSize)) / 2, nameSize, fade(accent, 255, opacity));
            text("Target", x + 47, y + (h + FontManager.getCapHeight(nameSize)) / 2 - 1,
                    nameSize, fade(Color.WHITE, 255, opacity));
            String hp = "15.0";
            text(hp, x + w - 12 - FontManager.getStringWidth(hp, nameSize),
                    y + (h + FontManager.getCapHeight(nameSize)) / 2 - 1, nameSize, fade(accent, 255, opacity));
            RenderUtil.drawRoundedRectWithGl(x + 47, y + h - 7, x + w - 12, y + h - 3.5F, 1.75F,
                    fade(Color.WHITE, 16, opacity));
            RenderUtil.drawRoundedRectWithGl(x + 47, y + h - 7, x + 47 + (w - 59) * 0.75F, y + h - 3.5F,
                    1.75F, fade(accent, 255, opacity));
        } else drawIdle(x, y, w, h, opacity, nameSize, metaSize, true);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

}
