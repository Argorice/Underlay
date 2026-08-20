package dev.argorice.underlay.network;

import dev.argorice.underlay.config.UnderlayServerConfig;
import dev.argorice.underlay.core.UnderlayData;
import dev.argorice.underlay.core.UnderlayLayers;
import dev.argorice.underlay.core.UnderlayOverrides;
import dev.argorice.underlay.core.UnderlayRules;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandler {

    /**
     * Fires a synthetic RightClickBlock purely so region-protection mods
     * (FTB Chunks, GriefPrevention ports, …) get their usual chance to veto —
     * our payloads bypass the vanilla interaction pipeline those mods listen to.
     *
     * @return true when a protection mod canceled the interaction
     */
    private static boolean protectionVetoed(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit).isCanceled();
    }

    private static boolean basicChecksFail(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!UnderlayServerConfig.allowUnderlay()) {
            return true;
        }
        if (!player.canInteractWithBlock(pos, 1.0)) {
            return true;
        }
        return !level.hasChunkAt(pos) || !level.mayInteract(player, pos);
    }

    /** Removal requested by the client's ray-pick: left click on a layer box. */
    public static void handleRemoveLayer(ServerboundRemoveLayerPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (basicChecksFail(player, level, pos) || protectionVetoed(player, pos)) {
            return;
        }
        UnderlayLayers.remove(level, pos).ifPresent(layer -> {
            if (UnderlayServerConfig.dropLayerOnBreak() && !player.getAbilities().instabuild) {
                UnderlayLayers.dropCovering(level, pos, layer);
            }
            Block block = BuiltInRegistries.BLOCK.getOptional(layer.block()).orElse(null);
            if (block != null) {
                level.playSound(null, pos, block.defaultBlockState().getSoundType().getBreakSound(),
                        SoundSource.BLOCKS, 1.0F, 0.9F);
            }
        });
    }

    /**
     * Placement requested by the client's input interception: a covering item
     * used against a face whose neighbouring cell holds a host block —
     * ordinary block-placement semantics, no modifier keys. Validated from
     * scratch here; the client is never trusted.
     */
    public static void handlePlaceLayer(ServerboundPlaceLayerPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (basicChecksFail(player, level, pos)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        Block covering = UnderlayRules.coveringFor(stack);
        if (covering == null) {
            return;
        }
        ResourceLocation coveringId = BuiltInRegistries.BLOCK.getKey(covering);
        var existing = UnderlayLayers.getLayer(level, pos);

        boolean placed;
        if (existing.isPresent()) {
            // Stacking: snow on snow, up to the block's own LAYERS maximum.
            // The cell may be orphaned (no carrier) — stacking is still fine.
            UnderlayData.Layer layer = existing.get();
            int max = UnderlayRules.maxAmount(covering);
            if (!layer.block().equals(coveringId) || layer.amount() >= max) {
                return;
            }
            if (protectionVetoed(player, pos)) {
                return;
            }
            UnderlayLayers.set(level, pos, layer.grown());
            // Reaching full height in a free cell turns the stack into a real block.
            UnderlayLayers.materializeIfFullOrphan(level, pos);
            placed = true;
        } else {
            if (!UnderlayRules.canHost(level.getBlockState(pos), false)) {
                return;
            }
            if (protectionVetoed(player, pos)) {
                return;
            }
            placed = UnderlayLayers.place(level, pos, UnderlayData.Layer.of(coveringId));
        }

        if (placed) {
            level.playSound(null, pos, covering.defaultBlockState().getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 0.9F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
    }

    /**
     * Absorb a real covering block (placed carpet, real snow 1–7) into an
     * underlay layer and put the player's main-hand block into the freed cell:
     * "the block stands on the carpet" instead of vanilla's floating-one-above.
     */
    public static void handleAbsorbPlace(ServerboundAbsorbPlacePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!UnderlayServerConfig.allowUnderlay()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (basicChecksFail(player, level, pos)) {
            return;
        }
        BlockState coveringState = level.getBlockState(pos);
        UnderlayData.Layer layer = UnderlayRules.absorbableLayer(coveringState);
        if (layer == null || UnderlayLayers.getLayer(level, pos).isPresent()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof net.minecraft.world.item.BlockItem) || UnderlayRules.coveringFor(held) != null) {
            return;
        }
        if (protectionVetoed(player, pos)) {
            return;
        }

        // 1) The real covering becomes a layer in the same cell.
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        UnderlayLayers.place(level, pos, layer);

        // 2) The held block goes into the freed cell through the ordinary use
        // pipeline (placement rules, sounds, item consumption, events).
        Vec3 hitLocation = new Vec3(pos.getX() + 0.5, pos.getY() + 0.0625, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitLocation, Direction.UP, pos, false);
        InteractionResult result = held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            // Revert the conversion — the click did nothing after all.
            UnderlayLayers.remove(level, pos);
            level.setBlock(pos, coveringState, 3);
        }
    }

    /** Lazy conversion of legacy full-height visual stacks into real blocks. */
    public static void handleMaterializeLayer(ServerboundMaterializeLayerPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!UnderlayServerConfig.allowUnderlay()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (basicChecksFail(player, level, pos)) {
            return;
        }
        UnderlayLayers.materializeIfFullOrphan(level, pos);
    }

    public static void handleOverridesUpdate(OverridesPayloads.Serverbound payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("underlay.config.no_permission"));
            return;
        }
        UnderlayOverrides overrides = UnderlayOverrides.server();
        overrides.setAll(payload.added(), payload.removed());
        overrides.save();

        // Broadcast the new authoritative state to every modded client.
        // Set.copyOf: the payload is encoded later on the network thread, while
        // another update may call setAll() on the live sets in the meantime.
        OverridesPayloads.Clientbound sync = new OverridesPayloads.Clientbound(
                Set.copyOf(overrides.added()), Set.copyOf(overrides.removed()));
        for (ServerPlayer online : player.serverLevel().getServer().getPlayerList().getPlayers()) {
            if (online.connection.hasChannel(OverridesPayloads.Clientbound.TYPE.id())) {
                PacketDistributor.sendToPlayer(online, sync);
            }
        }
        player.sendSystemMessage(Component.translatable("underlay.config.applied"));
    }

    private ServerPayloadHandler() {}
}
