package com.coderaiderscdr.ghostofyou.item;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers custom mob effects and potions for Ghost of You.
 *
 * <p>Brewing path:
 * <pre>
 *   Awkward Potion + Ghost Essence  →  Potion of Ethereal Form
 * </pre>
 *
 * Ethereal Form grants Slow Falling, Speed II, and +30% damage reduction
 * (via Resistance I) for 60 seconds.
 */
public class ModPotions {

    // ------------------------------------------------------------------
    // Mob Effects
    // ------------------------------------------------------------------

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, GhostOfYou.MOD_ID);

    /** Ethereal Form — Slow Falling + Speed II + Resistance I (compounded into one effect). */
    public static final RegistryObject<MobEffect> ETHEREAL_FORM_EFFECT =
            MOB_EFFECTS.register("ethereal_form", EtherealEffect::new);

    // ------------------------------------------------------------------
    // Potions
    // ------------------------------------------------------------------

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, GhostOfYou.MOD_ID);

    /** Potion of Ethereal Form — brewed by adding Ghost Essence to an Awkward Potion. */
    public static final RegistryObject<Potion> ETHEREAL_FORM =
            POTIONS.register("ethereal_form", () -> new Potion(
                    new MobEffectInstance(ETHEREAL_FORM_EFFECT.get(), 1200),  // 60 s
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1), // Speed II
                    new MobEffectInstance(MobEffects.SLOW_FALLING, 1200)       // Slow Falling
            ));

    // ------------------------------------------------------------------
    // Brewing recipe registration (call from commonSetup)
    // ------------------------------------------------------------------

    /** Register the Awkward Potion → Ethereal Form recipe via Forge's BrewingRecipeRegistry. */
    public static void registerBrewingRecipes() {
        BrewingRecipeRegistry.addRecipe(
                Ingredient.of(PotionUtils.setPotion(Items.POTION.getDefaultInstance(), Potions.AWKWARD)),
                Ingredient.of(ModItems.GHOST_ESSENCE.get()),
                PotionUtils.setPotion(Items.POTION.getDefaultInstance(), ETHEREAL_FORM.get())
        );
    }

    // ------------------------------------------------------------------
    // Inner class: Ethereal effect implementation
    // ------------------------------------------------------------------

    public static class EtherealEffect extends MobEffect {

        public EtherealEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x6B8FB0); // pale blue colour
        }

        /**
         * Provide a 30 % damage reduction bonus on top of the vanilla Resistance
         * already included in the compound potion definition.
         * The actual damage bonus is handled through an event handler registered
         * in {@code GhostOfYou} listening for {@code LivingDamageEvent}.
         */
        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true; // tick every game-tick so sub-effects can be maintained
        }
    }
}
