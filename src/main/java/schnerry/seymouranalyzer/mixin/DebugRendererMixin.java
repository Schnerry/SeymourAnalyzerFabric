package schnerry.seymouranalyzer.mixin;

import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import schnerry.seymouranalyzer.render.BlockHighlighter;

/**
 * DebugRendererMixin for 1.21.10
 *
 * WorldRenderEvents was removed in 1.21.9, so we use a direct mixin.
 * Injects into DebugRenderer.render to draw block highlights alongside debug rendering.
 */
@Mixin(net.minecraft.client.renderer.debug.DebugRenderer.class)
public class DebugRendererMixin {

    /**
     * Inject at the TAIL of DebugRenderer.render to draw our highlights
     * after vanilla debug rendering but in the same render pass.
     */
    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void onDebugRender(
            PoseStack poseStack,
            Frustum frustum,
            MultiBufferSource.BufferSource vertexConsumers,
            double cameraX,
            double cameraY,
            double cameraZ,
            boolean lateRender,
            CallbackInfo ci
    ) {
        // Only render during the late render pass (after translucent blocks)
        if (!lateRender) return;

        BlockHighlighter highlighter = BlockHighlighter.getInstance();
        if (!highlighter.hasHighlights()) return;

        Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
        highlighter.renderHighlights(poseStack, vertexConsumers, cameraPos);
    }
}



