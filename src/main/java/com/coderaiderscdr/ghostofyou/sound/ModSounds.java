package com.coderaiderscdr.ghostofyou.sound;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers all sound events for Ghost of You.
 *
 * <p>The actual sound files and randomization are defined in
 * {@code assets/ghostofyou/sounds.json}.  Minecraft automatically picks
 * one variant at random when the event is played.
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GhostOfYou.MOD_ID);

    /**
     * Exorcist incantation played when a player begins banishing a ghost.
     * Four variants registered in sounds.json; Minecraft picks one randomly.
     */
    public static final RegistryObject<SoundEvent> GHOST_EXORCIST =
            SOUNDS.register("ghost_exorcist",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(GhostOfYou.MOD_ID, "ghost_exorcist")));
}
