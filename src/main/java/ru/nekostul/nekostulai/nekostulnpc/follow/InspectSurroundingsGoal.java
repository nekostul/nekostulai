package ru.nekostul.nekostulai.nekostulnpc.follow;

import net.minecraft.world.entity.ai.goal.Goal;
import ru.nekostul.nekostulai.nekostulnpc.nekostulNPC;

import java.util.EnumSet;

public class InspectSurroundingsGoal extends Goal {

    private final nekostulNPC npc;

    private float baseYaw;
    private float yawOffset = 0f;
    private boolean yawRight = true;

    private float pitch = 0f;
    private float targetPitch = 0f;

    private int ticks;

    public InspectSurroundingsGoal(nekostulNPC npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.isInspecting();
    }

    @Override
    public void start() {
        ticks = 0;
        baseYaw = npc.getYRot();
        yawOffset = 0f;
        yawRight = true;

        pitch = 0f;
        targetPitch = 0f;
    }

    @Override
    public void tick() {
        ticks++;

        float yawSpeed = 2.0f;
        float maxYaw = 60f;

        if (yawRight) {
            yawOffset += yawSpeed;
            if (yawOffset >= maxYaw) yawRight = false;
        } else {
            yawOffset -= yawSpeed;
            if (yawOffset <= -maxYaw) yawRight = true;
        }

        // --- PITCH (вверх-вниз, редко) ---
        if (ticks % 40 == 0) { // раз в ~3 секунды
            int r = npc.getRandom().nextInt(3);
            if (r == 0) targetPitch = 30f;
            else if (r == 1) targetPitch = -25f;
            else targetPitch = 0f;
        }

        // плавное приближение к targetPitch
        pitch += (targetPitch - pitch) * 0.12f;

        float finalYaw = baseYaw + yawOffset;

        npc.getLookControl().setLookAt(
                npc.getX() + Math.cos(Math.toRadians(finalYaw)),
                npc.getEyeY() + Math.sin(Math.toRadians(pitch)),
                npc.getZ() + Math.sin(Math.toRadians(finalYaw)),
                20.0F,
                20.0F
        );

        // --- ВРЕМЯ ОСМОТРА ---
        if (ticks > 200) { // ~10 секунд
            npc.stopInspecting();
        }
    }
}