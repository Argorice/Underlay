package dev.argorice.underlay.client;

import dev.argorice.underlay.core.UnderlayData;
import dev.argorice.underlay.core.UnderlayLayers;
import dev.argorice.underlay.core.UnderlayRules;
import dev.argorice.underlay.network.ClientboundSetLayerPayload;
import dev.argorice.underlay.network.ServerboundAbsorbPlacePayload;
import dev.argorice.underlay.network.ServerboundMaterializeLayerPayload;
import dev.argorice.underlay.network.ServerboundPlaceLayerPayload;
import dev.argorice.underlay.network.ServerboundRemoveLayerPayload;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only helpers. Only ever classloaded on the physical client — callers
 * must guard with {@code level.isClientSide} or live in client-only classes.
 */
public final class ClientProxy {
    /** Game time of the last sent request per action; -1 = never. */
    private static long lastRemoveSentAt = -1;
    private static long lastPlaceSentAt = -1;

    /** True when the server we are connected to runs Underlay. */
    public static boolean serverHasUnderlay() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(ClientboundSetLayerPayload.TYPE.id());
    }

    /**
     * Sends a removal request for the layer the picker currently targets —
     * the "break it like a normal carpet" path.
     *
     * @return true if a request was sent (or is debounced for the moment)
     */
    public static boolean tryRemoveTargetedLayer() {
        BlockPos target = OrphanLayerPicker.target();
        if (target == null || !serverHasUnderlay()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        long now = minecraft.level.getGameTime();
        if (debounced(lastRemoveSentAt, now)) {
            return true;
        }
        lastRemoveSentAt = now;
        PacketDistributor.sendToServer(new ServerboundRemoveLayerPayload(target));
        return true;
    }

    /**
     * Sends a placement request when the click reads as normal block
     * placement into a host cell: the main hand holds a covering, and the
     * cell adjacent to the clicked face (the free spot of the floor an anvil
     * stands on, and so forth) holds a block that can host a layer. Vanilla
     * could never place anything into that occupied cell, so intercepting
     * here steals nothing from ordinary carpet-block placement — clicking
     * toward an empty cell falls through to vanilla untouched.
     *
     * @return true if the click should be considered handled
     */
    public static boolean tryPlaceLayerAtCrosshair(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !serverHasUnderlay()) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        Block covering = UnderlayRules.coveringFor(stack);
        if (covering == null) {
            return false;
        }

        // Aiming at a layer's own box (e.g. the top of a snow layer) targets
        // that cell directly — this is how snow stacks on snow. Otherwise use
        // ordinary block-placement semantics: the neighbour of the clicked face.
        BlockPos pos = OrphanLayerPicker.target();
        if (pos == null) {
            if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                return false;
            }
            pos = hit.getBlockPos().relative(hit.getDirection());
        }

        Optional<UnderlayData.Layer> existing = UnderlayLayers.getLayer(minecraft.level, pos);
        if (existing.isPresent()) {
            UnderlayData.Layer layer = existing.get();
            if (!layer.block().equals(BuiltInRegistries.BLOCK.getKey(covering))
                    || layer.amount() >= UnderlayRules.maxAmount(covering)) {
                return false;
            }
            // stacking onto an existing (possibly orphaned) layer — no host check
        } else if (!UnderlayRules.canHost(minecraft.level.getBlockState(pos), true)) {
            return false;
        }

        long now = minecraft.level.getGameTime();
        if (debounced(lastPlaceSentAt, now)) {
            return true;
        }
        lastPlaceSentAt = now;
        PacketDistributor.sendToServer(new ServerboundPlaceLayerPayload(pos));
        return true;
    }

    /** What the input handler should do with a use-click aimed at a layer. */
    public enum RedirectKind {
        /** Not our business — let vanilla proceed untouched. */
        NONE,
        /** Substitute {@code minecraft.hitResult} and let vanilla place normally. */
        REDIRECT,
        /** Consume the click entirely (it "hit" the solid layer). */
        SWALLOW,
        /** A real covering block was clicked: absorb it and place into its cell. */
        ABSORB,
        /** A legacy full-height visual stack was clicked: turn it into a real block. */
        MATERIALIZE
    }

    public record PlacementRedirect(RedirectKind kind, @Nullable BlockHitResult hit, @Nullable BlockPos pos) {
        static final PlacementRedirect NONE = new PlacementRedirect(RedirectKind.NONE, null, null);
        static final PlacementRedirect SWALLOW = new PlacementRedirect(RedirectKind.SWALLOW, null, null);

        static PlacementRedirect to(BlockHitResult hit) {
            return new PlacementRedirect(RedirectKind.REDIRECT, hit, null);
        }

        static PlacementRedirect absorb(BlockPos pos) {
            return new PlacementRedirect(RedirectKind.ABSORB, null, pos);
        }

        static PlacementRedirect materialize(BlockPos pos) {
            return new PlacementRedirect(RedirectKind.MATERIALIZE, null, pos);
        }
    }

    /**
     * Building on a layer, "like a real block". Instead of invoking placement
     * ourselves we hand vanilla a corrected {@code hitResult} and step aside —
     * the entire ordinary pipeline (placement state, canSurvive, sounds, the
     * single server round-trip) then runs untouched, exactly once:
     *
     * <ul>
     * <li>thin covering (carpet, low snow) in a free cell — the hit points at
     * the layer's own cell; air is replaceable, so the block lands <em>in</em>
     * that cell, standing flush on the covering;</li>
     * <li>full-height covering (snow at 8) — the hit points at the cell
     * adjacent to the clicked face, like clicking a real solid block; if that
     * cell is occupied the click is swallowed rather than passed through.</li>
     * </ul>
     */
    public static PlacementRedirect placementRedirect(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !serverHasUnderlay()) {
            return PlacementRedirect.NONE;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) {
            return PlacementRedirect.NONE;
        }

        BlockHitResult layerHit = OrphanLayerPicker.targetHit();
        if (layerHit == null) {
            return realCoveringRedirect(minecraft, stack);
        }

        BlockPos cell = layerHit.getBlockPos();
        boolean solid = OrphanLayerPicker.targetSolid();

        // A covering item never turns into its real block inside a cell that
        // already has a layer (e.g. snow at max stack) — that would double up.
        // A full-height layer still swallows the click like a real block.
        if (UnderlayRules.coveringFor(stack) != null
                && UnderlayLayers.getLayer(minecraft.level, cell).isPresent()) {
            return solid ? PlacementRedirect.SWALLOW : PlacementRedirect.NONE;
        }

        if (solid) {
            // Legacy full stacks (built before materialization existed) become
            // real blocks on first click; the next click then works vanilla.
            if (minecraft.level.getBlockState(cell).isAir()) {
                return PlacementRedirect.materialize(cell);
            }
            BlockPos placeCell = cell.relative(layerHit.getDirection());
            BlockState neighbour = minecraft.level.getBlockState(placeCell);
            if (!neighbour.isAir() && !neighbour.canBeReplaced()) {
                return PlacementRedirect.SWALLOW;
            }
            return PlacementRedirect.to(new BlockHitResult(
                    layerHit.getLocation(), layerHit.getDirection(), placeCell, false));
        }

        BlockState occupant = minecraft.level.getBlockState(cell);
        if (!occupant.isAir() && !occupant.canBeReplaced()) {
            return PlacementRedirect.NONE; // hosted cell: nothing can fit anyway
        }
        return PlacementRedirect.to(layerHit);
    }

    /**
     * No visual layer under the crosshair — but the click may be aimed at a
     * <em>real</em> covering block (a placed carpet, real snow 1–7), directly
     * or via the face next to it. Vanilla would put the new block one cell
     * above such a covering, floating; absorbing turns the covering into an
     * underlay layer and lets the block take its cell instead.
     */
    private static PlacementRedirect realCoveringRedirect(Minecraft minecraft, ItemStack stack) {
        if (UnderlayRules.coveringFor(stack) != null) {
            return PlacementRedirect.NONE; // covering-on-covering stays vanilla (snow stacks etc.)
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return PlacementRedirect.NONE;
        }
        BlockPos clicked = hit.getBlockPos();
        if (UnderlayRules.absorbableLayer(minecraft.level.getBlockState(clicked)) != null) {
            return PlacementRedirect.absorb(clicked);
        }
        BlockPos relative = clicked.relative(hit.getDirection());
        if (UnderlayRules.absorbableLayer(minecraft.level.getBlockState(relative)) != null) {
            return PlacementRedirect.absorb(relative);
        }
        return PlacementRedirect.NONE;
    }

    /** Debounced senders for the absorb/materialize requests. */
    public static void sendAbsorbPlace(BlockPos pos) {
        sendDebounced(new ServerboundAbsorbPlacePayload(pos));
    }

    public static void sendMaterialize(BlockPos pos) {
        sendDebounced(new ServerboundMaterializeLayerPayload(pos));
    }

    private static void sendDebounced(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (debounced(lastPlaceSentAt, now)) {
            return;
        }
        lastPlaceSentAt = now;
        PacketDistributor.sendToServer(payload);
    }

    /**
     * Debounce for the held-key repeat. Guards against world switches where
     * game time can jump backwards (a negative diff must not lock us out).
     */
    private static boolean debounced(long lastSentAt, long now) {
        return lastSentAt >= 0 && now >= lastSentAt && now - lastSentAt < 4;
    }

    private ClientProxy() {}
}
