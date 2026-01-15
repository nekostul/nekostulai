package ru.nekostul.nekostulai.client.keybinds;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.nekostulai.client.gui.AIScreen;

@Mod.EventBusSubscriber(
        modid = "nekostulai",
        value = Dist.CLIENT
)
public class AIKeyHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {

        if (AIKeybinds.OPEN_AI_MENU.consumeClick()) {

            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new AIScreen());
            }
        }
    }
}