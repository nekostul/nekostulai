package ru.nekostul.nekostulai;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import ru.nekostul.nekostulai.ai.alice.AliceConfig;
import ru.nekostul.nekostulai.bugreport.BugReportCooldown;

@Mod("nekostulai")
public class nekostulAI {

    public static final String MOD_ID = "nekostulai";

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
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .addListener(this::onCommonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AICommand.register(event.getDispatcher());
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        BugReportCooldown.load();
            }
        }