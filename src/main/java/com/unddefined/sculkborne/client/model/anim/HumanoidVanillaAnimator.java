package com.unddefined.sculkborne.client.model.anim;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版 {@code HumanoidModel#setupAnim} 的移植，作为僵尸、骷髅、徘徊者三个类人幽匿生物的公共部分。
 *
 * <p>覆盖：头部朝向（含游泳时的低头）、身体与四肢的行走摆动、骑乘姿势、手臂的
 * {@code bobModelPart} 起伏、{@code hat} 跟随 {@code head}，以及最后交给子类的收尾钩子
 * {@link #poseFinal}（僵尸换手臂、骷髅持弓/挥击、末影人缩放手臂与低头）。
 *
 * <p>没有移植的部分（这些分支在本 mod 的生物身上要么不可达、要么需要原版物品骨骼）：
 * 鞘翅滑翔时的速度系数、物品姿势（望远镜/弩/盾/弓以外的持物）、游泳时的手臂划水编排、
 * {@code crouching} 的下蹲位移与 {@code setupAttackAnimation} 里带身体的扭转。
 */
public abstract class HumanoidVanillaAnimator<T extends LivingEntity> extends VanillaAnimator<T> {

    /** 原版 {@code AnimationUtils#bobModelPart} 的 zRot 项，{@code multiplier} 为 ±1。 */
    private static final float BOB_Z = 0.05F;
    private static final float BOB_X = 0.05F;

    protected HumanoidVanillaAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(T entity, VanillaFrame frame) {
        GeoBone head = bone("head");
        GeoBone body = bone("body");
        GeoBone rightArm = bone("right_arm");
        GeoBone leftArm = bone("left_arm");
        GeoBone rightLeg = bone("right_leg");
        GeoBone leftLeg = bone("left_leg");

        // 模型被改过或换了骨架时直接跳过，避免整帧报错
        if (head == null || body == null || rightArm == null || leftArm == null || rightLeg == null || leftLeg == null) {
            return;
        }

        float limbSwing = frame.limbSwing();
        float limbSwingAmount = frame.limbSwingAmount();
        float ageInTicks = frame.ageInTicks();
        float swimAmount = frame.swimAmount();
        float headPitch = rad(frame.headPitch());

        // ---------- head ----------
        // 原版是逐帧插值（读的是上一帧的 head.xRot），这里改成直接从目标角度插值，省掉每实体状态
        if (swimAmount > 0.0F && entity.isVisuallySwimming()) {
            headPitch = Mth.lerp(swimAmount, headPitch, -((float) Math.PI / 4.0F));
        }

        // ---------- body / 四肢的行走摆动 ----------
        // 原版的 f 是鞘翅滑翔时的速度系数，本移植不处理鞘翅，固定为 1
        float walkPhase = limbSwing * 0.6662F;
        float armSwing = limbSwingAmount;
        float legSwing = 1.4F * limbSwingAmount;

        float rightArmX = Mth.cos(walkPhase + (float) Math.PI) * armSwing;
        float leftArmX = Mth.cos(walkPhase) * armSwing;
        float rightLegX = Mth.cos(walkPhase) * legSwing;
        float leftLegX = Mth.cos(walkPhase + (float) Math.PI) * legSwing;

        float rightArmY = 0.0F;
        float leftArmY = 0.0F;
        float rightArmZ = 0.0F;
        float leftArmZ = 0.0F;
        float rightLegY = 0.005F;
        float leftLegY = -0.005F;
        float rightLegZ = 0.005F;
        float leftLegZ = -0.005F;

        if (entity.isPassenger() && entity.getVehicle() != null && entity.getVehicle().shouldRiderSit()) {
            rightArmX += -((float) Math.PI / 5.0F);
            leftArmX += -((float) Math.PI / 5.0F);
            rightLegX = -1.4137167F;
            rightLegY = ((float) Math.PI / 10.0F);
            rightLegZ = 0.07853982F;
            leftLegX = -1.4137167F;
            leftLegY = -((float) Math.PI / 10.0F);
            leftLegZ = -0.07853982F;
        }

        // 原版到这里会给手臂叠一次 bobModelPart，子类随后整段改写手臂（僵尸/骷髅）
        rightArmZ += Mth.cos(ageInTicks * 0.09F) * BOB_Z + BOB_Z;
        rightArmX += Mth.sin(ageInTicks * 0.067F) * BOB_X;
        leftArmZ -= Mth.cos(ageInTicks * 0.09F) * BOB_Z + BOB_Z;
        leftArmX -= Mth.sin(ageInTicks * 0.067F) * BOB_X;

        setRotation(head, headPitch, rad(frame.headYaw()), 0.0F);
        setRotation(body, 0.0F, 0.0F, 0.0F);
        setRotation(rightArm, rightArmX, rightArmY, rightArmZ);
        setRotation(leftArm, leftArmX, leftArmY, leftArmZ);
        setRotation(rightLeg, rightLegX, rightLegY, rightLegZ);
        setRotation(leftLeg, leftLegX, leftLegY, leftLegZ);

        // 原版最后的 hat.copyFrom(head)，子类若要让头单独下沉（末影人）就在此之后再改
        GeoBone headwear = bone("headwear");

        if (headwear != null) {
            copyRotation(head, headwear);
        }

        this.poseFinal(entity, frame, head, body, rightArm, leftArm, rightLeg, leftLeg);
    }

    /**
     * 子类的收尾钩子，对应原版各个 {@code XxxModel#setupAnim} 在 {@code super.setupAnim(...)} 之后
     * 补的那一段（僵尸举臂、骷髅持弓/挥击、末影人的手臂减半与低头等）。
     */
    protected void poseFinal(T entity, VanillaFrame frame, GeoBone head, GeoBone body,
                             GeoBone rightArm, GeoBone leftArm, GeoBone rightLeg, GeoBone leftLeg) {
    }
}
