package dev.argorice.underlay;

import dev.argorice.underlay.config.UnderlayClientConfig;
import dev.argorice.underlay.config.UnderlayServerConfig;
import dev.argorice.underlay.core.UnderlayAttachments;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Underlay — a decorative bottom layer for any block cell.
 *
 * <p>Carpets continue under chests, snow sits under fences, parquet does not
 * tear under doors. Purely visual: no collision, no redstone, no mechanics.
 */
@Mod(Underlay.MOD_ID)
public final class Underlay {
    public static final String MOD_ID = "underlay";

    public Underlay(IEventBus modBus, ModContainer container) {
        UnderlayAttachments.REGISTER.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, UnderlayServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, UnderlayClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
