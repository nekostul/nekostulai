package ru.nekostul.nekostulai.ai.nekostuloffline;

import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class CraftHelper {

    private static final String[] CRAFT_PREFIXES = {
            "craft ",
            "recipe ",
            "\u043a\u0430\u043a \u0441\u043a\u0440\u0430\u0444\u0442\u0438\u0442\u044c ",
            "\u043a\u0430\u043a \u0441\u0434\u0435\u043b\u0430\u0442\u044c ",
            "\u0441\u043a\u0440\u0430\u0444\u0442\u0438\u0442\u044c ",
            "\u043a\u0440\u0430\u0444\u0442 ",
            "\u0440\u0435\u0446\u0435\u043f\u0442 "
    };

    private static final String[] CRAFT_HINTS = {
            "craft",
            "recipe",
            "\u0441\u043a\u0440\u0430\u0444\u0442",
            "\u043a\u0440\u0430\u0444\u0442",
            "\u0440\u0435\u0446\u0435\u043f\u0442"
    };
    private static final String[] CRAFT_EXACT_PREFIXES = {
            "craft exact ",
            "recipe exact ",
            "\u043a\u0440\u0430\u0444\u0442 \u0442\u043e\u0447\u043d\u043e ",
            "\u0440\u0435\u0446\u0435\u043f\u0442 \u0442\u043e\u0447\u043d\u043e "
    };

    private static final String[] LEADING_FILLERS = {
            "\u043d\u0430 ",
            "\u0434\u043b\u044f ",
            "\u0438\u0437 ",
            "the ",
            "a ",
            "an "
    };
    private static final int MAX_MATCHES = 6;
    private static final String CRAFT_EXACT_COMMAND_PREFIX = "/ai craft exact ";

    // Главная точка входа
    public static Component handleCraftRequest(String rawName, @Nullable Level level) {

        if (rawName == null || rawName.isBlank())
            return Component.literal("Укажи предмет.");

        if (level == null)
            return Component.literal("Мир не загружен.");

        List<Item> matches = findMatchingItems(rawName);

        if (matches.isEmpty())
            return Component.literal("Предмет не найден.");

        if (matches.size() == 1)
            return Component.literal(getRecipeText(matches.get(0), level));

        return buildDisambiguationMessage(matches);
    }

    @Nullable
    public static Component buildCraftReplyIfRequested(String rawMessage, @Nullable Level level) {
        CraftRequest request = parseCraftRequest(rawMessage);
        if (request == null) {
            return null;
        }
        return request.exactMatch
                ? handleCraftRequestExact(request.itemName, level)
                : handleCraftRequest(request.itemName, level);
    }

    @Nullable
    public static String getRecipe(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }

        Level level = resolveRecipeLevel();
        if (level == null) {
            return null;
        }

        List<Item> matches = findMatchingItems(rawName);
        if (matches.size() != 1) {
            return null;
        }

        return getRecipeText(matches.get(0), level);
    }

    private static Component handleCraftRequestExact(String rawName, @Nullable Level level) {
        if (rawName == null || rawName.isBlank()) {
            return Component.literal("\u0423\u043a\u0430\u0436\u0438 \u043f\u0440\u0435\u0434\u043c\u0435\u0442.");
        }

        if (level == null) {
            return Component.literal("\u041c\u0438\u0440 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d.");
        }

        List<Item> exactMatches = findExactMatchingItems(rawName);
        if (exactMatches.isEmpty()) {
            return handleCraftRequest(rawName, level);
        }

        if (exactMatches.size() == 1) {
            return Component.literal(getRecipeText(exactMatches.get(0), level));
        }

        return buildDisambiguationMessage(exactMatches.stream().limit(MAX_MATCHES).collect(Collectors.toList()));
    }

    @Nullable
    private static Level resolveRecipeLevel() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        return server.overworld();
    }

    @Nullable
    private static CraftRequest parseCraftRequest(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }

        String normalized = rawMessage
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isBlank()) {
            return null;
        }

        for (String prefix : CRAFT_EXACT_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                String itemName = cleanupItemName(normalized.substring(prefix.length()));
                return itemName == null ? null : new CraftRequest(itemName, true);
            }
        }

        for (String prefix : CRAFT_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                String itemName = cleanupItemName(normalized.substring(prefix.length()));
                return itemName == null ? null : new CraftRequest(itemName, false);
            }
        }

        for (String hint : CRAFT_HINTS) {
            int hintIndex = normalized.indexOf(hint);
            if (hintIndex < 0) {
                continue;
            }

            int start = hintIndex + hint.length();
            if (start >= normalized.length()) {
                return null;
            }
            String itemName = cleanupItemName(normalized.substring(start));
            return itemName == null ? null : new CraftRequest(itemName, false);
        }

        return null;
    }

    @Nullable
    private static String cleanupItemName(String rawItemName) {
        if (rawItemName == null) {
            return null;
        }

        String itemName = rawItemName.trim();
        if (itemName.isBlank()) {
            return null;
        }

        boolean changed;
        do {
            changed = false;
            for (String filler : LEADING_FILLERS) {
                if (itemName.startsWith(filler)) {
                    itemName = itemName.substring(filler.length()).trim();
                    changed = true;
                }
            }
        } while (changed && !itemName.isBlank());

        return itemName.isBlank() ? null : itemName;
    }

    // -------- Поиск предметов --------

    private static List<Item> findMatchingItems(String raw) {
        String search = raw.toLowerCase(Locale.ROOT).trim();
        if (search.isBlank()) {
            return Collections.emptyList();
        }

        List<Item> exact = new ArrayList<>();
        List<Item> starts = new ArrayList<>();
        List<Item> tokenContains = new ArrayList<>();
        List<Item> contains = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS.getValues()) {

            ItemStack stack = new ItemStack(item);
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT).trim();

            if (name.equals(search))
                exact.add(item);
            else if (name.startsWith(search))
                starts.add(item);
            else if (containsAsWord(name, search))
                tokenContains.add(item);
            else if (name.contains(search))
                contains.add(item);
        }

        if (!exact.isEmpty()) {
            LinkedHashSet<Item> related = new LinkedHashSet<>();
            related.addAll(exact);
            related.addAll(starts);
            related.addAll(tokenContains);

            if (related.size() > 1) {
                return related.stream().limit(MAX_MATCHES).collect(Collectors.toList());
            }
            return exact;
        }

        if (!starts.isEmpty()) {
            return starts.stream().limit(MAX_MATCHES).collect(Collectors.toList());
        }

        List<Item> fallback = new ArrayList<>(tokenContains);
        fallback.addAll(contains);
        return fallback.stream().limit(MAX_MATCHES).collect(Collectors.toList());
    }

    private static List<Item> findExactMatchingItems(String raw) {
        String search = raw.toLowerCase(Locale.ROOT).trim();
        if (search.isBlank()) {
            return Collections.emptyList();
        }

        List<Item> exact = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ItemStack stack = new ItemStack(item);
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT).trim();
            if (name.equals(search)) {
                exact.add(item);
            }
        }

        return exact;
    }

    private static boolean containsAsWord(String name, String search) {
        return name.startsWith(search + " ")
                || name.endsWith(" " + search)
                || name.contains(" " + search + " ");
    }

    // -------- Сообщение уточнения --------

    private static Component buildDisambiguationMessage(List<Item> matches) {

        MutableComponent message = Component.literal("Уточни, что именно нужно скрафтить:\n");


        for (Item item : matches) {

            ItemStack stack = new ItemStack(item);
            String name = stack.getHoverName().getString();

            MutableComponent clickable = Component.literal("• " + name + "\n")
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    CRAFT_EXACT_COMMAND_PREFIX + name
                            ))
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Нажми чтобы выбрать")
                            ))
                    );

            message = message.append(clickable);
        }

        return message;
    }

    // -------- Получение текста рецепта --------

    private static String getRecipeText(Item item, Level level) {

        Collection<Recipe<?>> recipes = level.getRecipeManager().getRecipes();

        List<String> results = new ArrayList<>();

        for (Recipe<?> recipe : recipes) {

            ItemStack result = recipe.getResultItem(level.registryAccess());

            if (result.isEmpty()) continue;

            if (result.getItem() == item) {
                results.add(formatRecipe(recipe, level));
            }
        }

        if (results.isEmpty())
            return "Рецепт не найден.";

        return String.join("\n\n", results);
    }

    // -------- Форматирование рецепта --------

    private static String formatRecipe(Recipe<?> recipe, Level level) {

        StringBuilder sb = new StringBuilder();

        ItemStack result = recipe.getResultItem(level.registryAccess());

        sb.append("Предмет: ")
                .append(result.getHoverName().getString())
                .append("\n");

        if (recipe instanceof ShapedRecipe shaped) {

            sb.append("Тип: Верстак 3x3\n");

            int width = shaped.getWidth();
            int height = shaped.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {

                    int index = y * width + x;
                    Ingredient ing = shaped.getIngredients().get(index);

                    sb.append(getIngredientName(ing)).append(" | ");
                }
                sb.append("\n");
            }

        } else if (recipe instanceof ShapelessRecipe shapeless) {

            sb.append("Тип: Без формы\n");

            for (Ingredient ing : shapeless.getIngredients()) {
                sb.append("- ")
                        .append(getIngredientName(ing))
                        .append("\n");
            }

        } else if (recipe instanceof AbstractCookingRecipe cooking) {

            sb.append("Тип: Плавка\n");
            sb.append("Ингредиент: ")
                    .append(getIngredientName(cooking.getIngredients().get(0)))
                    .append("\n");
            sb.append("Время: ")
                    .append(cooking.getCookingTime() / 20)
                    .append(" сек.\n");

        } else {
            sb.append("Тип: Особый (модовый рецепт)\n");
        }

        return sb.toString();
    }

    private static String getIngredientName(Ingredient ingredient) {

        ItemStack[] stacks = ingredient.getItems();

        if (stacks.length == 0)
            return "Пусто";

        return stacks[0].getHoverName().getString();
    }

    private record CraftRequest(String itemName, boolean exactMatch) {
    }
}
