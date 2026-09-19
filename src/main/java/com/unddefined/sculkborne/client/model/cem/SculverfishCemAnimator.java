package com.unddefined.sculkborne.client.model.cem;

import com.unddefined.sculkborne.entities.SculverfishEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Fresh Animations 蠹虫 CEM 动画的 GeckoLib 移植。
 *
 * <p>原始文件：{@code source_codes/FreshAnimations_v1.9.2/assets/minecraft/optifine/cem/silverfish.jem}。
 * 坐标系换算、每实体跨帧状态、每帧的实体输入都在 {@link CemAnimator} / {@link CemFrame} 里，
 * 这里只负责蠹虫自己的公式：{@code body2 / head1 / head1_s / head / tail / tail_s / tail1 /
 * tail1_s / tail2 / tail3 / tail4} 的旋转、位移与缩放，以及 {@code var.hy / var.r / var.b /
 * var.ls / var.yaw / var.die}。
 *
 * <h2>与原始文件的骨骼对应</h2>
 * <p>FA 把原版蠹虫从“七个平铺的体节 + 三个外层”改写成一条链：{@code body2} 是整条身体的根，
 * 前半段挂在 {@code head1} 下、后半段挂在 {@code tail} 下，每节再分出下一节。geo.json 按同样的
 * 层级重排过（方块坐标与 UV 未动，只改了骨骼名、父子关系与 pivot），因此这里的公式可以逐条照抄：
 * <ul>
 *   <li>{@code body2}（骨骼枢轴在腹部中央）、{@code head1}、{@code tail}、{@code tail1} 是 FA 新增的容器骨骼，
 *       本身不带方块。</li>
 *   <li>FA 的 {@code head1_s}（第 2 节 + wing3）、{@code head}（第 1 节）、{@code tail_s}（第 3 节 + wing1）、
 *       {@code tail1_s}（第 4 节）、{@code tail2}（第 5 节 + wing2）、{@code tail3}（第 6 节）、
 *       {@code tail4}（第 7 节）分别带着原来的 {@code body2..body7} 体节方块。</li>
 *   <li>FA 把三个外层（{@code wing1..wing3}）并进了对应体节的方块列表，geo.json 里仍保留成独立骨骼，
 *       作为 {@code tail_s / tail2 / head1_s} 的子骨骼，旋转与缩放会跟着宿主一起走。</li>
 *   <li>FA 的 {@code body1} 只是一个空壳（放 FA 的署名子模型），没有移植。</li>
 * </ul>
 *
 * <h2>取巧的地方</h2>
 * <p>CEM 的位移是绝对值，其中不随动画变化的常量（{@code body2.ty=24}、{@code body2.tz=-2}、
 * {@code head.ty=-0.5}）把 FA 的模型几何重新锚定回原版位置，geo.json 的骨骼 pivot 已经放在这些位置上，
 * 所以下面只把“动画值 - 静止常量”的偏差当成偏移叠加。
 *
 * <p>唯一一处偏差是欧拉角的应用顺序：OptiFine 与 GeckoLib 对同一骨骼上多轴同时旋转的组合顺序不同，
 * 关节角度很小（除了死亡时的 {@code rx}），因此只体现为亚像素级差别。
 */
public final class SculverfishCemAnimator extends CemAnimator<SculverfishEntity> {

