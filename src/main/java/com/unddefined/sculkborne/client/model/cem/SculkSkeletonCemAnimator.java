package com.unddefined.sculkborne.client.model.cem;

import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Fresh Animations 骷髅 CEM 动画的 GeckoLib 移植。
 *
 * <p>原始文件：{@code source_codes/FreshAnimations_v1.9.2/assets/minecraft/optifine/cem/skeleton.jem}。
 * 这里负责骷髅自己的公式：{@code head / headwear / body / left_arm / right_arm / left_leg / right_leg} 的
 * 旋转与位移，以及 {@code var.hy / var.r / var.b / var.ls / var.hurt / var.att / var.run / var.walk}。
 * 坐标系换算、每实体跨帧状态、每帧的实体输入都在 {@link CemAnimator} / {@link CemFrame} 里，
 * 移植其他生物时照着本类写即可。
 *
 * <h2>取巧的地方</h2>
 * <p>CEM 的位移是绝对值，其中不随动画变化的常量（头部 {@code ty=0.3}，手臂 {@code tx=±5}、{@code ty=2.5}、
 * {@code tz=-0.5}，腿 {@code tx=±2}、{@code ty=12.2}、{@code tz=-0.2}）把 FA 的模型几何重新锚定回原版位置，
 * geo.json 的静止姿势就是原版姿势，所以下面只把“动画值 - 静止常量”的偏差当成偏移叠加；旋转没有这个常量，
 * 直接写入公式值。
 *
 * <p>geo.json 里 {@code head / left_arm / right_arm / left_leg / right_leg} 都是顶层骨骼，与 {@code body}
 * 同级，因此都直接写入 CEM 的绝对旋转与位移（公式里的 {@code body.tx/ty} 已经在式子中显式相加）。
 * 原版 {@code HumanoidModel} 的各部位也确实挂在根骨骼下同级，CEM 不会让一个部位带动另一个部位的旋转：
 * 一旦把 {@code head} 挂到 {@code body} 下，{@code body.ry}（{@code var.att} 抬臂姿势下可达 ±70°）就会带着头
 * 一起转，头就不再朝向目标了。
 * {@code headwear} 是 {@code head} 的子骨骼、pivot 相同，且 CEM 里 {@code headwear.* = head.*}，
 * 所以不用写任何值就会跟着头走。
 *
 * <p>{@code var.att} 是跨帧变量：它读取上一帧的手臂 {@code ry}。{@code head.tz} 与 {@code body.rx} 里的
 * {@code if(var.att!=0||var.att!=2,0,1)} 恒为 0（{@code att} 不可能同时等于 0 和 2），这两项直接省略。
 */
public final class SculkSkeletonCemAnimator extends CemAnimator<SculkSkeletonEntity> {

    /** CEM 里 {@code var.NAME + step * frame_time * 20} 每 tick 的步长。 */
    private static final float RUN_STEP_PER_TICK = 0.1F;

    /** {@code var.run} 开始累加的移动速度阈值。 */
    private static final float RUN_LIMB_SPEED = 0.55F;

    /** {@code var.att} 的四种取值：非敌对（0）、敌对下的两种抬臂姿势（-1/1）、敌对下的普通姿势（2）。 */
    private static final float ATT_CALM = 0.0F;
    private static final float ATT_POSE_A = -1.0F;
    private static final float ATT_POSE_B = 1.0F;
    private static final float ATT_NORMAL = 2.0F;

    /** 头部/{@code body} 位移公式的静止常量，见类注释。 */
    private static final float HEAD_TY_REST = 0.3F;
    /** 手臂位移公式的静止常量，见类注释。 */
    private static final float ARM_TX_REST = 5.0F;
    private static final float ARM_TY_REST = 2.5F;
    private static final float ARM_TZ_REST = -0.5F;
    /** 腿部位移公式的静止常量，见类注释。 */
    private static final float LEG_TX_REST = 2.0F;
    private static final float LEG_TY_REST = 12.2F;
    private static final float LEG_TZ_REST = -0.2F;

