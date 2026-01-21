package ru.nekostul.nekostulai.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

public class PlayerContext {

        public static @NotNull String buildContextText(ServerPlayer player) {
            if (player == null || player.level() == null) {
                return "Контекст игрока недоступен.";
            }

            StringBuilder text = new StringBuilder();

            ItemStack stack = player.getMainHandItem();
            if (!stack.isEmpty()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                text.append("Предмет в руке: ")
                        .append(stack.getHoverName().getString());
                if (id != null) {
                    text.append(" (").append(id).append(")");
                }
                text.append("\n");
            } else {
                text.append("В руке ничего нет.\n");
            }

            HitResult hit = player.pick(5.0D, 0.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
                BlockState state = player.level().getBlockState(bhr.getBlockPos());
                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

                text.append("Смотрит на блок: ")
                        .append(state.getBlock().getName().getString());

                if (blockId != null) {
                    text.append(" (").append(blockId).append(")");
                }
            } else {
                text.append("Не смотрит на блок.");
            }

            return text.toString();
        }
    }
