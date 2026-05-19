package com.coderaiderscdr.ghostofyou.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GhostEssenceItem extends Item {

    public GhostEssenceItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getHealth() < player.getMaxHealth()
                || !player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            player.heal(8.0f);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.getCooldowns().addCooldown(this, 600);
            if (!level.isClientSide()) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.5f);
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        String owner  = tag.getString("OwnerName");
        long   minutes = tag.getLong("LivedMinutes");
        String cause  = tag.getString("DeathCause");
        String killer = tag.getString("KillerName");

        if (!owner.isEmpty()) {
            tooltip.add(Component.translatable("item.ghostofyou.essence.owner", owner)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (minutes > 0) {
            tooltip.add(Component.translatable("item.ghostofyou.essence.lived", minutes)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!cause.isEmpty() && !cause.equals("unknown")) {
            Component causeText = Component.translatable(
                    "death.attack." + cause,
                    killer.isEmpty() ? "?" : killer);
            tooltip.add(Component.translatable("item.ghostofyou.essence.died", causeText)
                    .withStyle(ChatFormatting.DARK_RED));
        }

        if (owner.isEmpty() && tag.contains("ghostName")) {
            String legacyName = tag.getString("ghostName");
            long   legacyLife = tag.getLong("lifespanMinutes");
            tooltip.add(Component.translatable("item.ghostofyou.essence.owner", legacyName)
                    .withStyle(ChatFormatting.GRAY));
            if (legacyLife > 0) {
                tooltip.add(Component.translatable("item.ghostofyou.essence.lived", legacyLife)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && (!tag.getString("OwnerName").isEmpty()
                || !tag.getString("ghostName").isEmpty());
    }
}
