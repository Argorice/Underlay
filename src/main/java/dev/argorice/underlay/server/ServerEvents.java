package dev.argorice.underlay.server;

import dev.argorice.underlay.Underlay;
import dev.argorice.underlay.core.UnderlayAttachments;
import dev.argorice.underlay.core.UnderlayData;
import dev.argorice.underlay.core.UnderlayLayers;
import dev.argorice.underlay.core.UnderlayOverrides;
import dev.argorice.underlay.core.UnderlayRules;
import dev.argorice.underlay.config.UnderlayServerConfig;
import dev.argorice.underlay.network.ClientboundChunkLayersPayload;
import dev.argorice.underlay.network.OverridesPayloads;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Underlay.MOD_ID)
public final class ServerEvents {

    /** Initial sync: ship the chunk's layers right after the chunk itself. */
    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        LevelChunk chunk = event.getChunk();
        UnderlayData data = chunk.getExistingDataOrNull(UnderlayAttachments.LAYERS.get());
        if (data == null || data.isEmpty()) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        // Copy: the payload is encoded later on the network thread, while the
        // server thread may keep mutating the live attachment map.
        ClientboundChunkLayersPayload payload = new ClientboundChunkLayersPayload(
                chunk.getPos(), new Int2ObjectOpenHashMap<>(data.view()));
        if (player.connection.hasChannel(ClientboundChunkLayersPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Send the authoritative tag overrides to a freshly logged-in modded client. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.connection.hasChannel(OverridesPayloads.Clientbound.TYPE.id())) {
            return;
        }
        UnderlayOverrides overrides = UnderlayOverrides.server();
        // Set.copyOf: encoding happens later on the network thread.
        PacketDistributor.sendToPlayer(player,
                new OverridesPayloads.Clientbound(Set.copyOf(overrides.added()), Set.copyOf(overrides.removed())));
    }

    /**
     * Breaking the carrier intentionally leaves the layer in place — a carpet
     * should not vanish because you moved a chest. But if a block that can NOT
     * host a layer is placed into an occupied cell (say, a stone block over a
     * carpet), the layer pops off as an item so it never sits invisible inside.
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (UnderlayLayers.getLayer(level, pos).isEmpty()) {
            return;
        }
        if (UnderlayRules.canHost(event.getPlacedBlock(), false)) {
            return;
        }
        UnderlayLayers.remove(level, pos).ifPresent(id -> {
            if (UnderlayServerConfig.dropLayerOnBreak()) {
                UnderlayLayers.dropCovering(level, pos, id);
            }
        });
    }

    /**
     * A covering behaves like a carpet: when the block <em>below</em> its cell
     * disappears, the covering loses support and pops off as an item — no
     * floating carpets in mid-air.
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!event.getState().isAir()) {
            return; // only care about blocks being removed
        }
        BlockPos above = event.getPos().above();
        UnderlayLayers.remove(level, above).ifPresent(layer -> {
            if (UnderlayServerConfig.dropLayerOnBreak()) {
                UnderlayLayers.dropCovering(level, above, layer);
            }
        });
    }

    // --- snow melt bookkeeping ---------------------------------------------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            SnowMeltHandler.onChunkLoad(level, chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            SnowMeltHandler.onChunkUnload(level, chunk.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SnowMeltHandler.onLevelUnload(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SnowMeltHandler.onLevelTick(level);
        }
    }

    private ServerEvents() {}
}
