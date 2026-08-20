package dev.argorice.underlay.client;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps the meshing region and replaces biome tints with neutral defaults —
 * used when the {@code biomeTint} client option is off.
 */
final class NoTintRegion implements BlockAndTintGetter {
    private final BlockAndTintGetter delegate;

    NoTintRegion(BlockAndTintGetter delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) {
            return GrassColor.getDefaultColor();
        }
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
            return FoliageColor.getDefaultColor();
        }
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
            return 0x3F76E4;
        }
        return delegate.getBlockTint(pos, resolver);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return delegate.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return delegate.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return delegate.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return delegate.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return delegate.getMinBuildHeight();
    }
}
