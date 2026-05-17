package com.coderaiderscdr.ghostofyou.item;

import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.coderaiderscdr.ghostofyou.entity.GhostInteraction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * Ghost Banisher — hold right-click on a Ghost Entity for 3 seconds to
 * permanently remove it. Spawns portal particles and plays sound during channeling.
 *
 * <p>Durability: 50 uses. Repair material: amethyst shard (via Forge tag).
 */
public class GhostBanisherItem extends Item {

    /** Channeling duration in ticks (3 seconds at 20 TPS). */
    private static final int USE_DURATION = 60;

    public GhostBanisherItem(Properties props) {
        super(props);
    }

    // ------------------------------------------------------------------
    // Use mechanics
    // ------------------------------------------------------------------

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /**
     * Only activate use if a ghost entity is being channeled server-side.
     * Right-clicking on air/blocks does nothing.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Use only starts via GhostEntity.mobInteract → GhostInteraction.startBanishing
        // which calls player.startUsingItem() directly. Return PASS here so that
        // clicking on air/blocks is a no-op.
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /**
     * HOT PATH — called every tick while the player holds right-click.
     * Spawns particles around the target ghost and plays the looping ambient sound.
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;

        UUID targetId = GhostInteraction.getTargetGhostId(player.getUUID());
        if (targetId == null) {
            // Banishing was cancelled externally
            player.stopUsingItem();
            return;
        }

        Entity target = sl.getEntity(targetId);
        if (!(target instanceof GhostEntity ghost) || !ghost.isAlive()) {
            player.stopUsingItem();
            GhostInteraction.cancelBanishing(player.getUUID());
            return;
        }

        // Spawn portal particles around the ghost every 4 ticks
        if (remainingUseTicks % 4 == 0) {
            double x = ghost.getX(), y = ghost.getY() + 1.0, z = ghost.getZ();
            for (int i = 0; i < 5; i++) {
                double ox = (sl.random.nextDouble() - 0.5) * 0.8;
                double oy = sl.random.nextDouble() * 1.5;
                double oz = (sl.random.nextDouble() - 0.5) * 0.8;
                sl.sendParticles(ParticleTypes.PORTAL, x + ox, y + oy, z + oz, 1, 0, 0, 0, 0.05);
            }
        }

        // Play sound every 20 ticks
        if (remainingUseTicks % 20 == 0) {
            sl.playSound(null, ghost.blockPosition(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.AMBIENT, 0.4f, 1.0f);
        }
    }

    /**
     * Called when the full 60-tick duration completes.
     * Banishes the ghost and damages the item.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !level.isClientSide()) {
            GhostInteraction.completeBanishing(player, stack);
        }
        return stack;
    }

    /**
     * Called when the player releases right-click before the duration is up.
     * Cancels the banishing.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (entity instanceof Player player && !level.isClientSide()) {
            GhostInteraction.cancelBanishing(player.getUUID());
            player.displayClientMessage(
                    Component.translatable("ghostofyou.banish.cancelled"), true);
        }
    }

    // ------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------

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
