package ru.nekostul.nekostulai.nekostulnpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.nekostul.nekostulai.nekostulnpc.follow.FollowPlayerGoal;
import ru.nekostul.nekostulai.nekostulnpc.follow.InspectSurroundingsGoal;
import ru.nekostul.nekostulai.nekostulnpc.follow.NPCChatReplyQueue;
import ru.nekostul.nekostulai.nekostulnpc.npcai.NpcAIContext;
import ru.nekostul.nekostulai.nekostulnpc.npcai.NpcAIService;
import ru.nekostul.nekostulai.nekostulnpc.npcai.NpcMemory;

import javax.annotation.Nullable;
import java.util.*;

public class nekostulNPC extends PathfinderMob {

    @Nullable
    private Vec3 inspectLookTarget;

    public enum BrainState {
        IDLE,
        FOLLOWING,
        INSPECTING
    }

    private BrainState brainState = BrainState.IDLE;
    private int inspectTick = 0;
    private Player inspectTarget;
    private long lastHitMessageTime = 0L;
    private boolean lookAtPlayer = false;
    private @Nullable Player lookTarget;
    private final Map<Block, Integer> inspectedBlocks = new HashMap<>();
    private int inspectedFloor = 0;
    private int inspectedCeiling = 0;
    private final Set<BlockPos> interiorAir = new HashSet<>();
    private String lastInspectionSummary;
    private final NpcMemory memory = new NpcMemory();

    public NpcMemory getMemory() {
        return memory;
    }

    public void setLastInspectionSummary(String summary) {
        this.lastInspectionSummary = summary;
    }

    public String getLastInspectionSummary() {
        return lastInspectionSummary;
    }

    private void collectInteriorAir() {
        interiorAir.clear();

        BlockPos start = this.blockPosition();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        interiorAir.add(start);

        int maxDistance = 6;

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();

            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);

                if (interiorAir.contains(next)) continue;

                if (next.distManhattan(start) > maxDistance) continue;

                if (!level().getBlockState(next).isAir()) continue;

