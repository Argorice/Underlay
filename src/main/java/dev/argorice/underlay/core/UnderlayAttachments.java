package dev.argorice.underlay.core;

import dev.argorice.underlay.Underlay;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class UnderlayAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Underlay.MOD_ID);

    /**
     * The per-chunk layer map. Serialized to chunk NBT on save; empty maps are
     * skipped entirely so untouched chunks carry zero overhead.
     */
    public static final Supplier<AttachmentType<UnderlayData>> LAYERS = REGISTER.register("layers",
            () -> AttachmentType.builder(UnderlayData::new)
                    .serialize(UnderlayData.CODEC, data -> !data.isEmpty())
                    .build());

    private UnderlayAttachments() {}
}
