package dev.argorice.underlay.network;

import dev.argorice.underlay.Underlay;
import dev.argorice.underlay.core.UnderlayData.Layer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * Full layer state of one chunk. Sent right after the chunk itself reaches the
 * client ({@code ChunkWatchEvent.Sent}), and only for chunks that have layers.
 */
public record ClientboundChunkLayersPayload(ChunkPos chunkPos, Int2ObjectMap<Layer> layers) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundChunkLayersPayload> TYPE =
            new CustomPacketPayload.Type<>(Underlay.id("chunk_layers"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundChunkLayersPayload> STREAM_CODEC = StreamCodec.of(
            (FriendlyByteBuf buf, ClientboundChunkLayersPayload payload) -> {
                buf.writeChunkPos(payload.chunkPos());
                buf.writeVarInt(payload.layers().size());
                for (Int2ObjectMap.Entry<Layer> entry : payload.layers().int2ObjectEntrySet()) {
                    buf.writeVarInt(entry.getIntKey());
                    buf.writeResourceLocation(entry.getValue().block());
                    buf.writeByte(entry.getValue().amount());
                }
            },
            (FriendlyByteBuf buf) -> {
                ChunkPos chunkPos = buf.readChunkPos();
                int size = buf.readVarInt();
                Int2ObjectMap<Layer> layers = new Int2ObjectOpenHashMap<>(size);
                for (int i = 0; i < size; i++) {
                    int key = buf.readVarInt();
                    ResourceLocation block = buf.readResourceLocation();
                    int amount = buf.readByte();
                    layers.put(key, new Layer(block, amount));
                }
                return new ClientboundChunkLayersPayload(chunkPos, layers);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
