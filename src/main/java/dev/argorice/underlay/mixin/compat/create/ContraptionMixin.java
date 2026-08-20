package dev.argorice.underlay.mixin.compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import dev.argorice.underlay.core.UnderlayData;
import dev.argorice.underlay.core.UnderlayLayers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create integration: layers travel with contraptions. On assembly the layers
 * under captured blocks are lifted out of the world into the contraption
 * (keyed by the same local coordinates Create uses); on disassembly they are
 * placed back through the contraption's {@link StructureTransform}, so
 * rotated contraptions restore their carpets in the right cells. The map is
 * persisted in the contraption's NBT and survives chunk unloads.
 *
 * <p>Applied only when Create is installed (see {@code UnderlayMixinPlugin}).
 * In-flight the layers are not rendered — they are data riding along, visible
 * again the moment the machine settles.
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionMixin {

    @Unique
    private Map<BlockPos, UnderlayData.Layer> underlay$layers;

    @Unique
    private Map<BlockPos, UnderlayData.Layer> underlay$layers() {
        if (underlay$layers == null) {
            underlay$layers = new HashMap<>();
        }
        return underlay$layers;
    }

    /** Assembly: lift layers out of the cells whose blocks join the contraption. */
    @Inject(method = "removeBlocksFromWorld", at = @At("HEAD"))
    private void underlay$captureLayers(Level world, BlockPos offset, CallbackInfo ci) {
        if (!(world instanceof ServerLevel serverLevel)) {
            return;
        }
        Contraption self = (Contraption) (Object) this;
        Map<BlockPos, UnderlayData.Layer> captured = underlay$layers();
        for (BlockPos localPos : self.getBlocks().keySet()) {
            BlockPos worldPos = localPos.offset(self.anchor).offset(offset);
            UnderlayLayers.remove(serverLevel, worldPos)
                    .ifPresent(layer -> captured.put(localPos.immutable(), layer));
        }
    }

    /** Disassembly: put the layers back, through the contraption's transform. */
    @Inject(method = "addBlocksToWorld", at = @At("TAIL"))
    private void underlay$restoreLayers(Level world, StructureTransform transform, CallbackInfo ci) {
        if (!(world instanceof ServerLevel serverLevel) || underlay$layers == null || underlay$layers.isEmpty()) {
            return;
        }
        underlay$layers.forEach((localPos, layer) -> {
            BlockPos targetPos = transform.apply(localPos);
            UnderlayLayers.place(serverLevel, targetPos, layer);
        });
        underlay$layers.clear();
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void underlay$writeNBT(HolderLookup.Provider registries, boolean spawnPacket,
            CallbackInfoReturnable<CompoundTag> cir) {
        if (underlay$layers == null || underlay$layers.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        underlay$layers.forEach((pos, layer) -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Block", layer.block().toString());
            tag.putByte("Amount", (byte) layer.amount());
            list.add(tag);
        });
        cir.getReturnValue().put("UnderlayLayers", list);
    }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void underlay$readNBT(Level world, CompoundTag nbt, boolean spawnData, CallbackInfo ci) {
        if (!nbt.contains("UnderlayLayers", Tag.TAG_LIST)) {
            return;
        }
        Map<BlockPos, UnderlayData.Layer> map = underlay$layers();
        map.clear();
        ListTag list = nbt.getList("UnderlayLayers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceLocation block = ResourceLocation.tryParse(tag.getString("Block"));
            if (block != null) {
                map.put(BlockPos.of(tag.getLong("Pos")), new UnderlayData.Layer(block, tag.getByte("Amount")));
            }
        }
    }
}
