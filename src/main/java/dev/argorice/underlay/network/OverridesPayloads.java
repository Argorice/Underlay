package dev.argorice.underlay.network;

import dev.argorice.underlay.Underlay;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Tag-override sync in both directions. */
public final class OverridesPayloads {

    private static void writeSet(FriendlyByteBuf buf, Set<ResourceLocation> set) {
        buf.writeVarInt(set.size());
        for (ResourceLocation id : set) {
            buf.writeResourceLocation(id);
        }
    }

    private static Set<ResourceLocation> readSet(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Set<ResourceLocation> set = new LinkedHashSet<>(size);
        for (int i = 0; i < size; i++) {
            set.add(buf.readResourceLocation());
        }
        return set;
    }

    /** Server → client: the authoritative override state (login + after changes). */
    public record Clientbound(Set<ResourceLocation> added, Set<ResourceLocation> removed) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Clientbound> TYPE =
                new CustomPacketPayload.Type<>(Underlay.id("overrides_sync"));

        public static final StreamCodec<FriendlyByteBuf, Clientbound> STREAM_CODEC = StreamCodec.of(
                (FriendlyByteBuf buf, Clientbound payload) -> {
                    writeSet(buf, payload.added());
                    writeSet(buf, payload.removed());
                },
                (FriendlyByteBuf buf) -> new Clientbound(readSet(buf), readSet(buf)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: a request to replace the overrides (requires permission level 2). */
    public record Serverbound(Set<ResourceLocation> added, Set<ResourceLocation> removed) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Serverbound> TYPE =
                new CustomPacketPayload.Type<>(Underlay.id("overrides_update"));

        public static final StreamCodec<FriendlyByteBuf, Serverbound> STREAM_CODEC = StreamCodec.of(
                (FriendlyByteBuf buf, Serverbound payload) -> {
                    writeSet(buf, payload.added());
                    writeSet(buf, payload.removed());
                },
                (FriendlyByteBuf buf) -> new Serverbound(readSet(buf), readSet(buf)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private OverridesPayloads() {}
}
