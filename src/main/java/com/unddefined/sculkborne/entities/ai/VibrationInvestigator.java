package com.unddefined.sculkborne.entities.ai;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 用 {@link InvestigateVibrationGoal} 调查振动的生物需要实现的能力。
 *
 * <p>振动的接收半径、「距离过远」的过滤，以及收到振动后把来源记在哪里，都由生物自己的
 * {@link net.minecraft.world.level.gameevent.vibrations.VibrationSystem.User} 完成；本接口描述
 * 「多久能接一次振动」（见 {@value #VIBRATION_RECEIVE_INTERVAL}）与「怎么朝振动源靠近」，目前的两种实现是：
 *
 * <ul>
 *     <li>{@link com.unddefined.sculkborne.entities.SculkSkeletonEntity}：用原版寻路走过去，
 *         振动源进入视野范围就不再靠近；</li>
 *     <li>{@link com.unddefined.sculkborne.entities.SculverfishEntity}：潜伏在幽匿块里，
 *         由 {@link SculverfishBurrowGoal} 沿着完整幽匿块朝振动源换位；它只走幽匿块，因此不会离开幽匿块，
 *         钻出地面后就不再靠近。</li>
 * </ul>
 */
public interface VibrationInvestigator {

    /**
     * 两次接收振动之间至少间隔的时间（tick），默认 60 刻（3 秒）。
     *
     * <p>接收半径与「距离过远」的过滤都在生物自己的振动回调里判断，这个间隔是「会调查振动的生物」的
     * 共用约定：上一次接收振动之后要隔这么久才接下一次，免得振动连着发生、或者调查刚开始就结束时
     * 一刻不停地改调查目标。实现方自己在服务端记一个剩余间隔（每刻递减），不大于 0 时才允许接收新的振动。
     */
    int VIBRATION_RECEIVE_INTERVAL = 60;

    /** 当前待调查的振动源位置，没有待调查的振动时返回 {@code null}。 */
    @Nullable
    BlockPos getVibrationSource();

    /** 结束本次调查，清空待调查的振动源。 */
    void clearVibrationSource();

    /**
     * 开始朝振动源靠近，由 {@link InvestigateVibrationGoal} 在开始调查时调用一次。
     *
     * @param source 待调查的振动源位置，来自 {@link #getVibrationSource()}
     * @return 已经开始靠近返回 {@code true}；不需要靠近（已经到达）或者没有可行路径时返回 {@code false}，
     *         本次调查会被取消
     */
    boolean beginVibrationInvestigation(BlockPos source);

    /** 是否还在朝振动源靠近；返回 {@code false} 时结束本次调查。 */
    boolean isApproachingVibrationSource();

    /** 结束调查时清理正在进行的移动（寻路、地下换位路径等），默认为没有需要清理的东西。 */
    default void stopVibrationInvestigation() {
    }
}
