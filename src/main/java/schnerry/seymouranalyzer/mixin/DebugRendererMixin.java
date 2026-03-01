package schnerry.seymouranalyzer.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import schnerry.seymouranalyzer.render.BlockHighlighter;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void onDebugRender(
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            float tickProgress,
            CallbackInfo ci
    ) {
        BlockHighlighter highlighter = BlockHighlighter.getInstance();
        if (!highlighter.hasHighlights()) return;

        PoseStack matrices = new PoseStack();
        Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(262_144)) {
            MultiBufferSource.BufferSource vertexConsumers = MultiBufferSource.immediate(allocator);
            highlighter.renderHighlights(matrices, vertexConsumers, cameraPos);
            vertexConsumers.endBatch();
        }
    }
}