    public SculkSkeletonCemAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(CemFrame<SculkSkeletonEntity> frame) {
        GeoBone head = bone("head");
        GeoBone body = bone("body");
        GeoBone leftArm = bone("left_arm");
        GeoBone rightArm = bone("right_arm");
        GeoBone leftLeg = bone("left_leg");
        GeoBone rightLeg = bone("right_leg");

        if (head == null || body == null || leftArm == null || rightArm == null || leftLeg == null || rightLeg == null) {
            return;
        }

        // ---------- CEM 基础变量 ----------
        boolean riding = frame.riding();
        boolean inWater = frame.inWater();
        boolean aggressive = frame.aggressive();
        float limbSwing = frame.limbSwing();
        float limbSpeed = frame.limbSpeed();
        float swingProgress = frame.swingProgress();
        float hurt = frame.hurt();
        float clampedYaw = frame.clampedHeadYaw();                                              // var.hy
        float headPitch = frame.headPitch();

        float entityRandom = frame.random();
        float randomPhase = entityRandom * Mth.PI * 4.0F;                                       // var.r
        float breathPhase = randomPhase + frame.age() / 80.0F * Mth.TWO_PI;                     // var.b
        float swayPhase = randomPhase + limbSwing / 1.3F;                                       // var.ls

        // ---------- 跨帧状态 var.run / var.walk ----------
        // 原式每帧按 frame_time 累加，这里改成按 tick 累加，行为等价且与帧率无关
        int elapsedTicks = frame.elapsedTicks();
        if (elapsedTicks > 0) {
            boolean running = frame.hurtTime() <= 0 && limbSpeed >= RUN_LIMB_SPEED;
            float runAccumulated = frame.var("run") + (running ? RUN_STEP_PER_TICK : -RUN_STEP_PER_TICK) * elapsedTicks;
            frame.setVar("run", Mth.clamp(runAccumulated, 0.0F, 1.0F));
        }

        float run = frame.var("run");                                                           // var.run
        float walk = 1.0F - run;                                                                // var.walk

        // ---------- 跨帧状态 var.att ----------
        // 原式按上一帧的手臂 ry 判断：左臂向内抬到 10° 以上取 -1，右臂向外掰到 -10° 以下取 1，否则取 2
        float att = ATT_CALM;
        if (aggressive) {
            att = frame.var("left_arm.ry") >= rad(10.0F) ? ATT_POSE_A
                    : frame.var("right_arm.ry") <= rad(-10.0F) ? ATT_POSE_B : ATT_NORMAL;
        }

        // ---------- body ----------
        float bodyTx = Mth.cos(-Mth.PI / 4.0F + swayPhase) / 2.0F * limbSpeed
                - 0.4F * hurt
                + Mth.sin(Mth.PI * swingProgress) * 2.0F;
        float bodyTy = Mth.sin(breathPhase) / 4.0F
                + (Mth.cos(rad(120.0F) + swayPhase * 2.0F - Mth.sin(swayPhase * 2.0F) / 3.0F) + 1.0F) * limbSpeed * walk
                + Mth.cos(rad(100.0F) + swayPhase * 2.0F - Mth.sin(rad(-60.0F) + swayPhase * 2.0F) / 3.0F)
                        * 1.3F * limbSpeed * run
                + (riding ? -3.0F : HEAD_TY_REST)
                + Mth.sin(Mth.PI * swingProgress) * 2.0F;
        // body.tz = head.tz
        float headTz = Mth.sin(-Mth.PI * swingProgress) * 4.0F
                + (riding ? -3.0F : 0.0F)
                - 1.7F * limbSpeed * (att == ATT_NORMAL ? 2.0F : 1.0F);
        float bodyTz = headTz;

        float bodyRx = -Mth.sin(breathPhase) / 40.0F
                + rad(10.0F * (att == ATT_NORMAL ? 1.5F : 1.0F)) * limbSpeed
                + Mth.sin(Mth.PI * swingProgress) / 3.0F
                + rad(riding ? 15.0F : 0.0F);
        float bodyRy = rad(att == ATT_POSE_A ? -70.0F : att == ATT_POSE_B ? 70.0F
                : (riding ? 0.0F : -15.0F * Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F)))
                - Mth.sin(swayPhase) / (aggressive ? 8.0F : 4.0F) * limbSpeed
                + rad(clampedYaw) / 6.0F
                + Mth.sin(-Mth.PI * swingProgress) / 4.0F
                + rad(20.0F * Mth.cos(limbSwing / 3.0F)) * hurt;
        float bodyRz = Mth.cos(-Mth.PI / 4.0F + swayPhase) / 20.0F * limbSpeed
                - Mth.sin(limbSwing / 2.0F) / 22.0F * hurt
                + Mth.sin(Mth.PI * swingProgress) / 8.0F;

