package schnerry.seymouranalyzer.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import schnerry.seymouranalyzer.render.InfoBoxRenderer;
import schnerry.seymouranalyzer.render.ItemSlotHighlighter;

/**
 * Mixin to inject slot highlighting directly into the slot rendering process
 * This ensures we're using the exact same coordinate space as the actual slot rendering
 * Also tracks the focused slot for InfoBox rendering
 */
@Mixin(value = AbstractContainerScreen.class, priority = 1500)
public abstract class HandledScreenMixin {

    @Unique
    private static final boolean DEBUG = false;
    @Unique
    private static Object lastScreen = null;
    @Unique
    private static Slot lastHoveredSlot = null;
    @Unique
    private static ItemStack lastHoveredStack = ItemStack.EMPTY;

    @Shadow
    protected Slot hoveredSlot;

    /**
     * Inject before each slot is drawn to render our highlight behind the item
     * This runs in the exact coordinate space as the slot, so no offset calculations needed
     * ALSO track the hovered slot here since we KNOW it exists at this point
     */
    @Inject(
        method = "renderSlot",
        at = @At("HEAD")
    )
    private void onDrawSlot(GuiGraphics context, Slot slot, int x, int y, CallbackInfo ci) {
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        // Let the highlighter render the highlight for this slot
        ItemSlotHighlighter.getInstance().renderSlotHighlight(context, slot);

        // Track the slot being drawn if it matches the focused slot
        // This captures the data BEFORE any other mod can modify it
        if (this.hoveredSlot != null && this.hoveredSlot == slot) {
            lastHoveredSlot = slot;
            lastHoveredStack = stack.copy(); // Copy to preserve the data

            String itemName = stack.getHoverName().getString();
            if (DEBUG) {
                System.out.println("[Mixin] Captured hover in drawSlot: " + itemName);
            }

            // Update InfoBox immediately while we have valid data
            InfoBoxRenderer.getInstance().setHoveredItem(stack, itemName);
        }
    }

    /**
     * Inject at HEAD of render to capture focusedSlot EARLY before other mods can interfere
     * This runs BEFORE any other mod's TAIL injections
     */
    @Inject(
        method = "render",
        at = @At("HEAD")
    )
    private void onRenderHead(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        // Reset logging flag when screen changes
        if (lastScreen != screen) {
            if (DEBUG) {
                System.out.println("\n[Mixin] ========== NEW SCREEN OPENED ==========");
                System.out.println("[Mixin] Screen class: " + screen.getClass().getName());
                System.out.println("[Mixin] Screen instance: " + System.identityHashCode(screen));
            }
            lastScreen = screen;
            lastHoveredSlot = null;
            lastHoveredStack = ItemStack.EMPTY;
        }

        // Early capture of focusedSlot before any other mod can modify it
        if (this.hoveredSlot != null && !this.hoveredSlot.getItem().isEmpty()) {
            ItemStack stack = this.hoveredSlot.getItem();
            String itemName = stack.getHoverName().getString();

            if (DEBUG) {
                System.out.println("[Mixin] Early capture in render HEAD:");
                System.out.println("[Mixin]   Slot #" + this.hoveredSlot.index);
                System.out.println("[Mixin]   Item: " + itemName);
            }

            lastHoveredSlot = this.hoveredSlot;
            lastHoveredStack = stack.copy();

            // Update InfoBox with early captured data
            InfoBoxRenderer.getInstance().setHoveredItem(stack, itemName);
        } else if (this.hoveredSlot == null) {
            // Only clear if we truly have no focused slot
            if (lastHoveredSlot != null) {
                if (DEBUG) {
                    System.out.println("[Mixin] Clearing hover - no focused slot");
                }
                lastHoveredSlot = null;
                lastHoveredStack = ItemStack.EMPTY;
            }
            InfoBoxRenderer.getInstance().clearHoveredItem();
        }
    }

    /**
     * Also inject at TAIL as a backup to catch any late updates
     * Uses our cached data if focusedSlot was cleared by another mod
     */
    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void onRenderTail(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // If focusedSlot exists and is still valid, re-update with current data
        if (this.hoveredSlot != null && !this.hoveredSlot.getItem().isEmpty()) {
            ItemStack stack = this.hoveredSlot.getItem();
            String itemName = stack.getHoverName().getString();
            InfoBoxRenderer.getInstance().setHoveredItem(stack, itemName);
        } else if (lastHoveredSlot != null && !lastHoveredStack.isEmpty()) {
            // Use cached data if focusedSlot was cleared but we still have valid cached data
            if (DEBUG) {
                System.out.println("[Mixin] Using cached hover data in TAIL");
            }
            InfoBoxRenderer.getInstance().setHoveredItem(lastHoveredStack, lastHoveredStack.getHoverName().getString());
        }
    }

    /**
     * Additional backup: Capture focusedSlot when tooltip is about to render
     * This is called independently and gives us another chance to capture the slot
     */
    @Inject(
        method = "renderTooltip",
        at = @At("HEAD")
    )
    private void onDrawTooltipHead(GuiGraphics context, int x, int y, CallbackInfo ci) {
        // Capture focused slot data when tooltip rendering starts
        if (this.hoveredSlot != null && !this.hoveredSlot.getItem().isEmpty()) {
            ItemStack stack = this.hoveredSlot.getItem();

            // Only update if changed to reduce overhead
            if (lastHoveredSlot != this.hoveredSlot) {
                lastHoveredSlot = this.hoveredSlot;
                lastHoveredStack = stack.copy();
                InfoBoxRenderer.getInstance().setHoveredItem(stack, stack.getHoverName().getString());
            }
        }
    }
}

