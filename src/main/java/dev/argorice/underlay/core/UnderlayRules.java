package dev.argorice.underlay.core;

import dev.argorice.underlay.Underlay;
import dev.argorice.underlay.UnderlayAPI;
import dev.argorice.underlay.config.UnderlayServerConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The single source of truth for "can this block host a layer" and
 * "what covering does this item place".
 *
 * <p>Order of precedence: {@code denies_underlay} tag beats everything,
 * then user override "removed", then the defaults (the {@code allows_underlay}
 * tag, or — when {@code allowNonFullBlocks} is on — any block that leaves the
 * bottom slice of its cell visible) or the user override "added".
 */
public final class UnderlayRules {
    public static final TagKey<Block> ALLOWS_UNDERLAY = TagKey.create(Registries.BLOCK, Underlay.id("allows_underlay"));
    public static final TagKey<Block> DENIES_UNDERLAY = TagKey.create(Registries.BLOCK, Underlay.id("denies_underlay"));
    public static final TagKey<Item> COVERINGS = TagKey.create(Registries.ITEM, Underlay.id("coverings"));

    /** The space a covering occupies: the bottom 1/16 slice of the cell. */
    private static final VoxelShape LAYER_SLICE = Block.box(0, 0, 0, 16, 1, 16);
    /** BlockStates are singletons, so shape visibility can be cached per state. */
    private static final Map<BlockState, Boolean> VISIBLE_HOST_CACHE = new ConcurrentHashMap<>();

    public static boolean canHost(BlockState state, boolean clientSide) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(DENIES_UNDERLAY)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        UnderlayOverrides overrides = clientSide ? UnderlayOverrides.clientView() : UnderlayOverrides.server();
        if (overrides.removed().contains(id)) {
            return false;
        }
        return isDefaultHost(state) || overrides.added().contains(id);
    }

    /** Whether this state hosts a layer before any user override is applied. */
    public static boolean isDefaultHost(BlockState state) {
        if (state.isAir() || state.is(DENIES_UNDERLAY)) {
            return false;
        }
        if (state.is(ALLOWS_UNDERLAY)) {
            return true;
        }
        return UnderlayServerConfig.allowNonFullBlocks() && layerWouldBeVisible(state);
    }

    /**
     * A layer only makes sense if it would actually be seen: the bottom slice
     * of the cell must not be fully swallowed by the block's collision shape.
     * This admits chests, fences, doors, machines, furniture — and naturally
     * rejects full cubes and bottom slabs.
     */
    public static boolean layerWouldBeVisible(BlockState state) {
        return VISIBLE_HOST_CACHE.computeIfAbsent(state, s -> {
            try {
                VoxelShape collision = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (collision.isEmpty()) {
                    return true;
                }
                return Shapes.joinIsNotEmpty(LAYER_SLICE, collision, BooleanOp.ONLY_FIRST);
            } catch (Exception e) {
                // Position-dependent shapes that can't be sampled in a void: be conservative.
                return false;
            }
        });
    }

    /**
     * The render state for a covering: the block's default state, with the
     * vanilla LAYERS property applied when the block has one (snow stacking).
     * Null when the block's mod is not installed.
     */
    @Nullable
    public static BlockState coveringState(ResourceLocation blockId, int amount) {
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS)) {
            int max = maxAmount(block);
            state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS,
                    Math.min(Math.max(amount, 1), max));
        }
        return state;
    }

    /** How high this covering stacks: 8 for snow-style blocks, 1 for the rest. */
    public static int maxAmount(Block block) {
        BlockState state = block.defaultBlockState();
        if (!state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS)) {
            return 1;
        }
        return java.util.Collections.max(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS.getPossibleValues());
    }

    /**
     * When a real block in the world is itself a covering (a placed carpet
     * block, a real snow layer 1–7), it can be absorbed into an underlay
     * layer so another block can take its cell. Returns the equivalent layer,
     * or null when the state is not absorbable (not a covering, or already
     * a full-height stack that should stay a real block).
     */
    @Nullable
    public static UnderlayData.Layer absorbableLayer(BlockState state) {
        if (state.isAir()) {
            return null;
        }
        Block block = state.getBlock();
        ItemStack asItem = new ItemStack(block.asItem());
        if (asItem.isEmpty() || !asItem.is(COVERINGS)) {
            return null;
        }
        int amount = 1;
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS)) {
            amount = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS);
            if (amount >= maxAmount(block)) {
                return null; // full stack acts as a solid block — leave it real
            }
        }
        return new UnderlayData.Layer(BuiltInRegistries.BLOCK.getKey(block), amount);
    }

    /**
     * Resolves the covering block an item stack would place, or null if the
     * stack is not a valid covering. API registrations win over the item tag.
     */
    @Nullable
    public static Block coveringFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Block custom = UnderlayAPI.customCoveringFor(stack.getItem());
        if (custom != null) {
            return custom;
        }
        if (!stack.is(COVERINGS)) {
            return null;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }
        return null;
    }

    private UnderlayRules() {}
}
