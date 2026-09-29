package com.unddefined.sculkborne.client.model.anim;

import com.unddefined.sculkborne.entities.SculkZombieEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版 {@code ZombieModel} 的移植：{@code AbstractZombieModel#setupAnim} 在
 * {@code HumanoidModel} 之后再调一次 {@code AnimationUtils#animateZombieArms}，
 * 把双臂改成前伸的僵尸姿势，并按 {@code attackTime} 挥击。
 */
public final class ZombieVanillaAnimator extends HumanoidVanillaAnimator<SculkZombieEntity> {

    /** 原版 {@code ZombieModel#isAggressive}：潜行/暴怒时判定为激进。 */
    private static final float AGGRO_ARM_PITCH = -((float) Math.PI / 1.5F);
    private static final float IDLE_ARM_PITCH = -((float) Math.PI / 2.25F);

    public ZombieVanillaAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void poseFinal(SculkZombieEntity entity, VanillaFrame frame, GeoBone head, GeoBone body,
                             GeoBone rightArm, GeoBone leftArm, GeoBone rightLeg, GeoBone leftLeg) {
        this.animateZombieArms(entity.isAggressive(), frame, rightArm, leftArm);
    }

    private void animateZombieArms(boolean aggressive, VanillaFrame frame, GeoBone rightArm, GeoBone leftArm) {
        float attackTime = frame.attackTime();
        float swing = Mth.sin(attackTime * (float) Math.PI);
        float swingDown = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);

        float armPitch = aggressive ? AGGRO_ARM_PITCH : IDLE_ARM_PITCH;
        float rightArmX = armPitch + swing * 1.2F - swingDown * 0.4F;
        float leftArmX = armPitch + swing * 1.2F - swingDown * 0.4F;

        // 原版在改姿势前把 zRot 清零，再把 bob 叠回来
        float rightArmZ = Mth.cos(frame.ageInTicks() * 0.09F) * 0.05F + 0.05F;
        float leftArmZ = -(Mth.cos(frame.ageInTicks() * 0.09F) * 0.05F + 0.05F);
        rightArmX += Mth.sin(frame.ageInTicks() * 0.067F) * 0.05F;
        leftArmX -= Mth.sin(frame.ageInTicks() * 0.067F) * 0.05F;

        setRotation(rightArm, rightArmX, -(0.1F - swing * 0.6F), rightArmZ);
        setRotation(leftArm, leftArmX, 0.1F - swing * 0.6F, leftArmZ);
    }
}
