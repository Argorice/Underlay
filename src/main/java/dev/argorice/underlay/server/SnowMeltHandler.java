package dev.argorice.underlay.server;

import dev.argorice.underlay.config.UnderlayServerConfig;
import dev.argorice.underlay.core.UnderlayAttachments;
import dev.argorice.underlay.core.UnderlayData;
import dev.argorice.underlay.core.UnderlayLayers;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Optional, config-gated melting of decorative snow layers.
 *
 * <p>Chunk attachments never tick, so melting is driven from the level tick:
 * we keep a cheap registry of chunks that are known to contain snow layers
 * and probe a few random entries every couple of seconds — the same spirit
 * as vanilla random ticks, without touching chunks that have no snow at all.
 */
public final class SnowMeltHandler {
    private static final ResourceLocation SNOW_ID = ResourceLocation.withDefaultNamespace("snow");
    /** Probe interval in ticks (~2.5 s) and chunks probed per interval. */
    private static final int PROBE_INTERVAL = 48;
    private static final int CHUNKS_PER_PROBE = 16;

    private static final Map<ResourceKey<Level>, LongSet> SNOW_CHUNKS = new ConcurrentHashMap<>();

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        UnderlayData data = chunk.getExistingDataOrNull(UnderlayAttachments.LAYERS.get());
        if (data == null || data.isEmpty()) {
            return;
        }
        for (Int2ObjectMap.Entry<UnderlayData.Layer> entry : data.view().int2ObjectEntrySet()) {
            if (SNOW_ID.equals(entry.getValue().block())) {
                chunks(level).add(chunk.getPos().toLong());
                return;
            }
        }
    }

    public static void onLayerPlaced(ServerLevel level, BlockPos pos, ResourceLocation blockId) {
        if (SNOW_ID.equals(blockId)) {
            chunks(level).add(new ChunkPos(pos).toLong());
        }
    }

    public static void onChunkUnload(ServerLevel level, ChunkPos pos) {
        LongSet set = SNOW_CHUNKS.get(level.dimension());
        if (set != null) {
            set.remove(pos.toLong());
        }
    }

    public static void onLevelUnload(ServerLevel level) {
        SNOW_CHUNKS.remove(level.dimension());
    }

    public static void onLevelTick(ServerLevel level) {
        if (!UnderlayServerConfig.snowLayerMelts()) {
            return;
        }
        if (level.getGameTime() % PROBE_INTERVAL != 0) {
            return;
        }
        LongSet set = SNOW_CHUNKS.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }
        long[] snapshot = set.toLongArray();
        int probes = Math.min(CHUNKS_PER_PROBE, snapshot.length);
        for (int i = 0; i < probes; i++) {
            long chunkKey = snapshot[level.random.nextInt(snapshot.length)];
            probeChunk(level, set, chunkKey);
        }
    }

    private static void probeChunk(ServerLevel level, LongSet set, long chunkKey) {
        ChunkPos chunkPos = new ChunkPos(chunkKey);
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return; // unloaded since; will re-register on next load
        }
        UnderlayData data = chunk.getExistingDataOrNull(UnderlayAttachments.LAYERS.get());
        if (data == null || data.isEmpty()) {
            set.remove(chunkKey);
            return;
        }
        boolean anySnow = false;
        BlockPos meltAt = null;
        for (Int2ObjectMap.Entry<UnderlayData.Layer> entry : data.view().int2ObjectEntrySet()) {
            if (!SNOW_ID.equals(entry.getValue().block())) {
                continue;
            }
            anySnow = true;
            BlockPos pos = UnderlayData.unpack(chunkPos, entry.getIntKey());
            if (shouldMelt(level, pos) && level.random.nextInt(4) == 0) {
                meltAt = pos;
                break;
            }
        }
        if (!anySnow) {
            set.remove(chunkKey);
        } else if (meltAt != null) {
            // Stacked snow melts layer by layer, like the real thing.
            UnderlayLayers.shrink(level, meltAt);
        }
    }

    private static boolean shouldMelt(ServerLevel level, BlockPos pos) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11) {
            return true; // same threshold vanilla snow uses
        }
        return level.getBiome(pos).value().warmEnoughToRain(pos);
    }

    private static LongSet chunks(ServerLevel level) {
        return SNOW_CHUNKS.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet());
    }

    private SnowMeltHandler() {}
}
