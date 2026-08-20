package dev.argorice.underlay.client;

import dev.argorice.underlay.core.UnderlayLayers;
import dev.argorice.underlay.core.UnderlayRules;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Layers are not blocks, so vanilla ray tracing can't target them — the
 * crosshair looks straight through a carpet lying in a cell. This picker
 * closes that gap: once per client tick it clips the view ray against the
 * shapes of nearby layers and, when such a shape is the closest thing under
 * the crosshair, exposes it (including the clicked face) as the current
 * target for the highlight renderer and the interactions.
 *
 * <p>This makes a layer behave like a real block: aim at the visible carpet
 * ring around an anvil and it highlights, left click breaks it; a full snow
 * stack catches clicks on its faces exactly like a snow block. Aiming at the
 * carrier block still wins by distance, so chests open and doors swing
 * exactly as before.
 */
public final class OrphanLayerPicker {
    /** Fallback shape when the covering block's mod is missing: a carpet slice. */
    private static final VoxelShape FALLBACK_SHAPE = Shapes.box(0, 0, 0, 1, 1 / 16.0, 1);
    private static final double RAY_STEP = 0.08;
    /** A layer at least this tall behaves as a full block for placement. */
    private static final double SOLID_HEIGHT = 0.99;

    @Nullable
    private static BlockHitResult targetHit;
    @Nullable
    private static AABB targetBox;
    private static boolean targetSolid;

    /** Called once per client tick. */
    public static void update(Minecraft minecraft) {
        targetHit = null;
        targetBox = null;
        targetSolid = false;
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null) {
            return;
        }

        double reach = player.blockInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(reach));

        double vanillaDistSqr = Double.MAX_VALUE;
        HitResult vanillaHit = minecraft.hitResult;
        if (vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS) {
            vanillaDistSqr = vanillaHit.getLocation().distanceToSqr(eye);
        }

        double bestDistSqr = Double.MAX_VALUE;
        BlockHitResult bestHit = null;
        VoxelShape bestShape = null;

        LongSet visited = new LongOpenHashSet();
        for (double t = 0; t <= reach; t += RAY_STEP) {
            Vec3 point = eye.add(view.scale(t));
            BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
            if (!visited.add(pos.asLong())) {
                continue;
            }
            VoxelShape shape = layerShape(level, pos);
            if (shape == null) {
                continue;
            }
            BlockHitResult clip = shape.clip(eye, end, pos);
            if (clip == null || clip.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            double distSqr = clip.getLocation().distanceToSqr(eye);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                bestHit = clip;
                bestShape = shape;
            }
        }

        // Only take over when the layer is closer than whatever vanilla targets.
        if (bestHit != null && bestDistSqr < vanillaDistSqr - 1.0E-4) {
            targetHit = bestHit;
            AABB bounds = bestShape.bounds();
            targetBox = bounds.move(bestHit.getBlockPos());
            targetSolid = bounds.maxY - bounds.minY >= SOLID_HEIGHT;
        }
    }

    /** The local-space shape of the layer at this cell, or null if there is none. */
    @Nullable
    private static VoxelShape layerShape(Level level, BlockPos pos) {
        return UnderlayLayers.getLayer(level, pos).map(layer -> {
            BlockState state = UnderlayRules.coveringState(layer.block(), layer.amount());
            if (state != null) {
                try {
                    VoxelShape shape = state.getShape(level, pos);
                    if (!shape.isEmpty()) {
                        return shape;
                    }
                } catch (Exception ignored) {}
            }
            return FALLBACK_SHAPE;
        }).orElse(null);
    }

    /** The layer cell currently under the crosshair, or null. */
    @Nullable
    public static BlockPos target() {
        return targetHit == null ? null : targetHit.getBlockPos();
    }

    /** The full hit (location + clicked face) on the layer's shape, or null. */
    @Nullable
    public static BlockHitResult targetHit() {
        return targetHit;
    }

    @Nullable
    public static AABB targetBox() {
        return targetBox;
    }

    /** True when the targeted layer is tall enough to act like a full block. */
    public static boolean targetSolid() {
        return targetSolid;
    }

    private OrphanLayerPicker() {}
}
