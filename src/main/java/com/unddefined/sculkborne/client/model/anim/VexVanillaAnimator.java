package com.unddefined.sculkborne.client.model.anim;

import com.unddefined.sculkborne.entities.SculkShadeEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版 {@code VexModel#setupAnim} 的移植：头部跟随、手臂前后摆动、身体前倾、翅膀拍动，
 * 充能（攻击）时切换成举武器的姿势。
 *
 * <h2>层级补偿</h2>
 * <p>原版的手臂与翅膀是 {@code body} 的子骨骼，身体前倾会带着它们一起转；geo 里它们与
 * {@code body} 平级，所以下面的手臂/翅膀都额外加上了身体的 {@code xRot}，等价于原版的父子关系。
 *
 * <p>原版 {@code body} 由两块方块组成，geo 拆成 {@code body} + 子骨骼 {@code body2}，
 * 只写 {@code body} 即可。
 */
public final class VexVanillaAnimator extends VanillaAnimator<SculkShadeEntity> {

    /** 原版未充能时的身体前倾角（弧度）。 */
    private static final float BODY_IDLE_PITCH = 0.15707964F;

    public VexVanillaAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(SculkShadeEntity entity, VanillaFrame frame) {
        GeoBone head = bone("head");
        GeoBone body = bone("body");
        GeoBone rightArm = bone("right_arm");
        GeoBone leftArm = bone("left_arm");
        GeoBone rightWing = bone("right_wing");
        GeoBone leftWing = bone("left_wing");

        if (head == null || body == null || rightArm == null || leftArm == null
                || rightWing == null || leftWing == null) {
            return;
        }

        float ageInTicks = frame.ageInTicks();
        boolean charging = entity.isCharging();

        // 原式是 cos(ageInTicks * 5.5F * (π/180))，5.5F 本身已经是角度，所以这里用 rad(5.5F)
        float flap = Mth.cos(ageInTicks * rad(5.5F)) * 0.1F;
        float bodyPitch = charging ? 0.0F : BODY_IDLE_PITCH;

        setRotation(head, rad(frame.headPitch()), rad(frame.headYaw()), 0.0F);
        setRotation(body, bodyPitch, 0.0F, 0.0F);

        float rightArmX = bodyPitch;
        float leftArmX = bodyPitch;
        float rightArmY = 0.0F;
        float leftArmY = 0.0F;
        float rightArmZ = (float) Math.PI / 5.0F + flap;
        float leftArmZ = -((float) Math.PI / 5.0F + flap);

        if (charging) {
            ItemStack mainHand = entity.getMainHandItem();
            ItemStack offHand = entity.getOffhandItem();

            if (mainHand.isEmpty() && offHand.isEmpty()) {
                rightArmX = -1.2217305F;
                rightArmY = 0.2617994F;
                rightArmZ = -0.47123888F - flap;
                leftArmX = -1.2217305F;
                leftArmY = -0.2617994F;
                leftArmZ = 0.47123888F + flap;
            } else {
                if (!mainHand.isEmpty()) {
                    rightArmX = 3.6651914F;
                    rightArmY = 0.2617994F;
                    rightArmZ = -0.47123888F - flap;
                }

                if (!offHand.isEmpty()) {
                    leftArmX = 3.6651914F;
                    leftArmY = -0.2617994F;
                    leftArmZ = 0.47123888F + flap;
                }
            }
        }

        setRotation(rightArm, rightArmX, rightArmY, rightArmZ);
        setRotation(leftArm, leftArmX, leftArmY, leftArmZ);

        // 原式是 1.0995574F + cos(ageInTicks * 45.836624F * (π/180)) * (π/180) * 16.2F
        float wingYaw = 1.0995574F + Mth.cos(ageInTicks * rad(45.836624F)) * Mth.DEG_TO_RAD * 16.2F;

        setRotation(leftWing, 0.47123888F + bodyPitch, wingYaw, -0.47123888F);
        setRotation(rightWing, 0.47123888F + bodyPitch, -wingYaw, 0.47123888F);
    }
}
