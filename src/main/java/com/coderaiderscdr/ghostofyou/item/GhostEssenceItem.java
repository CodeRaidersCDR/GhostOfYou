package com.coderaiderscdr.ghostofyou.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ghost Essence — dropped when a ghost is banished with the Ghost Banisher.
 *
 * <p>NBT keys stored on the item stack:
 * <ul>
 *   <li>{@code ghostName}      — display name of the player (at death)</li>
 *   <li>{@code lifespanMinutes} — how many in-game minutes the ghost existed</li>
 *   <li>{@code deathCauseKey}  — death cause translation key (or "unknown")</li>
 * </ul>
 *
 * Used in crafting the {@link com.coderaiderscdr.ghostofyou.block.MemorialBlock}.
 */
public class GhostEssenceItem extends Item {

    public GhostEssenceItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        String name      = tag.getString("ghostName");
        long   lifespan  = tag.getLong("lifespanMinutes");
        String causeKey  = tag.getString("deathCauseKey");

        String cause = causeKey.isEmpty() || causeKey.equals("unknown")
                ? "unknown"
                : Component.translatable(causeKey).getString();

        if (!name.isEmpty()) {
            tooltip.add(Component.translatable("ghostofyou.ghost_essence.tooltip",
                            name, lifespan, cause)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Enchantment glint for ghosts with a recorded name
        CompoundTag tag = stack.getTag();
        return tag != null && !tag.getString("ghostName").isEmpty();
    }
}
