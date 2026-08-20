package dev.argorice.underlay.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config — purely cosmetic. */
public final class UnderlayClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue RENDER_DISTANCE = BUILDER
            .comment("Maximum distance (in chunks) at which layers are rendered. 0 = no limit (follow the game's render distance).")
            .defineInRange("renderDistance", 0, 0, 64);

    public static final ModConfigSpec.BooleanValue BIOME_TINT = BUILDER
            .comment("Tint grass-like and leaf-like coverings by biome. When false a neutral default color is used.")
            .define("biomeTint", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int renderDistance() {
        return SPEC.isLoaded() ? RENDER_DISTANCE.get() : 0;
    }

    public static boolean biomeTint() {
        return !SPEC.isLoaded() || BIOME_TINT.get();
    }

    private UnderlayClientConfig() {}
}
