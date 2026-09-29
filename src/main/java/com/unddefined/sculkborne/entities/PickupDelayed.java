package com.unddefined.sculkborne.entities;

/**
 * 经验球级别的「还不能被捡走」状态。
 *
 * <p>原版的拾取延迟是玩家身上的 {@code Player#takeXpDelay}，而且只在
 * {@code ExperienceOrb#playerTouch} 里判定；Clumps 这类接管 playerTouch 的 mod
 * 会把那段判定整个取消掉（它的 HEAD 注入直接 cancel，也不看 takeXpDelay），延迟于是失效。
 * 所以徘徊者剥落的经验球改成一球一份的延迟，由
 * {@code com.unddefined.sculkborne.mixin.PlayerMixin} 在 {@code Player#touch} 那一步统一拦下：
 * 与玩家身上的字段无关，装不装 Clumps 都拦得住。
 *
 * <p>该接口由 {@code com.unddefined.sculkborne.mixin.ExperienceOrbMixin} 实现在所有经验球上，
 * 使用时把实体转过来即可：{@code ((PickupDelayed) orb).sculkborne$delayPickup(ticks)}。
 */
public interface PickupDelayed {

    /**
     * 让这颗球在接下来 {@code ticks} 刻内不能被玩家捡走。
     *
     * @param ticks 持续时间（刻），从当前游戏刻算起
     */
    void sculkborne$delayPickup(long ticks);

    /** @return 这颗球此刻是否还在拾取延迟里 */
    boolean sculkborne$isPickupDelayed();
}
