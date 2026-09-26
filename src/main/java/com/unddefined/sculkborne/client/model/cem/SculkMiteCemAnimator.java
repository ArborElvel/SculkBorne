package com.unddefined.sculkborne.client.model.cem;

import com.unddefined.sculkborne.entities.SculkMiteEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Fresh Animations 末影螨 CEM 动画的 GeckoLib 移植。
 *
 * <p>原始文件：{@code source_codes/FreshAnimations_v1.9.2/assets/minecraft/optifine/cem/endermite.jem}。
 * 坐标系换算与每帧的实体输入都在 {@link CemAnimator} / {@link CemFrame} 里，这里只负责末影螨自己的公式：
 * {@code body2 / head / head_s / tail / tail_s / tail1 / tail1_s / tail2} 的旋转、位移与缩放，
 * 以及 {@code var.r / var.b / var.ls / var.yaw / var.die}。
 *
 * <h2>与原始文件的骨骼对应</h2>
 * <p>FA 把原版末影螨从“四个平铺的体节”改写成一条链：{@code body2} 是整条身体的根，前半段挂在
 * {@code head} 下、后半段挂在 {@code tail} 下，每节再分出下一节。geo.json 按同样的层级重排过
 * （方块坐标与 UV 未动，只改了骨骼名、父子关系与 pivot），因此下面的公式可以逐条照抄：
 * <ul>
 *   <li>{@code body2}、{@code head}、{@code tail}、{@code tail1} 是 FA 新增的容器骨骼，本身不带方块。</li>
 *   <li>{@code head_s}（第 1 节）、{@code tail_s}（第 2 节）、{@code tail1_s}（第 3 节）、{@code tail2}（第 4 节）
 *       分别带着原版 {@code segment0..segment3} 的方块。</li>
 *   <li>FA 的 {@code body1} 只是一个空壳（放 FA 的署名子模型），{@code body3} 是挂动画用的零体积部件，
 *       两者都没有移植。</li>
 * </ul>
 *
 * <h2>取巧的地方</h2>
 * <p>CEM 的位移是绝对值，其中不随动画变化的常量（{@code body2.ty=24}、{@code body2.tz=-1}、{@code head.ty=-1}）
 * 把 FA 的模型几何重新锚定回原版位置，geo.json 的骨骼 pivot 已经放在这些位置上，
 * 所以下面只把“动画值 - 静止常量”的偏差当成偏移叠加，{@code head.ty} 的常量同样烘焙进了 pivot。
 */
public final class SculkMiteCemAnimator extends CemAnimator<SculkMiteEntity> {

