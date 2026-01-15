package ru.nekostul.nekostulai;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import ru.nekostul.nekostulai.ai.alice.AliceConfig;

@Mod("nekostulai")
public class nekostulAI {

    public nekostulAI() {
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                nekostulAIConfig.COMMON_SPEC
        );

        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                AliceConfig.COMMON_SPEC,
                "nekostulai-alice.toml"
        );

        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("nekostulAI loaded");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AICommand.register(event.getDispatcher());
    }
}