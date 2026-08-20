package dev.argorice.underlay.network;

import dev.argorice.underlay.Underlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: place a layer (from the main-hand covering item) into the
 * cell at this position. Sent from the input-event interception so that
 * placement works on any modded carrier block — the vanilla use pipeline
 * (which a mod block could consume) is bypassed entirely.
 */
public record ServerboundPlaceLayerPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundPlaceLayerPayload> TYPE =
            new CustomPacketPayload.Type<>(Underlay.id("place_layer"));

    public static final StreamCodec<FriendlyByteBuf, ServerboundPlaceLayerPayload> STREAM_CODEC = StreamCodec.of(
            (FriendlyByteBuf buf, ServerboundPlaceLayerPayload payload) -> buf.writeBlockPos(payload.pos()),
            (FriendlyByteBuf buf) -> new ServerboundPlaceLayerPayload(buf.readBlockPos()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
