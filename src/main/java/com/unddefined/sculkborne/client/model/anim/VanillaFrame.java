package com.unddefined.sculkborne.client.model.anim;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * 一帧内从实体与 GeckoLib 动画状态里取出的、原版 {@code setupAnim} 需要的输入。
 *
 * <p>取值方式与原版渲染器一致：{@code limb_swing} / {@code limb_speed} 来自
 * {@code WalkAnimation}，{@code age_in_ticks} 是 {@code tickCount + partialTick}，
 * {@code attack_time} 是 {@code LivingEntity#getAttackAnim}，头部朝向来自
 * {@link EntityModelData}（GeckoLib 写入时对 yaw/pitch 取过反，这里还原回原版符号）。
 */
public final class VanillaFrame {

    private final float limbSwing;
    private final float limbSwingAmount;
    private final float partialTick;
    private final float ageInTicks;
    private final float headYaw;
    private final float headPitch;
    private final float attackTime;
    private final float swimAmount;

    VanillaFrame(LivingEntity entity, AnimationState<?> animationState) {
        this.partialTick = animationState.getPartialTick();
        this.limbSwingAmount = animationState.getLimbSwingAmount();

        // GeckoLib 会给幼年实体预先乘 3（见 GeoEntityRenderer），这里还原成原版的 limb_swing
        float swing = animationState.getLimbSwing();
        this.limbSwing = entity.isBaby() ? swing / 3.0F : swing;

        this.ageInTicks = entity.tickCount + this.partialTick;
        this.attackTime = entity.getAttackAnim(this.partialTick);
        this.swimAmount = entity.getSwimAmount(this.partialTick);

        EntityModelData modelData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

        if (modelData != null) {
            this.headPitch = -modelData.headPitch();
            this.headYaw = -modelData.netHeadYaw();
        } else {
            this.headPitch = entity.getXRot();
            this.headYaw = Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot);
        }
    }

    /** 原版 {@code limbSwing}：{@code WalkAnimation#position}，幼年已还原。 */
    public float limbSwing() {
        return this.limbSwing;
    }

    /** 原版 {@code limbSwingAmount}：{@code WalkAnimation#speed}，已夹到 1。 */
    public float limbSwingAmount() {
        return this.limbSwingAmount;
    }

    public float partialTick() {
        return this.partialTick;
    }

    /** 原版 {@code ageInTicks}。 */
    public float ageInTicks() {
        return this.ageInTicks;
    }

    /** 原版 {@code netHeadYaw}，单位为度，与 {@code ModelPart.yRot} 同号。 */
    public float headYaw() {
        return this.headYaw;
    }

    /** 原版 {@code headPitch}，单位为度，与 {@code ModelPart.xRot} 同号。 */
    public float headPitch() {
        return this.headPitch;
    }

    /** 原版 {@code Model#attackTime}，即 {@code getAttackAnim}。 */
    public float attackTime() {
        return this.attackTime;
    }

    /** 原版 {@code HumanoidModel#swimAmount}。 */
    public float swimAmount() {
        return this.swimAmount;
    }
}
