package com.unddefined.sculkborne.entities.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * 牵引 Goal：让生物不会飞离附近的生物太远。
 *
 * <p>每刻以自己为中心、在 {@code searchRange} 格内找最近的生物作为牵引对象（自己除外，旁观者不算），
 * 并且优先挑同类之外的生物：只有附近全是同类（同一种实体类型）时才退而选同类。
 * 离牵引对象超过 {@code leashDistance} 格时就飞到「离它 {@code leashDistance} 格、位于自己这一侧」的那一点，
 * 也就是回到刚好维持这条距离的边界上，而不是一路撞到对方身上；回到边界以内本次牵引就结束，
 * 控制权交还给其它 Goal（随机飘移、调查振动等）。附近没有生物时什么都不做，生物照常自己行动。
 *
 * <p>移动方式是直接设置 {@link net.minecraft.world.entity.ai.control.MoveControl} 的移动目标，因此适用于
 * 靠移动控制器飞行的生物（幽影这类无重力的恼鬼）：这类生物不走寻路，方向、加速与到达都交给移动控制器，
 * 所以牵引速度必须快于往外跑的目标，否则永远追不回边界以内。
 */
public class StayNearCreatureGoal extends Goal {

    private final Mob mob;

    /** 允许离牵引对象的最远距离（格）。 */
    private final double leashDistance;

    /** 寻找牵引对象的搜索范围（格），应当略大于 {@link #leashDistance}，否则对方刚跑出边界就看不见了。 */
    private final double searchRange;

    /** 飞回边界时的速度倍率，见类注释。 */
    private final double speedModifier;

    /** 本刻的牵引对象，由 {@link #canUse()} 与 {@link #canContinueToUse()} 刷新。 */
    @Nullable
    private LivingEntity creature;

    /**
     * @param mob            要牵引的生物，必须靠移动控制器飞行
     * @param leashDistance  允许离牵引对象的最远距离（格）
     * @param searchRange    寻找牵引对象的搜索范围（格）
     * @param speedModifier  飞回边界时的速度倍率
     */
    public StayNearCreatureGoal(Mob mob, double leashDistance, double searchRange, double speedModifier) {
        this.mob = mob;
        this.leashDistance = leashDistance;
        this.searchRange = searchRange;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        this.creature = findNearestCreature();
        return isBeyondLeash(this.creature);
    }

    @Override
    public void start() {
        steerToward(this.creature);
    }

    @Override
    public boolean canContinueToUse() {
        this.creature = findNearestCreature();
        return isBeyondLeash(this.creature);
    }

    @Override
    public void tick() {
        // 牵引对象已经由 canContinueToUse() 每刻刷新过；追回边界以内就不再动它
        if (isBeyondLeash(this.creature)) steerToward(this.creature);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** 是否已经离牵引对象超过允许的距离，需要飞回去。 */
    private boolean isBeyondLeash(@Nullable LivingEntity creature) {
        return creature != null && this.mob.distanceToSqr(creature) > this.leashDistance * this.leashDistance;
    }

    /** 把移动目标设成「离牵引对象 {@link #leashDistance} 格、位于自己这一侧」的那一点。 */
    private void steerToward(@Nullable LivingEntity creature) {
        if (creature == null) return;

        Vec3 creaturePos = creature.position();
        Vec3 offset = this.mob.position().subtract(creaturePos);
        // 只有超出边界时才会调用，此时 offset 的长度大于 leashDistance，不会退化成零向量
        Vec3 holdPoint = creaturePos.add(offset.normalize().scale(this.leashDistance));
        this.mob.getMoveControl().setWantedPosition(holdPoint.x, holdPoint.y, holdPoint.z, this.speedModifier);
    }

    /**
     * 以自己为中心、搜索范围内最近的生物；同类之外的生物优先，附近只有同类时才选同类，
     * 没有符合条件的生物时返回 {@code null}。
     *
     * <p>「同类」按实体类型判定，即与原版 {@code AvoidEntityGoal} 判定同类的方式一致：
     * {@code candidate.getType() == mob.getType()}。
     */
    @Nullable
    private LivingEntity findNearestCreature() {
        double rangeSqr = this.searchRange * this.searchRange;
        List<LivingEntity> candidates = this.mob.level().getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(this.searchRange),
                candidate -> candidate != this.mob && candidate.isAlive() && !candidate.isSpectator()
                        && this.mob.distanceToSqr(candidate) <= rangeSqr);

        LivingEntity nearestOtherType = null;
        double nearestOtherTypeDistance = Double.MAX_VALUE;
        LivingEntity nearestSameType = null;
        double nearestSameTypeDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : candidates) {
            double distance = this.mob.distanceToSqr(candidate);
            if (candidate.getType() == this.mob.getType()) {
                if (distance < nearestSameTypeDistance) {
                    nearestSameTypeDistance = distance;
                    nearestSameType = candidate;
                }
            } else if (distance < nearestOtherTypeDistance) {
                nearestOtherTypeDistance = distance;
                nearestOtherType = candidate;
            }
        }
        return nearestOtherType != null ? nearestOtherType : nearestSameType;
    }
}
