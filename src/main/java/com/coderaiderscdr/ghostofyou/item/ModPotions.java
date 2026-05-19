package com.coderaiderscdr.ghostofyou.item;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.function.Consumer;

public class ModPotions {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, GhostOfYou.MOD_ID);

    public static final RegistryObject<MobEffect> ETHEREAL_FORM_EFFECT =
            MOB_EFFECTS.register("ethereal_form", EtherealEffect::new);

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, GhostOfYou.MOD_ID);

    public static final RegistryObject<Potion> ETHEREAL_FORM =
            POTIONS.register("ethereal_form", () -> new Potion(
                    new MobEffectInstance(ETHEREAL_FORM_EFFECT.get(), 1200),
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1),
                    new MobEffectInstance(MobEffects.SLOW_FALLING, 1200)
            ));

    public static void registerBrewingRecipes() {
        BrewingRecipeRegistry.addRecipe(
                Ingredient.of(PotionUtils.setPotion(Items.POTION.getDefaultInstance(), Potions.AWKWARD)),
                Ingredient.of(ModItems.GHOST_ESSENCE.get()),
                PotionUtils.setPotion(Items.POTION.getDefaultInstance(), ETHEREAL_FORM.get())
        );
    }

    public static class EtherealEffect extends MobEffect {

        public EtherealEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x6B8FB0);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
            consumer.accept(new IClientMobEffectExtensions() {
                private static final ResourceLocation ICON = new ResourceLocation(
                        GhostOfYou.MOD_ID, "textures/item/ghost_essence_python_16x16.png");

                @Override
                public boolean renderInventoryIcon(MobEffectInstance effect,
                        EffectRenderingInventoryScreen<?> screen,
                        GuiGraphics guiGraphics, int x, int y, int blitOffset) {
                    guiGraphics.blit(ICON, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
                    return true;
                }

                @Override
                public boolean renderGuiIcon(MobEffectInstance effect,
                        Gui gui, GuiGraphics guiGraphics, int x, int y,
                        float z, float alpha) {
                    guiGraphics.blit(ICON, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
                    return true;
                }
            });
        }
    }
}
