package ru.nekostul.nekostulai.nekostulnpc;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.nekostulai.nekostulAI;

@Mod.EventBusSubscriber(
        modid = nekostulAI.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public class ModNPCAttributes {

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(
                ModNPCEntities.NEKOSTUL_NPC.get(),
                nekostulNPC.createAttributes().build()
        );
    }
}