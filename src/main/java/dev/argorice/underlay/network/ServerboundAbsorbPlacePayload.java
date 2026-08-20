package dev.argorice.underlay.network;

import dev.argorice.underlay.Underlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: absorb the real covering block at this position (a placed
 * carpet block, a real snow layer) into an underlay layer, then place the
 * main-hand block into the freed cell — "the block stands on the carpet"
 * instead of vanilla's floating-one-above behaviour.
 */
public record ServerboundAbsorbPlacePayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundAbsorbPlacePayload> TYPE =
            new CustomPacketPayload.Type<>(Underlay.id("absorb_place"));

    public static final StreamCodec<FriendlyByteBuf, ServerboundAbsorbPlacePayload> STREAM_CODEC = StreamCodec.of(
            (FriendlyByteBuf buf, ServerboundAbsorbPlacePayload payload) -> buf.writeBlockPos(payload.pos()),
            (FriendlyByteBuf buf) -> new ServerboundAbsorbPlacePayload(buf.readBlockPos()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
