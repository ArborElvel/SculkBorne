package com.unddefined.sculkborne.client.model.cem;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * 一帧内从实体与 GeckoLib 动画状态里取出的 CEM 输入，外加该实体的跨帧变量。
 *
 * <p>命名尽量贴近 CEM 文档：{@link #limbSwing()} / {@link #limbSpeed()} 对应 {@code limb_swing} /
 * {@code limb_speed}，{@link #headYaw()} / {@link #headPitch()} 对应 {@code head_yaw} / {@code head_pitch}，
 * {@link #clampedHeadYaw()} 对应常见的 {@code var.hy}，{@link #hurt()} 对应 {@code var.hurt}。
 * 其余不常用的 CEM 变量（{@code is_on_ground}、{@code death_time} 等）可以直接从 {@link #entity()} 读取。
 */
public final class CemFrame<T extends LivingEntity> {

    private final T entity;
    private final CemAnimator.State state;
    private final float partialTick;
    private final boolean child;
    private final boolean riding;
    private final boolean inWater;
    private final boolean aggressive;
    private final float limbSwing;
    private final float limbSpeed;
    private final float swingProgress;
    private final int hurtTime;
    private final float hurt;
    private final float headYaw;
    private final float headPitch;
    private final float clampedHeadYaw;
    private final float age;
    private final float random;

    CemFrame(T entity, AnimationState<?> animationState, CemAnimator.State state) {
        this.entity = entity;
        this.state = state;
        this.partialTick = animationState.getPartialTick();
        this.child = entity.isBaby();
        this.riding = entity.isPassenger();
        this.inWater = entity.isInWater();
        this.aggressive = entity instanceof Mob mob && mob.isAggressive();

        float swing = animationState.getLimbSwing();

        // GeckoLib 会给幼年实体预先乘 3（见 GeoEntityRenderer），这里还原成 CEM 的 limb_swing
        this.limbSwing = this.child ? swing / 3.0F : swing;
        this.limbSpeed = animationState.getLimbSwingAmount();
        this.swingProgress = entity.getAttackAnim(this.partialTick);
        this.hurtTime = entity.hurtTime;
        this.hurt = -Mth.sin(this.hurtTime / 2.0F) * this.hurtTime / 10.0F;

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

        if (modelData != null) {
            // GeoEntityRenderer 写入 EntityModelData 时对这两个值取过反
            this.headPitch = -modelData.headPitch();
            this.headYaw = -modelData.netHeadYaw();
        } else {
            this.headPitch = entity.getXRot();
            this.headYaw = Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot);
        }

        this.clampedHeadYaw = Mth.clamp(this.headYaw, -90.0F, 90.0F);
        this.age = entity.tickCount + this.partialTick;
        this.random = CemAnimator.random(entity.getId());
    }

    public T entity() {
        return this.entity;
    }

    public float partialTick() {
        return this.partialTick;
    }

    /** {@code is_child} */
    public boolean child() {
        return this.child;
    }

    /** {@code is_riding} */
    public boolean riding() {
        return this.riding;
    }

    /** {@code is_in_water} */
    public boolean inWater() {
        return this.inWater;
    }

    /** {@code is_aggressive} */
    public boolean aggressive() {
        return this.aggressive;
    }

    /** {@code limb_swing} */
    public float limbSwing() {
        return this.limbSwing;
    }

    /** {@code limb_speed} */
    public float limbSpeed() {
        return this.limbSpeed;
    }

    /** {@code swing_progress} */
    public float swingProgress() {
        return this.swingProgress;
    }

    /** {@code hurt_time} */
    public int hurtTime() {
        return this.hurtTime;
    }

    /** {@code var.hurt} */
    public float hurt() {
        return this.hurt;
    }

    /** {@code head_yaw} */
    public float headYaw() {
        return this.headYaw;
    }

    /** {@code head_pitch} */
    public float headPitch() {
        return this.headPitch;
    }

    /** 常见的 {@code var.hy}：{@code clamp(head_yaw, -90, 90)} */
    public float clampedHeadYaw() {
        return this.clampedHeadYaw;
    }

    /** {@code age} */
    public float age() {
        return this.age;
    }

    /** {@code random(id)} */
    public float random() {
        return this.random;
    }

    /** 读取 {@code var.NAME}，未赋值时为 0（与 CEM 一致）。 */
    public float var(String name) {
        return this.state.get(name);
    }

    /** 写入 {@code var.NAME}，供后续帧使用。 */
    public void setVar(String name, float value) {
        this.state.set(name, value);
    }

    /**
     * 距离上一次状态更新的 tick 数（最多 20），并推进计数器。
     *
     * <p>对应 CEM 里 {@code var.x + step * frame_time * 20} 的累加：按 tick 而不是按帧累积，
     * 因此与帧率无关。同一帧内重复调用只会拿到一次步长。
     */
    public int elapsedTicks() {
        return this.state.elapsedTicks(this.entity.tickCount);
    }
}
