package leader.mixin;

import leader.Leader;
import leader.module.modules.misc.AntiObbyTrap;
import leader.module.modules.movement.Jesus;
import leader.util.ISkipTickPlayer;
import leader.util.ISkipTickWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = {World.class}, priority = 9999)
public abstract class MixinWorld implements ISkipTickWorld {
    @Unique
    private int leader$skipTicks;

    @Override
    public int leader$getSkipTicks() {
        return leader$skipTicks;
    }

    @Override
    public void leader$setSkipTicks(int ticks) {
        leader$skipTicks = Math.max(0, ticks);
    }

    @Unique
    private boolean leader$skipPlayerUpdate(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();

        // Never skip the integrated server's simulation.
        if (leader$skipTicks <= 0 || (Object) this != mc.theWorld || entity != mc.thePlayer) return false;
        --leader$skipTicks;
        ((ISkipTickPlayer) entity).leader$onSkipUpdate();
        return true;
    }

    @Redirect(method = "updateEntityWithOptionalForce", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;onUpdate()V"))
    private void leader$updateOrSkip(Entity entity) {

        if (!leader$skipPlayerUpdate(entity)) entity.onUpdate();
    }

    @Redirect(method = "updateEntityWithOptionalForce", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;updateRidden()V"))
    private void leader$updateRiddenOrSkip(Entity entity) {
        if (!leader$skipPlayerUpdate(entity)) entity.updateRidden();
    }

    @Redirect(
            method = {"handleMaterialAcceleration"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;isPushedByWater()Z"
            )
    )
    private boolean handleMaterialAcceleration(Entity entity) {
        if (entity instanceof EntityPlayerSP && Leader.moduleManager != null) {
            Jesus jesus = (Jesus) Leader.moduleManager.modules.get(Jesus.class);
            if (jesus.isEnabled() && jesus.noPush.getValue()) {
                return false;
            }
        }
        return entity.isPushedByWater();
    }

    @Redirect(
            method = {"rayTraceBlocks(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;"
            )
    )
    private IBlockState rayTraceBlocks(World world, BlockPos blockPos) {
        if (Leader.moduleManager == null) {
            return world.getBlockState(blockPos);
        } else {
            AntiObbyTrap antiObbyTrap = (AntiObbyTrap) Leader.moduleManager.modules.get(AntiObbyTrap.class);
            if (antiObbyTrap.isEnabled() && antiObbyTrap.isInsideBlock(world, blockPos)) {
                if (antiObbyTrap.setAir.getValue()) {
                    world.setBlockToAir(blockPos);
                }
                return Blocks.air.getDefaultState();
            } else {
                return world.getBlockState(blockPos);
            }
        }
    }
}
