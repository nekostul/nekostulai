package ru.nekostul.nekostulai.client.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class AIKeybinds {

    public static final String CATEGORY = "key.categories.nekostulai";

    public static final KeyMapping OPEN_AI_MENU =
            new KeyMapping(
                    "key.nekostulai.open_ai", // название (для перевода)
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_X,          // КНОПКА X
                    CATEGORY
            );
}