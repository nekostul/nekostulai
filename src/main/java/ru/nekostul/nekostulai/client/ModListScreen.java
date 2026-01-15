package ru.nekostul.nekostulai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModListScreen extends Screen {

    private final Screen parent;

    public ModListScreen(Screen parent) {
        super(Component.literal("Installed Mods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // кнопка "Назад"
        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), b -> {
                    Minecraft.getInstance().setScreen(parent);
                }).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build()
        );
    }
}