package dev.argorice.underlay.network;

import dev.argorice.underlay.Underlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: a full-height visual stack (legacy snow at 8 layers) in a
 * free cell was clicked — turn it into a real block so subsequent clicks work
 * fully vanilla.
 */
public record ServerboundMaterializeLayerPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundMaterializeLayerPayload> TYPE =
            new CustomPacketPayload.Type<>(Underlay.id("materialize_layer"));

    public static final StreamCodec<FriendlyByteBuf, ServerboundMaterializeLayerPayload> STREAM_CODEC = StreamCodec.of(
            (FriendlyByteBuf buf, ServerboundMaterializeLayerPayload payload) -> buf.writeBlockPos(payload.pos()),
            (FriendlyByteBuf buf) -> new ServerboundMaterializeLayerPayload(buf.readBlockPos()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
