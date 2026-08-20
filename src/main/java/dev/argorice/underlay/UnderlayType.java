package dev.argorice.underlay;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * A covering type. Wraps the registry id of the block whose model is rendered
 * as the layer (e.g. {@code minecraft:white_carpet}). Ids are kept even when
 * the owning mod is absent, so worlds survive mod removal.
 */
public record UnderlayType(ResourceLocation blockId, int amount) {
    public UnderlayType {
        amount = Math.max(1, amount);
    }

    public UnderlayType(ResourceLocation blockId) {
        this(blockId, 1);
    }

    public static UnderlayType of(Block block) {
        return new UnderlayType(BuiltInRegistries.BLOCK.getKey(block), 1);
    }

    /** Empty when the block's mod is not installed. */
    public Optional<Block> block() {
        return BuiltInRegistries.BLOCK.getOptional(blockId);
    }
}
