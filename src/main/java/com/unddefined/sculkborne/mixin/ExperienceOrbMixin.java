package com.unddefined.sculkborne.mixin;

import com.unddefined.sculkborne.entities.WandererEntity;
import com.unddefined.sculkborne.entities.PickupDelayed;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经验球优先飞向徘徊者。
 *
 * <p>原版经验球每隔 20 刻扫描一次 {@value WandererEntity#XP_ORB_ATTRACTION_DISTANCE} 格内最近的玩家，
 * 之后每刻朝它加速（{@code ExperienceOrb#scanForEntities} 与 {@code ExperienceOrb#tick}）。
 * 这里在同样的节奏上多找一只徘徊者（{@link WandererEntity#findXpOrbAttractor(ExperienceOrb)}）：
 * 范围内有徘徊者时，经验球把玩家目标清掉、只朝徘徊者加速；徘徊者死亡或走出范围后恢复原版行为。
 *
 * <p>加速公式与原版跟随玩家完全一致，只改移动，不介入经验球的结算（吸取经验球由徘徊者自己处理）。
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin implements PickupDelayed {

    /** 当前吸引该经验球的徘徊者，每次扫描时重选一只。 */
    @Unique
    @Nullable
    private WandererEntity sculkborne$followingWanderer;

    /** 不能被捡走的解禁时刻（游戏刻），见 {@link #sculkborne$delayPickup(long)}。 */
    @Unique
    private long sculkborne$pickupDelayUntil = Long.MIN_VALUE;

    /** 原版跟随的玩家，徘徊者吸引期间会被清掉。 */
    @Shadow
    private Player followingPlayer;

    @Override
    public void sculkborne$delayPickup(long ticks) {
        this.sculkborne$pickupDelayUntil = ((ExperienceOrb) (Object) this).level().getGameTime() + ticks;
    }

    @Override
    public boolean sculkborne$isPickupDelayed() {
        return ((ExperienceOrb) (Object) this).level().getGameTime() < this.sculkborne$pickupDelayUntil;
    }

    /**
     * 在原版扫描玩家的同一刻重选徘徊者：目标无效（死亡或走出范围）时清空，没有目标时再找最近的一只。
     */
    @Inject(method = "scanForEntities", at = @At("HEAD"))
    private void sculkborne$scanForWanderer(CallbackInfo ci) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        double range = WandererEntity.XP_ORB_ATTRACTION_DISTANCE;

        WandererEntity wanderer = this.sculkborne$followingWanderer;
        if (wanderer != null && (!wanderer.isAlive() || wanderer.distanceToSqr(orb) > range * range)) {
            this.sculkborne$followingWanderer = null;
            wanderer = null;
        }

        if (wanderer == null) this.sculkborne$followingWanderer = WandererEntity.findXpOrbAttractor(orb);
    }

    /**
     * 选完目标后清掉玩家目标：范围内有徘徊者时经验球不再跟随玩家。
     */
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;scanForEntities()V",
            shift = At.Shift.AFTER))
    private void sculkborne$dropPlayerTarget(CallbackInfo ci) {
        if (this.sculkborne$followingWanderer != null) this.followingPlayer = null;
    }

    /**
     * 每刻朝徘徊者加速，公式与原版跟随玩家一致：距离越近拉力越大，最远在作用距离处归零。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void sculkborne$attractToWanderer(CallbackInfo ci) {
        WandererEntity wanderer = this.sculkborne$followingWanderer;
        if (wanderer == null) return;
        if (!wanderer.isAlive()) {
            this.sculkborne$followingWanderer = null;
            return;
        }

        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        double range = WandererEntity.XP_ORB_ATTRACTION_DISTANCE;
        Vec3 toWanderer = new Vec3(wanderer.getX() - orb.getX(),
                wanderer.getY() + (double) wanderer.getEyeHeight() / 2.0 - orb.getY(),
                wanderer.getZ() - orb.getZ());
        double distanceSqr = toWanderer.lengthSqr();
        if (distanceSqr > range * range) return;

        double pull = 1.0 - Math.sqrt(distanceSqr) / range;
        orb.setDeltaMovement(orb.getDeltaMovement().add(toWanderer.normalize().scale(pull * pull * 0.1)));
    }
}