        setRotation(body, bodyRx, bodyRy, bodyRz);
        setTranslation(body, bodyTx, bodyTy - HEAD_TY_REST, bodyTz);

        // ---------- head ----------
        float headRx = -Mth.sin(Mth.PI / 4.0F + breathPhase) / 40.0F
                + rad(headPitch / 1.4F
                        + Mth.clamp(clampedYaw / 4.0F, 0.0F, 90.0F)
                        + Mth.clamp(-clampedYaw / 4.0F, 0.0F, 90.0F))
                + rad(10.0F + 30.0F * Mth.sin(limbSwing / 1.5F)) * hurt
                - Mth.sin(Mth.PI * 2.0F * swingProgress) / 3.0F;
        float headRy = rad(clampedYaw / 1.2F
                + Mth.clamp(headPitch / 4.0F, -90.0F, 0.0F)
                + Mth.clamp(-headPitch / 4.0F, -90.0F, 0.0F))
                + Mth.sin(limbSwing / 2.5F) * hurt;
        float headRz = rad(5.0F) * (entityRandom * 2.0F - 1.0F) * (1.0F - limbSpeed)
                - Mth.cos(swayPhase) / 15.0F * limbSpeed
                + rad(clampedYaw) / 4.0F
                + Mth.sin(Mth.PI * swingProgress) / 3.0F;

        float headTx = Mth.sin(Mth.PI / 4.0F + swayPhase) / 2.0F * limbSpeed
                + Mth.sin(Mth.PI * swingProgress) * 3.0F;
        float headTy = Mth.sin(-Mth.PI / 12.0F + breathPhase) / 4.0F
                + ((-Mth.sin(rad(10.0F) + swayPhase * 2.0F + Mth.cos(swayPhase * 2.0F) / 3.0F) + 1.5F)
                        * limbSpeed) * walk
                + ((Mth.cos(rad(60.0F) + swayPhase * 2.0F + Mth.cos(swayPhase * 2.0F) / 3.0F) * 1.2F - 0.6F)
                        * limbSpeed) * run
                + rad(Mth.clamp(clampedYaw / 4.0F + headPitch * 2.0F, -90.0F, 0.0F)
                        + Mth.clamp(-clampedYaw / 4.0F - headPitch * 2.0F, -90.0F, 0.0F))
                + (riding ? -3.0F : HEAD_TY_REST)
                + (Mth.sin(limbSwing / 2.0F) / 2.0F - 1.5F) * hurt
                + Mth.sin(Mth.PI * swingProgress) * 2.0F;

        setRotation(head, headRx, headRy, headRz);
        // head 与 body 同级，写入 CEM 的绝对位移即可（见类注释）
        setTranslation(head, headTx, headTy - HEAD_TY_REST, headTz);

        // ---------- left_arm / right_arm ----------
        float armSpeedSwing = Mth.sin(Mth.HALF_PI + swayPhase / 2.0F) / 6.0F * limbSpeed;
        float armLift = rad(Mth.clamp(-135.0F * limbSpeed, -90.0F, 0.0F));

