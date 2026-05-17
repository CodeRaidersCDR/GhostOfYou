package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers all entity types for Ghost of You.
 * Attribute registration is handled via {@link EntityAttributeCreationEvent}.
 */
@Mod.EventBusSubscriber(modid = GhostOfYou.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GhostOfYou.MOD_ID);

    /** The ghost entity — a replaying phantom of a fallen player. */
    public static final RegistryObject<EntityType<GhostEntity>> GHOST =
            ENTITY_TYPES.register("ghost", () ->
                    EntityType.Builder.<GhostEntity>of(GhostEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)        // same hitbox as a player
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build(new ResourceLocation(GhostOfYou.MOD_ID, "ghost").toString()));

    /**
     * Soul Crystal — a glowing orb at the player's death location that serves
     * as the primary visual indicator and Ghost Banisher interaction point.
     */
    public static final RegistryObject<EntityType<SoulCrystalEntity>> SOUL_CRYSTAL =
            ENTITY_TYPES.register("soul_crystal", () ->
                    EntityType.Builder.<SoulCrystalEntity>of(SoulCrystalEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(8)
                            .updateInterval(5)
                            .build(new ResourceLocation(GhostOfYou.MOD_ID, "soul_crystal").toString()));

    // ------------------------------------------------------------------
    // Attribute registration
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(GHOST.get(), GhostEntity.createAttributes().build());
    }
}
