package dev.argorice.underlay.network;

import dev.argorice.underlay.Underlay;
import dev.argorice.underlay.core.UnderlayData.Layer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Point update: a single layer was placed/changed (present) or removed (empty). */
public record ClientboundSetLayerPayload(BlockPos pos, Optional<Layer> layer) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundSetLayerPayload> TYPE =
            new CustomPacketPayload.Type<>(Underlay.id("set_layer"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundSetLayerPayload> STREAM_CODEC = StreamCodec.of(
            (FriendlyByteBuf buf, ClientboundSetLayerPayload payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeBoolean(payload.layer().isPresent());
                payload.layer().ifPresent(layer -> {
                    buf.writeResourceLocation(layer.block());
                    buf.writeByte(layer.amount());
                });
            },
            (FriendlyByteBuf buf) -> {
                BlockPos pos = buf.readBlockPos();
                Optional<Layer> layer = Optional.empty();
                if (buf.readBoolean()) {
                    ResourceLocation block = buf.readResourceLocation();
                    int amount = buf.readByte();
                    layer = Optional.of(new Layer(block, amount));
                }
                return new ClientboundSetLayerPayload(pos, layer);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
