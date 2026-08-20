package dev.argorice.underlay.client;

import dev.argorice.underlay.Underlay;
import dev.argorice.underlay.client.gui.UnderlayConfigScreen;
import dev.argorice.underlay.core.UnderlayOverrides;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = Underlay.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        OrphanLayerPicker.update(minecraft);
        while (UnderlayClient.OPEN_CONFIG.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new UnderlayConfigScreen(null));
            }
        }
    }

    /**
     * The whole interaction model lives here, at input level — <em>before</em>
     * {@code startAttack}/{@code startUseItem} — so vanilla and modded blocks
     * never get a chance to consume the click first:
     *
     * <ul>
     * <li><b>Attack (LMB)</b> while the picker targets a layer box → break it
     * like an ordinary carpet, whatever is in hand.</li>
     * <li><b>Use (RMB)</b> with a covering item, clicked on a face whose
     * neighbouring cell holds a block that can host a layer (the free spot of
     * the floor an anvil stands on) → place the layer there, exactly like
     * placing a normal block — no modifier keys. Clicking a covering onto its
     * own existing layer stacks it (snow grows 1→8). Vanilla could never
     * place anything into that occupied cell anyway, so nothing is stolen
     * from it, and the carrier's own use handler is bypassed entirely.</li>
     * <li><b>Use (RMB)</b> with any other placeable block while aiming at a
     * layer → the block is placed into the layer's cell, standing on the
     * covering, like building on a real carpet.</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        if (event.isAttack()) {
            if (OrphanLayerPicker.target() != null && ClientProxy.tryRemoveTargetedLayer()) {
                event.setSwingHand(true);
                event.setCanceled(true);
            }
            return;
        }

        if (event.isUseItem()) {
            if (ClientProxy.tryPlaceLayerAtCrosshair(player)) {
                event.setSwingHand(true);
                event.setCanceled(true);
                return;
            }
            ClientProxy.PlacementRedirect redirect = ClientProxy.placementRedirect(player);
            switch (redirect.kind()) {
                case REDIRECT ->
                    // Hand vanilla a corrected crosshair hit and step aside:
                    // the ordinary use pipeline runs exactly once against it.
                    // The next frame's pick() recomputes hitResult anyway.
                    minecraft.hitResult = redirect.hit();
                case ABSORB -> {
                    ClientProxy.sendAbsorbPlace(redirect.pos());
                    event.setSwingHand(true);
                    event.setCanceled(true);
                }
                case MATERIALIZE -> {
                    ClientProxy.sendMaterialize(redirect.pos());
                    event.setSwingHand(false);
                    event.setCanceled(true);
                }
                case SWALLOW -> {
                    event.setSwingHand(false);
                    event.setCanceled(true);
                }
                case NONE -> {}
            }
        }
    }

    /** Drop the server-synced override copy when leaving a world/server. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        UnderlayOverrides.setClientView(Set.of(), Set.of());
    }

    private ClientEvents() {}
}
