package leader.module.modules.render;

import leader.Leader;
import leader.event.EventTarget;
import leader.events.Render2DEvent;
import leader.module.Module;
import leader.module.modules.combat.KillAura;
import leader.module.modules.player.Scaffold;
import leader.module.modules.render.notification.Notification;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.util.BlockUtil;
import leader.util.ItemUtil;
import leader.util.Icon;
import leader.util.RenderUtil;
import leader.util.shader.ShaderElement;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class Island extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final FloatProperty fontScale = new FloatProperty("font-scale", 1.0F, 0.7F, 1.5F);
    public final IntProperty holdTime = new IntProperty("hold-time", 1100, 0, 5000);

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
    private int lastPing = 0;

    public Island() {
        super("Island", false);
    }

    private EntityLivingBase getTarget() {
        if (!Leader.hudElementManager.isSuppressed("TargetHUD")) return null;
        KillAura killAura = (KillAura) Leader.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return null;
        EntityLivingBase target = killAura.getTarget();
        return target != null && target.isEntityAlive() ? target : null;
    }

    private boolean isLowHealth() {
        return mc.thePlayer.getHealth() > 0.0F && mc.thePlayer.getHealth() <= 6.0F;
    }

    private boolean isScaffolding() {
        if (!Leader.hudElementManager.isSuppressed("ScaffoldCounter")) return false;
        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        if (scaffold == null || !scaffold.isEnabled() || !scaffold.blockCounter.getValue()) return false;
        return true;
    }

    private boolean hasAlert() {
        if (!Leader.hudElementManager.isSuppressed("Notification")) return false;
        return Notification.hasLatest() && Notification.latestProgress() > 0.0F;
    }

    private int resolveState(EntityLivingBase target) {
        if (target != null) return TARGET;
        if (this.isLowHealth()) return DANGER;
        if (this.isScaffolding()) return BLOCKS;
        if (this.hasAlert()) return ALERT;
        return IDLE;
    }

    private int getPing() {
        if (mc.getNetHandler() == null) return lastPing;
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info != null) {
            int ping = info.getResponseTime();
            if (ping > 0) lastPing = ping;
        }
        return lastPing;
    }

    private static int alpha(Color c, float a) {
        int v = Math.max(0, Math.min(255, (int) a));
        return (v << 24) | (c.getRGB() & 0x00FFFFFF);
    }

    private static float approach(float current, float target, float k) {
        return current + (target - current) * k;
    }

    private static String fit(String text, float maxW, float size) {
        if (FontManager.getStringWidth(text, size) <= maxW) return text;
        String out = text;
        while (out.length() > 2 && FontManager.getStringWidth(out, size) > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
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
        FontManager.drawString(s, x, baseline - FontManager.getBaseline(size), color, false, size);
    }

    private float contentWidth(int state, float nameSize, float metaSize, EntityLivingBase target) {
        switch (state) {
            case TARGET: {
                float nameW = Math.min(110.0F, FontManager.getStringWidth(target != null ? target.getName() : "", nameSize));
                return Math.max(168.0F, 12.0F + 26.0F + 9.0F + nameW + 46.0F + 12.0F);
            }
            case BLOCKS:
                return 14.0F + 18.0F + 8.0F + FontManager.getStringWidth("Scaffold", nameSize)
                        + 14.0F + FontManager.getStringWidth("999", nameSize + 2.0F) + 14.0F;
            case DANGER:
                return 14.0F + 14.0F + 8.0F + FontManager.getStringWidth("Low Health", nameSize)
                        + 16.0F + FontManager.getStringWidth("20.0", nameSize) + 14.0F;
            case ALERT:
                return Math.max(124.0F, FontManager.getStringWidth(Notification.latestText(), nameSize) + 40.0F);
            default: {
                String right = Minecraft.getDebugFPS() + " fps   " + this.getPing() + " ms";
                return 14.0F + FontManager.getStringWidth("Leader", nameSize)
                        + 14.0F + FontManager.getStringWidth(right, metaSize) + 14.0F;
            }
        }
    }

    private float contentHeight(int state, float nameSize) {
        float cap = FontManager.getCapHeight(nameSize);
        switch (state) {
            case TARGET:
                return Math.max(38.0F, cap + 26.0F);
            case BLOCKS:
            case DANGER:
                return Math.max(28.0F, cap + 17.0F);
            case ALERT:
                return Math.max(26.0F, cap + 13.0F);
            default:
                return Math.max(22.0F, cap + 13.0F);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        long now = System.currentTimeMillis();
        float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.05F, (now - this.lastFrame) / 1000.0F);
        this.lastFrame = now;

        EntityLivingBase liveTarget = this.getTarget();
        if (liveTarget != null) this.lastTarget = liveTarget;
        int resolved = this.resolveState(liveTarget);
        if (resolved != IDLE) {
            this.lastActive = now;
            this.pending = resolved;
        } else if (now - this.lastActive > this.holdTime.getValue()) {
            this.pending = IDLE;
        }

        if (this.pending != this.shown) {
            this.contentFade = Math.max(0.0F, this.contentFade - dt * 9.0F);
            if (this.contentFade <= 0.0F) this.shown = this.pending;
        } else {
            this.contentFade = Math.min(1.0F, this.contentFade + dt * 6.0F);
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
        float stiffness = 260.0F;
        float damping = 24.0F;
        this.velW += ((targetW - this.animW) * stiffness - this.velW * damping) * dt;
        this.velH += ((targetH - this.animH) * stiffness - this.velH * damping) * dt;
        this.animW += this.velW * dt;
        this.animH += this.velH * dt;

        float sc = this.scale.getValue();
        ScaledResolution sr = new ScaledResolution(mc);
        float w = this.animW;
        float h = this.animH;
        float r = Math.min(h / 2.0F, 8.0F);
        float x = Leader.hudElementManager.x("Island", sr.getScaledWidth() / sc / 2.0F - w / 2.0F, 6.0F / sc);
        float y = Leader.hudElementManager.y("Island", sr.getScaledWidth() / sc / 2.0F - w / 2.0F, 6.0F / sc);

        GlStateManager.pushMatrix();
        GlStateManager.scale(sc, sc, 1.0F);

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

        Color accent = this.accent(now);
        Color edge = this.shown == DANGER ? new Color(255, 96, 92) : accent;

        RenderUtil.drawGrayGlass(x, y, x + w, y + h, r, 1.0F,
                Leader.hudElementManager.backgroundColor("Island", 40.0F, 40.0F));
        RenderUtil.drawRoundedRectGradientH(x, y, x + w, y + h, r, alpha(edge, 14 * this.contentFade), alpha(edge, 0));

        float ease = this.contentFade * this.contentFade * (3.0F - 2.0F * this.contentFade);
        if (ease > 0.01F) {
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float slide = (1.0F - ease) * 3.0F;
            switch (this.shown) {
                case TARGET:
                    this.drawTarget(x, y + slide, w, baseH, ease, dt, nameSize, metaSize, accent);
                    break;
                case BLOCKS:
                    this.drawBlocks(x, y + slide, w, baseH, ease, dt, nameSize, metaSize, accent);
                    break;
                case DANGER:
                    this.drawDanger(x, y + slide, w, baseH, ease, now, nameSize, metaSize);
                    break;
                case ALERT:
                    this.drawAlert(x, y + slide, w, baseH, ease, nameSize);
                    break;
                default:
                    this.drawIdle(x, y + slide, w, baseH, ease, nameSize, metaSize);
                    break;
            }
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
        }

        GlStateManager.popMatrix();
    }

    private static int fade(Color c, float a, float ease) {
        return alpha(c, Math.max(5.0F, a * ease));
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

        float head = 26.0F;
        float hx = x + 12.0F;
        float hy = y + (h - head) / 2.0F;
        RenderUtil.drawRoundedRectWithGl(hx - 1.0F, hy - 1.0F, hx + head + 1.0F, hy + head + 1.0F, 5.0F, fade(accent, 90, ease));
        ResourceLocation skin = getSkin(target);
        if (skin != null) {
            float hurt = target.hurtTime > 0 ? target.hurtTime / 10.0F : 0.0F;
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F - hurt * 0.55F, 1.0F - hurt * 0.55F, ease);
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.pushMatrix();
            GlStateManager.translate(hx, hy, 0.0F);
            Gui.drawScaledCustomSizeModalRect(0, 0, 8.0F, 8.0F, 8, 8, (int) head, (int) head, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(0, 0, 40.0F, 8.0F, 8, 8, (int) head, (int) head, 64.0F, 64.0F);
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            RenderUtil.drawRoundedRectWithGl(hx, hy, hx + head, hy + head, 4.0F, fade(new Color(40, 42, 50), 255, ease));
            String initial = target.getName().isEmpty() ? "?" : target.getName().substring(0, 1).toUpperCase();
            float iw = FontManager.getStringWidth(initial, nameSize);
            this.text(initial, hx + (head - iw) / 2.0F, hy + (head + FontManager.getCapHeight(nameSize)) / 2.0F, nameSize, fade(Color.WHITE, 255, ease));
        }

        float left = hx + head + 9.0F;
        float right = x + w - 12.0F;
        float cap = FontManager.getCapHeight(nameSize);
        float nameBase = y + 7.0F + cap;
        Color hpColor = new Color(Color.HSBtoRGB(0.33F * this.hpAnim, 0.62F, 1.0F));
        String hpText = String.format("%.1f", hp);
        float hpW = FontManager.getStringWidth(hpText, nameSize);
        String name = fit(target.getName(), Math.max(10.0F, right - hpW - 8.0F - left), nameSize);
        this.text(name, left, nameBase, nameSize, fade(Color.WHITE, 255, ease));
        this.text(hpText, right - hpW, nameBase, nameSize, fade(hpColor, 255, ease));

        float barY = y + h - 11.0F;
        float barW = right - left;
        RenderUtil.drawRoundedRectWithGl(left, barY, right, barY + 3.0F, 1.5F, fade(Color.WHITE, 22, ease));
        if (this.hpGhost > this.hpAnim + 0.002F) {
            RenderUtil.drawRoundedRectWithGl(left, barY, left + barW * this.hpGhost, barY + 3.0F, 1.5F, fade(new Color(255, 236, 200), 120, ease));
        }
        if (this.hpAnim > 0.01F) {
            RenderUtil.drawRoundedRectGradientH(left, barY, left + Math.max(3.0F, barW * this.hpAnim), barY + 3.0F, 1.5F,
                    fade(accent, 255, ease), fade(hpColor, 255, ease));
        }
    }

    private void drawBlocks(float x, float y, float w, float h, float ease, float dt, float nameSize, float metaSize, Color accent) {
        int count = Math.max(0, Scaffold.count);
        float k = 1.0F - (float) Math.exp(-dt * 12.0F);
        this.blocksAnim = approach(this.blocksAnim, Math.min(1.0F, count / 64.0F), k);

        Scaffold scaffold = (Scaffold) Leader.moduleManager.modules.get(Scaffold.class);
        ItemStack iconStack = null;
        if (scaffold != null && mc.thePlayer != null) {
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

        float iconSize = 16.0F;
        float ix = x + 12.0F;
        float iy = y + (h - iconSize) / 2.0F;
        float iconWellSize = iconSize + 6.0F;
        RenderUtil.drawRoundedRectWithGl(ix, iy, ix + iconWellSize, iy + iconWellSize, 5.0F, fade(accent, 32, ease));

        if (iconStack != null && ease > 0.6F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(ix + 3.0F, iy + 3.0F, 0.0F);
            RenderUtil.renderItemInGUI(iconStack, 0, 0, false);
            GlStateManager.popMatrix();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        float cap = FontManager.getCapHeight(nameSize);
        float left = ix + iconWellSize + 8.0F;
        float base = y + (h + cap) / 2.0F - 1.0F;
        this.text("Scaffold", left, base, nameSize, fade(Color.WHITE, 255, ease));

        float countSize = nameSize + 1.0F;
        String countText = String.valueOf(count);
        float cw = FontManager.getStringWidth(countText, countSize);
        float countBase = y + (h + FontManager.getCapHeight(countSize)) / 2.0F;
        this.text(countText, x + w - 12.0F - cw, countBase, countSize, fade(Color.WHITE, 255, ease));

        float barLeft = x + 12.0F;
        float barRight = x + w - 12.0F;
        float barY = y + h - 4.0F;
        float barWidth = Math.min(barRight - barLeft, 70.0F);
        float barActualRight = barLeft + barWidth;
        RenderUtil.drawRoundedRectWithGl(barLeft, barY, barActualRight, barY + 2.0F, 1.0F, fade(Color.WHITE, 18, ease));
        if (this.blocksAnim > 0.01F) {
            RenderUtil.drawRoundedRectWithGl(barLeft, barY, barLeft + Math.max(2.0F, barWidth * this.blocksAnim), barY + 2.0F, 1.0F, fade(accent, 255, ease));
        }
    }

    private void drawDanger(float x, float y, float w, float h, float ease, long now, float nameSize, float metaSize) {
        Color red = new Color(255, 96, 92);
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 160.0);
        float icon = 14.0F;
        float ix = x + 14.0F;
        float iy = y + (h - icon) / 2.0F;
        RenderUtil.fillCircle(ix + icon / 2.0F, iy + icon / 2.0F, icon / 2.0F + 2.0F + pulse * 2.0F, 32, fade(red, 50 * (1.0F - pulse) + 20, ease));
        Icon.HEAL.draw(ix, iy, icon, red.getRGB(), ease * (0.75F + 0.25F * pulse));
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float cap = FontManager.getCapHeight(nameSize);
        float base = y + (h + cap) / 2.0F;
        this.text("Low Health", ix + icon + 8.0F, base, nameSize, fade(Color.WHITE, 255, ease));
        String hp = String.format("%.1f", mc.thePlayer.getHealth());
        float hw = FontManager.getStringWidth(hp, nameSize);
        this.text(hp, x + w - 14.0F - hw, base, nameSize, fade(red, 255, ease));
    }

    private void drawAlert(float x, float y, float w, float h, float ease, float nameSize) {
        String label = fit(Notification.latestText(), w - 64.0F, nameSize);
        Color tintColor = new Color(Notification.latestColor());
        float cap = FontManager.getCapHeight(nameSize);
        float base = y + (h - cap) / 2.0F + cap - 1.5F;

        int iconColor = tintColor.getRGB();
        float iconWellX = x + 11.0F;
        float iconWellY = y + h / 2.0F;
        float iconWellSize = 27.0F;
        float iconSize = 12.0F;

        RenderUtil.fillCircle(iconWellX, iconWellY, iconWellSize / 2.0F, 32, fade(tintColor, 32, ease));
        Icon.INFO.drawCentered(iconWellX, iconWellY, iconSize, iconColor, ease);

        this.text(label, iconWellX + iconWellSize / 2.0F + 8.0F, base, nameSize, fade(Color.WHITE, 245, ease));

        float progress = Notification.latestProgress();
        float barLeft = x + 8.0F;
        float barRight = x + w - 8.0F;
        float barY = y + h - 3.5F;
        RenderUtil.drawRoundedRectWithGl(barLeft, barY, barRight, barY + 1.6F, 0.8F, fade(Color.WHITE, 20, ease));
        if (progress > 0.01F) {
            RenderUtil.drawRoundedRectWithGl(barLeft, barY, barLeft + Math.max(1.6F, (barRight - barLeft) * progress), barY + 1.6F, 0.8F,
                    fade(tintColor, 240, ease));
        }
    }

    private void drawIdle(float x, float y, float w, float h, float ease, float nameSize, float metaSize) {
        float cap = FontManager.getCapHeight(nameSize);
        float nameBase = y + (h + cap) / 2.0F;
        this.text("Leader", x + 14.0F, nameBase, nameSize, fade(Color.WHITE, 240, ease));

        String right = Minecraft.getDebugFPS() + " fps   " + this.getPing() + " ms";
        float rw = FontManager.getStringWidth(right, metaSize);
        float metaBase = y + (h + FontManager.getCapHeight(metaSize)) / 2.0F;
        this.text(right, x + w - 14.0F - rw, metaBase, metaSize, fade(new Color(158, 163, 178), 230, ease));
    }

}
