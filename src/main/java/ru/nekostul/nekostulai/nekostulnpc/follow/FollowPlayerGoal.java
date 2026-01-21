package ru.nekostul.nekostulai.nekostulnpc.follow;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import ru.nekostul.nekostulai.nekostulnpc.nekostulNPC;

import java.util.EnumSet;

public class FollowPlayerGoal extends Goal {

    private final nekostulNPC npc;
    private final double speed;

    private static final double MIN_DIST = 2.0;
    private static final double MAX_DIST = 10.0;

    public FollowPlayerGoal(nekostulNPC npc, double speed) {
        this.npc = npc;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (npc.isWaiting()) return false;
        return npc.isFollowing()
                && npc.getFollowTarget() != null
                && npc.getFollowTarget().isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return npc.isFollowing()
                && npc.getFollowTarget() != null
                && npc.distanceToSqr(npc.getFollowTarget()) > 2.0;
    }

    @Override
    public void tick() {
        Player target = npc.getFollowTarget();
        if (target == null) return;

        npc.getNavigation().moveTo(target, speed);

        double dist = npc.distanceTo(target);

        if (dist > MAX_DIST) {
            npc.getNavigation().moveTo(target, speed);
        } else if (dist < MIN_DIST) {
            npc.getNavigation().stop();
        }
    }
}