package schnerry.seymouranalyzer.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages and renders highlighted block positions in the world
 * Used for showing chest locations with matching armor pieces
 * Ported from ChatTriggers index.js
 */
public class BlockHighlighter {
    private static BlockHighlighter instance;
    private final List<BlockPos> highlightedBlocks = new ArrayList<>();

    private BlockHighlighter() {
        // Register world render event - BEFORE_DEBUG_RENDER to ensure visibility
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderHighlights);
    }

    public static BlockHighlighter getInstance() {
        if (instance == null) {
            instance = new BlockHighlighter();
        }
        return instance;
    }

    /**
     * Add a block position to highlight
     */
    public void addBlock(BlockPos pos) {
        if (!highlightedBlocks.contains(pos)) {
            highlightedBlocks.add(pos);
        }
    }

    /**
     * Add multiple block positions at once
     */
    public void addBlocks(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            addBlock(pos);
        }
    }

    /**
     * Clear all highlighted blocks
     */
    public void clearAll() {
        highlightedBlocks.clear();
    }

    /**
     * Get the number of highlighted blocks
     */
    public int getCount() {
        return highlightedBlocks.size();
    }

    /**
     * Get all highlighted block positions (read-only)
     */
    public List<BlockPos> getHighlightedBlocks() {
        return new ArrayList<>(highlightedBlocks);
    }

    /**
     * Check if a specific block is highlighted
     */
    public boolean isHighlighted(BlockPos pos) {
        return highlightedBlocks.contains(pos);
    }

    /**
     * Render block highlights in the world
     * Disables depth test to render through blocks
     */
    private void renderHighlights(WorldRenderContext context) {
        if (highlightedBlocks.isEmpty()) return;

        try {
            MatrixStack matrices = context.matrixStack();
            if (matrices == null) return;

            Vec3d camera = context.camera().getPos();
            var consumers = context.consumers();
            if (consumers == null) return;

            // Disable depth test so highlights render through blocks
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);

            // Green color for highlights (RGBA 0-1)
            float red = 0.0f;
            float green = 1.0f;
            float blue = 0.0f;
            float alpha = 0.7f;

            // Draw each highlighted block
            for (BlockPos pos : highlightedBlocks) {
                Box box = new Box(pos);
                net.minecraft.client.render.debug.DebugRenderer.drawBox(matrices, consumers, box, red, green, blue, alpha);
            }

            matrices.pop();

            // Re-enable depth test
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } catch (Exception e) {
            // Silent fail to avoid spam
        }
    }
}

