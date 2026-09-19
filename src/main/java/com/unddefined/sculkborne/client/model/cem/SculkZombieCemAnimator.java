package com.unddefined.sculkborne.client.model.cem;

import com.unddefined.sculkborne.entities.SculkZombieEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Fresh Animations 僵尸 CEM 动画的 GeckoLib 移植。
 *
 * <p>原始文件：{@code source_codes/FreshAnimations_v1.9.2/assets/minecraft/optifine/cem/zombie.json}。
 * 这个文件只负责僵尸自己的公式：{@code head / headwear / body / left_arm / right_arm / left_leg / right_leg}
 * 的旋转、位移、缩放，以及 {@code var.hy / var.r / var.b / var.ls / var.hurt / var.aggroA / var.aggro / var.att}。
 * 坐标系换算、每实体跨帧状态、每帧的实体输入都在 {@link CemAnimator} / {@link CemFrame} 里，
 * 移植其他生物时照着本类写即可。
 *
 * <h2>取巧的地方</h2>
 * <p>CEM 的位移是绝对值，其中一部分是不随动画变化的常量（头部 {@code ty=0.5}、{@code tz=±0.9/1.8}，
 * 手臂 {@code tx=±5.1}、{@code ty=2.5}、{@code tz=-0.5/-1.8}，腿 {@code tx=±2}、{@code ty=12.2}、
 * {@code tz=-0.2/-0.5}）。这些静止值已经体现在 geo.json 的骨骼静止姿势里，所以下面只把随动画变化的项
 * 当作偏移叠加；若要完全复刻 Fresh Animations 的静止姿势，把这些常量烘焙进 geo.json 即可。
 *
 * <p>{@code var.aggroA} / {@code var.att} 是跨帧变量，按实体保存在 {@link CemFrame} 的状态里；
 * 腿部的 {@code right_leg.rx} 这类自引用在 CEM 中读到的是上一帧的值，这里同样用跨帧变量实现。
 *
 * <p>geo.json 里 {@code head / headwear / left_arm / right_arm} 是 {@code body} 的子骨骼，
 * 这样身体前倾时头和手臂会跟着走（CEM 里这些部位挂在原版模型的同级骨骼上，旋转不会互相带动）。
 * 位移仍按 CEM 的绝对坐标来：写入子骨骼时减掉 {@code body} 的位移，父子叠加后与公式一致。
 */
public final class SculkZombieCemAnimator extends CemAnimator<SculkZombieEntity> {

    /**
     * CEM 里 {@code var.aggroA} 判断手臂是否抬起的阈值，对应 {@code torad(-115)}。
     */
    private static final float AGGRO_ARM_PITCH = rad(-115.0F);

    /**
     * CEM 中 {@code head.ty} 的静止常量，只有腿部位移会用到这个绝对值。
     */
    private static final float HEAD_TY_REST = 0.5F;

    /**
     * CEM 每 tick 的状态变化量（原式是 {@code 0.08 * frame_time * 20}，即约 0.08/tick）。
     */
    private static final float AGGRO_GAIN_PER_TICK = 0.08F;
    private static final float AGGRO_DECAY_PER_TICK = -0.1F;
    private static final float ATTENTION_GAIN_PER_TICK = 0.1F;
    private static final float ATTENTION_DECAY_PER_TICK = -0.1F;

