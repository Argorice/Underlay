package dev.argorice.underlay.mixin;

import dev.argorice.underlay.core.UnderlayCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mod's single mixin. Adds the layer's own collision to the cell's
 * collision shape, so a carpet gives the authentic vanilla 1/16 micro-step
 * and stacked snow gives the vanilla stepped heights.
 *
 * <p>Why here: this 3-arg overload is what entity movement queries go through
 * ({@code BlockCollisions} → {@code getCollisionShape(getter, pos, context)}),
 * on both logical sides — players, mobs, projectiles, and Create contraptions
 * colliding with the world all read the same method, so everything stays in
 * agreement. The injection is an additive tail-modification of the return
 * value, the safest possible shape for coexistence with other mods' mixins.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void underlay$addLayerCollision(BlockGetter getter, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape original = cir.getReturnValue();
        VoxelShape amended = UnderlayCollision.amend(original, getter, pos);
        if (amended != original) {
            cir.setReturnValue(amended);
        }
    }
}
