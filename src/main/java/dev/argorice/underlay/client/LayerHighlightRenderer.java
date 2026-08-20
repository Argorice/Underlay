package dev.argorice.underlay.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.argorice.underlay.Underlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the vanilla-style selection outline around the orphaned layer the
 * player is currently looking at, so it reads as a targetable object.
 */
@EventBusSubscriber(modid = Underlay.MOD_ID, value = Dist.CLIENT)
public final class LayerHighlightRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        BlockPos pos = OrphanLayerPicker.target();
        AABB box = OrphanLayerPicker.targetBox();
        if (pos == null || box == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(RenderType.lines());

        AABB local = box.move(-pos.getX(), -pos.getY(), -pos.getZ()).inflate(0.002);
        Vec3 offset = Vec3.atLowerCornerOf(pos).subtract(camera);

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        // Same color vanilla uses for the block selection outline.
        LevelRenderer.renderLineBox(poseStack, consumer, local, 0.0F, 0.0F, 0.0F, 0.4F);
        poseStack.popPose();
        // Vanilla's own lines flush has already happened by AFTER_PARTICLES —
        // flush explicitly or the vertices leak into item/GUI rendering.
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private LayerHighlightRenderer() {}
}
