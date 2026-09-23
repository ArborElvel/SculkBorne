package com.unddefined.sculkborne.client.model.cem;

import com.unddefined.sculkborne.entities.SculkShadeEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Fresh Animations 恼鬼 CEM 动画的 GeckoLib 移植，用于幽影。
 *
 * <p>原始文件：{@code source_codes/FreshAnimations_v1.9.2/assets/minecraft/optifine/cem/vex.jem}。
 * 坐标系换算、每实体跨帧状态、每帧的实体输入都在 {@link CemAnimator} / {@link CemFrame} 里，
 * 这里只负责恼鬼自己的公式：{@code head2 / body / right_arm / left_arm / right_wing2 / left_wing2}
 * 的旋转与位移，以及 {@code var.hy / var.r / var.b / var.wings / var.hurt / var.y /
 * var.aggro_empty / var.raggro / var.laggro / var.all}。
 *
 * <h2>骨骼对应</h2>
 * <ul>
 *   <li>FA 的 {@code head2}（FA 模型里的头方块）对应 geo 的 {@code head}。</li>
 *   <li>FA 的 {@code body} 对应 geo 的 {@code body}，{@code right_arm / left_arm} 同名对应，
 *       {@code right_wing2 / left_wing2} 对应 {@code right_wing / left_wing}。</li>
 *   <li>FA 的 {@code body2}（原版身体的内层加粗方块）对应 geo 的 {@code body2}：它是 {@code body} 的
 *       子骨骼，因此 FA 的局部公式可以直接照抄。geo 里这个方块的枢轴落在外层方块内部（距顶面 1 像素），
 *       与 FA 里 {@code body2} 的枢轴相对方块的位置一致，晃动幅度才不会让内层方块穿出外层。</li>
 * </ul>
 *
 * <h2>没有移植的部分</h2>
 * <ul>
 *   <li>{@code right_brow / left_brow / right_eye / left_eye} 与 {@code *_part}：
 *       FA 附加的眉毛、眼睛和细节子模型，这个模型没有对应的骨骼与方块。</li>
 *   <li>{@code var.testing / var.fps}：FA 的调试开关，出厂值分别是 0 和 1，这里直接取该值，
 *       于是 {@code body.tx*var.testing}、{@code body.ty*var.testing} 两项恒为 0。</li>
 * </ul>
 *
 * <h2>取巧的地方</h2>
 * <p>CEM 的位移是绝对值，其中不随动画变化的常量（{@code body.ty=19.5}、手臂的
 * {@code tx=±2.1 / ty=0.6 / tz=0.2}、翼面的 {@code tx=∓0.85}）把 FA 的模型几何锚定在 FA 自己的
 * 模型坐标系里，geo.json 的骨骼 pivot 已经把这套静止姿势表示出来了，所以下面只把“动画值 - 静止常量”
 * 的偏差当作偏移叠加。
 *
 * <p>geo.json 的六根骨骼都是顶层骨骼（没有 {@code parent}），而 FA 里 {@code head2}、两侧翼面挂在
 * {@code body} 下、手臂挂在原版身体部件下，所以：
 * <ul>
 *   <li>头和翼面写的是父子叠加后的净姿势（{@code body.* + head2.*}、{@code body.* + wing.*}）；</li>
 *   <li>FA 的手臂公式里带 {@code -body.rx/ry/rz}：FA 的手臂挂在身体下，这三项是用来抵消父级旋转的，
 *       抵消之后手臂的净姿势不随身体旋转。geo 的手臂是顶层骨骼、本来就不会继承身体旋转，
 *       所以这里把这层抵消一并去掉，两边的净姿势才对得上（与骷髅移植里显式加回 {@code bodyRy} 是同一件事）；</li>
 *   <li>位移同理：头和手臂、翼面在 FA 里会跟着身体一起平移，这里把 {@code body} 的位移加到它们的偏移上。</li>
 * </ul>
 *
 * <p>翼面的基础后掠 {@code ry=torad(55)+torad(8.8)} 视为已烘焙：FA 的翼面方块沿 ±x 伸展，靠这 55° 的
 * 偏航摆到身后，而 geo 的翼面本来就朝后平铺，所以只写相对这个静止角度的变化量；{@code rx}（翅膀上下扇动）
 * 与 {@code rz}（上反角）是姿势本身，照抄。
 *
 * <p>{@code var.aggro_empty / var.raggro / var.laggro} 是跨帧变量，FA 通过上一帧手臂的 {@code rz/rx}
 * 反推原版恼鬼的冲锋架势（空手前伸、持械后摆），这里直接读 {@link SculkShadeEntity#isCharging()}
 * 与主手/副手物品，语义相同且不受本类自己写入的手臂角度影响。
 *
 * <p>FA 还有一项把上下移动转成俯仰的公式（{@code -(pos_y-var.y)}，{@code var.y} 是 {@code pos_y}
 * 的 6 帧历史）以及翼面相位里的 {@code pos_y}。恼鬼几乎一直在调整高度，这一项的系数又是 1、
 * 夹取范围直接开到 {@code [-pi/3, pi/4]}，于是每 6 帧的高度差只要超过约 1 格就会打满，
 * 随升降来回翻转，看起来就是整只在抽搐；同时它让模型姿势取决于世界坐标，
 * 同一个体在天上地下会不一样。因此没有移植这两处，{@code var.y..var.y6} 自然也不需要。
 */
public final class SculkShadeCemAnimator extends CemAnimator<SculkShadeEntity> {

    /** {@code var.aggro_empty / var.raggro / var.laggro} 每 tick 的变化量（FA 的 {@code 0.2*frame_time*20}）。 */
    private static final float AGGRO_STEP_PER_TICK = 0.2F;

    public SculkShadeCemAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(CemFrame<SculkShadeEntity> frame) {
        GeoBone head = bone("head");
        GeoBone body = bone("body");
        // body2 是可选骨骼：模型里没有拆出内层方块时整段跳过，其余动画照常
        GeoBone body2 = bone("body2");
        GeoBone rightArm = bone("right_arm");
        GeoBone leftArm = bone("left_arm");
        GeoBone rightWing = bone("right_wing");
        GeoBone leftWing = bone("left_wing");

        // 模型被改过或换了骨架时直接跳过，避免整帧报错
        if (head == null || body == null || rightArm == null || leftArm == null
                || rightWing == null || leftWing == null) {
            return;
        }

        SculkShadeEntity entity = frame.entity();

        // ---------- CEM 基础变量 ----------
        boolean riding = frame.riding();
        float limbSwing = frame.limbSwing();
        float limbSpeed = frame.limbSpeed();
        float age = frame.age();
        float hurt = frame.hurt() + deathHurt(entity);                                       // var.hurt
        float lookYaw = rad(frame.clampedHeadYaw());                                         // var.hy
        float headPitch = rad(frame.headPitch());
        float randomPhase = frame.random() * Mth.PI * 4.0F;                                  // var.r
        float breathPhase = randomPhase + age / 20.0F * Mth.PI;                              // var.b
        // var.wings：翅膀相位，摆动幅度还取决于水平移动速度（FA 里的 pos_y 项没有移植，见类注释）
        float wingPhase = randomPhase + (limbSwing / 1.5F + age / 5.0F * Mth.PI) * 1.5F;

        // ---------- 跨帧状态 ----------
        int elapsedTicks = frame.elapsedTicks();

        if (elapsedTicks > 0) {
            boolean charging = entity.isCharging();
            ramp(frame, "aggro_empty", charging && entity.getMainHandItem().isEmpty()
                    && entity.getOffhandItem().isEmpty(), elapsedTicks);
            ramp(frame, "raggro", charging && !entity.getMainHandItem().isEmpty(), elapsedTicks);
            ramp(frame, "laggro", charging && !entity.getOffhandItem().isEmpty(), elapsedTicks);
        }

        float emptyAggro = frame.var("aggro_empty");
        float rightAggro = frame.var("raggro");
        float leftAggro = frame.var("laggro");
        float allAggro = Mth.clamp(rightAggro + leftAggro + emptyAggro, 0.0F, 1.0F);         // var.all

        // ---------- body ----------
        float bodyRx = rad(2.5F)
                + rad(30.0F) * limbSpeed * (1.0F - hurt)
                + Mth.sin(breathPhase - Mth.HALF_PI) / 20.0F
                + headPitch / 4.0F
                + rad(-40.0F) * hurt;
        float bodyRy = lookYaw / 2.0F
                + Mth.sin(breathPhase * 23.0F) / 50.0F * allAggro
                + rad(20.0F * Mth.cos(limbSwing / 3.0F)) * hurt;
        float bodyRz = Mth.sin(breathPhase / 3.0F) / 16.0F
                - Mth.sin(limbSwing / 2.0F) / 22.0F * hurt;

        // body.tx 的整身左右摆动；body.ty 的静止常量 19.5 已烘焙进 pivot，只取呼吸起伏
        float bodyTx = Mth.sin(breathPhase / 3.0F - Mth.HALF_PI) / 2.0F;
        float bodyTy = Mth.sin(breathPhase - Mth.HALF_PI) / 2.0F;
        float bodyTz = -Mth.sin(randomPhase + age / 100.0F * Mth.PI) / 4.0F - limbSpeed / 2.0F;

        setRotation(body, bodyRx, bodyRy, bodyRz);
        setTranslation(body, bodyTx, bodyTy, bodyTz);

        // ---------- body2（原版身体的内层加粗方块） ----------
        // FA 用这几条公式给内层方块做次级晃动：移动时前倾 20°、随呼吸前后轻摆、
        // 抬头低头时跟着压一点、整体沿 z 微移，另外纵向以约 2.5% 的幅度随呼吸伸缩。
        // 它是 body 的子骨骼，公式里的值是相对身体的局部姿势，不需要再加 body 的旋转与位移
        if (body2 != null) {
            // FA 的 body2.sy 里还有一项 cos(var.b*2.3)/40*0，恒为 0，这里省略
            setRotation(body2,
                    rad(20.0F) * limbSpeed - Mth.sin(breathPhase) / 16.0F - headPitch / 6.0F,
                    0.0F,
                    Mth.sin(breathPhase / 3.0F - Mth.HALF_PI) / 10.0F);
            setTranslation(body2, 0.0F, 0.0F, limbSpeed / 10.0F);
            setScale(body2, 1.0F, 1.0F + Mth.sin(breathPhase * 5.3F) / 40.0F, 1.0F);
        }

        // ---------- head（FA 的 head2） ----------
        // FA 里 head2 挂在 body 下，身体前倾时头跟着走，同时 head2 自己再转一份，
        // 两边合起来正好让静止时头是正的、转身时头朝目标；这里写父子叠加后的净姿势
        float head2Rx = headPitch / 2.0F
                - Mth.sin(breathPhase - Mth.HALF_PI) / 20.0F
                - rad(2.5F)
                - rad(30.0F) * limbSpeed
                + rad(30.0F + 30.0F * Mth.sin(limbSwing / 1.5F)) * hurt;
        // 冲锋架势下 var.all 会把低头/回头那一半抵消掉，头因此正对前方
        float head2Ry = lookYaw / 2.0F * (1.0F - allAggro * 2.0F)
                + Mth.sin(breathPhase * 18.0F) / 40.0F * allAggro
                + Mth.sin(limbSwing / 2.5F) * hurt;
        float head2Rz = -Mth.sin(breathPhase / 3.0F) / 16.0F
                + Mth.sin(breathPhase * 25.0F) / 40.0F * allAggro;
        // head2.tz：移动时整体后仰、受伤时上下点动
        float head2Tz = -limbSpeed / 2.0F * (1.0F - hurt)
                + (-0.5F - Mth.sin(limbSwing / 2.0F) / 2.0F) * hurt;

        setRotation(head, bodyRx + head2Rx, bodyRy + head2Ry, bodyRz + head2Rz);
        setTranslation(head, bodyTx, bodyTy, bodyTz + head2Tz);

        // ---------- right_arm / left_arm ----------
        // FA 的 -body.rx/ry/rz 是它用来抵消父级旋转的项：FA 的手臂挂在身体下，抵消后手臂不随身体转。
        // geo 的手臂是顶层骨骼、本来就不继承身体旋转，所以这里连同那三项一起去掉，净姿势才与 FA 一致
        float armRxBase = riding ? rad(-45.0F) : 0.0F;
        float armRxCharge = -rad(80.0F + 30.0F * limbSpeed) * emptyAggro + rad(50.0F) * limbSpeed
                - Mth.sin(breathPhase + rad(60.0F)) / 20.0F;

        float rightArmRx = armRxBase - rad(160.0F) * rightAggro + armRxCharge + lookYaw / 4.0F
                + rad(Mth.sin(limbSwing / 1.7F) * 30.0F - 30.0F) * hurt;
        float leftArmRx = armRxBase - rad(160.0F) * leftAggro + armRxCharge - lookYaw / 4.0F
                + rad(Mth.sin(limbSwing / 2.0F) * 30.0F - 30.0F) * hurt;

        float rightArmRy = rad(30.0F) * rightAggro
                + rad(-15.0F)
                + Mth.sin(breathPhase + rad(60.0F)) / 20.0F
                + rad(-10.0F) * limbSpeed * (1.0F - rightAggro)
                + rad(8.0F) * emptyAggro;
        float leftArmRy = rad(-30.0F) * leftAggro
                + rad(15.0F)
                - Mth.sin(breathPhase + rad(60.0F)) / 20.0F
                + rad(10.0F) * limbSpeed * (1.0F - leftAggro)
                - rad(8.0F) * emptyAggro;

        float rightArmRz = rad(-30.0F) * rightAggro
                - (rad(-15.0F) + Mth.sin(breathPhase + rad(60.0F)) / 20.0F + rad(-10.0F) * limbSpeed)
                        / 2.0F * (1.0F - rightAggro)
                - rad(30.0F) * emptyAggro;
        float leftArmRz = rad(30.0F) * leftAggro
                - (rad(15.0F) - Mth.sin(breathPhase + rad(60.0F)) / 20.0F + rad(10.0F) * limbSpeed)
                        / 2.0F * (1.0F - leftAggro)
                + rad(30.0F) * emptyAggro;

        setRotation(rightArm, rightArmRx, rightArmRy, rightArmRz);
        setRotation(leftArm, leftArmRx, leftArmRy, leftArmRz);

        // 手臂 tx 的静止常量 ±2.1 已烘焙进 pivot（var.testing=0，公式里那一项恒为 0），
        // ty / tz 的静止常量 0.6 与 0.2 同理，所以下面写的就是公式相对静止姿势的变化量，
        // 再加上身体平移，手臂不会在身体晃动时脱开
        float armTy = -Mth.sin(breathPhase) / 6.0F + (riding ? 0.5F : 0.0F) + 0.4F * emptyAggro;
        float armTz = (riding ? -0.5F : 0.0F) - 0.7F * emptyAggro;

        setTranslation(rightArm, bodyTx, bodyTy + armTy, bodyTz + armTz);
        setTranslation(leftArm, bodyTx, bodyTy + armTy, bodyTz + armTz);

        // ---------- right_wing / left_wing（FA 的 wing2） ----------
        float wingRx = (rad(15.0F) - Mth.cos(wingPhase) / 3.0F) * (1.0F - allAggro);
        float wingRy = Mth.sin(wingPhase - Mth.cos(wingPhase) / 2.0F
                * Mth.clamp(2.0F - limbSpeed * 4.0F, 0.0F, 1.0F)) / 2.0F * (1.0F - allAggro)
                + rad(8.8F) * (Mth.clamp(1.0F + limbSpeed, 0.0F, 1.2F) - 1.0F);
        float wingRz = rad(25.0F)
                + Mth.cos(Mth.HALF_PI + wingPhase) / 6.0F * Mth.clamp(1.0F - limbSpeed * 2.0F, 0.0F, 1.0F);

        setRotation(rightWing, bodyRx + wingRx, bodyRy + wingRy, bodyRz + wingRz);
        setRotation(leftWing, bodyRx + wingRx, bodyRy - wingRy, bodyRz - wingRz);

        // 翼面 tx：FA 的 left_wing2.tx = -right_wing2.tx，静止常量 ∓0.85 已烘焙进 pivot
        float wingTx = Mth.sin(Mth.PI / 3.0F + wingPhase) / 3.0F;

        setTranslation(rightWing, bodyTx + wingTx, bodyTy, bodyTz);
        setTranslation(leftWing, bodyTx - wingTx, bodyTy, bodyTz);
    }

    /**
     * {@code var.hurt} 里属于死亡的那一半：{@code -cos(death_time/6)*clamp(death_time/2, 0, 1)}。
     *
     * <p>{@link CemFrame#hurt()} 只给出受伤的一半（{@code -sin(hurt_time/2)*hurt_time/10}），
     * 死亡动画的抖动由这里补上。
     *
     * <p>{@code death_time} 只在死亡动画期间累加（原版 {@code LivingEntity#deathTime} 活着时恒为 0）。
     * 曾经在这里额外乘上 {@code partialTick}，结果活着时该项会在每个 tick 里从 0 爬到 0.49 再归零，
     * 也就是每 tick 给 {@code var.hurt} 塞一次约 0.5 的抖动，身体、头、手臂跟着以 20 Hz 抽动。
     */
    private static float deathHurt(SculkShadeEntity entity) {
        int deathTime = entity.deathTime;

        if (deathTime <= 0) return 0.0F;

        return -Mth.cos(deathTime / 6.0F) * Mth.clamp(deathTime / 2.0F, 0.0F, 1.0F);
    }

    /**
     * 推进一个 0~1 的跨帧状态：{@code rising} 为真时增加，否则减少，每 tick 变化
     * {@value #AGGRO_STEP_PER_TICK}（对应 FA 的 {@code 0.2*frame_time*20}）。
     */
    private static void ramp(CemFrame<SculkShadeEntity> frame, String name, boolean rising, int elapsedTicks) {
        float step = (rising ? AGGRO_STEP_PER_TICK : -AGGRO_STEP_PER_TICK) * elapsedTicks;

        frame.setVar(name, Mth.clamp(frame.var(name) + step, 0.0F, 1.0F));
    }

}
