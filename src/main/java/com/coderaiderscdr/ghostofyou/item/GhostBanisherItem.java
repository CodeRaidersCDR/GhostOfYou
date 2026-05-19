package com.coderaiderscdr.ghostofyou.item;

import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.coderaiderscdr.ghostofyou.entity.GhostInteraction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class GhostBanisherItem extends Item {

    private static final int USE_DURATION = 60;

    public GhostBanisherItem(Properties props) {
        super(props);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {

        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;

        UUID targetId = GhostInteraction.getTargetGhostId(player.getUUID());
        if (targetId == null) {

            player.stopUsingItem();
            return;
        }

        Entity target = sl.getEntity(targetId);
        if (!(target instanceof GhostEntity ghost) || !ghost.isAlive()) {
            player.stopUsingItem();
            GhostInteraction.cancelBanishing(player.getUUID());
            return;
        }

        if (remainingUseTicks % 4 == 0) {
            double x = ghost.getX(), y = ghost.getY() + 1.0, z = ghost.getZ();
            for (int i = 0; i < 5; i++) {
                double ox = (sl.random.nextDouble() - 0.5) * 0.8;
                double oy = sl.random.nextDouble() * 1.5;
                double oz = (sl.random.nextDouble() - 0.5) * 0.8;
                sl.sendParticles(ParticleTypes.PORTAL, x + ox, y + oy, z + oz, 1, 0, 0, 0, 0.05);
            }
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !level.isClientSide()) {
            GhostInteraction.completeBanishing(player, stack);
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (entity instanceof Player player && !level.isClientSide()) {
            GhostInteraction.cancelBanishing(player.getUUID());
            player.displayClientMessage(
                    Component.translatable("ghostofyou.banish.cancelled"), true);

            player.getCooldowns().addCooldown(stack.getItem(), 100);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("ghostofyou.ghost_banisher.tooltip"));
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("ghostofyou.ghost_banisher.tooltip_shift")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
