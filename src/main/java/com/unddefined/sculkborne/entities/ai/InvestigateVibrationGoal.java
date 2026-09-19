package com.unddefined.sculkborne.entities.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * 调查振动的 Goal：接收到振动后朝振动源移动，直到生物自己认为不用再靠近。
 *
 * <p>振动源由 {@link VibrationInvestigator#getVibrationSource()} 给出，接收半径与“距离过远”的过滤都在
 * 生物自己的振动回调里完成，本 Goal 只负责开始和结束一次调查：
 *
 * <ul>
 *     <li>开始调查时先问一次生物能不能过去（{@link VibrationInvestigator#beginVibrationInvestigation(BlockPos)}）：
 *         已经到达、或者没有可行路径时不移动，直接放弃本次调查；</li>
 *     <li>已经锁定攻击目标（原版目标选择器在看得见生物时锁定目标）时结束调查，把移动交给战斗用的 Goal。</li>
 * </ul>
 *
 * <p>要占用的控制标记由构造方给出：自己寻路走过去的生物（如幽匿骷髅）传 {@link Flag#MOVE}；
 * 移动交给其它 Goal 的生物（如潜伏时的地下移动归 {@link SculverfishBurrowGoal} 管的幽匿蠹虫）不传标记，
 * 本 Goal 只是跟着调查的开始与结束。
 *
 * @param <T> 会调查振动的生物，既要实现 {@link VibrationInvestigator}，也必须是 {@link Mob}
 */
public class InvestigateVibrationGoal<T extends Mob & VibrationInvestigator> extends Goal {

    private final T mob;

    /** 本次调查的寻路目标，取自 {@link #canUse()} 时读到的振动源。 */
    @Nullable
    private BlockPos target;

    /**
     * @param mob   会调查振动的生物
     * @param flags 调查时要占用的控制标记，见类注释
     */
    public InvestigateVibrationGoal(T mob, Flag... flags) {
        this.mob = mob;

        EnumSet<Flag> flagSet = EnumSet.noneOf(Flag.class);
        for (Flag flag : flags) flagSet.add(flag);
        this.setFlags(flagSet);
    }

    @Override
    public boolean canUse() {
        return this.mob.getVibrationSource() != null && this.mob.getTarget() == null;
    }

    @Override
    public void start() {
        BlockPos source = this.mob.getVibrationSource();
        if (source == null) return;

        // 不需要靠近或者没有可行路径时不移动，放弃本次调查
        if (!this.mob.beginVibrationInvestigation(source)) {
            this.mob.clearVibrationSource();
            return;
        }

        this.target = source;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || this.mob.getVibrationSource() == null) return false;

        // 已经锁定攻击目标（振动源多半就是它）时交给战斗用的 Goal
        if (this.mob.getTarget() != null) return false;

        return this.mob.isApproachingVibrationSource();
    }

    @Override
    public void stop() {
        this.mob.clearVibrationSource();
        this.mob.stopVibrationInvestigation();
        this.target = null;
    }
}
