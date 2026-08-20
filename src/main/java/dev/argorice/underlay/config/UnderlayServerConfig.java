package dev.argorice.underlay.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config — affects every player, synced to clients by NeoForge. */
public final class UnderlayServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALLOW_UNDERLAY = BUILDER
            .comment("Master switch. When false, layers can not be placed or removed (existing ones stay).")
            .define("allowUnderlay", true);

    public static final ModConfigSpec.BooleanValue DROP_LAYER_ON_BREAK = BUILDER
            .comment("Drop the covering item when a layer is removed by hand.")
            .define("dropLayerOnBreak", true);

    public static final ModConfigSpec.BooleanValue SNOW_LAYER_MELTS = BUILDER
            .comment("Whether decorative snow layers melt in warm biomes or under bright block light.")
            .define("snowLayerMelts", false);

    public static final ModConfigSpec.BooleanValue LAYER_COLLISION = BUILDER
            .comment("Layers contribute their covering's real collision: carpets give the vanilla 1/16",
                    "micro-step, stacked snow gives the vanilla stepped heights. Synced to clients.")
            .define("layerCollision", true);

    public static final ModConfigSpec.BooleanValue ALLOW_NON_FULL_BLOCKS = BUILDER
            .comment("Treat every non-full block as a layer host by default (on top of the allows_underlay tag).",
                    "A block qualifies when the covering would actually be visible, i.e. the bottom slice of the cell",
                    "is not fully covered by the block's collision shape. denies_underlay still wins.")
            .define("allowNonFullBlocks", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean allowUnderlay() {
        return SPEC.isLoaded() && ALLOW_UNDERLAY.get();
    }

    public static boolean dropLayerOnBreak() {
        return !SPEC.isLoaded() || DROP_LAYER_ON_BREAK.get();
    }

    public static boolean snowLayerMelts() {
        return SPEC.isLoaded() && SNOW_LAYER_MELTS.get();
    }

    public static boolean allowNonFullBlocks() {
        return !SPEC.isLoaded() || ALLOW_NON_FULL_BLOCKS.get();
    }

    public static boolean layerCollision() {
        return !SPEC.isLoaded() || LAYER_COLLISION.get();
    }

    private UnderlayServerConfig() {}
}
