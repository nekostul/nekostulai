package ru.nekostul.nekostulai.client;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.nekostulai.nekostulAI;

import java.lang.reflect.Field;
import java.util.List;

@Mod.EventBusSubscriber(modid = nekostulAI.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CraftChoiceCleanup {

    private static final String AI_PREFIX = "[nekostulAI]";
    private static final String RECIPE_MARKER = "\u041f\u0440\u0435\u0434\u043c\u0435\u0442:";
    private static final String DISAMBIGUATION_MARKER =
            "\u0423\u0442\u043e\u0447\u043d\u0438, \u0447\u0442\u043e \u0438\u043c\u0435\u043d\u043d\u043e \u043d\u0443\u0436\u043d\u043e \u0441\u043a\u0440\u0430\u0444\u0442\u0438\u0442\u044c";

    private static Field allMessagesField;

    private CraftChoiceCleanup() {
    }

    public static void removeLatestCraftChoiceMessageNow() {
        removeLatestCraftChoiceMessage();
    }

    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }

        String text = event.getMessage().getString();
        if (text == null || !text.contains(AI_PREFIX) || !text.contains(RECIPE_MARKER)) {
            return;
        }

        removeLatestCraftChoiceMessage();
    }

    @SuppressWarnings("unchecked")
    private static void removeLatestCraftChoiceMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return;
        }

        ChatComponent chat = mc.gui.getChat();
        List<GuiMessage> allMessages = resolveAllMessages(chat);
        if (allMessages == null || allMessages.isEmpty()) {
            return;
        }

        for (int i = allMessages.size() - 1; i >= 0; i--) {
            GuiMessage guiMessage = allMessages.get(i);
            if (guiMessage == null || guiMessage.content() == null) {
                continue;
            }

            String text = guiMessage.content().getString();
            if (text != null && text.contains(DISAMBIGUATION_MARKER)) {
                allMessages.remove(i);
                chat.rescaleChat();
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<GuiMessage> resolveAllMessages(ChatComponent chat) {
        if (allMessagesField != null) {
            try {
                return (List<GuiMessage>) allMessagesField.get(chat);
            } catch (IllegalAccessException ignored) {
                allMessagesField = null;
            }
        }

        Field[] fields = ChatComponent.class.getDeclaredFields();
        for (Field field : fields) {
            if (!List.class.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(chat);
                if (!(value instanceof List<?> list)) {
                    continue;
                }

                if (!list.isEmpty() && list.get(0) instanceof GuiMessage) {
                    allMessagesField = field;
                    return (List<GuiMessage>) list;
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        for (Field field : fields) {
            if (!"allMessages".equals(field.getName())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(chat);
                if (value instanceof List<?> list) {
                    allMessagesField = field;
                    return (List<GuiMessage>) list;
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        return null;
    }
}
