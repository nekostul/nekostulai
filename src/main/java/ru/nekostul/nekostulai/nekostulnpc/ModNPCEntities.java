package ru.nekostul.nekostulai.nekostulnpc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.nekostul.nekostulai.nekostulAI;

public class ModNPCEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, nekostulAI.MOD_ID);

    public static final RegistryObject<EntityType<nekostulNPC>> NEKOSTUL_NPC =
            ENTITIES.register("nekostul_npc",
                    () -> EntityType.Builder.of(nekostulNPC::new, MobCategory.MISC)
                            .sized(0.6F, 1.8F)
                            .build(new ResourceLocation(
                                    nekostulAI.MOD_ID,
                                    "nekostul_npc"
                            ).toString())
            );

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}