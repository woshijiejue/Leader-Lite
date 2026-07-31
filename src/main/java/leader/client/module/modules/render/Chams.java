package leader.client.module.modules.render;

import leader.client.event.EventTarget;
import leader.client.events.RenderLivingEvent;
import leader.client.module.Module;
import leader.client.util.player.TeamUtil;
import leader.client.module.values.impl.BoolValue;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

public class Chams extends Module {

    public final BoolValue players = new BoolValue("players", true, this);
    public final BoolValue friends = new BoolValue("friends", true, this);
    public final BoolValue enemiess = new BoolValue("enemies", true, this);
    public final BoolValue bosses = new BoolValue("bosses", false, this);
    public final BoolValue mobs = new BoolValue("mobs", false, this);
    public final BoolValue creepers = new BoolValue("creepers", false, this);
    public final BoolValue enderman = new BoolValue("endermen", false, this);
    public final BoolValue blaze = new BoolValue("blazes", false, this);
    public final BoolValue animals = new BoolValue("animals", false, this);
    public final BoolValue self = new BoolValue("self", false, this);
    public final BoolValue bots = new BoolValue("bots", false, this);

    private boolean shouldRenderChams(EntityLivingBase entityLivingBase) {
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
                    return TeamUtil.isTarget((EntityPlayer) entityLivingBase) ? this.enemiess.getValue() : this.players.getValue();
                }
            } else {
                return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
        } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
            return !entityLivingBase.isInvisible() && this.bosses.getValue();
        } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
            return (entityLivingBase instanceof EntityAnimal
                    || entityLivingBase instanceof EntityBat
                    || entityLivingBase instanceof EntitySquid
                    || entityLivingBase instanceof EntityVillager) && this.animals.getValue();
        } else if (entityLivingBase instanceof EntityCreeper) {
            return this.creepers.getValue();
        } else if (entityLivingBase instanceof EntityEnderman) {
            return this.enderman.getValue();
        } else {
            return entityLivingBase instanceof EntityBlaze ? this.blaze.getValue() : this.mobs.getValue();
        }
    }

    public Chams() {
        super("Chams", false);
    }

    @EventTarget
    public void onRenderLiving(RenderLivingEvent event) {
        if (this.isEnabled()) {
            if (this.shouldRenderChams(event.getEntity())) {
                switch (event.getType()) {
                    case PRE:
                        GL11.glEnable(32823);
                        GL11.glPolygonOffset(1.0F, -2500000.0F);
                        break;
                    case POST:
                        GL11.glPolygonOffset(1.0F, 2500000.0F);
                        GL11.glDisable(32823);
                }
            }
        }
    }
}
