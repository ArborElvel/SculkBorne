package com.unddefined.sculkborne.client.model.anim;

import com.unddefined.sculkborne.client.model.cem.CemAnimatorHandle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版生物动作的公共层：把 {@code net.minecraft.client.model} 里那些 mob 模型类的
 * {@code setupAnim} 公式移植到本 mod 自己的骨骼上。
 *
 * <p>这是 sculkborne 自带的姿势实现：没有安装 CEM 附属时由它接管整帧姿势，装了之后
 * 由附属的 {@link CemAnimatorHandle} 覆盖（见 {@code CemAnimatorRegistry} 的两级注册）。
 *
 * <h2>坐标系约定</h2>
 * <p>与原版 {@code ModelPart} 一一对应，和 CEM 移植层用的是同一套换算：
 * <ul>
 *   <li>旋转用 {@link #setRotation}，参数是原版 {@code ModelPart} 的 {@code xRot/yRot/zRot}（弧度）；
 *       GeckoLib 加载 geo.json 时把 x/y 取反（见 {@code BakedModelFactory#constructBone}），由该方法统一处理。</li>
 *   <li>位移用 {@link #setTranslation}，参数是原版 {@code ModelPart} 的 {@code x/y/z}（像素、y 轴向下）；
 *       因为 geo.json 的静止姿势已经烘焙在骨骼 pivot 上，这里传的是相对静止姿势的差值。</li>
 * </ul>
 *
 * <p>实例只在客户端渲染线程使用，每个模型持有一个（由 {@code CemAnimatorRegistry} 按模型缓存）。
 */
public abstract class VanillaAnimator<T extends LivingEntity> implements CemAnimatorHandle<T> {

    private final GeoModel<?> model;

    protected VanillaAnimator(GeoModel<?> model) {
        this.model = model;
    }

    @Override
    public final void apply(T entity, AnimationState<?> animationState) {
        this.animate(entity, new VanillaFrame(entity, animationState));
    }

    /**
     * 把对应原版 {@code setupAnim} 的公式写到骨骼上。
     *
     * @param entity 正在渲染的生物
     * @param frame  本帧的原版动画输入
     */
    protected abstract void animate(T entity, VanillaFrame frame);

    /** 按名字取骨骼；模型里没有这个骨骼时返回 {@code null}。 */
    @Nullable
    protected final GeoBone bone(String name) {
        return this.model.getAnimationProcessor().getBone(name);
    }

    /** 写入原版 {@code ModelPart} 的旋转，参数单位为弧度。 */
    protected static void setRotation(GeoBone bone, float rx, float ry, float rz) {
        bone.setRotX(-rx);
        bone.setRotY(-ry);
        bone.setRotZ(rz);
    }

    /** 写入原版 {@code ModelPart} 的位移，参数单位为像素、y 轴向下。 */
    protected static void setTranslation(GeoBone bone, float tx, float ty, float tz) {
        bone.setPosX(tx);
        bone.setPosY(-ty);
        bone.setPosZ(tz);
    }

    /** 写入骨骼缩放（GeckoLib 的缩放绕骨骼自身 pivot 应用，与原版 {@code ModelPart} 的语义一致）。 */
    protected static void setScale(GeoBone bone, float sx, float sy, float sz) {
        bone.setScaleX(sx);
        bone.setScaleY(sy);
        bone.setScaleZ(sz);
    }

    /**
     * 把 FA 版模型带进来的眼皮片压扁。
     *
     * <p>geo 的骨骼是按 Fresh Animations 的形状重排的，其中 {@code left_blink / right_blink}
     * 是原版没有、只在 FA 里存在的眼皮方块（平时靠 CEM 的眨眼公式压到 0 才看不见）。
     * 原版动作没有眨眼，因此这里固定压扁，让眼睛保持睁开。
     */
    protected final void hideFreshAnimationsEyelids() {
        for (String name : new String[]{"left_blink", "right_blink"}) {
            GeoBone lid = bone(name);

            if (lid != null) setScale(lid, 1.0F, 0.0F, 1.0F);
        }
    }

    /** 原版 {@code ModelPart#copyFrom} 的旋转部分：直接照搬 GeckoLib 侧已经换算好的欧拉角。 */
    protected static void copyRotation(GeoBone from, GeoBone to) {
        to.setRotX(from.getRotX());
        to.setRotY(from.getRotY());
        to.setRotZ(from.getRotZ());
    }

    /** 读出骨骼上当前的原版 {@code xRot}（弧度），配合 {@link #setRotation} 使用。 */
    protected static float rotX(GeoBone bone) {
        return -bone.getRotX();
    }

    /** 读出骨骼上当前的原版 {@code yRot}（弧度）。 */
    protected static float rotY(GeoBone bone) {
        return -bone.getRotY();
    }

    /** 读出骨骼上当前的原版 {@code zRot}（弧度）。 */
    protected static float rotZ(GeoBone bone) {
        return bone.getRotZ();
    }

    /** 角度转弧度，方便照抄原版公式里的常数与 {@code Math.PI} 写法。 */
    protected static float rad(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }
}
