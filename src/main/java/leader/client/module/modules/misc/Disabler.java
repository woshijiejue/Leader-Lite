package leader.client.module.modules.misc;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.event.types.EventType;
import leader.client.events.PacketEvent;
import leader.client.module.Module;
import leader.client.module.values.Representation;
import leader.client.module.values.impl.BoolValue;
import leader.client.module.values.impl.ListValue;
import leader.client.module.values.impl.SliderValue;
import leader.client.util.DebugUtil;
import leader.client.util.player.ItemUtil;
import leader.client.util.server.PacketUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Disabler extends Module {
    public final ListValue mode = new ListValue("Mode", new String[]{"Prediction"}, "Prediction", this);
    public final BoolValue inventory = new BoolValue("Inventory", false, () -> mode.is("Prediction"), this);
    public final BoolValue c09 = new BoolValue("C09", false, () -> mode.is("Prediction"), this);
    public final SliderValue secondSwordSlot = new SliderValue("SecondSwordSlot", 2, 1, 9, () -> mode.is("Prediction") && c09.getValue(), Representation.INT, this);

    private static final Random random = new Random();
    private final List<Packet<?>> inventoryPackets = new ArrayList<>();
    private static boolean c09Warned = false;

    public Disabler() {
        super("Disabler", false);
    }
    @Override
    public String[] getSuffix() {
        return new String[]{mode.getValue()};
    }
    @Override
    public void onEnabled() {
        if (mode.is("Prediction") && inventory.getValue()) {
            DebugUtil.sendFormatted(String.format("%s%s: You can use Vanilla-InvWalk & Silent-InvManager now",
                    Leader.clientName, this.getName()));
        }
        c09Warned = false;
        resetStates();
    }

    @Override
    public void onDisabled() {
        if (mode.is("Prediction") && inventory.getValue()) {
            if (!inventoryPackets.isEmpty()) {
                for (Packet<?> p : inventoryPackets) {
                    PacketUtil.sendPacketNoEvent(p);
                }
            }
        }
        resetStates();
    }

    private void resetStates() {
        inventoryPackets.clear();
        c09Warned = false;
    }

    private boolean checkCompass() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getUnlocalizedName().toLowerCase().contains("compass")) {
                return true;
            }
        }
        return false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (mode.is("Prediction") && inventory.getValue()) {
            if (!checkCompass()) {
                if (event.getType() == EventType.SEND) {
                    handlePredictionInventory(event);
                }
            }
        }
    }

    private void handlePredictionInventory(PacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof C16PacketClientStatus || packet instanceof C0EPacketClickWindow) {
            event.setCancelled(true);
            inventoryPackets.add(packet);
        } else if (packet instanceof C0DPacketCloseWindow) {
            for (Packet<?> p : inventoryPackets) {
                PacketUtil.sendPacketNoEvent(p);
            }
            inventoryPackets.clear();
        }
    }
    public static int getC09TargetSlot() {
        Disabler disabler = (Disabler) Leader.moduleManager.getModule(Disabler.class);
        if (disabler == null || !disabler.isEnabled() || !disabler.c09.getValue()) {
            c09Warned = false;
            return -1;
        }

        int preferredSlot = disabler.secondSwordSlot.getValue().intValue() - 1;
        if (preferredSlot < 0 || preferredSlot > 8) return -1;
        if (preferredSlot == mc.thePlayer.inventory.currentItem) return -1;

        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
        if (stack != null && stack.getItem() instanceof ItemSword) {
            c09Warned = false;
            return preferredSlot;
        }
        if (!c09Warned) {
            int mainSword = ItemUtil.findSwordInInventorySlot(0, true);
            int secondSword = findSecondSwordSlot(mainSword);
            if (secondSword == -1) {
                c09Warned = true;
                DebugUtil.sendFormatted(String.format("%sDisabler: &cNo second sword in inventory, C09 swaps will use fallback.",
                        Leader.clientName));
            }
        }
        return -1;
    }

    public static int getSwapSlot() {
        int target = getC09TargetSlot();
        if (target >= 0) return target;

        int slot = random.nextInt(9);
        while (slot == mc.thePlayer.inventory.currentItem) {
            slot = random.nextInt(9);
        }
        return slot;
    }
    public static int getAltSlot(int handle) {
        int target = getC09TargetSlot();
        if (target >= 0) return target;
        return handle % 8 + 1;
    }
    public static int findSecondSwordSlot(int excludeSlot) {
        double bestDamage = 0;
        List<Integer> bestSlots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (i == excludeSlot) continue;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemSword)) continue;
            double damage = ItemUtil.getAttackBonus(stack);
            if (damage >= bestDamage && damage > 0) {
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlots.clear();
                }
                bestSlots.add(i);
            }
        }
        if (bestSlots.isEmpty()) return -1;
        if (bestSlots.size() == 1) return bestSlots.get(0);
        return bestSlots.get(random.nextInt(bestSlots.size()));
    }
}
