package com.unddefined.sculkborne.client.model.anim;

import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版 {@code SkeletonModel} 的移植：在 {@code HumanoidModel} 的基础上，激进时按手里的东西切换双臂姿势。
 *
 * <p>原版分成两段：{@code prepareMobModel} 在“激进 + 主手持弓”时把持弓侧的手臂姿势设成
 * {@code BOW_AND_ARROW}（对应 {@code HumanoidModel#setupAnim} 里的 {@code poseRightArm/poseLeftArm}），
 * 之后 {@code setupAnim} 再在“激进 + 主手不是弓”时改成双手挥击。
 *
 * <p>本 mod 的幽匿骷髅沿用原版骷髅的 Goal，会拿弓射击，所以这两段都移植了过来；
 * 其余物品姿势（望远镜、弩、盾）没有移植。
 */
public final class SkeletonVanillaAnimator extends HumanoidVanillaAnimator<SculkSkeletonEntity> {

    public SkeletonVanillaAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void poseFinal(SculkSkeletonEntity entity, VanillaFrame frame, GeoBone head, GeoBone body,
                             GeoBone rightArm, GeoBone leftArm, GeoBone rightLeg, GeoBone leftLeg) {
        boolean aggressive = entity.isAggressive();
        ItemStack mainHand = entity.getMainHandItem();

        if (aggressive && mainHand.is(Items.BOW)) {
            this.poseBow(entity.getMainArm() == HumanoidArm.RIGHT, frame, rightArm, leftArm);
        } else if (aggressive) {
            this.poseMeleeSwing(frame, rightArm, leftArm);
        }
    }

    /**
     * 原版 {@code ArmPose.BOW_AND_ARROW}：拉弓时双臂都转到身前，持弓那只手偏内、另一只偏外。
     *
     * <p>原版按“副手 EMPTY、主手 BOW_AND_ARROW”的顺序分别调用 {@code poseLeftArm / poseRightArm}，
     * 因为持弓姿势会同时写双臂，最终留下的就是持弓那一侧的数值。
     */
    private void poseBow(boolean mainHandRight, VanillaFrame frame, GeoBone rightArm, GeoBone leftArm) {
        float headPitch = rad(frame.headPitch());
        float headYaw = rad(frame.headYaw());
        float armPitch = -((float) Math.PI / 2.0F) + headPitch;

        float rightYaw = mainHandRight ? -0.1F + headYaw : -0.1F + headYaw - 0.4F;
        float leftYaw = mainHandRight ? 0.1F + headYaw + 0.4F : 0.1F + headYaw;

        setRotation(rightArm, armPitch, rightYaw, 0.0F);
        setRotation(leftArm, armPitch, leftYaw, 0.0F);
    }

    /** 原版 {@code SkeletonModel#setupAnim} 的后半段：双手一起向下挥。 */
    private void poseMeleeSwing(VanillaFrame frame, GeoBone rightArm, GeoBone leftArm) {
        float attackTime = frame.attackTime();
        float swing = Mth.sin(attackTime * (float) Math.PI);
        float swingDown = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);
        float armPitch = -((float) Math.PI / 2.0F) - (swing * 1.2F - swingDown * 0.4F);

        float rightArmX = armPitch + Mth.sin(frame.ageInTicks() * 0.067F) * 0.05F;
        float leftArmX = armPitch - Mth.sin(frame.ageInTicks() * 0.067F) * 0.05F;
        float rightArmZ = Mth.cos(frame.ageInTicks() * 0.09F) * 0.05F + 0.05F;
        float leftArmZ = -(Mth.cos(frame.ageInTicks() * 0.09F) * 0.05F + 0.05F);

        setRotation(rightArm, rightArmX, -(0.1F - swing * 0.6F), rightArmZ);
        setRotation(leftArm, leftArmX, 0.1F - swing * 0.6F, leftArmZ);
    }
}
