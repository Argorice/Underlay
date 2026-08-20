package dev.argorice.underlay.network;

import dev.argorice.underlay.Underlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: remove the layer at this position. Used for layers whose
 * carrier block is gone — there is nothing left to right-click, so the client
 * ray-picks the thin layer box itself and asks the server directly.
 */
public record ServerboundRemoveLayerPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundRemoveLayerPayload> TYPE =
            new CustomPacketPayload.Type<>(Underlay.id("remove_layer"));

    public static final StreamCodec<FriendlyByteBuf, ServerboundRemoveLayerPayload> STREAM_CODEC = StreamCodec.of(
            (FriendlyByteBuf buf, ServerboundRemoveLayerPayload payload) -> buf.writeBlockPos(payload.pos()),
            (FriendlyByteBuf buf) -> new ServerboundRemoveLayerPayload(buf.readBlockPos()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
