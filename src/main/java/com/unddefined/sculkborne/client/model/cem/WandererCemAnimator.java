package com.unddefined.sculkborne.client.model.cem;

import com.unddefined.sculkborne.entities.WandererEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Fresh Animations 末影人 CEM 动画的 GeckoLib 移植，给徘徊者用。
 *
 * <p>原始文件：{@code source_codes/FreshAnimations_v1.9.2/assets/minecraft/optifine/cem/enderman.jem}。
 * 坐标系换算、每实体跨帧状态、每帧的实体输入都在 {@link CemAnimator} / {@link CemFrame} 里，
 * 这里负责末影人自己的公式：{@code body / headwear / left_arm / right_arm / left_leg / right_leg} 的
 * 旋转与位移，以及 {@code var.hy / var.r / var.b / var.ls / var.walk / var.idle / var.ylook /
 * var.nov1 / var.nov2 / var.aggroA / var.aggroB / var.aggroC / var.carry}。
 *
 * <h2>与原始文件的骨骼对应</h2>
 * <p>头部在 FA 里是两层：{@code headwear} 只是“头部几何”的容器（原版 {@code head} 部件的方块被换成空壳），
 * {@code headwear.*} 是朝向与晃动，带着头部方块的 {@code head2}、下颌 {@code jaw}、眼睛都挂在它下面；
 * {@code head2.rx / ty} 才是那一层几何体自己的动作（受伤点头、头部悬浮下沉）。
 *
 * <p>本模型的两根骨骼正好对得上这两层：{@code head} 带着可见的头部方块，等于 {@code head2} 那一层，
 * 所以拿 {@code headwear.* + head2.*}；{@code headwear} 是第二层几何（对应 FA 挂在 {@code headwear} 下的
 * {@code jaw}、眼睛这类子模型），只拿 {@code headwear.*}，<b>不跟</b> {@code head2} 的点头与悬浮——
 * 它不是原版那种跟着头刚性复制的帽子，两层是分开动的。
 *
 * <p>FA 把每个部位的方块都放进了子骨骼里，本模型的骨骼是直接带着方块的，所以把父子两级摊平到同一根骨骼上：
 * {@code body_y} 的 {@code ry}（身体的次级扭动）加到 {@code body}，{@code right_hand / left_hand}
 * 的 {@code rx / rz / sy}
 * （只搬运方块时生效的腕部姿势）加到手臂，{@code right_foot / left_foot} 的
 * {@code rx / ry / rz / tx / ty / tz}（腿部的摆动其实都在这里）加到腿。父子两级同 pivot（脚、手相对各自的
 * 部位只有横向偏移，已经由常量抵消），旋转直接相加与原式等价。
 * {@code head2.tx=0}、{@code head2.tz=0} 原文没有定义，等于 0，不参与。
 * 剩下的 {@code jaw}、眼睛、眼皮与挂在身上的 {@code *_part} 模型本模型都没有，直接省略；
 * {@code varb.distance} 只有眼睛、下颌在用，也一并省略。
 *
 * <h2>取巧的地方</h2>
 * <p>CEM 的位移是绝对值，其中不随动画变化的常量（{@code body.ty=-14.8}、{@code headwear.ty=-13.8}、
 * 手臂 {@code tx=±5}、{@code ty=-13.4}）把 FA 的模型几何重新锚定回原版位置；geo.json 的静止姿势
 * 就是原版姿势，所以下面只把“动画值 - 静止常量”的偏差当成偏移叠加。旋转没有这种常量，直接写公式值。
 * 腿的 {@code ty=-3} 是纯常量，geo.json 的 pivot 已经在这个位置上，整条位移都不需要写。
 *
 * <p>腿还多一层处理：FA 的腿骨骼 pivot 在脚踝（腿的方块从脚踝往上长到腿根），{@code right_foot.tz}
 * 那 ±14 像素是“绕脚踝旋转时腿根被带走”的补偿量；本模型的腿骨骼 pivot 在髋部（原版位置），
 * 照抄这个位移会把整条腿连着腿根一起推出去。把绕脚踝换算成绕髋部之后那点位移只剩 ±3 像素的残差，
 * 但它会让腿根在髋部来回蹭、和身体之间出现缝，所以腿干脆不写前后位移：前后迈步完全交给髋部的旋转
 * （同角度下脚的前后位移与原式基本一致），竖直方向只保留抬脚与跟随身体上下，见 {@code animate} 里的注释。
 *
 * <p>另外腿按手臂的规则跟随躯干的左右摆动（滚动 {@code body.rz}、横向位移 {@code body.tx} 全程跟随，
 * 扭转 {@code body.ry} 只在待机时跟随），FA 的腿公式里没有这些项，照抄会变成躯干摆、腿不动。
 *
 * <p>眼睛是单独一套：geo.json 里 {@code *_eye → *_eye_pupil → _in → _do → _up}、{@code *_eyelid → *_blink}
 * 与 FA 的骨骼同名同层级，见 {@link #animateEyes}；CEM 里 FA 的 {@code right_*} 在模型 -x，
 * 本模型 -x 的那只眼叫 {@code left_*}，所以左右两套公式是交叉用的。
 *
 * <p>模型的骨骼与 {@code body} 同级（没有父子关系），所以位移可以直接写 CEM 的绝对值再减去静止常量；
 * 公式里出现的 {@code body.tx / body.ty / body.tz} 也照原样相加，不需要像挂在 {@code body} 下的骨架
 * 那样再减一次父级位移。
 *
 * <h2>两个从原版姿势里读出来的变量</h2>
 * <p>FA 的 {@code var.carry} 与 {@code var.aggroC} 都不是实体状态，而是回头读原版模型这一帧算好的姿势：
 * {@code var.carry} 读双臂是否抬到 {@code rx<=-0.49}（原版搬运方块时把手臂设成 {@code -0.5}），
 * {@code var.aggroC} 读 {@code head.ty<=-15}（原版被注视暴怒时把头部下移 5 像素，{@code -13-5=-18}）。
 * 本移植整帧接管姿势、原版动画不再参与，因此改成读实体本身的对应状态：
 * {@code EnderMan#getCarriedBlock()} 与 {@code EnderMan#isCreepy()}，语义完全等价。
 */
public final class WandererCemAnimator extends CemAnimator<WandererEntity> {

    /** {@code var.aggroA} 每 tick 的增长/衰减步长，对应原式的 {@code ±0.4*frame_time*20}。 */
    private static final float AGGRO_GAIN_PER_TICK = 0.4F;
    private static final float AGGRO_DECAY_PER_TICK = -0.4F;

    /** {@code var.aggroC} 每 tick 的增长/衰减步长，对应原式的 {@code +0.03/-0.2 *frame_time*20}。 */
    private static final float STARE_GAIN_PER_TICK = 0.03F;
    private static final float STARE_DECAY_PER_TICK = -0.2F;

    /** {@code body.ty} 在静止姿势下的常量，见类注释。 */
    private static final float BODY_TY_REST = -14.8F;

    /** {@code headwear.ty} 在静止姿势下的常量（{@code body.ty + 1}），见类注释。 */
    private static final float HEAD_TY_REST = -13.8F;

    /** 手臂 {@code ty} 在静止姿势下的常量（{@code body.ty + 1.4}），见类注释。 */
    private static final float ARM_TY_REST = -13.4F;

    /** 手臂 {@code tx} 在静止姿势下的常量（右臂 {@code -5}、左臂 {@code +5}），见类注释。 */
    private static final float ARM_TX_REST = 5.0F;

    public WandererCemAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(CemFrame<WandererEntity> frame) {
        GeoBone body = bone("body");
        GeoBone head = bone("head");
        GeoBone headwear = bone("headwear");
        GeoBone leftArm = bone("left_arm");
        GeoBone rightArm = bone("right_arm");
        GeoBone leftLeg = bone("left_leg");
        GeoBone rightLeg = bone("right_leg");

        // 模型被改过或换了骨架时直接跳过，避免整帧报错
        if (body == null || head == null || leftArm == null || rightArm == null
                || leftLeg == null || rightLeg == null) {
            return;
        }

        // ---------- CEM 基础输入 ----------
        WandererEntity entity = frame.entity();
        boolean aggressive = frame.aggressive();
        boolean carrying = entity.getCarriedBlock() != null;                                    // var.carry
        boolean staring = entity.isCreepy();                                                    // var.aggroC
        float limbSwing = frame.limbSwing();
        float limbSpeed = frame.limbSpeed();
        float swingProgress = frame.swingProgress();
        float headPitch = frame.headPitch();
        float clampedYaw = frame.clampedHeadYaw();                                              // var.hy

        // CEM 的 if(is_hurt, ...) 都乘了 hurt_time，受伤结束时自然归零，不需要额外的判空
        int hurtTime = frame.hurtTime();
        float hurtSwing = -Mth.sin(hurtTime * Mth.PI / 5.0F) * hurtTime;                         // 手臂用
        float hurt = hurtSwing / 6.0F;                                                          // 身体、头部用
        float hurtPitch = Mth.clamp(-Mth.sin(hurtTime * Mth.PI / 5.0F) / 3.0F * hurtTime, 0.0F, 3.0F);

        // ---------- 基础变量 ----------
        float entityRandom = frame.random();                                                    // random(id)
        float randomPhase = entityRandom * Mth.PI * 4.0F;                                       // var.r
        float breathPhase = randomPhase + frame.age() / (13.0F + 3.0F * entityRandom) - limbSwing / 3.0F; // var.b
        float carry = carrying ? 1.0F : 0.0F;                                                   // var.carry

        // ---------- 跨帧状态 var.aggroA / var.aggroC ----------
        // 原式每帧按 frame_time 累加，这里改成按 tick 累加，行为等价且与帧率无关
        int elapsedTicks = frame.elapsedTicks();
        if (!entity.isAlive()) {
            // clamp 的下界在死亡时变成 1：立刻进入最激烈的姿势
            frame.setVar("aggroA", 1.0F);
            frame.setVar("aggroC", 1.0F);
        } else if (elapsedTicks > 0) {
            float aggroStep = aggressive ? AGGRO_GAIN_PER_TICK : AGGRO_DECAY_PER_TICK;
            float stareStep = staring ? STARE_GAIN_PER_TICK : STARE_DECAY_PER_TICK;
            frame.setVar("aggroA", Mth.clamp(frame.var("aggroA") + aggroStep * elapsedTicks, 0.0F, 1.0F));
            frame.setVar("aggroC", Mth.clamp(frame.var("aggroC") + stareStep * elapsedTicks, 0.0F, 1.0F));
        }

        float aggroA = frame.var("aggroA");                                                     // var.aggroA
        float aggroC = frame.var("aggroC");                                                     // var.aggroC
        float aggroB = 0.5F - 0.5F * Mth.cos(aggroC * Mth.PI);                                  // var.aggroB

        float walk = Mth.clamp(limbSpeed * 3.0F, 0.0F, 1.0F);                                   // var.walk
        float idle = 1.0F - walk;                                                               // var.idle
        float swayPhase = randomPhase + limbSwing / 1.9F * (aggroB != 0.0F ? 1.6F : 1.0F);      // var.ls
        float age = frame.age();

        // var.ylook：低头看人的程度，只在没暴怒时才有意义
        float ylook = 0.5F - 0.5F * Mth.cos(
                Mth.clamp(-3.0F + Mth.sin(randomPhase + age / 88.0F) * 6.0F, aggroB, 1.0F) * Mth.PI);
        // var.nov1 / var.nov2：末影人的“抽搐”抖动，只在抬头、不在暴怒时出现
        float nov1 = (aggroB != 0.0F ? 0.0F
                : Mth.clamp(-5.6F + Mth.cos(-Mth.PI / 6.0F + randomPhase + age / 80.0F) * 6.0F, 0.0F, 1.0F)
                        * Mth.clamp(2.0F - limbSpeed * 4.0F, 0.0F, 1.0F))
                * Mth.clamp(1.0F - rad(headPitch) * 2.0F, 0.0F, 1.0F);
        boolean twitching = aggroB != 1.0F && ylook == 0.0F;
        float nov2 = Mth.clamp(twitching
                ? Math.abs(clampedYaw) / 5.0F + Math.abs(headPitch) / 5.0F
                        + Mth.clamp(-15.0F + Mth.sin(randomPhase + age / 90.0F) * 30.0F, 0.0F, 1.0F)
                : 0.0F, 0.0F, 1.0F - nov1);

        // ---------- body ----------
        float bodyRx = rad(-1.0F
                + Mth.cos(breathPhase / 2.0F) * 2.0F * idle * (1.0F - aggroB / 3.0F)
                + headPitch / 8.0F * ylook
                + (15.0F * (1.0F - carry)) * aggroB
                + (-3.0F * Mth.cos(Mth.PI / 3.0F + swayPhase * 2.0F) + 10.0F * limbSpeed) * walk
                - 10.0F * carry)
                - (Mth.sin(limbSwing / 2.0F) / 2.0F + rad(20.0F)) / 3.0F * hurt
                + Mth.sin(Mth.PI * swingProgress) / 3.0F;
        float bodyRy = rad(Mth.cos(breathPhase / 1.5F) * 15.0F * idle * (1.0F - aggroB / 3.0F) * (1.0F - bodyRx)
                + Mth.clamp(clampedYaw / 5.0F, -18.0F, 18.0F)
                + (20.0F * Mth.cos(swayPhase) * (1.0F - bodyRx)) * walk)
                * (1.0F - carry / 1.2F);
        float bodyRz = rad(Mth.sin(Mth.PI / 8.0F + breathPhase) * 2.0F * idle * (1.0F - aggroB / 3.0F)
                + (5.0F * Mth.cos(Mth.PI / 4.0F + swayPhase)) * walk * (1.0F + aggroB * (1.0F - carry)));

        // body.tx / ty / tz 是绝对位移，其中只有 ty 带常量
        float bodyTx = Mth.sin(bodyRz) * 12.0F
                + Mth.sin(breathPhase) * 1.5F * idle * (1.0F - aggroB / 3.0F)
                + (0.4F * idle * (1.0F - carry)) * aggroB
                + (-Mth.sin(swayPhase + Mth.sin(Mth.PI / 3.0F + swayPhase * 2.0F) / 6.0F) / 1.2F
                        - Mth.cos(swayPhase) * (bodyRx * Mth.PI) / 4.0F * (1.0F - carry / 1.5F))
                        * walk * (1.0F + aggroB * (1.0F - carry));
        float bodyTy = -2.8F - Mth.cos(bodyRx) * 12.0F - Mth.cos(bodyRz) * 12.0F + 12.0F
                + (1.6F * limbSpeed * (1.0F - carry)) * aggroB
                + (Mth.sin(Mth.PI / 3.0F + swayPhase * 2.0F + Mth.cos(swayPhase * 2.0F) / 3.0F) * 1.3F + 2.0F)
                        * walk * Mth.clamp(1.0F - aggroB / 2.0F, 0.0F, 1.0F);
        float bodyTz = -Mth.sin(bodyRx) * 12.0F
                + headPitch / 60.0F * ylook
                + (-walk * (1.0F - carry)) * aggroB
                + (-Mth.cos(Mth.PI / 3.0F + swayPhase * 2.0F) + 0.5F * carry) * walk;

        // body_y.ry：身体内层方块的次级扭动，本模型没有这层子骨骼，直接叠到 body 上
        float bodyTwist = rad((30.0F * Mth.sin(Mth.sqrt(swingProgress) * Mth.PI * 2.0F) + clampedYaw / 3.0F)
                * Mth.sin(swingProgress * Mth.PI)
                + (-10.0F * (1.0F - carry) * idle) * aggroB);

        setRotation(body, bodyRx, bodyRy + bodyTwist, bodyRz);
        setTranslation(body, bodyTx, bodyTy - BODY_TY_REST, bodyTz);

        // ---------- head / headwear（原文件里的 headwear.*）与 head2 的点头与悬浮 ----------
        // head2.ty 是 "-39 + 偏差"，-39 是 FA 几何的锚点（geo.json 的静止姿势已经在那个位置上），
        // 所以这里只叠加后面的偏差。headwear.* 与 head2.* 分开写：head 拿两者，headwear 只拿 headwear.*
        float headBob = Mth.clamp(Mth.clamp((aggroB != 0.0F ? -4.0F : -98.9F)
                        + Mth.sin(randomPhase + age / (aggroB != 0.0F ? 10.0F : 46.0F))
                                * (aggroB != 0.0F ? 5.0F : 100.0F),
                        0.0F, 1.0F + 2.0F * aggroB) * (-1.0F + 2.0F * aggroB)
                + (-4.0F + 0.25F * Mth.sin(age * 3.0F)) * aggroB
                + (-2.5F) * aggroA * aggroB
                - Mth.clamp(3.0F * hurt, 0.0F, 3.0F), -6.0F, 0.0F);

        // headwear.rx：这一层没有 head2 的项
        float headwearRx = rad(headPitch / (2.0F - aggroB) * ylook
                + Mth.sin(breathPhase / 2.0F) * 3.0F * Mth.clamp(1.0F - ylook, 0.0F, 1.0F) * idle * (1.0F - aggroB)
                + 2.0F * hurtPitch)
                + Mth.sin(limbSwing / 2.0F) / 3.0F * hurt
                + (-0.2F + Mth.sin(-Mth.PI / 4.0F + swingProgress * Mth.PI) / 3.0F) * Mth.sin(swingProgress * Mth.PI);
        float headRx = headwearRx + rad(-3.0F * hurtPitch);                                     // head2.rx
        float headRy = rad(Mth.clamp(clampedYaw / (10.0F - 6.0F * ylook), -60.0F, 60.0F) * (1.0F - aggroB)
                + clampedYaw * aggroB
                + Mth.sin(breathPhase / 1.5F) * 3.0F * Mth.clamp(1.0F - ylook, 0.0F, 1.0F) * idle * (1.0F - aggroB))
                + rad(3.0F * Mth.clamp(Mth.sin(Mth.PI / 8.0F + age / 17.0F) * 3.0F
                        + Mth.cos(Mth.PI / 8.0F + age / 7.3F) * 2.0F, -1.0F, 1.0F)) * nov2;
        float headRz = rad(clampedYaw / 9.0F * ylook * (1.0F - aggroB)
                + Mth.cos(Mth.PI / 2.0F * Mth.clamp(ylook, 0.0F, 1.0F) + breathPhase) * 3.0F * idle * (1.0F - aggroB))
                + rad(3.0F * Mth.clamp(Mth.sin(Mth.PI / 12.0F + age / 17.0F) * 3.0F
                        + Mth.cos(Mth.PI / 12.0F + age / 7.3F) * 2.0F, -1.0F, 1.0F)) * nov2;

        float headTx = bodyTx - Mth.sin(clampedYaw / 90.0F) * aggroB;
        float headwearTy = bodyTy + 1.0F
                + Mth.clamp(0.2F + 6.0F * Mth.sin(headPitch / 90.0F), -6.0F, 2.0F) * aggroB;
        float headTy = headwearTy + headBob;                                                    // head2.ty
        float headTz = bodyTz
                + Mth.clamp(headPitch / 30.0F * ylook, -2.0F, 0.0F)
                + Mth.clamp(-2.0F - 2.0F * Mth.cos(headPitch / 45.0F), -2.0F, 2.0F) * aggroB;

        setRotation(head, headRx, headRy, headRz);
        setTranslation(head, headTx, headTy - HEAD_TY_REST, headTz);

        // headwear 不是原版那种跟着头刚性复制的帽子：它是第二层几何，只拿 headwear.*，
        // 不跟 head2 的点头与悬浮（对应 FA 里挂在 headwear 下面的 jaw、眼睛这类子模型）
        if (headwear != null) {
            setRotation(headwear, headwearRx, headRy, headRz);
            setTranslation(headwear, headTx, headwearTy - HEAD_TY_REST, headTz);
        }

        // ---------- left_arm / right_arm ----------
        float armLift = Mth.clamp(limbSpeed * 1.5F, 0.0F, 1.0F);
        float armCarryBlend = 1.0F - carry;

        float rightArmRx = rad(Mth.sin(breathPhase / 1.5F + Mth.sin(breathPhase / 1.5F)) * 3.0F
                - Mth.clamp(clampedYaw / 80.0F, -1.125F, 1.125F)
                + headPitch / 40.0F * ylook
                + (20.0F + 4.0F * Mth.sin(breathPhase * 3.5F)) * aggroB
                + (20.0F * Mth.cos(swayPhase + Mth.sin(swayPhase) / 3.0F) + 7.0F * limbSpeed) * armLift
                - 3.0F * Mth.cos(limbSwing / 2.0F) * hurtSwing) * armCarryBlend
                + rad(-30.0F * (1.0F - Mth.clamp(Mth.sin(swingProgress * Mth.PI) * 2.0F, 0.0F, 1.0F))) * carry
                + rad(60.0F * Mth.sin(Mth.sqrt(swingProgress) * Mth.PI * 2.0F)) * Mth.sin(swingProgress * Mth.PI);
        float leftArmRx = rad(-Mth.sin(breathPhase / 1.5F - Mth.sin(breathPhase / 1.5F)) * 3.0F
                + Mth.clamp(clampedYaw / 80.0F, -1.125F, 1.125F)
                + headPitch / 40.0F * ylook
                + (10.0F - 4.0F * Mth.sin(breathPhase * 3.3F)) * aggroB
                + (-20.0F * Mth.cos(swayPhase - Mth.sin(swayPhase) / 3.0F) + 7.0F * limbSpeed) * armLift
                - 3.0F * Mth.cos(limbSwing / 2.0F) * hurtSwing
                + (20.0F - 20.0F * Mth.sin(Mth.sqrt(swingProgress) * Mth.PI * 2.0F)) * Mth.sin(swingProgress * Mth.PI))
                * armCarryBlend
                + rad(-30.0F) * carry;

        float rightArmRy = (bodyRy * (1.0F - walk)
                + rad((-20.0F * idle) * aggroB + (-10.0F + 10.0F * Mth.sin(Mth.PI / 3.0F + swayPhase)) * walk))
                * armCarryBlend
                + rad(clampedYaw / 2.0F) * Mth.sin(swingProgress * Mth.PI);
        float leftArmRy = (bodyRy * (1.0F - walk)
                + rad((20.0F * idle) * aggroB + (10.0F + 10.0F * Mth.sin(Mth.PI / 3.0F + swayPhase)) * walk)
                - Mth.sin(-Mth.PI / 2.0F * swingProgress * 2.0F) / 4.0F) * armCarryBlend;

        float rightArmRz = (rad(2.0F - Mth.sin(-Mth.PI / 8.0F + breathPhase) * 1.3F
                + (5.0F * idle) * aggroB
                + (5.0F - 2.0F * Mth.cos(Mth.PI / 3.0F + swayPhase)) * walk)
                + bodyRz
                + rad(20.0F * Mth.sin(swingProgress * Mth.PI * 2.0F)) * Mth.sin(swingProgress * Mth.PI)) * armCarryBlend
                + rad(20.0F * Mth.sin(swingProgress * Mth.PI * 2.0F)) * Mth.sin(swingProgress * 2.0F * Mth.PI);
        float leftArmRz = (rad(-2.0F - Mth.sin(-Mth.PI / 8.0F + breathPhase) * 1.3F
                + (-5.0F * idle) * aggroB
                + (-5.0F - 2.0F * Mth.cos(Mth.PI / 3.0F + swayPhase)) * walk)
                + bodyRz) * armCarryBlend;

        float armSwingArc = Mth.sin(Mth.sqrt(swingProgress) * Mth.PI * 2.0F) * Mth.sin(swingProgress * Mth.PI);
        // 搬运方块时 FA 三条手臂位移是常数（不含 body.tx/ty/tz），而本模型的骨骼和 body 同级、
        // 不像 FA 那样是 body 的子级，照抄会变成"身体动、手臂留在原地"，身体一摆肩部就裂开，
        // 所以给 ty/tz 补上 body 相对静止姿势的位移偏差，让手臂跟着身体走。
        float armBodyTy = bodyTy - BODY_TY_REST;
        float armBodyTz = bodyTz;
        // tx 连 FA 的 ±6 一起丢掉：FA 的模型是自己一套几何，外挪 1 像素没事；本模型的手臂 x∈[-6,-4]
        // 和身体 x∈[-4,4] 正好面贴面，外挪这 1 像素会在肩到上臂之间拉出一条竖缝（就是"胳膊和身体分开"），
        // 而搬起的方块宽 8 像素、也正好卡在两手之间（±4），所以横坐标始终按静止公式走。
        float rightArmTx = (bodyTx - Mth.cos(bodyRy) * 5.0F) - armSwingArc;
        float leftArmTx = bodyTx + Mth.cos(bodyRy) * 5.0F + 0.5F * armSwingArc * armCarryBlend;
        float rightArmTy = (bodyTy + 1.4F + (-Mth.cos(Mth.PI / 4.0F + swayPhase) / 2.0F) * walk) * armCarryBlend
                + (-13.0F + armBodyTy) * carry;
        float leftArmTy = (bodyTy + 1.4F + (Mth.cos(Mth.PI / 4.0F + swayPhase) / 2.0F) * walk) * armCarryBlend
                + (-13.0F + armBodyTy) * carry;
        float rightArmTz = (bodyTz + Mth.sin(bodyRy) * 5.0F
                + (0.2F + 0.7F * Mth.sin(Mth.PI / 3.0F + swayPhase) + 0.6F * limbSpeed) * armLift) * armCarryBlend
                + (1.5F + armBodyTz) * carry
                + 3.0F * armSwingArc * (1.0F + carry);
        float leftArmTz = (bodyTz - Mth.sin(bodyRy) * 5.0F
                + (1.5F * idle) * aggroB
                + (0.2F - 0.7F * Mth.sin(Mth.PI / 3.0F + swayPhase) + 0.6F * limbSpeed) * armLift
                - 3.0F * armSwingArc) * armCarryBlend
                + (1.5F + armBodyTz) * carry;

        // right_hand.* / left_hand.*：只有搬运方块（右手公式整条乘了 var.carry）时才有的腕部姿势，
        // 本模型没有手这段子骨骼，叠到手臂上；pivot 略高于 FA 的手，属于可接受的近似
        float handIdle = (Mth.sin(breathPhase) * 3.3F + Mth.sin(Mth.PI / 8.0F + breathPhase) / 1.5F
                - Mth.cos(breathPhase / 1.5F) / 4.0F * (1.0F - bodyRx)) * idle * (1.0F - aggroB / 3.0F);
        float handWalk = (-(Mth.sin(swayPhase + Mth.sin(Mth.PI / 3.0F + swayPhase * 2.0F) / 6.0F) / 1.2F
                - Mth.cos(swayPhase) * (bodyRx * Mth.PI) / 4.0F) * 2.0F
                + 2.5F * Mth.cos(Mth.PI / 4.0F + swayPhase)) * walk;
        float handSwing = (Mth.cos(Mth.PI / 3.0F + swayPhase * 2.0F)
                - Mth.sin(Mth.PI / 3.0F + swayPhase * 2.0F + Mth.cos(swayPhase * 2.0F) / 3.0F) * (1.0F - aggroB / 2.0F)
                + (3.0F * Mth.cos(Mth.PI / 3.0F + swayPhase * 2.0F) + 10.0F * limbSpeed) / 10.0F - 2.0F) * walk;
        float handHurt = -(28.0F * Mth.sin(limbSwing / 2.0F) + 20.0F) / 8.0F * hurt;

        float rightHandRx = rad((Mth.cos(breathPhase / 2.0F) - Mth.cos(breathPhase / 1.5F) * (1.0F - bodyRx))
                * idle * (1.0F - aggroB / 3.0F) + handSwing + handHurt - clampedYaw / 200.0F) * carry;
        float leftHandRx = rad((Mth.cos(breathPhase / 2.0F) + Mth.cos(breathPhase / 1.5F) * (1.0F - bodyRx))
                * idle * (1.0F - aggroB / 3.0F) + handSwing + handHurt + clampedYaw / 200.0F
                + Mth.sin(Mth.PI * swingProgress) / 3.0F * 12.0F) * carry;
        float rightHandRz = rad(2.1F + handIdle + handWalk) * carry;
        float leftHandRz = rad(-2.1F + handIdle + handWalk) * carry;
        float handScaleY = 1.0F + (-(Mth.sin(Mth.PI / 3.0F + swayPhase * 2.0F + Mth.cos(swayPhase * 2.0F) / 3.0F) * 1.3F
                + 2.0F) / 40.0F * walk) * carry;

        setRotation(rightArm, rightArmRx + rightHandRx, rightArmRy, rightArmRz + rightHandRz);
        setRotation(leftArm, leftArmRx + leftHandRx, leftArmRy, leftArmRz + leftHandRz);
        rightArm.setScaleY(handScaleY);
        leftArm.setScaleY(handScaleY);
        setTranslation(rightArm, rightArmTx + ARM_TX_REST, rightArmTy - ARM_TY_REST, rightArmTz);
        setTranslation(leftArm, leftArmTx - ARM_TX_REST, leftArmTy - ARM_TY_REST, leftArmTz);

        // ---------- left_leg / right_leg ----------
        // 末影人是滑着走的：腿部骨骼本身几乎不转，真正的摆动都在脚那一段子骨骼上，本模型把两级摊平到同一根
        // 腿骨骼上，所以这里是“腿部公式 + 脚部公式”。腿部自己的位移是纯常量 -3，geo.json 的 pivot 已烘焙好
        float legSwing = Mth.clamp(0.2F - limbSpeed * 3.0F, 0.0F, 0.2F);
        float rightLegRx = (-Mth.cos(swayPhase + Mth.cos(swayPhase) / 3.0F) / 2.5F) * legSwing * (1.0F - aggroB);
        float leftLegRx = (Mth.cos(swayPhase - Mth.cos(swayPhase) / 3.0F) / 2.5F) * legSwing * (1.0F - aggroB);
        float rightLegRy = rad(Mth.clamp(-3.0F * Mth.sin(swayPhase), 0.0F, 3.0F)) * walk;
        float leftLegRy = rad(Mth.clamp(-3.0F * Mth.sin(swayPhase), -3.0F, 0.0F)) * walk;
        float rightLegRz = -rad(Mth.clamp(-Mth.sin(swayPhase), 0.0F, 1.0F) - 2.0F * aggroB) * walk
                + rad(idle * (1.0F - carry)) * aggroB;
        float leftLegRz = -rad(Mth.clamp(-Mth.sin(swayPhase), -1.0F, 0.0F) + 2.0F * aggroB) * walk
                + rad(-6.0F * idle * (1.0F - carry)) * aggroB;

        // right_foot.* / left_foot.*
        float footIdleRx = rad(-Mth.cos(breathPhase / 1.5F) * 1.3F * (1.0F - aggroB / 3.0F)
                - Mth.clamp(clampedYaw / 60.0F, -1.5F, 1.5F)
                - headPitch / 50.0F * ylook
                + (-8.0F * (1.0F - carry)) * aggroB) * idle * (1.0F - carry / 1.5F);
        float footStepLift = rad(7.0F + 7.0F * aggroB * (1.0F - carry / 2.0F));
        float rightFootRx = footIdleRx
                + (footStepLift
                        - Mth.cos(swayPhase + Mth.cos(swayPhase) / 3.0F * (1.0F - aggroB)) / 2.5F
                                * (1.0F - 0.3F * aggroB)
                        - Mth.sin(Mth.PI / 6.0F + swayPhase + Mth.cos(Mth.PI / 6.0F + swayPhase) / 2.0F) / 15.0F) * walk;
        float leftFootRx = rad(Mth.cos(breathPhase / 1.5F) * 1.3F * (1.0F - aggroB / 3.0F)
                + Mth.clamp(clampedYaw / 60.0F, -1.5F, 1.5F)
                - headPitch / 50.0F * ylook
                + (6.0F * (1.0F - carry)) * aggroB) * idle * (1.0F - carry / 1.5F)
                + (footStepLift
                        + Mth.cos(swayPhase - Mth.cos(swayPhase) / 3.0F * (1.0F - aggroB)) / 2.5F
                                * (1.0F - 0.3F * aggroB)
                        + Mth.sin(Mth.PI / 6.0F + swayPhase - Mth.cos(Mth.PI / 6.0F + swayPhase) / 2.0F) / 15.0F) * walk;

        float rightFootRy = rad(6.0F + Mth.cos(breathPhase / 1.5F) * 5.0F * (1.0F - aggroB / 3.0F)) * idle + rad(2.0F);
        float leftFootRy = rad(-6.0F + Mth.cos(breathPhase / 1.5F) * 5.0F * (1.0F - aggroB / 3.0F)
                + (-8.0F * (1.0F - carry)) * aggroB) * idle + rad(-2.0F);

        float footRz = rad(Mth.sin(breathPhase + Mth.sin(breathPhase * 2.0F) / 40.0F * (1.0F - carry / 1.5F))
                * 3.0F * idle * (1.0F - aggroB / 3.0F))
                + rad(-(2.0F + 2.0F * aggroB * (1.0F - carry))
                                * Mth.sin(swayPhase + Mth.sin(Mth.PI / 3.0F + swayPhase * 2.0F) / 6.0F)
                        + (1.0F + aggroB * (1.0F - carry)) * Mth.cos(swayPhase) * (bodyRx * Mth.PI / 2.0F)) * walk;

        float rightFootTy = Mth.clamp(1.0F - Mth.cos(Mth.PI / 2.0F + Mth.PI / 18.0F + swayPhase) * 2.5F * walk,
                -4.0F, 0.0F) * 2.0F * walk;
        float leftFootTy = Mth.clamp(1.0F + Mth.cos(Mth.PI / 2.0F + Mth.PI / 18.0F + swayPhase) * 2.5F * walk,
                -4.0F, 0.0F) * 2.0F * walk;

        // 腿和手臂用同一套跟随规则（上面手臂就是这么写的）：躯干的滚动 body.rz 与横向位移 body.tx
        // 全程带上，躯干的扭转 body.ry 只在待机时带上（手臂用的是 body.ry*(1-var.walk)），
        // 走路时躯干自己的摆肩不拖着腿走。FA 的腿公式里本来没有这些项，结果是躯干在摆、腿不动，
        // 看起来就像两边反着摆；这里按手臂的规则补齐。
        float legFollowRy = bodyRy * (1.0F - walk);

        // 两级旋转同 pivot，直接相加就是腿的总摆动
        setRotation(rightLeg, rightLegRx + rightFootRx,
                rightLegRy + rightFootRy + legFollowRy,
                rightLegRz + footRz + bodyRz);
        setRotation(leftLeg, leftLegRx + leftFootRx,
                leftLegRy + leftFootRy + legFollowRy,
                leftLegRz + footRz + bodyRz);

        // 腿不写前后（tz）位移：FA 的 tz 是绕脚踝旋转的补偿量，换到髋部 pivot 之后还剩 ±3 像素残差，
        // 会让腿根在髋部来回蹭、从身体底面探出去；前后迈步交给髋部旋转即可，脚的前后位移几乎一样。
        // 竖直方向只保留抬脚（foot ty，最多 3 像素、方向朝上，只会让腿根更深地埋进身体）。
        // 身体自己的 ty 下限是静止值（-2.8-12-12+12 再加始终为正的动画项），只会往下沉、增加重叠，
        // 所以腿不用跟着它动，腿根不会和身体分开。
        setTranslation(rightLeg, bodyTx, rightFootTy, 0.0F);
        setTranslation(leftLeg, bodyTx, leftFootTy, 0.0F);

        // ---------- 眼睛 ----------
        animateEyes(randomPhase, age, clampedYaw, headPitch, ylook, nov1, aggroB);
    }

    /**
     * FA 的眼睛子模型：瞳孔的转动与上下裁剪、眼皮的眨眼与眯眼。
     *
     * <p>层级照原文件：眼球 {@code *_eye} 下挂瞳孔链 {@code *_eye_pupil → _in → _do → _up}，
     * 瞳孔方块挂在 {@code *_eye_pupil_up} 上；眼皮是 {@code *_eyelid → *_blink}（本模型的
     * {@code *_eyelid} 同时带着眼窝方块）。geo.json 里缺哪根骨骼就跳过对应效果。
     *
     * <p>CEM 里 FA 的 {@code right_*} 骨骼在模型的 -x，本模型 -x 的那只眼叫 {@code left_*}，
     * 所以 {@code left_*} 用 {@code r_*} 那套公式、{@code right_*} 用 {@code l_*} 那套。
     * 位移按“动画值 - 静止常量”叠加，{@code ctrl_*_pupil} 的静止值是 {@code (∓0.5, 0.5)}。
     *
     * <p>没有移植的三处：{@code *_eye_white / *_eye_top} 两根骨骼本模型没有，它们的值在下面当局部变量用；
     * {@code *_eye.sz = if(varb.distance, 1, 2)} 没做（本模型眼骨骼的 pivot 在脸面上，缩放 z 会把眼窝
     * 和眼皮片整个推到脸外面去）；{@code *_eyelid.sy/sz} 也没写（这里的 {@code *_eyelid} 是眼窝方块，
     * 必须保持静止），FA 用眼皮高度表达的“眯眼”改成并进 {@code *_blink} 的覆盖量。
     */
    private void animateEyes(float randomPhase, float age, float clampedYaw, float headPitch,
                             float ylook, float novelty1, float aggroB) {
        // r_eye_top.ty / l_eye_top.ty（两只眼相同）：上眼睑的下压量，被 head_pitch 与噪声驱动，暴怒时归零
        float eyeTopTy = (Mth.clamp(2.0F * Mth.clamp(-0.2F - Mth.cos(randomPhase + age / 70.0F) * 2.0F, 0.0F, 1.0F),
                        0.0F, 0.3F)
                + Mth.clamp(Mth.sin(randomPhase + age / 38.0F) + Mth.sin(randomPhase + age / 13.0F), -0.2F, 0.2F)
                        * Mth.clamp(-40.0F - Mth.cos(randomPhase + age / 125.0F) * 60.0F, 0.0F, 1.0F) / 2.0F
                + rad(headPitch) / 10.0F) * (1.0F - aggroB);

        // ctrl_r_pupil.tx / ty：视线方向（横向还吃 var.hy、var.ylook、var.nov1）
        float ctrlTx = -0.5F + (-clampedYaw / (80.0F + 80.0F * ylook)
                + Mth.clamp(Mth.sin(randomPhase + age / 27.0F) + Mth.sin(randomPhase + age / 16.0F), -0.2F, 0.2F)
                        * Mth.clamp(-40.0F - Mth.cos(randomPhase + age / 125.0F) * 60.0F, 0.0F, 1.0F)
                        * Mth.clamp(236.0F - Mth.sin(randomPhase + age / 187.0F) * 240.0F, 0.0F, 1.0F)
                + (-Mth.sin(Mth.PI / 4.0F + randomPhase + age / 10.0F) * 9.0F)
                        * Mth.clamp(novelty1 * 4.0F, 0.0F, 1.0F)) * (1.0F - aggroB)
                + Mth.clamp(Mth.cos(age / 3.0F) * 10.0F, -0.1F, 0.1F) * aggroB;
        float ctrlTy = 0.5F + (Mth.clamp(headPitch / (30.0F + 200.0F * ylook), -0.3F, 0.5F)
                + (Mth.clamp(Mth.sin(randomPhase + age / 38.0F) + Mth.sin(randomPhase + age / 13.0F), -0.2F, 0.2F)
                        * Mth.clamp(-40.0F - Mth.cos(randomPhase + age / 125.0F) * 60.0F, 0.0F, 1.0F)
                        - Mth.clamp(-24.0F + Mth.sin(randomPhase + age / 100.0F) * 40.0F, 0.0F, 1.0F) / 10.0F
                        + Mth.clamp(2.0F * Mth.clamp(-0.2F - Mth.cos(randomPhase + age / 70.0F) * 2.0F, 0.0F, 1.0F),
                                0.0F, 0.3F))
                + (Mth.clamp(rad(clampedYaw) / 8.0F, 0.0F, 0.2F)
                        + Mth.clamp(-rad(clampedYaw) / 8.0F, 0.0F, 0.2F)) * ylook) * (1.0F - aggroB);

        // r_eye_pupil / l_eye_pupil：瞳孔最终位置，减掉静止值后的偏差正好落在 geo.json 画好的位置上
        float leftPupilTx = Mth.clamp(ctrlTx, -1.5F, 0.5F) + 0.5F;                     // geo 的 left_* = FA 的 r_*
        float rightPupilTx = Mth.clamp(ctrlTx + 1.0F, -0.5F, 1.5F) - 0.5F;             // geo 的 right_* = FA 的 l_*
        float pupilTy = Mth.clamp(ctrlTy, eyeTopTy, 1.0F) - 0.5F;
        // 上/下眼睑把瞳孔裁在眼白里
        float pupilUpSy = Mth.clamp(1.0F - (ctrlTy - 0.5F), 0.5F, Mth.clamp(1.0F - eyeTopTy, 0.5F, 1.0F));
        float pupilDownSy = Mth.clamp(1.0F + (ctrlTy - 0.5F) - eyeTopTy, 0.5F, 1.0F);

        applyPupil("l_eye_pupil", leftPupilTx, pupilTy, pupilUpSy, pupilDownSy);
        applyPupil("r_eye_pupil", rightPupilTx, pupilTy, pupilUpSy, pupilDownSy);

        // right_blink.sy = left_blink.sy：常态 0（眼皮收在眼上方），眨眼时冲到 1；上眼睑被压下来时也跟着盖住一部分
        float blink = Mth.clamp((1.5F - Mth.abs(Mth.sin(randomPhase + age / 16.0F) * 12.0F))
                * Mth.clamp(-32.0F + Mth.cos((randomPhase + age / 16.0F) / 1.5F) * 40.0F
                        + Mth.cos((randomPhase + age / 16.0F) / 4.0F) * 40.0F, 0.0F, 1.0F),
                0.0F, 1.0F - aggroB);
        float lid = Mth.clamp(Math.max(blink, eyeTopTy), 0.0F, 1.0F);

        setLid("left_blink", lid);
        setLid("right_blink", lid);
    }

    /** 一条瞳孔链：位移写在 {@code *_eye_pupil} 上，上下裁剪用 {@code _up} / {@code _do} 的 sy。 */
    private void applyPupil(String pupilName, float tx, float ty, float upSy, float downSy) {
        GeoBone pupil = bone(pupilName);
        if (pupil != null) setTranslation(pupil, tx, ty, 0.0F);

        GeoBone clipUp = bone(pupilName + "_up");
        if (clipUp != null) setScale(clipUp, 1.0F, upSy, 1.0F);

        GeoBone clipDown = bone(pupilName + "_do");
        if (clipDown != null) setScale(clipDown, 1.0F, downSy, 1.0F);
    }

    /** 眼皮片：{@code sy} 就是"盖住多少"，0 = 收在眼上方，1 = 完全盖住。 */
    private void setLid(String name, float amount) {
        GeoBone lid = bone(name);
        if (lid != null) setScale(lid, 1.0F, amount, 1.0F);
    }
}
