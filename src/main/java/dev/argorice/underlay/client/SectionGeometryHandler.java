package dev.argorice.underlay.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.argorice.underlay.Underlay;
import dev.argorice.underlay.config.UnderlayClientConfig;
import dev.argorice.underlay.core.UnderlayAttachments;
import dev.argorice.underlay.core.UnderlayData;
import dev.argorice.underlay.core.UnderlayRules;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The key decision of the whole mod: layers are baked into the static chunk
 * mesh via {@link AddSectionGeometryEvent}, not drawn every frame. Render cost
 * is zero; the only cost is paid when a section is rebuilt.
 *
 * <p>The event fires on the main thread — that is where we snapshot the chunk
 * attachment into a plain list. The renderer we register is then executed on a
 * meshing worker thread and touches only that snapshot plus the thread-safe
 * rendering context.
 */
@EventBusSubscriber(modid = Underlay.MOD_ID, value = Dist.CLIENT)
public final class SectionGeometryHandler {

    private record LayerToRender(BlockPos pos, BlockState state) {}

    @SubscribeEvent
    public static void onAddSectionGeometry(AddSectionGeometryEvent event) {
        Level level = event.getLevel();
        BlockPos origin = event.getSectionOrigin();

        int limit = UnderlayClientConfig.renderDistance();
        if (limit > 0 && !withinDistance(origin, limit)) {
            return;
        }

        LevelChunk chunk = level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(origin.getX()),
                SectionPos.blockToSectionCoord(origin.getZ()));
        if (chunk == null) {
            return;
        }
        UnderlayData data = chunk.getExistingDataOrNull(UnderlayAttachments.LAYERS.get());
        if (data == null || data.isEmpty()) {
            return;
        }

        int minY = origin.getY();
        ChunkPos chunkPos = chunk.getPos();
        List<LayerToRender> toRender = new ArrayList<>();
        for (Int2ObjectMap.Entry<UnderlayData.Layer> entry : data.view().int2ObjectEntrySet()) {
            BlockPos pos = UnderlayData.unpack(chunkPos, entry.getIntKey());
            if (pos.getY() < minY || pos.getY() >= minY + 16) {
                continue;
            }
            BlockState state = UnderlayRules.coveringState(entry.getValue().block(), entry.getValue().amount());
            if (state != null) {
                toRender.add(new LayerToRender(pos, state));
            }
        }
        if (toRender.isEmpty()) {
            return;
        }

        boolean biomeTint = UnderlayClientConfig.biomeTint();
        event.addRenderer(context -> renderLayers(context, origin, toRender, biomeTint));
    }

    private static boolean withinDistance(BlockPos origin, int limitChunks) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        int camChunkX = SectionPos.blockToSectionCoord(camera.getBlockPosition().getX());
        int camChunkZ = SectionPos.blockToSectionCoord(camera.getBlockPosition().getZ());
        int dx = Math.abs(SectionPos.blockToSectionCoord(origin.getX()) - camChunkX);
        int dz = Math.abs(SectionPos.blockToSectionCoord(origin.getZ()) - camChunkZ);
        return Math.max(dx, dz) <= limitChunks;
    }

    /** Runs on a chunk-meshing worker thread. */
    private static void renderLayers(AddSectionGeometryEvent.SectionRenderingContext context,
            BlockPos origin, List<LayerToRender> layers, boolean biomeTint) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        BlockAndTintGetter region = biomeTint ? context.getRegion() : new NoTintRegion(context.getRegion());
        PoseStack poseStack = context.getPoseStack();

        for (LayerToRender layer : layers) {
            BlockPos pos = layer.pos();
            BlockState state = layer.state();
            var model = dispatcher.getBlockModel(state);
            ModelData modelData = model.getModelData(region, pos, state, ModelData.EMPTY);

            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() - origin.getX(),
                    pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ());

            random.setSeed(state.getSeed(pos));
            for (RenderType renderType : model.getRenderTypes(state, random, modelData)) {
                VertexConsumer buffer = context.getOrCreateChunkBuffer(renderType);
                // checkSides=true gives free culling: the bottom face disappears
                // against a solid floor, side faces against solid neighbours.
                dispatcher.renderBatched(state, pos, region, poseStack, buffer,
                        true, random, modelData, renderType);
            }
            poseStack.popPose();
        }
    }

    private SectionGeometryHandler() {}
}
