package leader.module.modules.legit.blockhit;

import leader.Leader;
import leader.enums.BlinkModules;
import leader.mixin.IAccessorKeyBinding;
import leader.module.modules.combat.KillAura;
import leader.module.modules.combat.Velocity;
import leader.module.modules.legit.BlockHit;
import leader.module.modules.movement.Stuck;
import leader.module.modules.player.Scaffold;
import leader.util.BlockUtil;
import leader.util.KeyBindUtil;
import leader.util.RotationUtil;
import leader.util.ServerUtil;
import leader.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.Display;

/**
 * Adapted from Amunix-Legacy LagBlockhit / PredictBlockhit.
 * Uses Leader-Lite's input, targeting and owner-scoped Blink APIs. No releaseAll,
 * render-frame timers, private packet queue, or diagnostic-only ServerBlockMonitor.
 */
public final class AmunixBlockHit {
    private enum PredictState { IDLE, BLOCKING, GAP }
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final BlockHit owner;
    private PredictState predictState = PredictState.IDLE;
    private EntityPlayer target;
    private int holdTicks, gapTicks, cooldown, lastHurt, slot = -1;
    private Item sword;
    private boolean ownsInput, blocking, lagOwned, pendingRelease, attackQueued, flushRequested, blockPacketSeen;
    private long pulseAt;

    public AmunixBlockHit(BlockHit owner) { this.owner = owner; }

    public void reset() {
        if (ownsInput) {
            unblock();
            if (mc.currentScreen == null && mc.inGameHasFocus && mc.thePlayer != null) {
                KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
            }
        }
        flush(false);
        ownsInput = blocking = lagOwned = pendingRelease = attackQueued = flushRequested = blockPacketSeen = false;
        predictState = PredictState.IDLE;
        target = null; sword = null; slot = -1;
        pulseAt = 0;
        holdTicks = gapTicks = cooldown = 0;
        lastHurt = mc.thePlayer == null ? 0 : mc.thePlayer.hurtTime;
    }

    public boolean ownsBlink() {
        return owner.isEnabled() && lagOwned && Leader.blinkManager != null
                && Leader.blinkManager.getBlinkingModule() == BlinkModules.BLOCK_HIT;
    }

    public boolean controlsInput() { return owner.isEnabled() && ownsInput; }

    public boolean suppressUse() { return controlsInput() && !blocking && !interactable(); }

