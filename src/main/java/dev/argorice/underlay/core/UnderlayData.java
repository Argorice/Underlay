package dev.argorice.underlay.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Per-chunk storage of decorative bottom layers.
 *
 * <p>Stored as a chunk data attachment, NOT as block entities: a thousand
 * carpets in a decorated hall must not become a thousand ticking BEs.
 * Nothing here ever ticks; the map is saved and loaded with the chunk.
 *
 * <p>Keys are packed local coordinates ({@link #pack(BlockPos)}), values are
 * the registry ids of the covering blocks (e.g. {@code minecraft:white_carpet}).
 * Ids of blocks from mods that were removed are kept as-is and simply skipped
 * by the renderer, so uninstalling a covering mod never corrupts chunks.
 */
public final class UnderlayData {
    /**
     * Bump when the serialized format changes; old data must stay readable.
     * v1: pos + block id. v2: added optional "amount" (snow-style stacking).
     */
    public static final int DATA_VERSION = 2;

    /**
     * One covering: which block's model to render and how many "layers" of it
     * (only meaningful for blocks with the vanilla LAYERS property, e.g. snow;
     * everything else is always 1).
     */
    public record Layer(ResourceLocation block, int amount) {
        public Layer {
            amount = Math.max(1, amount);
        }

        public static Layer of(ResourceLocation block) {
            return new Layer(block, 1);
        }

        public Layer grown() {
            return new Layer(block, amount + 1);
        }

        public Layer shrunk() {
            return new Layer(block, amount - 1);
        }
    }

    private final Int2ObjectMap<Layer> layers = new Int2ObjectOpenHashMap<>();

    public UnderlayData() {}

    private UnderlayData(List<SavedEntry> entries) {
        for (SavedEntry e : entries) {
            layers.put(e.pos(), new Layer(e.block(), e.amount()));
        }
    }

    /**
     * Packs a block position into a chunk-local int key.
     * x and z take 4 bits each; y is stored with a +2048 offset which covers
     * the full datapack-allowed dimension height range (-2032..2031).
     */
    public static int pack(BlockPos pos) {
        return ((pos.getY() + 2048) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
    }

    public static BlockPos unpack(ChunkPos chunk, int key) {
        int y = (key >>> 8) - 2048;
        int z = (key >>> 4) & 15;
        int x = key & 15;
        return new BlockPos(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
    }

    @Nullable
    public Layer get(int packedPos) {
        return layers.get(packedPos);
    }

    /** @return the previous value, if any */
    @Nullable
    public Layer put(int packedPos, Layer layer) {
        return layers.put(packedPos, layer);
    }

    /** @return the removed value, if any */
    @Nullable
    public Layer remove(int packedPos) {
        return layers.remove(packedPos);
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public int size() {
        return layers.size();
    }

    public void clear() {
        layers.clear();
    }

    /** Read-only view for iteration and network encoding. */
    public Int2ObjectMap<Layer> view() {
        return Int2ObjectMaps.unmodifiable(layers);
    }

    public void putAll(Int2ObjectMap<Layer> other) {
        layers.putAll(other);
    }

    private List<SavedEntry> toEntries() {
        List<SavedEntry> list = new ArrayList<>(layers.size());
        for (Int2ObjectMap.Entry<Layer> e : layers.int2ObjectEntrySet()) {
            list.add(new SavedEntry(e.getIntKey(), e.getValue().block(), e.getValue().amount()));
        }
        return list;
    }

    private record SavedEntry(int pos, ResourceLocation block, int amount) {
        static final Codec<SavedEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("pos").forGetter(SavedEntry::pos),
                ResourceLocation.CODEC.fieldOf("block").forGetter(SavedEntry::block),
                Codec.INT.optionalFieldOf("amount", 1).forGetter(SavedEntry::amount))
                .apply(i, SavedEntry::new));
    }

    /** Versioned NBT codec used by the chunk attachment. */
    public static final Codec<UnderlayData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("dataVersion", DATA_VERSION).forGetter(d -> DATA_VERSION),
            SavedEntry.CODEC.listOf().fieldOf("layers").forGetter(UnderlayData::toEntries))
            .apply(i, (version, entries) -> new UnderlayData(entries)));
}