                interiorAir.add(next);
                queue.add(next);
            }
        }
    }

    private void scanBlockAt(BlockPos pos) {
        BlockPos npcPos = this.blockPosition();

        int RADIUS_XZ = 6;
        int MAX_UP = 6;
        int MAX_DOWN = 2;

        if (Math.abs(pos.getX() - npcPos.getX()) > RADIUS_XZ) return;
        if (Math.abs(pos.getZ() - npcPos.getZ()) > RADIUS_XZ) return;
        if (pos.getY() < npcPos.getY() - MAX_DOWN) return;
        if (pos.getY() > npcPos.getY() + MAX_UP) return;

        BlockState state = level().getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.AIR) return;

        if (
                block == Blocks.GRASS_BLOCK ||
                        block == Blocks.DIRT ||
                        block == Blocks.GRASS ||
                        block == Blocks.TALL_GRASS ||
                        block == Blocks.DIRT_PATH
        ) {
            return;
        }

        if (
                block instanceof BushBlock ||
                        block instanceof FlowerBlock
        ) {
            return;
        }

        inspectedBlocks.merge(block, 1, Integer::sum);

        int baseY = npcPos.getY();

        if (pos.getY() <= baseY) {
            inspectedFloor++;
        }

    }

    private boolean hasCeiling() {
        BlockPos base = this.blockPosition();
        int eyeY = Mth.floor(this.getEyeY());

        // радиус проверки потолка (3x3)
        for (int y = eyeY + 2; y <= eyeY + 8; y++) {
            int solidCount = 0;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = base.offset(dx, y - base.getY(), dz);
                    BlockState state = level().getBlockState(pos);

                    if (!state.isAir()) {
                        solidCount++;
                    }
                }
            }

            // если хотя бы 4 блока из 9 — считаем потолком
            if (solidCount >= 4) {
                return true;
            }
        }

        return false;
    }

    public void resetInspectionData() {
        inspectedBlocks.clear();
        inspectedFloor = 0;
        inspectedCeiling = 0;
    }

    public void startInspecting(Player player) {
        this.brainState = BrainState.INSPECTING;
        this.inspectTarget = player;
        this.inspectTick = 0;
        this.inspecting = true;

        resetInspectionData();

        BlockPos base = this.blockPosition();

        int radius = 6;   // радиус дома
        int height = 6;   // высота (для потолков)

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -1; y <= height; y++) {
                    BlockPos pos = base.offset(x, y, z);
                    scanBlockAt(pos);
                }
            }
        }
    }


    public void startLookingAt(Player player) {
        this.lookAtPlayer = true;
        this.lookTarget = player;
    }

    public void stopLooking() {
        this.lookAtPlayer = false;
        this.lookTarget = null;
    }

    public boolean shouldLookAtPlayer() {
        return shouldLookAtPlayer;
    }

    public @Nullable Player getLookTarget() {
        return lookTarget;
    }

    public nekostulNPC(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal("nekostulAI"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D);
    }

    private ServerPlayer followTarget;

    public void setFollowing(ServerPlayer player) {
        this.followTarget = player;
        this.following = true;
    }

    public void setFollowing(boolean value) {
        if (!value) {
            this.followTarget = null;
        }
        this.following = value;
    }

    @Nullable
    public ServerPlayer getFollowTarget() {
        return followTarget;
    }

    private boolean following = false;

    public boolean isFollowing() {
        return following;
    }

    private boolean shouldLookAtPlayer = false;

    public void setShouldLookAtPlayer(boolean value) {
        this.shouldLookAtPlayer = value;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        // ================= ОСМОТР ПОСТРОЙКИ =================
        if (brainState == BrainState.INSPECTING && inspectTarget != null) {
            inspectTick++;

            LookControl look = this.getLookControl();

            float speed = 4.0F; // МЕДЛЕННО, без дерганий

            // ФАЗЫ ОСМОТРА — цель МЕНЯЕТСЯ РЕДКО
            if (inspectTick == 1) {
                // влево
                look.setLookAt(
                        inspectTarget.getX() - 2.5,
                        inspectTarget.getEyeY(),
                        inspectTarget.getZ(),
                        speed,
                        speed
                );
            }
            else if (inspectTick == 50) {
                // вправо
                look.setLookAt(
                        inspectTarget.getX() + 2.5,
                        inspectTarget.getEyeY(),
                        inspectTarget.getZ(),
                        speed,
                        speed
                );
            }
            else if (inspectTick == 100) {
                // пол
                look.setLookAt(
                        inspectTarget.getX(),
                        this.getY() - 1.5,
                        inspectTarget.getZ(),
                        speed,
                        speed
                );
            }
            else if (inspectTick == 150) {
                // потолок
                look.setLookAt(
                        inspectTarget.getX(),
                        this.getY() + 3.5,
                        inspectTarget.getZ(),
                        speed,
                        speed
                );
            }
            else if (inspectTick > 200) {
                stopInspecting();
            }

            return; // <<< ВАЖНО
        }

        // ================= ОБЫЧНЫЙ ВЗГЛЯД НА ИГРОКА =================
        if (!shouldLookAtPlayer) return;

        Player player = this.level().getNearestPlayer(this, 6.0);
        if (player != null) {
            this.getLookControl().setLookAt(player, 20.0F, 20.0F);
        }
    }

    private void scanHouseArea() {
        BlockPos center = this.blockPosition();

        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 5; y++) {
                for (int z = -4; z <= 4; z++) {
                    scanBlockAt(center.offset(x, y, z));
                }
            }
        }
    }

    private void lookAtPlayer(ServerPlayer player) {
        this.getLookControl().setLookAt(
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                30.0F,
                30.0F
        );
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(
                1,
                new FollowPlayerGoal(this, 1.4D)
        );
        this.goalSelector.addGoal(0, new InspectSurroundingsGoal(this));
    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            player.sendSystemMessage(
                    Component.literal("§6[nekostulAI] §fЭй. Я тебя вижу 👀")
            );
        }
        return InteractionResult.SUCCESS;
    }
    public boolean isCustomNameVisible() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {

        if (this.getHealth() - amount <= 0.0F) {
            return super.hurt(source, amount);
        }

        if (!this.level().isClientSide && source.getEntity() instanceof Player player) {

            long now = System.currentTimeMillis();

            if (now - lastHitMessageTime >= 2000) { // ⏱ 2 секунды
                lastHitMessageTime = now;

                String phrase = HIT_PHRASES[this.random.nextInt(HIT_PHRASES.length)];

                player.sendSystemMessage(
                        Component.literal(phrase)
                );
            }
        }
        return super.hurt(source, amount);
    }

    private static final String[] HIT_PHRASES = new String[] {
            "§6[nekostulAI] §fАй! Ты чего дерёшься?",
            "§6[nekostulAI] §fЭй! Больно вообще-то!",
            "§6[nekostulAI] §fТы нормальный?",
            "§6[nekostulAI] §fХэй! Осторожнее!",
            "§6[nekostulAI] §fЗа что?..",
            "§6[nekostulAI] §fЯ тебе что сделал?",
            "§6[nekostulAI] §fОЙ!",
            "§6[nekostulAI] §fАУЧ!",
            "§6[nekostulAI] §fАй, аккуратнее!",
            "§6[nekostulAI] §fТы совсем что ли?",
            "§6[nekostulAI] §fМне вообще-то больно!",
            "§6[nekostulAI] §fЭй-эй-эй!",
            "§6[nekostulAI] §fТы это сейчас серьёзно?",
            "§6[nekostulAI] §fНу и зачем?",
            "§6[nekostulAI] §fВот это было лишнее.",
            "§6[nekostulAI] §fЯ не для этого тут стою.",
            "§6[nekostulAI] §fМожет поговорим?",
            "§6[nekostulAI] §fБез рук, пожалуйста.",
            "§6[nekostulAI] §fТы агрессивный сегодня.",
            "§6[nekostulAI] §fСпокойнее, воин.",
            "§6[nekostulAI] §fЯ вообще-то мирный.",
            "§6[nekostulAI] §fРуки убрал.",
            "§6[nekostulAI] §fЭй, я не моб!",
            "§6[nekostulAI] §fЭто было обидно.",
            "§6[nekostulAI] §fТы точно хочешь это делать?",
            "§6[nekostulAI] §fЯ запомню это.",
            "§6[nekostulAI] §fНу всё, начинаем.",
            "§6[nekostulAI] §fЗря ты так.",
            "§6[nekostulAI] §fМне это не нравится.",
            "§6[nekostulAI] §fОкей, понял.",
            "§6[nekostulAI] §fТы меня ударил.",
            "§6[nekostulAI] §fЭто было не очень.",
            "§6[nekostulAI] §fТы сейчас серьёзно?",
            "§6[nekostulAI] §fМда...",
            "§6[nekostulAI] §fВот это хамство.",
            "§6[nekostulAI] §fТак, стоп.",
            "§6[nekostulAI] §fЯ же просто стоял.",
            "§6[nekostulAI] §fЧего ты добиваешься?",
            "§6[nekostulAI] §fЯ тебя чем-то задел?",
            "§6[nekostulAI] §fТы злой какой-то.",
            "§6[nekostulAI] §fНе начинай.",
            "§6[nekostulAI] §fЭто плохая идея.",
            "§6[nekostulAI] §fАй, полегче!",
            "§6[nekostulAI] §fОй-ой-ой!",
            "§6[nekostulAI] §fНу всё, хватит.",
            "§6[nekostulAI] §fТы перебарщиваешь.",
            "§6[nekostulAI] §fЯ не боксерская груша.",
            "§6[nekostulAI] §fМожет без насилия?",
            "§6[nekostulAI] §fТы явно не в настроении.",
            "§6[nekostulAI] §fЭто уже грубо.",
            "§6[nekostulAI] §fТак, мне это не нравится.",
            "§6[nekostulAI] §fХватит, серьёзно.",
            "§6[nekostulAI] §fТы чего бесишься?",
            "§6[nekostulAI] §fЯ вообще-то тут по делу.",
            "§6[nekostulAI] §fАй, ну зачем?",
            "§6[nekostulAI] §fТы издеваешься?",
            "§6[nekostulAI] §fЭто было больно.",
            "§6[nekostulAI] §fПрекрати.",
            "§6[nekostulAI] §fПоследнее предупреждение.",
            "§6[nekostulAI] §fМне не смешно."
    };


    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {

            String phrase;

            // 🧍 Убит игроком
            if (source.getEntity() instanceof Player) {
                phrase = DEATH_BY_PLAYER_PHRASES[
                        this.random.nextInt(DEATH_BY_PLAYER_PHRASES.length)
                        ];
            }
            // 🔥 Лава
            else if (source.is(DamageTypes.LAVA)) {
                phrase = DEATH_LAVA_PHRASES[
                        this.random.nextInt(DEATH_LAVA_PHRASES.length)
                        ];
            }
            // 🔥 Огонь
            else if (
                    source.is(DamageTypes.ON_FIRE) ||
                            source.is(DamageTypes.IN_FIRE)
            ) {
                phrase = DEATH_FIRE_PHRASES[
                        this.random.nextInt(DEATH_FIRE_PHRASES.length)
                        ];
            }
            // 🕳 Падение
            else if (source.is(DamageTypes.FALL)) {
                phrase = DEATH_FALL_PHRASES[
                        this.random.nextInt(DEATH_FALL_PHRASES.length)
                        ];
            }
            // 🌊 Утопление
            else if (source.is(DamageTypes.DROWN)) {
                phrase = DEATH_DROWN_PHRASES[
                        this.random.nextInt(DEATH_DROWN_PHRASES.length)
                        ];
            }
            // ☠ Всё остальное
            else {
                phrase = DEATH_GENERIC_PHRASES[
                        this.random.nextInt(DEATH_GENERIC_PHRASES.length)
                        ];
            }

            // 📢 Отправляем всем игрокам
            this.level().players().forEach(player ->
                    player.sendSystemMessage(Component.literal(phrase))
            );
        }

        super.die(source);
    }


    private static final String[] DEATH_LAVA_PHRASES = {
            "§6[nekostulAI] §fААААА! ЛАВА!!!",
            "§6[nekostulAI] §fЯ недооценил лаву...",
            "§6[nekostulAI] §fГорячо... слишком горячо...",
            "§6[nekostulAI] §fПлохая идея была прыгать туда...",
            "§6[nekostulAI] §fЛава победила."
    };

    private static final String[] DEATH_FIRE_PHRASES = {
            "§6[nekostulAI] §fЯ горю!!!",
            "§6[nekostulAI] §fЭто было не по плану...",
            "§6[nekostulAI] §fОгонь — не мой друг.",
            "§6[nekostulAI] §fАй! Ай! Ай!",
            "§6[nekostulAI] §fСлишком жарко для жизни."
    };

    private static final String[] DEATH_FALL_PHRASES = {
            "§6[nekostulAI] §fЯ переоценил высоту...",
            "§6[nekostulAI] §fГравитация снова победила.",
            "§6[nekostulAI] §fЭто был долгий полёт...",
            "§6[nekostulAI] §fНадо было поставить блок.",
            "§6[nekostulAI] §fЯ больше так не буду. Наверное."
    };

    private static final String[] DEATH_DROWN_PHRASES = {
            "§6[nekostulAI] §fЯ не умею дышать под водой...",
            "§6[nekostulAI] §fБуль-буль...",
            "§6[nekostulAI] §fВоды было слишком много.",
            "§6[nekostulAI] §fЯ думал, что успею всплыть...",
            "§6[nekostulAI] §fПлохая идея — без пузырьков."
    };

    private static final String[] DEATH_BY_PLAYER_PHRASES = {
            "§6[nekostulAI] §fТы... меня убил...",
            "§6[nekostulAI] §fВот и всё... ты победил...",
            "§6[nekostulAI] §fНу всё... гг...",
            "§6[nekostulAI] §fЗря ты так...",
            "§6[nekostulAI] §fЯ тебе доверял...",
            "§6[nekostulAI] §fА я думал, мы друзья...",
            "§6[nekostulAI] §fЭто было больно...",
            "§6[nekostulAI] §fНу и зачем?..",
            "§6[nekostulAI] §fТы серьёзно?..",
            "§6[nekostulAI] §fВот так вот просто?..",
            "§6[nekostulAI] §fЛадно... твоя взяла...",
            "§6[nekostulAI] §fМог бы и поговорить...",
            "§6[nekostulAI] §fЯ же ничего плохого не сделал...",
            "§6[nekostulAI] §fТы выбрал насилие...",
            "§6[nekostulAI] §fЭто конец...",
            "§6[nekostulAI] §fЯ этого не забуду...",
            "§6[nekostulAI] §fКарма тебе это вернёт...",
            "§6[nekostulAI] §fЖестоко...",
            "§6[nekostulAI] §fТак не честно...",
            "§6[nekostulAI] §fЯ просто стоял...",
            "§6[nekostulAI] §fЗа что?..",
            "§6[nekostulAI] §fНу и мир у вас...",
            "§6[nekostulAI] §fТы доволен теперь?",
            "§6[nekostulAI] §fМне конец...",
            "§6[nekostulAI] §fВот и поговорили...",
            "§6[nekostulAI] §fЯ не ожидал такого от тебя...",
            "§6[nekostulAI] §fЭто запомнится...",
            "§6[nekostulAI] §fПрощай...",
            "§6[nekostulAI] §fТак заканчивается моя история...",
            "§6[nekostulAI] §fТы выбрал плохой путь...",
            "§6[nekostulAI] §fЯ думал, ты другой...",
            "§6[nekostulAI] §fЗачем ты это сделал?..",
            "§6[nekostulAI] §fМне жаль...",
            "§6[nekostulAI] §fЯ не сопротивляюсь...",
            "§6[nekostulAI] §fТы сильнее...",
            "§6[nekostulAI] §fТы победил...",
            "§6[nekostulAI] §fКонец."
    };

    private static final String[] DEATH_GENERIC_PHRASES = {
            "§6[nekostulAI] §fЯ погиб...",
            "§6[nekostulAI] §fВот и конец...",
            "§6[nekostulAI] §fМеня больше нет...",
            "§6[nekostulAI] §fЭтот мир жесток...",
            "§6[nekostulAI] §fПрощайте...",
            "§6[nekostulAI] §fТак и знал...",
            "§6[nekostulAI] §fПохоже, это всё...",
            "§6[nekostulAI] §fЯ не справился...",
            "§6[nekostulAI] §fМне не повезло...",
            "§6[nekostulAI] §fСлишком опасно...",
            "§6[nekostulAI] §fЯ недооценил этот мир...",
            "§6[nekostulAI] §fНадо было быть осторожнее...",
            "§6[nekostulAI] §fОшибся...",
            "§6[nekostulAI] §fПлохая была идея...",
            "§6[nekostulAI] §fЯ зашёл слишком далеко...",
            "§6[nekostulAI] §fЭтот мир меня сломал...",
            "§6[nekostulAI] §fЯ не выжил...",
            "§6[nekostulAI] §fСил больше нет...",
            "§6[nekostulAI] §fЯ не был готов...",
            "§6[nekostulAI] §fМоя история заканчивается здесь...",
            "§6[nekostulAI] §fВсё пошло не по плану...",
            "§6[nekostulAI] §fЯ допустил ошибку...",
            "§6[nekostulAI] §fЭто было неизбежно...",
            "§6[nekostulAI] §fМир оказался сильнее...",
            "§6[nekostulAI] §fЯ проиграл...",
            "§6[nekostulAI] §fНе так я это представлял...",
            "§6[nekostulAI] §fВот так всё и кончается...",
            "§6[nekostulAI] §fЯ не успел...",
            "§6[nekostulAI] §fСлишком опасно для меня...",
            "§6[nekostulAI] §fМне конец...",
            "§6[nekostulAI] §fПроиграл этой реальности...",
            "§6[nekostulAI] §fЭтот мир не прощает ошибок...",
            "§6[nekostulAI] §fЯ был слишком самоуверен...",
            "§6[nekostulAI] §fНужно было отступить...",
            "§6[nekostulAI] §fЯ больше не встану..."
    };
    @Nullable
    private BlockPos waitPos;

    public void waitHere() {
        this.waitPos = this.blockPosition();
        this.setFollowing(false);
        this.getNavigation().stop();
    }

    public boolean isWaiting() {
        return waitPos != null;
    }

    public void clearWait() {
        this.waitPos = null;
    }
    private boolean inspecting = false;
    private int inspectTicks = 0;

    public boolean isInspecting() {
        return inspecting;
    }
    public void stopInspecting() {

        if (inspectTarget instanceof ServerPlayer player) {
            String summary = buildInspectionSummary();

            this.getMemory().rememberInspection(player, summary);

            NpcAIContext ctx = new NpcAIContext();
            ctx.playerName = player.getName().getString();
            ctx.question = "Оцени дом";
            ctx.inspectionSummary = summary;

            NpcAIService.askNpc(ctx, answer -> {
                NPCChatReplyQueue.replyNextTick(
                        player,
                        "§6[nekostulAI] §f" + answer
                );
            });
        }

        this.inspecting = false;
        this.brainState = BrainState.IDLE;
        this.inspectTarget = null;
    }
    public String buildInspectionSummary() {
        StringBuilder sb = new StringBuilder();

        sb.append("Дом состоит из: ");

        inspectedBlocks.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .forEach(e -> {
                    sb.append(e.getKey().getName().getString())
                            .append(" (")
                            .append(e.getValue())
                            .append("), ");
                });

        // убираем последнюю ", "
        if (sb.toString().endsWith(", ")) {
            sb.setLength(sb.length() - 2);
        }

        sb.append(". ");

        if (hasCeiling()) {
            sb.append("есть потолок, ");
        } else {
            sb.append("потолка нет, ");
        }

        if (inspectedFloor > 0) {
            sb.append("есть пол.");
        } else {
            sb.append("пол отсутствует.");
        }

        return sb.toString();
    }
}