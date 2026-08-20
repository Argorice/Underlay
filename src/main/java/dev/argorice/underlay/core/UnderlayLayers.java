package dev.argorice.underlay.core;

import dev.argorice.underlay.core.UnderlayData.Layer;
import dev.argorice.underlay.network.ClientboundSetLayerPayload;
import java.util.Optional;
import dev.argorice.underlay.core.UnderlayRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Level-facing operations on layers. Reads work on both sides; mutations are
 * server-only and take care of persistence flags and network sync.
 */
public final class UnderlayLayers {

    /** Side-safe read. Never loads chunks. */
    public static Optional<Layer> getLayer(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return Optional.empty();
        }
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.getExistingData(UnderlayAttachments.LAYERS)
                .map(data -> data.get(UnderlayData.pack(pos)));
    }

    /**
     * Places a layer. Assumes all rule checks already passed.
     *
     * @return false if a layer was already present
     */
    public static boolean place(ServerLevel level, BlockPos pos, Layer layer) {
        if (getLayer(level, pos).isPresent()) {
            return false;
        }
        set(level, pos, layer);
        dev.argorice.underlay.server.SnowMeltHandler.onLayerPlaced(level, pos, layer.block());
        return true;
    }

    /** Unconditionally writes (or overwrites) the layer and syncs it. */
    public static void set(ServerLevel level, BlockPos pos, Layer layer) {
        LevelChunk chunk = level.getChunkAt(pos);
        UnderlayData data = chunk.getData(UnderlayAttachments.LAYERS);
        data.put(UnderlayData.pack(pos), layer);
        chunk.setUnsaved(true);
        sendToTracking(level, chunk, new ClientboundSetLayerPayload(pos, Optional.of(layer)));
    }

    /** Removes a layer, syncing the removal. @return the removed layer */
    public static Optional<Layer> remove(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return Optional.empty();
        }
        LevelChunk chunk = level.getChunkAt(pos);
        UnderlayData data = chunk.getExistingDataOrNull(UnderlayAttachments.LAYERS.get());
        if (data == null) {
            return Optional.empty();
        }
        Layer removed = data.remove(UnderlayData.pack(pos));
        if (removed == null) {
            return Optional.empty();
        }
        chunk.setUnsaved(true);
        sendToTracking(level, chunk, new ClientboundSetLayerPayload(pos, Optional.empty()));
        return Optional.of(removed);
    }

    /**
     * Reduces a stacked covering by one layer, removing it entirely when only
     * one layer is left. Used by snow melting.
     */
    public static void shrink(ServerLevel level, BlockPos pos) {
        Optional<Layer> existing = getLayer(level, pos);
        if (existing.isEmpty()) {
            return;
        }
        Layer layer = existing.get();
        if (layer.amount() > 1) {
            set(level, pos, layer.shrunk());
        } else {
            remove(level, pos);
        }
    }

    /**
     * A visual stack at full height in a <em>free</em> cell becomes a real
     * block: snow stacked to 8 layers in an empty cell turns into an actual
     * snow block. From then on it is fully vanilla — it supports torches,
     * catches clicks on every face and mines normally. Stacks under a carrier
     * can't materialize (the cell is occupied) and stay visual.
     *
     * @return true if the layer was replaced by a real block
     */
    public static boolean materializeIfFullOrphan(ServerLevel level, BlockPos pos) {
        Optional<Layer> existing = getLayer(level, pos);
        if (existing.isEmpty()) {
            return false;
        }
        Layer layer = existing.get();
        Block block = BuiltInRegistries.BLOCK.getOptional(layer.block()).orElse(null);
        if (block == null) {
            return false;
        }
        int max = UnderlayRules.maxAmount(block);
        if (max <= 1 || layer.amount() < max) {
            return false;
        }
        if (!level.getBlockState(pos).isAir()) {
            return false; // hosted cell — stays a visual layer
        }
        var state = UnderlayRules.coveringState(layer.block(), layer.amount());
        if (state == null || !state.canSurvive(level, pos)) {
            return false;
        }
        remove(level, pos);
        level.setBlock(pos, state, 3);
        return true;
    }

    /** Drops the covering's item form — one item per stacked layer. */
    public static void dropCovering(ServerLevel level, BlockPos pos, Layer layer) {
        Block block = BuiltInRegistries.BLOCK.getOptional(layer.block()).orElse(null);
        if (block == null) {
            return;
        }
        ItemStack drop = new ItemStack(block.asItem(), Math.max(1, layer.amount()));
        if (!drop.isEmpty()) {
            Block.popResource(level, pos, drop);
        }
    }

    /**
     * Sends a payload to every player tracking the chunk that actually has the
     * mod installed — vanilla clients on a modded server are silently skipped.
     */
    public static void sendToTracking(ServerLevel level, LevelChunk chunk, CustomPacketPayload payload) {
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)) {
            if (player.connection.hasChannel(payload.type().id())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private UnderlayLayers() {}
}