        float rightArmRx;
        if (att == ATT_POSE_A) {
            rightArmRx = rad(-100.0F) + rad(headPitch);
        } else if (att == ATT_POSE_B) {
            rightArmRx = rad(-90.0F) + rad(headPitch);
        } else if (att == ATT_NORMAL) {
            rightArmRx = rad(clampedYaw) / 8.0F - Mth.sin(swayPhase) / 6.0F * limbSpeed + armSpeedSwing + armLift;
        } else {
            rightArmRx = rad(riding ? -8.0F : clampedYaw / 8.0F - 50.0F * Mth.sin(swayPhase) * limbSpeed);
        }
        rightArmRx += riding ? 0.0F : Mth.sin(breathPhase + rad(60.0F)) / 40.0F;
        rightArmRx += rad(Mth.sin(limbSwing / 1.7F) * 30.0F - 30.0F) * hurt;
        rightArmRx -= Mth.sin(Mth.PI * 2.0F * swingProgress);

        float leftArmRx;
        if (att == ATT_POSE_A) {
            leftArmRx = rad(-90.0F) + rad(headPitch);
        } else if (att == ATT_POSE_B) {
            leftArmRx = rad(-100.0F) + rad(headPitch);
        } else if (att == ATT_NORMAL) {
            leftArmRx = -rad(clampedYaw) / 8.0F + Mth.sin(swayPhase) / 6.0F * limbSpeed + armSpeedSwing + armLift;
        } else {
            leftArmRx = rad(riding ? -35.0F : -clampedYaw / 8.0F + 50.0F * Mth.sin(swayPhase) * limbSpeed);
        }
        leftArmRx += riding ? 0.0F : Mth.sin(breathPhase + rad(60.0F)) / 40.0F;
        leftArmRx += rad(Mth.sin(limbSwing / 2.0F) * 30.0F - 30.0F) * hurt;
        leftArmRx += Mth.sin(Mth.PI * swingProgress) * 2.0F;

        float rightArmRy = (att == ATT_POSE_A ? 0.0F
                : att == ATT_POSE_B ? rad(-20.0F)
                : bodyRy + Mth.sin(breathPhase + Mth.HALF_PI) / 20.0F)
                - rad(8.0F) * Mth.clamp(limbSpeed * 3.0F, 0.0F, 1.0F)
                + Mth.sin(-Mth.PI * swingProgress) / 4.0F;
        float leftArmRy = (att == ATT_POSE_A ? rad(20.0F)
                : att == ATT_POSE_B ? 0.0F
                : bodyRy + Mth.sin(breathPhase - Mth.HALF_PI) / 20.0F)
                + rad(8.0F) * Mth.clamp(limbSpeed * 3.0F, 0.0F, 1.0F);

        float rightArmRz = (att == ATT_POSE_A ? rad(-8.0F)
                : att == ATT_POSE_B ? rad(-5.0F)
                : riding ? rad(35.0F)
                : rad(5.0F + (inWater ? 10.0F : 0.0F) + Mth.clamp(-headPitch / 8.0F, 0.0F, 45.0F)))
                + Mth.sin(-Mth.PI * swingProgress)
                + rad(10.0F) * run;
        float leftArmRz = (att == ATT_POSE_A ? rad(5.0F)
                : att == ATT_POSE_B ? rad(8.0F)
                : riding ? rad(10.0F)
                : rad(-5.0F + (inWater ? -10.0F : 0.0F) + Mth.clamp(headPitch / 8.0F, -45.0F, 0.0F)))
                - Mth.sin(Mth.PI * swingProgress)
                - rad(10.0F) * run;

        float rightArmTx = (att == ATT_POSE_A ? -1.0F : att == ATT_POSE_B ? -4.0F : -ARM_TX_REST + bodyTx)
                + Mth.clamp(-rad(clampedYaw) / 2.7F, 0.0F, 0.6F)
                - hurt
                + Mth.sin(Mth.PI * swingProgress) * 2.0F;
        float leftArmTx = (att == ATT_POSE_A ? 4.0F : att == ATT_POSE_B ? 1.0F : ARM_TX_REST + bodyTx)
                + Mth.clamp(rad(clampedYaw) / 2.0F, -0.8F, 0.0F)
                + hurt
                - Mth.sin(Mth.PI * swingProgress) * 2.0F;

