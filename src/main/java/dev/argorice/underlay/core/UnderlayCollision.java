package dev.argorice.underlay.core;

import dev.argorice.underlay.config.UnderlayServerConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collision support for layers, driven by the single mixin in
 * {@code BlockStateBaseMixin}. The layer contributes exactly the collision
 * shape its covering block would have as a real block: 1/16 for carpets,
 * the vanilla stepped heights for snow. This runs on a hot path, so the
 * common no-layer case must bail out with as little work as possible.
 */
public final class UnderlayCollision {
    /** Collision shapes per covering — a handful of entries, cached forever. */
    private static final Map<UnderlayData.Layer, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Returns the original shape unchanged (same instance!) when the cell has
     * no layer — the mixin uses identity comparison to skip the override.
     */
    public static VoxelShape amend(VoxelShape original, BlockGetter getter, BlockPos pos) {
        // Only real levels carry layer data; regions, empty getters and the
        // recursive shape-cache lookup below all fall through instantly.
        if (!(getter instanceof Level level)) {
            return original;
        }
        if (!UnderlayServerConfig.layerCollision()) {
            return original;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return original; // unloaded, or an off-thread query — degrade gracefully
        }
        UnderlayData data = chunk.getExistingDataOrNull(UnderlayAttachments.LAYERS.get());
        if (data == null || data.isEmpty()) {
            return original;
        }
        UnderlayData.Layer layer = data.get(UnderlayData.pack(pos));
        if (layer == null) {
            return original;
        }
        VoxelShape layerShape = shapeFor(layer);
        if (layerShape.isEmpty()) {
            return original;
        }
        return original.isEmpty() ? layerShape : Shapes.or(original, layerShape);
    }

    private static VoxelShape shapeFor(UnderlayData.Layer layer) {
        return SHAPE_CACHE.computeIfAbsent(layer, l -> {
            try {
                BlockState state = UnderlayRules.coveringState(l.block(), l.amount());
                if (state == null) {
                    return Shapes.empty();
                }
                // EmptyBlockGetter → the mixin's Level check fails → no recursion.
                return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            } catch (Exception e) {
                return Shapes.empty();
            }
        });
    }

    private UnderlayCollision() {}
}
