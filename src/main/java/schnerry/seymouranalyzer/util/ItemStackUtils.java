package schnerry.seymouranalyzer.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

public class ItemStackUtils {

    public static String getOrCreateItemUUID(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag nbt = customData.copyTag();

        if (nbt.contains("uuid")) {
            return nbt.getString("uuid").orElse(null);
        }

        return null;
    }

    public static String extractHex(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag nbt = customData.copyTag();

        if (nbt.contains("color")) {
            String colorStr = nbt.getString("color").orElse("");
            if (colorStr.contains(":")) {
                return ColorMath.rgbStringToHex(colorStr);
            }
        }

        DyedItemColor dyedColor = stack.getOrDefault(DataComponents.DYED_COLOR, null);
        if (dyedColor == null) {
            return null;
        }

        int rgb = dyedColor.rgb();
        return String.format("%02X%02X%02X", (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}