    public SculkZombieCemAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(CemFrame<SculkZombieEntity> frame) {
        GeoBone head = bone("head");
        GeoBone headwear = bone("headwear");
        GeoBone body = bone("body");
        GeoBone leftArm = bone("left_arm");
        GeoBone rightArm = bone("right_arm");
        GeoBone leftLeg = bone("left_leg");
        GeoBone rightLeg = bone("right_leg");

        if (head == null || body == null || leftArm == null || rightArm == null || leftLeg == null || rightLeg == null) {
            return;
        }

        // ---------- CEM 基础变量 ----------
        boolean child = frame.child();
        boolean riding = frame.riding();
        float limbSwing = frame.limbSwing();
        float limbSpeed = frame.limbSpeed();
        float swingProgress = frame.swingProgress();
        float hurt = frame.hurt();
        float clampedYaw = frame.clampedHeadYaw();                                              // var.hy

        // random(id)：同一实体恒定不变，CEM 里既用于 var.r 也单独出现在 head.rx
        float entityRandom = frame.random();
        float randomPhase = entityRandom * Mth.PI * 4.0F;                                       // var.r
        float breathPhase = randomPhase + frame.age() / (child ? 45.0F : 80.0F) * Mth.TWO_PI;   // var.b
        float swayPhase = randomPhase + limbSwing / 1.4F / (child ? 3.0F : 1.0F);               // var.ls

        // ---------- 跨帧状态 var.aggroA / var.att ----------
        // 原式每帧按 frame_time 累加，这里改成按 tick 累加，行为等价且与帧率无关
        int elapsedTicks = frame.elapsedTicks();

        if (elapsedTicks > 0) {
            boolean armsRaised = frame.var("right_arm.rx") < AGGRO_ARM_PITCH && frame.var("left_arm.rx") < AGGRO_ARM_PITCH;
            boolean swinging = swingProgress >= 0.01F && swingProgress <= 0.6F;
            float aggroStep = armsRaised || swinging ? AGGRO_GAIN_PER_TICK : AGGRO_DECAY_PER_TICK;
            float attentionStep = frame.aggressive() ? ATTENTION_GAIN_PER_TICK : ATTENTION_DECAY_PER_TICK;

            frame.setVar("aggroA", Mth.clamp(frame.var("aggroA") + aggroStep * elapsedTicks, 0.0F, 1.0F));
            frame.setVar("att", Mth.clamp(frame.var("att") + attentionStep * elapsedTicks, 0.0F, 1.0F));
        }

        float aggro = 0.5F - 0.5F * Mth.cos(frame.var("aggroA") * Mth.PI);                      // var.aggro
        float attention = frame.var("att");                                                     // var.att

        // ---------- head / headwear / body ----------
        // CEM 在幼年时会放大头部与头饰的位移、缩放
        float headScale = child ? 1.5F : 1.0F;

        // head.rx
        float headRx = Mth.sin(Mth.HALF_PI + swayPhase * 2.0F) / 8.0F * limbSpeed;
        headRx += rad((child ? -5.0F : 10.0F) + Mth.clamp(-10.0F * limbSpeed * 3.0F, -10.0F, -10.0F * aggro))
                * entityRandom * (1.0F - aggro);
        headRx += Mth.sin(-Mth.HALF_PI + breathPhase) / 40.0F;
        headRx += rad(frame.headPitch()) / 1.4F;
        headRx -= Mth.sin(swingProgress * Mth.PI * 2.0F) / 3.0F;
        headRx += rad(30.0F + 30.0F * Mth.sin(limbSwing / 1.5F)) * hurt;
        // head.ry
        float headRy = rad(clampedYaw) / 1.2F + Mth.sin(limbSwing / 2.5F) * hurt;
        // head.rz
        float headRz = Mth.sin(Mth.PI / 4.0F + swayPhase) / 15.0F * limbSpeed + rad(clampedYaw) / 4.0F;

        setRotation(head, headRx, headRy, headRz);

        if (headwear != null) {
            // headwear.rx/ry/rz = head.rx/ry/rz
            setRotation(headwear, headRx, headRy, headRz);
            // headwear.sx/sy/sz = if(is_child, 1.5, 1)
            headwear.setScaleX(headScale);
            headwear.setScaleY(headScale);
            headwear.setScaleZ(headScale);
        }

        // head.tx / ty / tz（相对静止姿势的偏移，见类注释）
        float headTx = -Mth.sin(swayPhase) * limbSpeed / headScale;
        float headTy = (Mth.sin(breathPhase) / 4.0F
                - Mth.sin(rad(child || limbSpeed >= 0.6F ? -45.0F : 45.0F)
                + swayPhase * 2.0F
                + (child || limbSpeed >= 0.6F ? 0.0F : -Mth.cos(swayPhase * 2.0F) / 3.0F))
                * 1.5F * limbSpeed) / headScale;
        headTy += child ? -limbSpeed : 0.0F;
        headTy += Mth.sin(Mth.PI * swingProgress) * 2.0F;
        headTy += (-Mth.sin(limbSwing / 2.0F) / 2.0F - 0.5F) * hurt;
        float headTz = (-Mth.sin(breathPhase) / 4.0F + Mth.sin(-Mth.PI * swingProgress) * 2.0F) / headScale;
        headTz += limbSpeed >= 0.6F ? 0.0F : -Mth.cos(swayPhase * 2.0F) * limbSpeed;

        // body.tx = head.tx, body.ty = head.ty * if(is_child, 1.3, 1), body.tz = head.tz
        float bodyTx = headTx;
        float bodyTy = headTy * (child ? 1.3F : 1.0F);
        float bodyTz = headTz;

        // head 挂在 body 下，减掉 body 的位移后父子叠加的结果仍是 CEM 的绝对值
        setTranslation(head, headTx - bodyTx, headTy - bodyTy, headTz - bodyTz);

        if (headwear != null) {
            // headwear.tx/ty/tz = head.tx/ty/tz * if(is_child, 1.5, 1)
            setTranslation(headwear, headTx * headScale - bodyTx, headTy * headScale - bodyTy, headTz * headScale - bodyTz);
        }

        // body.rx / ry / rz
        float bodyRx = rad(child ? -5.0F : 10.0F);
        bodyRx += Mth.sin(breathPhase) / 40.0F * (child ? -0.8F : 1.0F);
        bodyRx += rad(5.0F) * limbSpeed;
        bodyRx += Mth.sin(Mth.PI * swingProgress) / 3.0F;
        bodyRx += rad(-3.0F - 10.0F * Mth.sin(limbSwing / 2.0F)) * hurt;

        float bodyRy = Mth.sin(Mth.PI / 4.0F + swayPhase) / 10.0F * limbSpeed;
        bodyRy += rad(clampedYaw) / 6.0F;
        bodyRy += rad(20.0F * Mth.cos(limbSwing / 3.0F)) * hurt;

        float bodyRz = headRz - rad(clampedYaw) / 4.0F;
        bodyRz -= Mth.sin(Mth.PI / 4.0F + swayPhase) / 10.0F * limbSpeed;
        bodyRz -= Mth.sin(limbSwing / 2.0F) / 22.0F * hurt;

        setRotation(body, bodyRx, bodyRy, bodyRz);

        // body.tx = head.tx, body.ty = head.ty * if(is_child, 1.3, 1), body.tz = head.tz
        setTranslation(body, bodyTx, bodyTy, bodyTz);

        // ---------- left_arm / right_arm ----------
        float armDivisor = child ? 4.0F : 6.0F;
        float armClamp = child ? -40.0F : -80.0F;
        float armAngle = rad(child || limbSpeed >= 0.6F ? 45.0F : 135.0F);
        float armDrop = Mth.clamp(-120.0F * limbSpeed * 2.0F * (1.0F - aggro) - 70.0F * aggro - 10.0F * attention,
                armClamp, 0.0F);
        float armBase = rad(child ? -70.0F : -10.0F);

        // right_arm.rx
        float rightArmRx = armBase;
        rightArmRx += rad(clampedYaw) / 8.0F;
        rightArmRx += Mth.sin(swayPhase) / armDivisor * limbSpeed;
        rightArmRx += Mth.sin(armAngle + swayPhase * 2.0F) / armDivisor * limbSpeed;
        rightArmRx += rad(armDrop);
        rightArmRx += Mth.sin(breathPhase - Mth.PI / 3.0F) / 20.0F;
        rightArmRx -= Mth.sin(Mth.PI * swingProgress * 2.0F);
        rightArmRx += rad(Mth.sin(limbSwing / 1.7F) * 30.0F - 30.0F) * hurt;
        // left_arm.rx
        float leftArmRx = armBase;
        leftArmRx -= rad(clampedYaw) / 8.0F;
        leftArmRx -= Mth.sin(swayPhase) / armDivisor * limbSpeed;
        leftArmRx += Mth.sin(armAngle + swayPhase * 2.0F) / armDivisor * limbSpeed;
        leftArmRx += rad(armDrop);
        leftArmRx += Mth.sin(breathPhase - Mth.PI / 3.0F) / 20.0F;
        leftArmRx -= Mth.sin(Mth.PI * swingProgress * 2.0F);
        leftArmRx += rad(Mth.sin(limbSwing / 2.0F) * 30.0F - 30.0F) * hurt;

        // right_arm.ry
        float rightArmRy = Mth.clamp(-clampedYaw / 65.0F * rightArmRx, -Mth.PI / 4.0F, Mth.PI / 4.0F) * aggro;
        rightArmRy += rad(-5.0F + 25.0F * limbSpeed);
        rightArmRy += Mth.sin(Mth.HALF_PI + breathPhase) / 20.0F;
        rightArmRy += Mth.sin(-Mth.HALF_PI * swingProgress * 2.0F) / 4.0F;
        rightArmRy += rad(-10.0F) * attention;
        // left_arm.ry
        float leftArmRy = Mth.clamp(-clampedYaw / 65.0F * leftArmRx, -Mth.PI / 4.0F, Mth.PI / 4.0F) * aggro;
        leftArmRy += rad(5.0F - 25.0F * limbSpeed);
        leftArmRy += Mth.sin(-Mth.HALF_PI + breathPhase) / 20.0F;
        leftArmRy -= Mth.sin(-Mth.HALF_PI * swingProgress * 2.0F) / 4.0F;
        leftArmRy += rad(10.0F) * attention;

        // right_arm.rz
        float rightArmRz = rad(frame.inWater() ? 15.0F : 2.0F);
        rightArmRz += Mth.clamp(-rad(frame.headPitch()) / 8.0F, 0.0F, Mth.PI / 4.0F);
        rightArmRz -= Mth.sin(Mth.PI * swingProgress) / 2.0F;
        // left_arm.rz
        float leftArmRz = rad(frame.inWater() ? -15.0F : -2.0F);
        leftArmRz += Mth.clamp(rad(frame.headPitch()) / 8.0F, -Mth.PI / 4.0F, 0.0F);
        leftArmRz += Mth.sin(Mth.PI * swingProgress) / 2.0F;

        setRotation(rightArm, rightArmRx, rightArmRy, rightArmRz);
        setRotation(leftArm, leftArmRx, leftArmRy, leftArmRz);

        // 供下一帧的 var.aggroA 判断使用（CEM 中这些变量在手臂旋转之前求值）
        frame.setVar("right_arm.rx", rightArmRx);
        frame.setVar("left_arm.rx", leftArmRx);

        // right_arm.tx/ty/tz 与 left_arm.tx/ty/tz（同样只取动态部分）
        float armTx = headTx;                                            // -5.1/5.1 + body.tx，静止值由模型提供
        float armTy = -limbSpeed + headTy * (child ? 1.3F : 1.0F) + Mth.sin(-Mth.PI / 3.0F + breathPhase) / 5.0F;
        float armTz = -Mth.sin(breathPhase) / 4.0F
                + (limbSpeed >= 0.6F ? 0.0F : -Mth.cos(swayPhase * 2.0F) * limbSpeed)
                - Mth.sin(Mth.PI * swingProgress) * 4.0F;

        // 手臂同样是 body 的子骨骼，减掉 body 的位移（CEM 公式里的 ±5.1 与 body.tx/ty 由父子关系承担）
        setTranslation(rightArm, armTx - bodyTx, armTy - bodyTy, armTz + rad(clampedYaw) - bodyTz);
        setTranslation(leftArm, armTx - bodyTx, armTy - bodyTy, armTz - rad(clampedYaw) - bodyTz);

        // ---------- left_leg / right_leg ----------
        float legLift = Mth.clamp(limbSpeed * 1.5F, 0.0F, 1.0F);
        float legBaseRx = Mth.sin(breathPhase) / 40.0F;
        legBaseRx += rad(7.0F) * limbSpeed;
        legBaseRx += child ? rad(2.0F) : 0.0F;
        float legExtraRx = Mth.sin(-Mth.PI * swingProgress) / 6.0F;
        float legHurtRx = -40.0F;

        // right_leg.rx：骑乘时保留上一帧的值（CEM: if(is_riding, right_leg.rx, ...)）
        float rightLegRx = riding
                ? frame.var("right_leg.rx")
                : legBaseRx + Mth.sin(swayPhase) / 6.0F + Mth.sin(swayPhase) / (child ? 1.0F : 1.5F) * limbSpeed
                + Mth.clamp(-Mth.cos(Mth.PI / 6.0F + swayPhase) / 3.0F * legLift, 0.0F, Mth.PI / 6.0F);
        rightLegRx += -rad(clampedYaw) / 30.0F;
        rightLegRx += legExtraRx;
        rightLegRx += rad(legHurtRx - 20.0F * Mth.sin(limbSwing / 2.0F)) * hurt;
        // left_leg.rx
        float leftLegRx = riding
                ? frame.var("left_leg.rx")
                : legBaseRx - Mth.sin(swayPhase) / 6.0F - Mth.sin(swayPhase) / (child ? 1.0F : 1.5F) * limbSpeed
                + Mth.clamp(Mth.cos(Mth.PI / 6.0F + swayPhase) / 3.0F * legLift, 0.0F, Mth.PI / 6.0F);
        leftLegRx += rad(clampedYaw) / 30.0F;
        leftLegRx += legExtraRx;
        leftLegRx += rad(legHurtRx + 20.0F * Mth.sin(limbSwing / 2.0F)) * hurt;

        // right_leg.ry / left_leg.ry
        float childLegSplay = child ? Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F) : 0.0F;
        float rightLegRy = riding ? frame.var("right_leg.ry") : rad(5.0F);
        rightLegRy += rad(15.0F) * childLegSplay;
        float leftLegRy = riding ? frame.var("left_leg.ry") : rad(-5.0F);
        leftLegRy += rad(-15.0F) * childLegSplay;

