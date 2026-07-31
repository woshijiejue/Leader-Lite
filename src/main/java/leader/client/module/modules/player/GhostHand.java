package leader.client.module.modules.player;

import leader.client.module.Module;
import leader.client.util.player.ItemUtil;
import leader.client.util.player.TeamUtil;
import leader.client.module.values.impl.BoolValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class GhostHand extends Module {
    public final BoolValue teamsOnly = new BoolValue("team-only", true, this);
    public final BoolValue ignoreWeapons = new BoolValue("ignore-weapons", false, this);

    public GhostHand() {
        super("GhostHand", false);
    }

    public boolean shouldSkip(Entity entity) {
        return entity instanceof EntityPlayer
                && !TeamUtil.isBot((EntityPlayer) entity)
                && (!this.teamsOnly.getValue() || TeamUtil.isSameTeam((EntityPlayer) entity))
                && (!this.ignoreWeapons.getValue() || !ItemUtil.hasRawUnbreakingEnchant());
    }
}
