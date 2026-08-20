package dev.argorice.underlay.network;

import dev.argorice.underlay.core.UnderlayAttachments;
import dev.argorice.underlay.core.UnderlayData;
import dev.argorice.underlay.core.UnderlayData.Layer;
import dev.argorice.underlay.core.UnderlayOverrides;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The client never decides anything itself: it renders exactly what the
 * server sent. Handlers run on the main thread (registrar default).
 */
public final class ClientPayloadHandler {

    public static void handleSetLayer(ClientboundSetLayerPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        BlockPos pos = payload.pos();
        if (!level.hasChunkAt(pos)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        UnderlayData data = chunk.getData(UnderlayAttachments.LAYERS);
        int key = UnderlayData.pack(pos);
        if (payload.layer().isPresent()) {
            data.put(key, payload.layer().get());
        } else {
            data.remove(key);
        }
        markSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public static void handleChunkLayers(ClientboundChunkLayersPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        ChunkPos chunkPos = payload.chunkPos();
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
            return;
        }
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        UnderlayData data = chunk.getData(UnderlayAttachments.LAYERS);
        data.clear();
        data.putAll(payload.layers());

        IntSet dirtySections = new IntOpenHashSet();
        for (Int2ObjectMap.Entry<Layer> entry : payload.layers().int2ObjectEntrySet()) {
            BlockPos pos = UnderlayData.unpack(chunkPos, entry.getIntKey());
            if (dirtySections.add(pos.getY() >> 4)) {
                markSectionDirty(chunkPos.x, pos.getY() >> 4, chunkPos.z);
            }
        }
    }

    public static void handleOverrides(OverridesPayloads.Clientbound payload, IPayloadContext context) {
        UnderlayOverrides.setClientView(payload.added(), payload.removed());
    }

    private static void markSectionDirty(int sectionX, int sectionY, int sectionZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.setSectionDirty(sectionX, sectionY, sectionZ);
        }
    }

    private ClientPayloadHandler() {}
}
