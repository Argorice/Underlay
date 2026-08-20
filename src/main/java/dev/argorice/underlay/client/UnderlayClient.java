package dev.argorice.underlay.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.argorice.underlay.Underlay;
import dev.argorice.underlay.client.gui.UnderlayConfigScreen;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;

/** Client mod-bus initialization: key binding and the mod-list config screen. */
@EventBusSubscriber(modid = Underlay.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class UnderlayClient {

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.underlay.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            "key.categories.underlay");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(Underlay.MOD_ID).ifPresent(container ->
                container.registerExtensionPoint(IConfigScreenFactory.class,
                        (modContainer, parent) -> new UnderlayConfigScreen(parent)));
    }

    private UnderlayClient() {}
}
