package dev.argorice.underlay.server;

/**
 * Historical note: placement/removal used to be handled here via
 * {@code PlayerInteractEvent.RightClickBlock}. That pipeline proved unreliable
 * with modded carrier blocks (their own use handlers could consume the click
 * first), so both interactions moved to client-side input interception plus
 * dedicated payloads:
 *
 * <ul>
 * <li>break a targeted layer — {@code ClientEvents.onInteractionKey} (attack) →
 * {@code ServerboundRemoveLayerPayload};</li>
 * <li>place a layer — {@code ClientEvents.onInteractionKey} (use, ordinary
 * block-placement semantics against the clicked face) →
 * {@code ServerboundPlaceLayerPayload}.</li>
 * </ul>
 *
 * Server-side validation and the region-protection probe live in
 * {@code ServerPayloadHandler}. This class intentionally subscribes to nothing.
 */
final class InteractionHandler {
    private InteractionHandler() {}
}
