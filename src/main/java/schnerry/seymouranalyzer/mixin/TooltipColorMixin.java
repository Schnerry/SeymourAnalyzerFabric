package schnerry.seymouranalyzer.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import schnerry.seymouranalyzer.config.ClothConfig;

import java.util.List;
import java.util.Optional;

/**
 * Mixin to force-refresh hex color styles at render time, bypassing any tooltip caching
 * from other mods that might cache the rendered tooltip texture/components.
 */
@Mixin(value = GuiGraphics.class, priority = 2000)
public class TooltipColorMixin {

    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V", at = @At("HEAD"))
    private void seymour$refreshHexLineColors(Font font, List<Component> tooltipLines, Optional<TooltipComponent> visual, int mouseX, int mouseY, Identifier id, CallbackInfo ci) {
        try {
            for (int i = 0; i < tooltipLines.size(); i++) {
                Component line = tooltipLines.get(i);
                String text = line.getString();

                // Find our hex line by content pattern
                if (text.startsWith("Hex: #") && text.length() >= 12) {
                    Component rebuilt = seymour$rebuildHexLine(text);
                    if (rebuilt != null) {
                        tooltipLines.set(i, rebuilt);
                    }
                }
            }
        } catch (UnsupportedOperationException ignored) {
            // List is unmodifiable - nothing we can do
        }
    }

    @Unique
    private static Component seymour$rebuildHexLine(String fullText) {
        boolean useColor = ClothConfig.getInstance().isColoredHexText();

        // Extract hex code (6 chars after first #)
        int hashIdx = fullText.indexOf('#');
        if (hashIdx < 0 || hashIdx + 7 > fullText.length()) return null;
        String hex = fullText.substring(hashIdx + 1, hashIdx + 7);

        // Validate hex
        if (!hex.matches("[0-9A-Fa-f]{6}")) return null;

        int rgb;
        try {
            rgb = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            rgb = 0xFFFFFF;
        }

        MutableComponent label = Component.literal("Hex: ");
        label.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xA8A8A8)).withItalic(false));

        MutableComponent value = Component.literal("#" + hex);
        value.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(useColor ? rgb : 0xFFFFFF)).withItalic(false));

        MutableComponent result = Component.empty().append(label).append(value);

        // Preserve dyed warning if present
        if (fullText.contains("[DYED")) {
            int dyedIdx = fullText.indexOf(" [DYED");
            if (dyedIdx >= 0) {
                MutableComponent warn = Component.literal(fullText.substring(dyedIdx));
                warn.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)).withItalic(false).withBold(true));
                result.append(warn);
            }
        }

        return result;
    }
}
