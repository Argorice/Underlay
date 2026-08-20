package dev.argorice.underlay.network;

import dev.argorice.underlay.Underlay;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Underlay.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class UnderlayNetworking {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // optional() — a vanilla client may join a modded server (it just won't
        // see the layers), and the mod can run client-only on vanilla servers.
        PayloadRegistrar registrar = event.registrar("2").optional();

        registrar.playToClient(ClientboundSetLayerPayload.TYPE,
                ClientboundSetLayerPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSetLayer);

        registrar.playToClient(ClientboundChunkLayersPayload.TYPE,
                ClientboundChunkLayersPayload.STREAM_CODEC,
                ClientPayloadHandler::handleChunkLayers);

        registrar.playToClient(OverridesPayloads.Clientbound.TYPE,
                OverridesPayloads.Clientbound.STREAM_CODEC,
                ClientPayloadHandler::handleOverrides);

        registrar.playToServer(OverridesPayloads.Serverbound.TYPE,
                OverridesPayloads.Serverbound.STREAM_CODEC,
                ServerPayloadHandler::handleOverridesUpdate);

        registrar.playToServer(ServerboundRemoveLayerPayload.TYPE,
                ServerboundRemoveLayerPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRemoveLayer);

        registrar.playToServer(ServerboundPlaceLayerPayload.TYPE,
                ServerboundPlaceLayerPayload.STREAM_CODEC,
                ServerPayloadHandler::handlePlaceLayer);

        registrar.playToServer(ServerboundAbsorbPlacePayload.TYPE,
                ServerboundAbsorbPlacePayload.STREAM_CODEC,
                ServerPayloadHandler::handleAbsorbPlace);

        registrar.playToServer(ServerboundMaterializeLayerPayload.TYPE,
                ServerboundMaterializeLayerPayload.STREAM_CODEC,
                ServerPayloadHandler::handleMaterializeLayer);
    }

    private UnderlayNetworking() {}
}
