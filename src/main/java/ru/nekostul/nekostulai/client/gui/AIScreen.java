package ru.nekostul.nekostulai.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;
import ru.nekostul.nekostulai.client.AIInfoHelper;

import java.util.ArrayList;
import java.util.List;

public class AIScreen extends Screen {

    public AIScreen() {
        super(Component.literal("AI"));
    }

    @Override
    protected void init() {
        super.init();

        int cx = this.width / 2;
        int cy = this.height / 2;

        int buttonWidth = 220;
        int buttonHeight = 20;
        int spacing = 6;

        int startY = cy - (buttonHeight + spacing) * 3;

        // --- Список модов ---
        this.addRenderableWidget(
                Button.builder(
                        Component.literal("📦 Список модов"),
                        b -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> mc.setScreen(new ModListScreen(mc.screen)));
                        }
                ).bounds(
                        cx - buttonWidth / 2,
                        startY,
                        buttonWidth,
                        buttonHeight
                ).build()
        );

        // --- Предмет / блок ---
        this.addRenderableWidget(
                Button.builder(
                        Component.literal("🧱 Предмет / блок"),
                        b -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null || mc.level == null) return;

                            List<String> parts = new ArrayList<>();

                            // ----- ПРЕДМЕТ -----
                            ItemStack stack = mc.player.getMainHandItem();
                            if (!stack.isEmpty()) {
                                ResourceLocation itemId =
                                        ForgeRegistries.ITEMS.getKey(stack.getItem());
                                if (itemId != null) {
                                    String ns = itemId.getNamespace();
                                    parts.add(
                                            ns.equals("minecraft")
                                                    ? "§fПредмет: §aванильный"
                                                    : "§fПредмет: §eиз мода §a" + ns
                                    );
                                }
                            } else {
                                parts.add("§cВ руке ничего нет, гений");
                            }

                            // ----- БЛОК (ТОЛЬКО ЕСЛИ НАВЁЛСЯ) -----
                            if (mc.hitResult != null
                                    && mc.hitResult.getType() == HitResult.Type.BLOCK
                                    && mc.hitResult instanceof BlockHitResult bhr) {

                                BlockState state =
                                        mc.level.getBlockState(bhr.getBlockPos());
                                ResourceLocation blockId =
                                        ForgeRegistries.BLOCKS.getKey(state.getBlock());

                                if (blockId != null) {
                                    String ns = blockId.getNamespace();
                                    parts.add(
                                            ns.equals("minecraft")
                                                    ? "§fБлок: §aванильный"
                                                    : "§fБлок: §eиз мода §a" + ns
                                    );
                                }
                            }

                            if (!parts.isEmpty()) {
                                mc.gui.setOverlayMessage(
                                        Component.literal(String.join(" §7|§r ", parts)),
                                        false
                                );
                            }

                            mc.setScreen(null);
                        }
                ).bounds(
                        cx - buttonWidth / 2,
                        startY + (buttonHeight + spacing),
                        buttonWidth,
                        buttonHeight
                ).build()
        );

        // --- Игрок ---
        this.addRenderableWidget(
                Button.builder(
                        Component.literal("👤 Игрок"),
                        b -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                AIInfoHelper.showPlayerInfo();
                                Minecraft.getInstance().setScreen(null);
                            }
                            mc.setScreen(null);
                        }
                ).bounds(
                        cx - buttonWidth / 2,
                        startY + (buttonHeight + spacing) * 2,
                        buttonWidth,
                        buttonHeight
                ).build()
        );

        // --- Мир ---
        this.addRenderableWidget(
                Button.builder(
                        Component.literal("🌍 Мир"),
                        b -> {
                            AIInfoHelper.showWorldInfo();
                            Minecraft.getInstance().setScreen(null);
                        }
                ).bounds(
                        cx - buttonWidth / 2,
                        startY + (buttonHeight + spacing) * 3,
                        buttonWidth,
                        buttonHeight
                ).build()
        );

        // --- Прокси ---
        this.addRenderableWidget(
                Button.builder(
                        Component.literal("🌐 Прокси"),
                        b -> {
                            AIInfoHelper.showProxyInfo();
                            Minecraft.getInstance().setScreen(null);
                        }
                ).bounds(
                        cx - buttonWidth / 2,
                        startY + (buttonHeight + spacing) * 4,
                        buttonWidth,
                        buttonHeight
                ).build()
        );

        // подъёбка
        this.addRenderableWidget(
                Button.builder(
                        Component.literal("🔥 Подъёбка"),
                        b -> {
                            AIInfoHelper.showRoast();
                            Minecraft.getInstance().setScreen(null);
                        }
                ).bounds(
                        cx - buttonWidth / 2,
                        startY + (buttonHeight + spacing) * 5,
                        buttonWidth,
                        buttonHeight
                ).build()
        );

        // --- Закрыть ---
        this.addRenderableWidget(
                Button.builder(
                        Component.literal("❌ Закрыть"),
                        b -> Minecraft.getInstance().setScreen(null)
                ).bounds(
                        cx - buttonWidth / 2,
                        startY + (buttonHeight + spacing) * 6,
                        buttonWidth,
                        buttonHeight
                ).build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        guiGraphics.fillGradient(
                0, 0, this.width, this.height,
                0xC0101010,
                0xC0101010
        );
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}