    public SculkMiteCemAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(CemFrame<SculkMiteEntity> frame) {
        GeoBone body2 = bone("body2");
        GeoBone head = bone("head");
        GeoBone headS = bone("head_s");
        GeoBone tail = bone("tail");
        GeoBone tailS = bone("tail_s");
        GeoBone tail1 = bone("tail1");
        GeoBone tail1S = bone("tail1_s");
        GeoBone tail2 = bone("tail2");

        // 模型被改过或换了骨架时直接跳过，避免整帧报错
        if (body2 == null || head == null || headS == null || tail == null || tailS == null
                || tail1 == null || tail1S == null || tail2 == null) {
            return;
        }

        // ---------- CEM 基础变量 ----------
        float entityRandom = frame.random();
        float randomPhase = entityRandom * Mth.PI * 4.0F;                                       // var.r
        float breathPhase = randomPhase + frame.age() / (8.0F - entityRandom * 2.0F) * Mth.PI;  // var.b
        float swingPhase = randomPhase + frame.limbSwing() * 1.3F;                              // var.ls
        float lookYaw = rad(frame.clampedHeadYaw())                                             // var.yaw
                * Mth.clamp(frame.limbSpeed() * 4.0F, 0.0F, 1.0F);
        float death = deathProgress(frame);                                                     // var.die

        // 两个反复出现的活动度系数：移动与静止
        float moving = Mth.clamp(frame.limbSpeed() * 2.0F, 0.0F, 1.0F);                         // clamp(limb_speed*2, 0, 1)
        float resting = Mth.clamp(1.0F - frame.limbSpeed() * 2.0F, 0.0F, 1.0F);                 // clamp(1-limb_speed*2, 0, 1)

        // 抬头/低头量，head 与 body2.rx 共用
        float pitchRot = Mth.clamp(rad(frame.headPitch()) / 2.0F, rad(-15.0F), 0.0F);

        // ---------- body2：整条身体的根节点 ----------
        float body2Rx = (frame.hurtTime() > 0 ? -Mth.sin(frame.hurtTime() / 10.0F * Mth.PI) : 0.0F)
                - Mth.sin(Mth.PI * frame.swingProgress()) / 2.0F;

        setRotation(body2, body2Rx, 0.0F, 0.0F);
        setTranslation(body2,
                Mth.sin(rad(315.0F) + swingPhase),                                      // body2.tx：整条身体左右摆动
                3.5F * death - Mth.sin(Mth.PI * frame.swingProgress()),                 // body2.ty（静止常量 24 已烘焙进 pivot）
                -Mth.sin(Mth.PI * frame.swingProgress()) * 2.0F);                       // body2.tz（静止常量 -1 已烘焙进 pivot）

        // 每只个体的固定体型差异
        float bodyScale = 0.9F + (0.5F - entityRandom) / 3.0F;
        setScale(body2, bodyScale, bodyScale, bodyScale);

        // ---------- head / head_s ----------
        setRotation(head,
                pitchRot + (rad(-5.0F) + Mth.cos(breathPhase / 2.3F + Mth.sin(breathPhase / 2.3F) / 2.0F) / 16.0F) * resting
                        - body2Rx + rad(50.0F) * death,
                Mth.sin(rad(270.0F) + swingPhase) / 8.0F - lookYaw / 3.0F
                        + Mth.sin(breathPhase / 4.6F + Mth.sin(breathPhase / 2.3F) / 2.0F) / 6.0F * resting,
                -Mth.sin(rad(240.0F) + swingPhase) / 10.0F * moving);
        // head.ty 的静止常量 -1 已烘焙进 pivot
        setTranslation(head, Mth.sin(rad(360.0F) + swingPhase) / 1.5F * moving, 0.0F, 0.0F);

        float segmentBreath = 1.05F - Mth.cos(breathPhase * 1.2F) / 60.0F;
        setScale(headS, segmentBreath, segmentBreath, segmentBreath);

        // ---------- tail / tail_s ----------
        // FA 没有给 tail 写 rx，尾根只跟着 body2.rx 走
        setRotation(tail,
                0.0F,
                Mth.sin(rad(225.0F) + swingPhase) / 4.0F + lookYaw / 2.0F,
                -Mth.cos(rad(225.0F) + swingPhase) / 10.0F * moving);
        setTranslation(tail, Mth.sin(rad(315.0F) + swingPhase) / 1.5F * moving, 0.0F, 0.0F);

        // 第 2 节的呼吸起伏比其它节明显，FA 用 1/40 而不是 1/60
        float tailSegmentBreath = 1.05F - Mth.cos(breathPhase * 1.2F) / 40.0F;
        setScale(tailS, tailSegmentBreath, tailSegmentBreath, tailSegmentBreath);

        // ---------- tail1 / tail1_s ----------
        setRotation(tail1,
                -body2Rx / 1.5F - rad(30.0F) * death,
                Mth.sin(rad(180.0F) + swingPhase) / 4.0F + lookYaw / 2.0F,
                -Mth.cos(rad(180.0F) + swingPhase) / 10.0F * moving);
        setTranslation(tail1, Mth.sin(rad(270.0F) + swingPhase) / 2.5F * moving, 0.0F, 0.0F);

        setScale(tail1S, segmentBreath, segmentBreath, segmentBreath);

        // ---------- tail2：尾端，死亡时蜷缩得比前一节更明显 ----------
        setRotation(tail2,
                -body2Rx / 2.0F - rad(50.0F) * death,
                Mth.sin(rad(135.0F) + swingPhase) / 4.0F + lookYaw / 2.0F,
                -Mth.cos(rad(135.0F) + swingPhase) / 10.0F * moving);
    }

    /**
     * {@code var.die}：活着时为 0；死亡后若已经过了受伤动画（{@code hurt_time} 归零）就是 1，
     * 否则用一段余弦从约 0.9 过渡到 1，让身体蜷缩的幅度接上死亡前的那一下抖动。
     */
    private static float deathProgress(CemFrame<SculkMiteEntity> frame) {
        if (frame.entity().isAlive()) return 0.0F;

        int hurtTime = frame.hurtTime();

        return hurtTime > 0 ? Mth.cos(hurtTime / 4.0F) / 1.8F + 0.4F : 1.0F;
    }
}