        float armTyBase = att == ATT_POSE_A || att == ATT_POSE_B ? 2.0F : ARM_TY_REST;
        float armTyMotion = (-Mth.sin(rad(10.0F) + swayPhase * 2.0F + Mth.cos(Mth.PI / 4.0F + swayPhase * 2.0F) / 3.0F) * walk
                + Mth.cos(rad(-20.0F) + swayPhase * 2.0F + Mth.cos(Mth.PI / 4.0F + swayPhase * 2.0F) / 3.0F) * run
                + 1.5F) * limbSpeed / (1.0F + run);
        float rightArmTy = armTyBase
                + Mth.sin(breathPhase - Mth.PI / 8.0F) / 4.0F
                + armTyMotion
                + (bodyTy - 1.0F) * run
                + (riding ? -3.0F : 0.0F);
        float leftArmTy = armTyBase
                + Mth.sin(breathPhase - Mth.PI / 8.0F) / 4.0F
                + (riding ? 1.0F : 0.0F)
                + armTyMotion
                + (bodyTy - 1.0F) * run
                + (riding ? -3.0F : 0.0F);

        float armTzAggro = (aggressive ? -2.0F * (att == ATT_NORMAL ? 1.5F : 1.0F) * limbSpeed : 0.0F);
        float rightArmTz = (att == ATT_POSE_A ? -6.0F
                : att == ATT_POSE_B ? -1.0F
                : ARM_TZ_REST + (-1.0F - Mth.sin(Mth.PI / 12.0F + swayPhase - Mth.cos(swayPhase) / 2.0F) * 3.0F)
                        / (aggressive ? 4.0F : 1.0F) * limbSpeed
                        + armTzAggro
                        + (riding ? 0.0F : -0.5F * Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F)))
                + (riding ? -2.0F : 0.0F)
                + rad(clampedYaw)
                - Mth.sin(Mth.PI * swingProgress) * 6.0F;
        float leftArmTz = (att == ATT_POSE_A ? -1.0F
                : att == ATT_POSE_B ? -6.0F
                : ARM_TZ_REST + (-1.0F + Mth.sin(Mth.PI / 12.0F + swayPhase + Mth.cos(swayPhase) / 2.0F) * 3.0F)
                        / (aggressive ? 4.0F : 1.0F) * limbSpeed
                        + armTzAggro
                        + (riding ? 0.0F : 2.0F * Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F)))
                + (riding ? -2.0F : 0.0F)
                - rad(clampedYaw);

        setRotation(rightArm, rightArmRx, rightArmRy, rightArmRz);
        setRotation(leftArm, leftArmRx, leftArmRy, leftArmRz);
        // 供下一帧的 var.att 判断使用（CEM 中这些变量在手臂旋转之前求值）
        frame.setVar("right_arm.ry", rightArmRy);
        frame.setVar("left_arm.ry", leftArmRy);

        // 手臂是顶层骨骼，公式里的 body.tx / body.ty 直接相加即可
        setTranslation(rightArm,
                rightArmTx - (-ARM_TX_REST),
                rightArmTy - ARM_TY_REST,
                rightArmTz - ARM_TZ_REST);
        setTranslation(leftArm,
                leftArmTx - ARM_TX_REST,
                leftArmTy - ARM_TY_REST,
                leftArmTz - ARM_TZ_REST);

        // ---------- left_leg / right_leg ----------
        float legLift = Mth.clamp(limbSpeed * 1.5F, 0.0F, 1.0F);
        float legTwist = -Mth.sin(swayPhase) / 6.0F * limbSpeed * walk;

        float rightLegStep = (asin(Mth.sin(swayPhase)) / 1.3F * Mth.clamp(limbSpeed, 0.15F, 1.0F)
                + rad(3.0F + 40.0F * Mth.clamp(-Mth.cos(swayPhase), 0.0F, 1.0F)) * legLift) * walk;
        float rightLegRun = (Mth.sin(swayPhase) / 1.5F * limbSpeed
                + Mth.clamp(-Mth.cos(rad(15.0F) + swayPhase) / 3.0F * legLift, 0.0F, Mth.PI / 6.0F)
                + rad(10.0F) * limbSpeed) * run;
        float leftLegStep = (asin(-Mth.sin(swayPhase)) / 1.3F * Mth.clamp(limbSpeed, 0.15F, 1.0F)
                + rad(3.0F + 40.0F * Mth.clamp(Mth.cos(swayPhase), 0.0F, 1.0F)) * legLift) * walk;
        float leftLegRun = (-Mth.sin(swayPhase) / 1.5F * limbSpeed
                + Mth.clamp(Mth.cos(rad(15.0F) + swayPhase) / 3.0F * legLift, 0.0F, Mth.PI / 6.0F)
                + rad(10.0F) * limbSpeed) * run;

        float legIdleRx = Mth.sin(breathPhase) / 40.0F;
        float rightLegRx = riding ? rad(-80.0F)
                : (att == ATT_POSE_A || att == ATT_POSE_B ? 0.0F : rightLegStep + rightLegRun) + legIdleRx;
        rightLegRx += rad(-40.0F - 20.0F * Mth.sin(limbSwing / 2.0F)) * hurt;
        float leftLegRx = riding ? rad(-80.0F)
                : (att == ATT_POSE_A || att == ATT_POSE_B ? 0.0F : leftLegStep + leftLegRun) + legIdleRx;
        leftLegRx += rad(-40.0F + 20.0F * Mth.sin(limbSwing / 2.0F)) * hurt;

        float rightLegRy = (att == ATT_POSE_A ? rad(-10.0F + (riding ? 20.0F : 0.0F))
                : att == ATT_POSE_B ? rad(60.0F)
                : (riding ? rad(20.0F) : 0.0F)) + legTwist;
        float leftLegRy = (att == ATT_POSE_A ? rad(-60.0F)
                : att == ATT_POSE_B ? rad(10.0F + (riding ? -20.0F : 0.0F))
                : (riding ? rad(-20.0F) : rad(-20.0F * Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F)))) + legTwist;

        float legAttRzRight = att == ATT_POSE_A || att == ATT_POSE_B
                ? Mth.clamp(Mth.sin(swayPhase) / 6.0F + Mth.sin(swayPhase) / 1.5F * limbSpeed + rad(10.0F) * limbSpeed, -1.0F, 1.0F)
                : 0.0F;
        float legAttRzLeft = att == ATT_POSE_A || att == ATT_POSE_B
                ? Mth.clamp(-Mth.sin(swayPhase) / 6.0F - Mth.sin(swayPhase) / 1.5F * limbSpeed + rad(10.0F) * limbSpeed, -1.0F, 1.0F)
                : 0.0F;
        float rightLegRz = (riding ? 0.0F
                : rad(5.0F) * Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F) + legAttRzRight)
                + Mth.clamp(Mth.cos(swayPhase) / 7.0F * limbSpeed - rad(3.0F), 0.0F, 1.0F) * walk
                + rad(-Mth.sin(limbSwing / 2.0F) + 5.0F) * hurt;
        float leftLegRz = (riding ? 0.0F
                : rad(-5.0F) * Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F) + legAttRzLeft)
                + Mth.clamp(Mth.cos(swayPhase) / 7.0F * limbSpeed + rad(3.0F), -1.0F, 0.0F) * walk
                + rad(Mth.sin(limbSwing / 2.0F) - 5.0F) * hurt;

        float rightLegTx = (att == ATT_POSE_A ? -0.5F + (riding ? -3.0F : 0.0F)
                : att == ATT_POSE_B ? -0.5F + (riding ? 3.0F : 0.0F)
                : -LEG_TX_REST) + (riding ? -1.0F : 0.0F);
        float leftLegTx = (att == ATT_POSE_A ? 0.5F + (riding ? -3.0F : 0.0F)
                : att == ATT_POSE_B ? 0.5F + (riding ? 3.0F : 0.0F)
                : LEG_TX_REST + (!riding && att != ATT_POSE_A && att != ATT_POSE_B
                        ? Mth.sin(breathPhase) / 10.0F : 0.0F))
                + (riding ? 1.0F : 0.0F);

        float rightLegTy = (att == ATT_POSE_A || att == ATT_POSE_B ? 0.2F + (riding ? 1.0F : 0.0F)
                : 0.2F + Mth.clamp(Mth.cos(swayPhase) * 1.5F * limbSpeed + bodyTy - 0.8F, -4.0F, 0.0F) * run)
                + 12.0F
                + (riding ? -4.5F : 0.0F)
                + (-asin(Mth.cos(swayPhase)) * 1.15F * limbSpeed
                        + (1.0F - 2.0F * Mth.clamp(-Mth.cos(-Mth.PI / 6.0F + swayPhase), 0.0F, 1.0F)) * legLift) * walk;
        float leftLegTy = (att == ATT_POSE_A || att == ATT_POSE_B ? 0.2F + (riding ? 1.0F : 0.0F)
                : 0.2F + Mth.clamp(-Mth.cos(swayPhase) * 1.5F * limbSpeed + bodyTy - 0.8F, -4.0F, 0.0F) * run)
                + 12.0F
                + (riding ? -4.5F : 0.0F)
                + (asin(Mth.cos(swayPhase)) * 1.15F * limbSpeed
                        + (1.0F - 2.0F * Mth.clamp(Mth.cos(-Mth.PI / 6.0F + swayPhase), 0.0F, 1.0F)) * legLift) * walk;

        float rightLegTz = (att == ATT_POSE_A ? -2.0F + (riding ? -2.0F : 0.0F)
                : att == ATT_POSE_B ? 2.0F + (riding ? -2.0F : 0.0F)
                : LEG_TZ_REST
                        + (Mth.clamp(Mth.cos(-Mth.PI / 8.0F + swayPhase) * 4.0F * legLift + limbSpeed, -3.0F, 0.0F)
                                + limbSpeed) * walk
                        + (0.5F - 3.5F * Mth.clamp(-Mth.cos(-Mth.PI / 8.0F + swayPhase), 0.0F, 1.0F) * legLift) * run
                        + (riding ? 0.0F : -0.5F * Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F)))
                + (riding ? 0.0F : -Mth.sin(breathPhase) / 3.0F)
                + (-1.0F - Mth.sin(limbSwing / 2.0F)) * hurt;
        float leftLegTz = (att == ATT_POSE_A ? 2.0F + (riding ? -2.0F : 0.0F)
                : att == ATT_POSE_B ? -2.0F + (riding ? -2.0F : 0.0F)
                : LEG_TZ_REST
                        + (Mth.clamp(-Mth.cos(-Mth.PI / 8.0F + swayPhase) * 4.0F * legLift + limbSpeed, -3.0F, 0.0F)
                                + limbSpeed) * walk
                        + (0.5F - 3.5F * Mth.clamp(Mth.cos(-Mth.PI / 8.0F + swayPhase), 0.0F, 1.0F) * legLift) * run
                        + (riding ? 0.0F : 0.7F * Mth.clamp(1.0F - limbSpeed * 3.0F, 0.0F, 1.0F)))
                + (riding ? 0.0F : -Mth.sin(breathPhase) / 4.0F)
                + (-1.0F - Mth.sin(limbSwing / 2.0F)) * hurt;

        setRotation(rightLeg, rightLegRx, rightLegRy, rightLegRz);
        setRotation(leftLeg, leftLegRx, leftLegRy, leftLegRz);
        setTranslation(rightLeg,
                rightLegTx - (-LEG_TX_REST),
                rightLegTy - LEG_TY_REST,
                rightLegTz - LEG_TZ_REST);
        setTranslation(leftLeg,
                leftLegTx - LEG_TX_REST,
                leftLegTy - LEG_TY_REST,
                leftLegTz - LEG_TZ_REST);
    }

    /** CEM 的 {@code asin()}。 */
    private static float asin(float value) {
        return (float) Math.asin(value);
    }
}
