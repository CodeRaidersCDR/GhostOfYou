package com.coderaiderscdr.ghostofyou.sound;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GhostOfYou.MOD_ID);

    public static final RegistryObject<SoundEvent> GHOST_EXORCIST =
            SOUNDS.register("ghost_exorcist",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(GhostOfYou.MOD_ID, "ghost_exorcist")));
}
