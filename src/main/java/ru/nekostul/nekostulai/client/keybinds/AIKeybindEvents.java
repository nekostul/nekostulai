package ru.nekostul.nekostulai.client.keybinds;

import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = "nekostulai",
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public class AIKeybindEvents {

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(AIKeybinds.OPEN_AI_MENU);
    }
}