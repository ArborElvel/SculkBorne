package com.unddefined.sculkborne.client.model.cem;

import com.unddefined.sculkborne.entities.CreesperEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Fresh Animations 苦力怕 CEM 动画的 GeckoLib 移植。
 *
 * <p>原始文件：{@code source_codes/FreshAnimations_v1.9.2/assets/minecraft/optifine/cem/creeper.json}。
 * 这里负责苦力怕自己的公式：{@code head2 / body / leg1..leg4 / legN_foot} 的旋转与位移，以及
 * {@code var.hy / var.r / var.b / var.ls / var.swim / var.hurt / var.run / var.walk / var.aggro /
 * var.nov1 / var.nov2}。坐标系换算、每实体跨帧状态、每帧的实体输入都在 {@link CemAnimator} /
 * {@link CemFrame} 里，移植其他生物时照着本类写即可。
 *
 * <h2>与原始文件的骨骼对应</h2>
 * <ul>
 *   <li>FA 的 {@code head2} 是挂在 body 下、绕脖子转动的头部方块（脸与眼睛在 {@code head_part.jpm} 里），
 *       对应 geo.json 的 {@code head}。</li>
 *   <li>FA 的 {@code body} 对应 {@code body}。FA 的身体绕髋部转动（{@code body.ty} 的静止值是 17.5，
 *       即 y-up 的 6.5），geo.json 的 body 骨骼 pivot 已挪到同一位置，这样身体前倾时头会跟着一起走。</li>
 *   <li>FA 的 {@code leg1..leg4} 对应 {@code leg1..leg4}（原版 right_hind / left_hind / right_front /
 *       left_front）。FA 的腿是 {@code legN}（绕髋部）加 {@code legN_foot}（绕脚掌）两级，geo.json 里只有一根腿骨骼，
 *       所以把脚掌的旋转叠加到大腿上。脚掌的 {@code ry} 只绕竖直轴，与两者的 pivot 无关，完全等价；
 *       脚掌的 {@code rx / rz} 会相差约 1 像素。</li>
 *   <li>FA 的 {@code *_eye（眼球）/ *_eye_pupil（瞳孔，含 {@code _in/_ou/_do/_up} 逐边裁剪）/
 *       *_eyelid + *_blink（眼皮）} 对应 geo.json 里同名的眼睛骨骼；缺失的骨骼会被跳过，
 *       例如只有 {@code right_eye} 时只推瞳孔方块、没有眨眼。眼睛的角度量在 CEM 里是“度数”，不经过 {@code torad}。</li>
 * </ul>
 *
 * <h2>取巧的地方</h2>
 * <p>CEM 的位移是绝对值，其中不随动画变化的常量（{@code body.ty=17.5}、{@code legN.ty=19}、
 * {@code legN.tz=±3}、{@code legN_foot.tx=∓2.2/∓2.4}、{@code legN_foot.tz=3/-5}）把 FA 的模型几何
 * 重新锚定回原版位置，geo.json 的静止姿势就是原版姿势，所以下面只把“动画值 - 静止常量”的偏差当成偏移叠加。
 *
 * <p>唯一一处主动偏离原式的是脚掌的前后滑动（见 {@link #FOOT_SLIDE_SCALE}）：FA 的滑动量是按它自己的腿部模型
 * 调的，放到原版尺寸的腿上会让后腿整根离开身体。
 */
public final class CreesperCemAnimator extends CemAnimator<CreesperEntity> {

    /**
     * CEM 里 {@code var.N + step * frame_time * 20} 的每 tick 步长。
     */
    private static final float RUN_STEP_PER_TICK = 0.1F;
    private static final float AGGRO_GAIN_PER_TICK = 0.08F;
    private static final float AGGRO_DECAY_PER_TICK = -0.1F;
    private static final float NOV2_STEP_PER_TICK = 0.2F;

    /**
     * 走路时脚掌前后滑动的缩放。
     *
     * <p>FA 原式里 {@code legN_foot.tz} 会相对静止位置前后滑 3.8 像素（后腿最多后撤 4.6 像素），
     * 那是按 FA 自己的腿部模型调出来的；本模型用的是原版 4×6×4 的腿，整根腿一起平移会很明显地
     * 看到后腿离开身体。这里保留原来的相位与快慢，只把这个滑动量收窄到约 1 像素以内。
     */
    private static final float FOOT_SLIDE_SCALE = 0.4F;

    public CreesperCemAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(CemFrame<CreesperEntity> frame) {
        GeoBone head = bone("head");
        GeoBone body = bone("body");
        GeoBone leg1 = bone("leg1");
        GeoBone leg2 = bone("leg2");
        GeoBone leg3 = bone("leg3");
        GeoBone leg4 = bone("leg4");

        if (head == null || body == null || leg1 == null || leg2 == null || leg3 == null || leg4 == null) {
            return;
        }

        // ---------- CEM 基础变量 ----------
        float limbSwing = frame.limbSwing();
        float limbSpeed = frame.limbSpeed();
        float hurt = frame.hurt();
        float clampedYaw = frame.clampedHeadYaw();                                  // var.hy
        float headPitch = frame.headPitch();
        float age = frame.age();

        float randomPhase = frame.random() * Mth.PI * 4.0F;                         // var.r
        float breathPhase = randomPhase + age / 35.0F * Mth.TWO_PI;                 // var.b
        float swayPhase = limbSwing * 1.3F;                                         // var.ls
        float swimPhase = randomPhase + age / 2.1F + limbSwing;                     // var.swim

        // CEM 里到处出现的 !is_on_ground && is_in_water
        boolean swimming = !frame.entity().onGround() && frame.inWater();

        // ---------- 跨帧状态 var.run / var.aggro / var.nov2 ----------
        // 原式每帧按 frame_time 累加，这里改成按 tick 累加，行为等价且与帧率无关
        int elapsedTicks = frame.elapsedTicks();

        if (elapsedTicks > 0) {
            boolean sprinting = frame.entity().onGround() && frame.hurtTime() == 0 && limbSpeed >= 0.67F;
            float runStep = sprinting ? RUN_STEP_PER_TICK : -RUN_STEP_PER_TICK;
            frame.setVar("run", Mth.clamp(frame.var("run") + runStep * elapsedTicks, 0.0F, 1.0F));

            float aggroStep = frame.aggressive() ? AGGRO_GAIN_PER_TICK : AGGRO_DECAY_PER_TICK;
            frame.setVar("aggro", Mth.clamp(frame.var("aggro") + aggroStep * elapsedTicks, 0.0F, 1.0F));

            float glanceSweep = Mth.sin(randomPhase + age / 90.0F) * 10.0F;
            boolean noticing = headPitch != 0.0F || clampedYaw != 0.0F || (glanceSweep >= -4.0F && glanceSweep <= 4.0F);
            float noveltyStep = noticing ? NOV2_STEP_PER_TICK : -NOV2_STEP_PER_TICK;
            frame.setVar("nov2", Mth.clamp(frame.var("nov2") + noveltyStep * elapsedTicks, 0.0F, 1.0F));
        }

        float run = frame.var("run");                                               // var.run
        float walk = swimming ? 0.0F : Mth.clamp(1.0F - run, 0.0F, 1.0F);           // var.walk
        float novelty2 = frame.var("nov2");                                         // var.nov2
        float novelty1 = (0.5F - 0.5F * Mth.cos(Mth.clamp(Mth.cos(randomPhase + age / 80.0F) * 10.0F - 8.0F, 0.0F, 1.0F) * Mth.PI))
                * (1.0F - frame.var("aggro"));                                      // var.nov1

        // ---------- 公式里反复出现的系数 ----------
        float stance = Mth.clamp(1.0F - limbSpeed * 1.5F, 0.5F, 1.0F);             // clamp(1-limb_speed*1.5, 0.5, 1)
        float step = Mth.clamp(limbSpeed * 3.0F, 0.0F, 1.0F);                       // clamp(limb_speed*3, 0, 1)
        float lift = Mth.clamp(limbSpeed * 3.0F, 0.2F, 1.0F);                       // clamp(limb_speed*3, 0.2, 1)
        float glide = Mth.clamp(1.5F - limbSpeed * 3.0F, 0.0F, 1.0F);               // clamp(1.5-limb_speed*3, 0, 1)
        float bob = Mth.clamp(1.0F - limbSpeed * 3.0F, 0.2F, 1.0F);                 // clamp(1-limb_speed*3, 0.2, 1)
        float swimKick = Mth.clamp(limbSpeed * 10.0F, 0.0F, 1.0F);                  // clamp(limb_speed*10, 0, 1)
        float knee = Mth.clamp(limbSpeed * 1.5F, 0.0F, 1.0F);                       // clamp(limb_speed*1.5, 0, 1)
        float thighSwing = step * walk * 1.2F;                                      // clamp(limb_speed*3, 0, 1)*var.walk*1.2
        float footSwing = lift * walk * 1.2F;                                       // clamp(limb_speed*3, 0.2, 1)*var.walk*1.2

        // var.nov1 的那段 sin 在 CEM 里有三种相位（0、-pi/8、+pi/8）
        float glancePhase = randomPhase + age / 12.0F;                              // var.r + age/12
        float glanceDouble = glancePhase * 2.0F;
        float nodSway = Mth.clamp(Mth.sin(glancePhase + Mth.sin(glanceDouble)) * 1.05F, -1.0F, 1.0F);
        float nodBack = Mth.clamp(Mth.sin(glancePhase - Mth.PI / 8.0F + Mth.sin(glanceDouble - Mth.PI / 8.0F)) * 1.05F, -1.0F, 1.0F);
        float nodAhead = Mth.clamp(Mth.sin(glancePhase + Mth.PI / 8.0F + Mth.sin(glanceDouble + Mth.PI / 8.0F)) * 1.05F, -1.0F, 1.0F);

        // ---------- head2 ----------
        float headRx = rad(headPitch) / 1.2F;
        headRx += swimming
                ? -rad(5.0F) - Mth.sin(swimPhase) / 10.0F
                : Mth.sin(Mth.PI / 4.0F + swayPhase / 1.3F) / 4.0F * run
                + rad(Mth.sin(swayPhase * 2.0F)) * walk * step;
        headRx -= Mth.cos(breathPhase) / 50.0F;
        headRx += rad(30.0F + 30.0F * Mth.sin(limbSwing / 1.5F)) * hurt;
        headRx -= rad(20.0F * limbSpeed);

        float headRy = rad(clampedYaw) / 2.0F;
        headRy += swimming
                ? -Mth.sin(Mth.PI / 4.0F + swimPhase) / 6.0F
                : rad(Mth.cos(swayPhase) * 8.0F) * walk;
        headRy += Mth.sin(limbSwing / 2.5F) * hurt;
        headRy += nodSway / 3.0F * novelty1;
        headRy += rad(3.0F * Mth.clamp(Mth.sin(5.0F + age / 16.0F) * 7.0F + Mth.cos(5.0F + age / 7.0F) * 7.0F, -1.0F, 1.0F)) * novelty2;

        float headRz = -rad(clampedYaw) / 8.0F;
        headRz += rad(Mth.sin(swayPhase) * 3.0F) * walk * step;
        headRz -= Mth.cos(breathPhase / 2.0F) / 30.0F * bob;
        headRz += nodAhead / 9.0F * novelty1;
        headRz += rad(3.0F * Mth.clamp(Mth.sin(age / 19.0F) * 7.0F, -1.0F, 1.0F)) * novelty2;

        setRotation(head, headRx, headRy, headRz);
        setTranslation(head,
                -rad(clampedYaw) / 2.0F + Mth.sin(swayPhase) / 6.0F * step * walk,     // head2.tx
                0.0F,                                                                   // head2 没有 ty 动画
                (-0.3F + Mth.cos(Mth.PI / 4.0F + swayPhase * 2.0F) / 4.0F) * step * walk - 0.7F * run);   // head2.tz

        // ---------- body ----------
        float bodyRx = swimming
                ? rad(5.0F) + Mth.sin(swimPhase) / 10.0F
                : -Mth.sin(swayPhase / 1.3F) / 8.0F * run - rad(Mth.sin(swayPhase * 2.0F)) * walk * step;
        bodyRx += Mth.sin(breathPhase - Mth.cos(breathPhase) / 2.0F) / 40.0F;
        bodyRx -= rad(headPitch) / 4.0F;
        bodyRx += rad(-35.0F - 7.0F * Mth.sin(limbSwing / 2.0F)) * hurt;
        bodyRx += rad(20.0F * limbSpeed);

        float bodyRy = swimming
                ? Mth.sin(Mth.PI / 4.0F + swimPhase) / 6.0F
                : -rad(Mth.cos(swayPhase) * 8.0F) * walk;
        bodyRy += rad(clampedYaw) / 3.0F;
        bodyRy += rad(20.0F * Mth.cos(limbSwing / 2.5F)) * hurt;
        bodyRy += nodBack / 6.0F * novelty1;

        float bodyRz = swimming ? 0.0F : -rad(Mth.sin(swayPhase) * 3.0F) * walk * step;
        bodyRz += Mth.sin(breathPhase / 2.0F) / 30.0F * bob;
        bodyRz += rad(clampedYaw) / 8.0F;
        bodyRz -= Mth.sin(limbSwing / 2.0F) / 22.0F * hurt;
        bodyRz -= nodSway / 18.0F * novelty1;

        setRotation(body, bodyRx, bodyRy, bodyRz);

        // body.tx 恒为 0；ty / tz 只叠加相对静止常量（17.5 / 0）的偏差
        float bodyTy = Mth.sin(breathPhase) / 6.0F;
        bodyTy += swimming
                ? 4.0F + Mth.sin(-Mth.PI / 1.5F + swimPhase) / 2.0F
                : (Mth.sin(-Mth.PI / 4.0F + swayPhase / 1.3F) * 1.5F - 1.0F) * limbSpeed * run
                + (-0.3F + Mth.sin(swayPhase * 2.0F - Mth.sin(swayPhase * 2.0F) / 2.0F) / 2.0F) * walk * step;
        bodyTy += (-Mth.sin(limbSwing / 2.0F) / 2.0F - 0.5F) * hurt;
        bodyTy += knee;

        float bodyTz = swimming
                ? Mth.cos(swimPhase) / 2.0F
                : Mth.cos(swayPhase / 1.3F) / 3.0F * limbSpeed * run
                + (-0.5F + Mth.sin(-Mth.PI / 4.0F + swayPhase * 2.0F + Mth.sin(Mth.PI / 4.0F + swayPhase * 2.0F) / 5.0F) / 3.0F)
                * walk * step * glide;
        bodyTz += hurt;

        setTranslation(body, 0.0F, bodyTy, bodyTz);

        // ---------- leg1..leg4 ----------
        // 大腿相位：同一侧的前后腿共用，左右腿相差 180°
        float thighPhase = rad(30.0F) + swayPhase / 1.3F;
        float thighPhaseMirror = rad(-30.0F) + swayPhase / 1.3F;

        // ---------- leg1 = right_hind_leg ----------
        float leg1Rx = rad(5.0F) * stance;
        leg1Rx += swimming
                ? rad(10.0F - 20.0F * swimKick) + Mth.sin(swimPhase) / 2.0F
                : (-Mth.cos(thighPhase) / 1.4F + rad(10.0F)) * run
                + Mth.sin(Mth.HALF_PI + rad(40.0F) + swayPhase) / 2.0F
                * Mth.clamp(0.3F - Mth.cos(Mth.HALF_PI + rad(-20.0F) + swayPhase), -1.0F, 0.0F) * thighSwing;
        float leg1Ty = swimming
                ? 3.0F + Mth.cos(swimPhase) * 1.3F
                : Mth.clamp((-2.0F + Mth.sin(thighPhase) * 2.5F) * run, -4.0F, 0.0F);

        float leg1FootRx = -Mth.sin(breathPhase) / 50.0F - rad(clampedYaw) / 6.0F;
        leg1FootRx += (((-Mth.sin(Mth.HALF_PI + swayPhase + Mth.cos(Mth.HALF_PI + swayPhase) / 3.0F) + 0.5F) / 3.0F
                + Mth.clamp(Mth.cos(Mth.HALF_PI + rad(10.0F) + swayPhase) / 3.0F, 0.0F, 1.0F))
                + Mth.sin(Mth.HALF_PI + rad(-60.0F) + swayPhase) / 4.0F
                * Mth.clamp(-0.3F - Mth.cos(Mth.HALF_PI + rad(-20.0F) + swayPhase), -1.0F, 0.0F)
                + rad(3.0F)) * footSwing;
        leg1FootRx += rad(-20.0F + 10.0F * Mth.sin(limbSwing / 2.0F)) * hurt;
        leg1FootRx -= nodBack / 30.0F * novelty1;

        setRotation(leg1,
                leg1Rx + leg1FootRx,
                rad(-10.0F) * stance,
                rad(3.0F) * stance + rad(clampedYaw) / 8.0F);
        setTranslation(leg1,
                -0.2F * stance,
                leg1Ty + Mth.clamp(1.0F - Mth.cos(Mth.HALF_PI + rad(10.0F) + swayPhase) * 2.5F * walk, -2.0F, 0.0F) * step,
                (-Mth.sin(Mth.HALF_PI + swayPhase + Mth.sin(Mth.HALF_PI + swayPhase) / 3.0F) * 3.0F + 0.8F)
                        * footSwing * FOOT_SLIDE_SCALE);

        // ---------- leg2 = left_hind_leg ----------
        float leg2Rx = rad(5.0F) * stance;
        leg2Rx += swimming
                ? rad(10.0F - 20.0F * swimKick) - Mth.sin(swimPhase) / 2.0F
                : (-Mth.cos(thighPhaseMirror) / 1.4F + rad(10.0F)) * run
                - Mth.sin(Mth.HALF_PI + rad(40.0F) + swayPhase) / 2.0F
                * Mth.clamp(0.3F + Mth.cos(Mth.HALF_PI + rad(-20.0F) + swayPhase), -1.0F, 0.0F) * thighSwing;
        float leg2Ty = swimming
                ? 3.0F - Mth.cos(swimPhase) * 1.3F
                : Mth.clamp((-2.0F + Mth.sin(thighPhaseMirror) * 2.5F) * run, -4.0F, 0.0F);

        float leg2FootRx = -Mth.sin(breathPhase) / 50.0F + rad(clampedYaw) / 6.0F;
        leg2FootRx += (((Mth.sin(Mth.HALF_PI + swayPhase - Mth.cos(Mth.HALF_PI + swayPhase) / 3.0F) + 0.5F) / 3.0F
                + Mth.clamp(-Mth.cos(Mth.HALF_PI + rad(10.0F) + swayPhase) / 3.0F, 0.0F, 1.0F))
                - Mth.sin(Mth.HALF_PI + rad(-60.0F) + swayPhase) / 4.0F
                * Mth.clamp(-0.3F + Mth.cos(Mth.HALF_PI + rad(-20.0F) + swayPhase), -1.0F, 0.0F)
                + rad(3.0F)) * footSwing;
        leg2FootRx += rad(-20.0F - 10.0F * Mth.sin(limbSwing / 1.7F)) * hurt;
        leg2FootRx += nodBack / 30.0F * novelty1;

        setRotation(leg2,
                leg2Rx + leg2FootRx,
                rad(10.0F) * stance,
                rad(-3.0F) * stance + rad(clampedYaw) / 8.0F);
        setTranslation(leg2,
                0.2F * stance,
                leg2Ty + Mth.clamp(1.0F + Mth.cos(Mth.HALF_PI + rad(10.0F) + swayPhase) * 2.5F * walk, -2.0F, 0.0F) * step,
                (Mth.sin(Mth.HALF_PI + swayPhase - Mth.sin(Mth.HALF_PI + swayPhase) / 3.0F) * 3.0F + 0.8F)
                        * footSwing * FOOT_SLIDE_SCALE);

        // ---------- leg3 = right_front_leg ----------
        float leg3Rx = rad(-5.0F) * stance;
        leg3Rx += swimming
                ? rad(-20.0F * swimKick) - Mth.cos(swimPhase) / 2.0F
                : -Mth.sin(thighPhase) / 1.4F * run
                + Mth.sin(rad(40.0F) + swayPhase) / 2.0F
                * Mth.clamp(0.3F - Mth.cos(rad(-20.0F) + swayPhase), -1.0F, 0.0F) * thighSwing;
        float leg3Ty = swimming
                ? 3.0F + Mth.sin(swimPhase) * 1.3F
                : Mth.clamp((-2.0F - Mth.cos(thighPhase) * 2.5F) * run, -4.0F, 0.0F);
        leg3Ty += Mth.clamp(-4.0F * Mth.cos(limbSwing / 3.0F), -3.0F, 0.0F) * hurt;

        float leg3FootRx = Mth.sin(breathPhase) / 50.0F - rad(clampedYaw) / 6.0F;
        leg3FootRx += (((-Mth.sin(swayPhase + Mth.cos(swayPhase) / 3.0F) + 0.5F) / 3.0F
                + Mth.clamp(Mth.cos(rad(10.0F) + swayPhase) / 3.0F, 0.0F, 1.0F))
                + Mth.sin(rad(-60.0F) + swayPhase) / 4.0F
                * Mth.clamp(-0.3F - Mth.cos(rad(-20.0F) + swayPhase), -1.0F, 0.0F)
                - rad(3.0F)) * footSwing;
        leg3FootRx += rad(-20.0F + 20.0F * Mth.sin(limbSwing / 1.7F)) * hurt;
        leg3FootRx -= nodBack / 30.0F * novelty1;

        setRotation(leg3,
                leg3Rx + leg3FootRx,
                rad(20.0F) * stance + rad(-5.0F + 5.0F * Mth.sin(swayPhase)) * step * walk,
                rad(3.0F) * stance - rad(clampedYaw) / 8.0F - rad(2.0F * Mth.sin(swayPhase)) * step * walk);
        setTranslation(leg3,
                -0.6F * stance,
                leg3Ty + Mth.clamp(1.0F - Mth.cos(rad(10.0F) + swayPhase) * 2.5F * walk, -2.0F, 0.0F) * step,
                (-Mth.sin(swayPhase + Mth.sin(swayPhase) / 3.0F) * 3.0F + 1.0F) * footSwing * FOOT_SLIDE_SCALE);

        // ---------- leg4 = left_front_leg ----------
        float leg4Rx = rad(-5.0F) * stance;
        leg4Rx += swimming
                ? rad(-20.0F * swimKick) + Mth.cos(swimPhase) / 2.0F
                : -Mth.sin(thighPhaseMirror) / 1.4F * run
                - Mth.sin(rad(40.0F) + swayPhase) / 2.0F
                * Mth.clamp(0.3F + Mth.cos(rad(-20.0F) + swayPhase), -1.0F, 0.0F) * thighSwing;
        float leg4Ty = swimming
                ? 3.0F - Mth.sin(swimPhase) * 1.3F
                : Mth.clamp((-2.0F - Mth.cos(thighPhaseMirror) * 2.5F) * run, -4.0F, 0.0F);
        leg4Ty += Mth.clamp(4.0F * Mth.cos(limbSwing / 3.0F), -3.0F, 0.0F) * hurt;

        float leg4FootRx = Mth.sin(breathPhase) / 50.0F + rad(clampedYaw) / 6.0F;
        leg4FootRx += (((Mth.sin(swayPhase - Mth.cos(swayPhase) / 3.0F) + 0.5F) / 3.0F
                + Mth.clamp(-Mth.cos(rad(10.0F) + swayPhase) / 3.0F, 0.0F, 1.0F))
                - Mth.sin(rad(-60.0F) + swayPhase) / 4.0F
                * Mth.clamp(-0.3F + Mth.cos(rad(-20.0F) + swayPhase), -1.0F, 0.0F)
                - rad(3.0F)) * footSwing;
        leg4FootRx += rad(-20.0F + 20.0F * Mth.sin(limbSwing / 2.0F)) * hurt;
        leg4FootRx += nodBack / 30.0F * novelty1;

        setRotation(leg4,
                leg4Rx + leg4FootRx,
                rad(-20.0F) * stance + rad(5.0F + 5.0F * Mth.sin(swayPhase)) * step * walk,
                rad(-3.0F) * stance - rad(clampedYaw) / 8.0F - rad(2.0F * Mth.sin(swayPhase)) * step * walk);
        setTranslation(leg4,
                0.6F * stance,
                leg4Ty + Mth.clamp(1.0F + Mth.cos(rad(10.0F) + swayPhase) * 2.5F * walk, -2.0F, 0.0F) * step,
                (Mth.sin(swayPhase - Mth.sin(swayPhase) / 3.0F) * 3.0F + 1.0F) * footSwing * FOOT_SLIDE_SCALE);

        // ---------- 眼睛 ----------
        animateEyes(frame, randomPhase, age, limbSwing, clampedYaw, headPitch, novelty1);
    }

    /**
     * FA 的眼睛子模型：瞳孔的转动与裁剪、眼皮的眨眼与受伤闭眼。
     *
     * <p>骨骼沿用原文件的层级：眼球（{@code *_eye}）下挂瞳孔链
     * {@code *_eye_pupil → _in → _ou → _do → _up}，用逐边缩放把瞳孔裁在眼白里，眼皮是 {@code *_eyelid → *_blink}。
     * geo.json 里缺哪根骨骼就跳过对应效果（例如没有 {@code *_blink} 就不眨眼；
     * 瞳孔方块直接挂在眼球骨骼上时改推眼球本身）。
     *
     * <p>位移按“相对静止姿势的偏移”叠加：CEM 里 {@code ctrl_*_pupil} 的静止值是 {@code (0.5, 1.5)}
     * （x 是右眼那侧的值，左眼为镜像的 {@code -0.5}），减掉它之后正好对应 geo.json 里画好的瞳孔位置。
     * 注意 FA 的“右”是模型的 -x，所以 geo.json 里 x&lt;0 的那只眼睛（当前叫 {@code left_eye}）用 {@code r_*} 那一套公式。
     */
    private void animateEyes(CemFrame<CreesperEntity> frame, float randomPhase, float age,
                             float limbSwing, float clampedYaw, float headPitch, float novelty1) {
        int hurtTime = frame.hurtTime();

        // ---------- ctrl_l/r_pupil ----------
        // 原式结尾的 (head2.ry+body.ry)*pi/2*0 是 FA 自己乘 0 的死项，去掉
        float ctrlTy = 1.5F + Mth.clamp(headPitch / 120.0F, -0.5F, 0.5F)
                + (-1.5F - Mth.cos(limbSwing / 3.0F) / 2.0F) / 8.0F
                  * (hurtTime > 0 ? -Mth.sin(hurtTime * Mth.PI / 5.0F) / 6.0F * hurtTime : 0.0F)
                - Mth.clamp(Mth.sin(randomPhase + age / 100.0F) * 40.0F - 24.0F, 0.0F, 1.0F) / 10.0F;
        float ctrlTx = 0.5F - clampedYaw / 120.0F
                + Mth.clamp(Mth.sin(randomPhase + age / 27.0F) + Mth.sin(randomPhase + age / 16.0F), -0.1F, 0.1F)
                  * Mth.clamp(-40.0F - Mth.cos(randomPhase + age / 125.0F) * 60.0F, 0.0F, 1.0F)
                  * Mth.clamp(236.0F - Mth.sin(randomPhase + age / 187.0F) * 240.0F, 0.0F, 1.0F)
                - Mth.clamp(Mth.sin(Mth.PI / 12.0F + randomPhase + age / 12.0F) * 8.0F, -1.0F, 1.0F) / 1.5F * novelty1;

        // 瞳孔的纵向位置：0 = 贴着眼球上沿，2 = 下沿，1.5 是静止姿势
        float pupilTy = Mth.clamp(ctrlTy, 0.0F, 2.0F) - 1.5F;
        // 横向：左右眼互为镜像（CEM 的 ctrl_l_pupil.tx = -1 + ctrl_r_pupil.tx）
        float pupilTx = Mth.clamp(ctrlTx, -0.5F, 0.5F) - 0.5F;
        float pupilTxMirror = Mth.clamp(ctrlTx - 1.0F, -0.5F, 0.5F) + 0.5F;

        // 横向裁剪：FA 的 right_eye 用 _in、left_eye 用 _ou（两边互为镜像）；
        // 这里按名字找，找不到 _ou 就用 _in（geo.json 里右侧那条链只保留了 _in）
        moveEye("l_eye_pupil", "l_eye_pupil_in", Mth.clamp(1.0F - (ctrlTx - 0.5F), 0.75F, 1.0F),
                ctrlTy, pupilTx, pupilTy);
        moveEye("r_eye_pupil", pickBone("r_eye_pupil_ou", "r_eye_pupil_in"),
                Mth.clamp(0.5F + ctrlTx, 0.75F, 1.0F),
                ctrlTy, pupilTxMirror, pupilTy);

        // 瞳孔方块直接挂在 right_eye 上时（没有 pupil 链）才整体推眼球骨骼，
        // 否则会和 r_eye_pupil 的位移叠加成两倍
        if (bone("r_eye_pupil") == null) {
            GeoBone plainEye = bone("right_eye");
            if (plainEye != null) setTranslation(plainEye, pupilTxMirror, pupilTy, 0.0F);
        }

        // ---------- 眼皮 ----------
        // 常态是 0（眼皮片被压扁 = 睁眼），偶发地冲到 1（盖住眼球 = 闭眼）；
        // 受伤时 clamp 的下界被抬到 1，于是 hurt_time 9..6 这几个 tick 被强制闭眼
        float blink = Mth.clamp(
                (1.5F - Mth.abs(Mth.sin(randomPhase + age / 9.0F) * 6.0F))
                        * Mth.clamp(Mth.cos((randomPhase + age / 9.0F) / 1.5F) * 40.0F
                                    + Mth.cos((randomPhase + age / 9.0F) / 4.0F) * 40.0F - 32.0F, 0.0F, 1.0F),
                Mth.clamp(-Mth.sin(hurtTime * Mth.PI / 5.0F) / 3.0F * hurtTime, 0.0F, 1.0F),
                1.0F);

        blinkLid("left_blink", blink);
        blinkLid("right_blink", blink);
    }

    /** 驱动一条 {@code *_eye_pupil} 链：位移写在瞳孔骨骼上，逐边裁剪缩放写在 {@code _in/_do/_up} 上。 */
    private void moveEye(String pupilName, String clipSideName, float clipSideScale, float ctrlTy,
                         float tx, float ty) {
        GeoBone pupil = bone(pupilName);
        if (pupil != null) setTranslation(pupil, tx, ty, 0.0F);

        GeoBone clipSide = bone(clipSideName);
        if (clipSide != null) {
            setScale(clipSide, clipSideScale, 1.0F, 1.0F);
        }

        GeoBone clipDown = bone(pupilName + "_do");
        if (clipDown != null) {
            setScale(clipDown, 1.0F, Mth.clamp(1.0F + (ctrlTy - 0.5F), 0.5F, 1.0F), 1.0F);
        }

        GeoBone clipUp = bone(pupilName + "_up");
        if (clipUp != null) {
            setScale(clipUp, 1.0F, Mth.clamp(1.0F - (ctrlTy - 1.5F), 0.5F, 1.0F), 1.0F);
        }
    }

    /** 眼皮片：sy 就是“闭眼程度”，0 = 睁开、1 = 完全盖住眼球。 */
    private void blinkLid(String name, float amount) {
        GeoBone lid = bone(name);
        if (lid != null) setScale(lid, 1.0F, amount, 1.0F);
    }

    /** 取第一个存在的骨骼名，用于兼容左右侧裁剪骨骼的命名差异。 */
    private String pickBone(String firstName, String secondName) {
        return bone(firstName) != null ? firstName : secondName;
    }
}
