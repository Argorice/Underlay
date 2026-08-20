package dev.argorice.underlay;

import dev.argorice.underlay.core.UnderlayLayers;
import dev.argorice.underlay.core.UnderlayRules;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Public API for addon mods. Stable across 0.x releases.
 *
 * <p>Most integrations need no code at all — add blocks to the
 * {@code underlay:allows_underlay} block tag and items to the
 * {@code underlay:coverings} item tag from a datapack. This class is for the
 * cases tags can't express: custom item→covering mappings and programmatic
 * layer access.
 */
public final class UnderlayAPI {
    private static final Map<Item, Block> CUSTOM_COVERINGS = new ConcurrentHashMap<>();

    /**
     * Registers an item as a covering that renders as the given block.
     * Use for items that are not {@code BlockItem}s of their covering
     * (the common case is already handled by the {@code underlay:coverings} tag).
     */
    public static void registerCovering(Item item, Block renderBlock) {
        CUSTOM_COVERINGS.put(item, renderBlock);
    }

    /** Convenience overload matching the id-based style of the tag files. */
    public static void registerCovering(ResourceLocation itemId, UnderlayType type) {
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        Block block = type.block().orElse(null);
        if (item != null && block != null) {
            registerCovering(item, block);
        }
    }

    @Nullable
    public static Block customCoveringFor(Item item) {
        return CUSTOM_COVERINGS.get(item);
    }

    /** Whether a layer may be placed under this block state (server-side view). */
    public static boolean canPlaceUnder(BlockState state) {
        return UnderlayRules.canHost(state, false);
    }

    /** The layer occupying the cell, if any. Works on both sides. */
    public static Optional<UnderlayType> getLayer(Level level, BlockPos pos) {
        return UnderlayLayers.getLayer(level, pos)
                .map(layer -> new UnderlayType(layer.block(), layer.amount()));
    }

    /**
     * Places a layer programmatically, bypassing item consumption but not the
     * "one layer per cell" rule. Server only.
     *
     * @return false if the cell already has a layer
     */
    public static boolean placeLayer(ServerLevel level, BlockPos pos, UnderlayType type) {
        return UnderlayLayers.place(level, pos,
                new dev.argorice.underlay.core.UnderlayData.Layer(type.blockId(), type.amount()));
    }

    /** Removes a layer programmatically (no item drop). Server only. */
    public static Optional<UnderlayType> removeLayer(ServerLevel level, BlockPos pos) {
        return UnderlayLayers.remove(level, pos)
                .map(layer -> new UnderlayType(layer.block(), layer.amount()));
    }

    private UnderlayAPI() {}
}
