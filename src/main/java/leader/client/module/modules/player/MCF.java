package leader.client.module.modules.player;

import leader.client.Leader;
import leader.client.event.EventTarget;
import leader.client.events.KeyEvent;
import leader.client.module.Module;
import leader.client.util.DebugUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class MCF extends Module {

    public MCF() {
        super("MCF", false, true);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (this.isEnabled() && event.getKey() == -98) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.ENTITY && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
                String hitName = mc.objectMouseOver.entityHit.getName();
                if (!Leader.friendComponent.isFriend(hitName)) {
                    Leader.friendComponent.add(hitName);
                    DebugUtil.sendFormatted(String.format("%sAdded &o%s&r to your friend list&r", Leader.clientName, hitName));
                } else {
                    Leader.friendComponent.remove(hitName);
                    DebugUtil.sendFormatted(String.format("%sRemoved &o%s&r from your friend list&r", Leader.clientName, hitName));
                }
            }
        }
    }
}