        // right_leg.rz / left_leg.rz
        float rightLegRz = riding ? frame.var("right_leg.rz") : rad(2.0F);
        rightLegRz += child ? Mth.sin(breathPhase) / 100.0F : 0.0F;
        rightLegRz += rad(-Mth.sin(limbSwing / 2.0F) + 5.0F) * hurt;
        float leftLegRz = riding ? frame.var("left_leg.rz") : rad(-2.0F);
        leftLegRz += child ? -Mth.sin(breathPhase) / 100.0F : 0.0F;
        leftLegRz += rad(Mth.sin(limbSwing / 2.0F) - 5.0F) * hurt;

        setRotation(rightLeg, rightLegRx, rightLegRy, rightLegRz);
        setRotation(leftLeg, leftLegRx, leftLegRy, leftLegRz);

        frame.setVar("right_leg.rx", rightLegRx);
        frame.setVar("left_leg.rx", leftLegRx);
        frame.setVar("right_leg.ry", rightLegRy);
        frame.setVar("left_leg.ry", leftLegRy);
        frame.setVar("right_leg.rz", rightLegRz);
        frame.setVar("left_leg.rz", leftLegRz);

        // right_leg.tx / left_leg.tx
        float legTx = -Mth.sin(swayPhase) * limbSpeed / headScale;
        // right_leg/left_leg.ty：抬腿幅度被限制在 4 像素以内（这里用的是 head.ty 的绝对值）
        float headTyAbsolute = HEAD_TY_REST + headTy;
        float rightLegTy = Mth.clamp(Mth.sin(Mth.HALF_PI + swayPhase) * 4.0F * limbSpeed + headTyAbsolute * limbSpeed * 2.0F, -4.0F, 0.0F);
        float leftLegTy = Mth.clamp(-Mth.sin(Mth.HALF_PI + swayPhase) * 4.0F * limbSpeed + headTyAbsolute * limbSpeed * 2.0F, -4.0F, 0.0F);
        // right_leg.tz / left_leg.tz
        float legTz = riding ? 0.0F : -Mth.sin(breathPhase) / 3.0F;
        legTz += Mth.sin(Mth.PI * swingProgress) * 2.0F;
        legTz += (-1.0F - Mth.sin(limbSwing / 2.0F)) * hurt;
        float rightLegTz = legTz + Mth.clamp(Mth.sin(Mth.HALF_PI + swayPhase) * 6.0F * legLift + 3.0F * limbSpeed, -6.0F, 1.0F);
        float leftLegTz = legTz + Mth.clamp(Mth.sin(-Mth.HALF_PI + swayPhase) * 6.0F * legLift + 3.0F * limbSpeed, -6.0F, 1.0F);

        setTranslation(rightLeg, legTx, rightLegTy, rightLegTz + rad(clampedYaw) / 2.0F);
        setTranslation(leftLeg, legTx, leftLegTy, leftLegTz - rad(clampedYaw) / 2.0F);
    }
}