    public SculverfishCemAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(CemFrame<SculverfishEntity> frame) {
        GeoBone body2 = bone("body2");
        GeoBone head1 = bone("head1");
        GeoBone head1S = bone("head1_s");
        GeoBone head = bone("head");
        GeoBone tail = bone("tail");
        GeoBone tailS = bone("tail_s");
        GeoBone tail1 = bone("tail1");
        GeoBone tail1S = bone("tail1_s");
        GeoBone tail2 = bone("tail2");
        GeoBone tail3 = bone("tail3");
        GeoBone tail4 = bone("tail4");

        // 模型被改过或换了骨架时直接跳过，避免整帧报错
        if (body2 == null || head1 == null || head1S == null || head == null || tail == null || tailS == null
                || tail1 == null || tail1S == null || tail2 == null || tail3 == null || tail4 == null) {
            return;
        }

        // ---------- CEM 基础变量 ----------
        float entityRandom = frame.random();
        float randomPhase = entityRandom * Mth.PI * 4.0F;                               // var.r
        float breathPhase = randomPhase + frame.age() / (10.0F - entityRandom * 2.0F) * Mth.PI; // var.b
        float swingPhase = randomPhase + frame.limbSwing();                             // var.ls
        float lookYaw = rad(frame.clampedHeadYaw())                                     // var.yaw
                * Mth.clamp(frame.limbSpeed() * 4.0F, 0.0F, 1.0F);
        float death = deathProgress(frame);                                             // var.die

        // 两个反复出现的活动度系数：移动与静止
        float moving = Mth.clamp(frame.limbSpeed() * 2.0F, 0.0F, 1.0F);                 // clamp(limb_speed*2, 0, 1)
        float resting = Mth.clamp(1.0F - frame.limbSpeed() * 2.0F, 0.0F, 1.0F);         // clamp(1-limb_speed*2, 0, 1)

        // 抬头/低头量，head1 与 head 共用
        float pitchRot = Mth.clamp(rad(frame.headPitch()) / 2.0F, rad(-15.0F), 0.0F);

        // ---------- body2：整条身体的根节点 ----------
        float body2Rx = (frame.hurtTime() > 0 ? -Mth.sin(frame.hurtTime() / 10.0F * Mth.PI) : 0.0F)
                + Mth.sin(Mth.PI * frame.swingProgress()) / 2.0F
                - rad(20.0F) * death;

        setRotation(body2, body2Rx, 0.0F, 0.0F);
        setTranslation(body2,
                Mth.sin(rad(315.0F) + swingPhase),                                     // body2.tx：整条身体左右摆动
                3.0F * death,                                                           // body2.ty（静止常量 24 已烘焙进 pivot）
                0.0F);                                                                  // body2.tz（静止常量 -2 已烘焙进 pivot）

        // 每只个体的固定体型差异
        float bodyScale = 0.9F + (0.5F - entityRandom) / 3.0F;
        setScale(body2, bodyScale, bodyScale, bodyScale);

        // ---------- head1 / head1_s / head ----------
        setRotation(head1,
                pitchRot - rad(2.0F) + Mth.sin(breathPhase / 2.0F) / 20.0F - body2Rx + rad(40.0F) * death,
                Mth.sin(rad(270.0F) + swingPhase) / 3.0F + lookYaw / 2.0F,
                -Mth.cos(rad(270.0F) + swingPhase) / 10.0F * moving);
        setTranslation(head1, Mth.sin(rad(360.0F) + swingPhase) / 1.5F * moving, 0.0F, 0.0F);

        float segmentBreath = 1.05F - Mth.cos(breathPhase * 1.2F) / 60.0F;
        setScale(head1S, segmentBreath, segmentBreath, segmentBreath);

        setRotation(head,
                pitchRot + Mth.sin(breathPhase * 1.2F) / 20.0F - body2Rx / 3.0F + rad(40.0F) * death,
                -Mth.sin(rad(270.0F) + swingPhase) / 3.0F + lookYaw / 2.0F
                        + Mth.sin(breathPhase / 2.3F) / 10.0F * resting,
                Mth.sin(rad(315.0F) + swingPhase) / 10.0F * moving);
        // head.ty 的静止常量 -0.5 已烘焙进 pivot
        setTranslation(head, Mth.sin(rad(405.0F) + swingPhase) / 1.5F * moving, 0.0F, 0.0F);

        // ---------- tail / tail_s / tail1 / tail1_s ----------
        setRotation(tail,
                rad(30.0F) * death,
                Mth.sin(rad(225.0F) + swingPhase) / 4.0F + lookYaw / 2.0F,
                -Mth.cos(rad(225.0F) + swingPhase) / 10.0F * moving);
        setTranslation(tail, Mth.sin(rad(315.0F) + swingPhase) / 1.5F * moving, 0.0F, 0.0F);

        // 第 3 节（含 wing1）的呼吸起伏比其它节明显，FA 用 1/40 而不是 1/60
        float tailSegmentBreath = 1.05F - Mth.cos(breathPhase * 1.2F) / 40.0F;
        setScale(tailS, tailSegmentBreath, tailSegmentBreath, tailSegmentBreath);

        setRotation(tail1,
                body2Rx,
                Mth.sin(rad(180.0F) + swingPhase) / 4.0F + lookYaw / 2.0F,
                -Mth.cos(rad(180.0F) + swingPhase) / 10.0F * moving);
        setTranslation(tail1, Mth.sin(rad(270.0F) + swingPhase) / 2.5F * moving, 0.0F, 0.0F);

        setScale(tail1S, segmentBreath, segmentBreath, segmentBreath);

        // ---------- tail2 / tail3 / tail4 ----------
        setRotation(tail2,
                body2Rx - rad(20.0F) * death,
                Mth.sin(rad(135.0F) + swingPhase) / 4.0F + lookYaw / 2.0F
                        + Mth.sin(breathPhase + rad(225.0F)) / 10.0F * resting,
                -Mth.cos(rad(135.0F) + swingPhase) / 10.0F * moving);

        setRotation(tail3,
                (rad(10.0F) - Mth.sin(breathPhase) / 6.0F) * resting + body2Rx - rad(60.0F) * death,
                Mth.sin(rad(90.0F) + swingPhase) / 4.0F + lookYaw / 2.0F
                        + Mth.sin(breathPhase + rad(180.0F)) / 6.0F * resting,
                -Mth.cos(rad(90.0F) + swingPhase) / 10.0F * moving);

        setRotation(tail4,
                rad(30.0F) * Mth.clamp(1.0F - frame.limbSpeed(), 0.5F, 1.0F)
                        + Mth.sin(breathPhase + rad(135.0F)) / 6.0F * resting
                        + body2Rx - rad(60.0F) * death,
                Mth.sin(rad(45.0F) + swingPhase) / 4.0F + lookYaw / 2.0F
                        + Mth.sin(breathPhase + rad(90.0F)) / 6.0F * resting,
                -Mth.cos(rad(45.0F) + swingPhase) / 10.0F * moving);
    }

    /**
     * {@code var.die}：活着时为 0；死亡后若已经过了受伤动画（{@code hurt_time} 归零）就是 1，
     * 否则用一段余弦从约 0.9 过渡到 1，让身体蜷缩的幅度接上死亡前的那一下抖动。
     */
    private static float deathProgress(CemFrame<SculverfishEntity> frame) {
        if (frame.entity().isAlive()) return 0.0F;

        int hurtTime = frame.hurtTime();

        return hurtTime > 0 ? Mth.cos(hurtTime / 4.0F) / 1.8F + 0.4F : 1.0F;
    }
}