    private boolean available() {
        if (!owner.isEnabled() || owner.mode.getValue() < 2 || mc.thePlayer == null || mc.theWorld == null
                || mc.playerController == null || mc.currentScreen != null || !mc.inGameHasFocus
                || !Display.isActive() || mc.thePlayer.isDead || mc.thePlayer.isSpectator()
                || mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)
                || !KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode()) || interactable()) return false;
        Scaffold scaffold = (Scaffold) Leader.moduleManager.getModule(Scaffold.class);
        KillAura aura = (KillAura) Leader.moduleManager.getModule(KillAura.class);
        Stuck stuck = (Stuck) Leader.moduleManager.getModule(Stuck.class);
        // Let modules that manage attacks/movement/blocking own their input instead of fighting them.
        return !(scaffold != null && scaffold.isEnabled()) && !(aura != null && aura.isEnabled())
                && !(stuck != null && stuck.isStuckActive());
    }

    public void tick() {
        if (!available()) { reset(); return; }
        int currentSlot = mc.thePlayer.inventory.currentItem;
        Item currentSword = mc.thePlayer.getHeldItem().getItem();
        if (slot >= 0 && (currentSlot != slot || sword != currentSword)) reset();
        slot = currentSlot; sword = currentSword;
        if (owner.mode.getValue() == 2) lagTick();
        else predictTick();
    }

    public void update() {
        if (!available()) { reset(); return; }
        if (owner.mode.getValue() == 2 && ownsBlink() && flushRequested
                && (!pendingRelease || blockPacketSeen)) {
            // Runs after tick-generated vanilla use/attack packets have entered Blink's FIFO queue.
            flush(true);
            flushRequested = pendingRelease = attackQueued = blockPacketSeen = false;
        }
    }

    public void packet(Packet<?> packet) {
        if (owner.mode.getValue() != 2 || !ownsBlink()) return;
        if (packet instanceof C02PacketUseEntity && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            if (!Velocity.velocityAttacked && !pendingRelease) attackQueued = true;
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement use = (C08PacketPlayerBlockPlacement) packet;
            if (use.getPlacedBlockDirection() == 255 && use.getStack() != null && use.getStack().getItem() instanceof ItemSword) {
                blockPacketSeen = true;
            }
        }
    }

    private void lagTick() {
        if (lagOwned && !ownsBlink()) {
            // Ownership may be taken by Velocity; never flush its buffer or blindly reacquire it.
            reset();
            return;
        }
        int hurt = mc.thePlayer.hurtTime;
        boolean damaged = hurt > lastHurt;
        lastHurt = hurt;
        if (damaged && owner.smartUnblock.getValue()) {
            unblock();
            flush(false);
            lagOwned = pendingRelease = attackQueued = flushRequested = blockPacketSeen = false;
            holdTicks = 0;
            cooldown = 4;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            if (ownsInput) unblock();
            return;
        }
        if (!lagOwned) {
            if (!Leader.blinkManager.acquireExclusive(BlinkModules.BLOCK_HIT)) return;
            lagOwned = true;
            ownsInput = true;
            unblock();
            pulseAt = System.nanoTime() - owner.lagDuration.getValue() * 1000000L;
        }
        long elapsed = (System.nanoTime() - pulseAt) / 1000000L;
        long watchdog = owner.lagDuration.getValue() + 200L + owner.lagHoldTime.getValue() * 50L;
        if (pendingRelease && elapsed > watchdog) {
            unblock();
            flush(false);
            lagOwned = pendingRelease = attackQueued = flushRequested = blockPacketSeen = false;
            holdTicks = 0;
            cooldown = 2;
            return;
        }
        if (holdTicks > 0) {
            if (--holdTicks == 0) {
                unblock();
                flushRequested = true;
                cooldown = 2;
            }
            return;
        }
        if (!pendingRelease && !flushRequested && (elapsed >= owner.lagDuration.getValue()
                || attackQueued && elapsed >= owner.minLagDuration.getValue())) {
            if (owner.lagChance.getValue() < 100 && Math.random() * 100 >= owner.lagChance.getValue()) {
                flushRequested = true;
                attackQueued = false;
                pulseAt = System.nanoTime();
                return;
            }
            ownsInput = true;
            blockPacketSeen = false;
            block();
            holdTicks = owner.lagHoldTime.getValue();
            pulseAt = System.nanoTime();
            attackQueued = false;
            pendingRelease = true;
            if (holdTicks <= 1) flushRequested = true;
        }
    }

    private void predictTick() {
        int hurt = mc.thePlayer.hurtTime;
        boolean damaged = hurt > lastHurt;
        lastHurt = hurt;
        EntityPlayer next = selectTarget();
        if (next == null || distance(next) > owner.predictRange.getValue()) {
            reset();
            return;
        }
        if (next != target) {
            if (ownsInput) unblock();
            target = next;
            predictState = PredictState.IDLE;
            gapTicks = holdTicks = 0;
        }
        ownsInput = true;
        if (distance(target) >= Math.min(owner.firstMeleeRange.getValue(), owner.predictRange.getValue())) {
            unblock();
            predictState = PredictState.IDLE;
            gapTicks = holdTicks = 0;
            return;
        }
        if (damaged && predictState == PredictState.BLOCKING) {
            int delay = millisTicks(owner.unblockDelay.getValue());
            if (delay == 0) { beginGap(); return; }
            holdTicks = delay;
        } else if (predictState == PredictState.BLOCKING && --holdTicks <= 0) {
            beginGap();
            return;
        }
        if (predictState == PredictState.GAP) {
            unblock();
            if (--gapTicks > 0) return;
            predictState = PredictState.IDLE;
        }
        if (predictState == PredictState.IDLE) {
            gapTicks = 0;
            holdTicks = millisTicks(owner.maxHold.getValue());
            predictState = PredictState.BLOCKING;
            block();
        } else if (blocking) {
            // AutoClicker/vanilla attack processing may have reset the logical use key.
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
    }

    private int millisTicks(int ms) { return (ms + 49) / 50; }

    private void beginGap() {
        predictState = PredictState.GAP;
        holdTicks = 0;
        gapTicks = millisTicks(owner.unblockTime.getValue());
        unblock();
    }

    private EntityPlayer selectTarget() {
        if (owner.lobbyCheck.getValue() && lobby()) return null;
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
            EntityPlayer hit = (EntityPlayer) mc.objectMouseOver.entityHit;
            if (valid(hit)) return hit;
        }
        EntityPlayer best = null;
        double bestAngle = Double.MAX_VALUE, bestDistance = Double.MAX_VALUE;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!valid(player)) continue;
            double angle = RotationUtil.angleToEntity(player), distance = distance(player);
            if (angle < bestAngle || angle == bestAngle && distance < bestDistance) {
                best = player; bestAngle = angle; bestDistance = distance;
            }
        }
        return best;
    }

    private boolean valid(EntityPlayer player) {
        return player != mc.thePlayer && player != mc.thePlayer.ridingEntity && player.isEntityAlive() && !player.isSpectator()
                && player.ticksExisted >= 5 && distance(player) <= owner.predictRange.getValue()
                && Math.abs(player.posY - mc.thePlayer.posY) < 4
                && !TeamUtil.isFriend(player) && !TeamUtil.isSameTeam(player) && !TeamUtil.isBot(player)
                && mc.thePlayer.canEntityBeSeen(player);
    }

    private boolean lobby() {
        for (String line : ServerUtil.getScoreboardLines()) {
            String text = EnumChatFormatting.getTextWithoutFormattingCodes(line).toLowerCase(java.util.Locale.ROOT);
            if (text.contains("lobby") || text.contains("大厅") || text.contains("大廳") || text.contains("replay")
                    || text.contains("practice") || text.contains("waiting") || text.contains("starting in")) return true;
        }
        return false;
    }

    private double distance(EntityLivingBase entity) {
        AxisAlignedBB a = mc.thePlayer.getEntityBoundingBox(), b = entity.getEntityBoundingBox();
        double dx = Math.max(0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
        double dz = Math.max(0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean interactable() {
        if (mc.theWorld == null) return false;
        MovingObjectPosition hit = mc.objectMouseOver;
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.getBlockPos() != null
                && BlockUtil.isInteractable(hit.getBlockPos());
    }

    private void block() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) {
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
        }
        blocking = true;
    }

    private void unblock() {
        blocking = false;
        ((IAccessorKeyBinding) mc.gameSettings.keyBindUseItem).callUnpressKey();
        if (mc.thePlayer != null && mc.playerController != null && mc.thePlayer.isBlocking()) {
            mc.playerController.onStoppedUsingItem(mc.thePlayer);
        }
    }

    private void flush(boolean retain) {
        if (lagOwned && Leader.blinkManager != null) Leader.blinkManager.flushOwned(BlinkModules.BLOCK_HIT, retain);
    }
}
