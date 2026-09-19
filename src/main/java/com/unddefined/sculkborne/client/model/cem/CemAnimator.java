package com.unddefined.sculkborne.client.model.cem;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fresh Animations 风格 CEM（Custom Entity Model）动画的公共层。
 *
 * <p>要移植新的生物时继承本类，只在 {@link #animate(CemFrame)} 里写 CEM 公式即可：坐标系换算、
 * 每实体跨帧状态、每帧的实体输入都由这里负责。已经移植好的例子见
 * {@code client.model.entity.SculkZombieCemAnimator}。
 *
 * <h2>坐标系约定</h2>
 * <ul>
 *   <li>CEM 的 {@code rx/ry/rz} 与 MC {@code ModelPart} 的 {@code xRot/yRot/zRot} 同号；
 *       GeckoLib 在加载 geo.json 时把 x/y 的旋转取反（见 {@code BakedModelFactory#constructBone}），
 *       因此统一用 {@link #setRotation} 写入，不要直接调 {@code GeoBone#setRotX}。</li>
 *   <li>CEM 的 {@code tx/ty/tz} 与 MC {@code ModelPart} 的 {@code x/y/z} 同号（单位像素，1 像素 = 1/16 格，
 *       y 轴向下）；geo.json 的 y 轴朝上，因此用 {@link #setTranslation} 写入。</li>
 *   <li>CEM 公式给出的是绝对姿势（会覆盖原版动画），所以是 set 而不是 add。</li>
 * </ul>
 *
 * <p>实例只在客户端渲染线程使用，每个模型持有一个。跨帧状态按实体存放，不同个体会互相隔离。
 */
public abstract class CemAnimator<T extends LivingEntity> {

    /**
     * 单次状态更新的最大 tick 步长，对应 CEM 里 {@code step * frame_time * 20} 的“每 tick”语义。
     */
    static final int MAX_STEP_TICKS = 20;

    private final GeoModel<?> model;
    private final Map<T, State> states = new WeakHashMap<>();

    protected CemAnimator(GeoModel<?> model) {
        this.model = model;
    }

    /**
     * 每帧入口，在 {@code GeoModel#setCustomAnimations} 中调用。
     *
     * @param entity         正在渲染的生物
     * @param animationState GeckoLib 传入的动画状态
     */
    public final void apply(T entity, AnimationState<?> animationState) {
        State state = this.states.computeIfAbsent(entity, key -> new State());

        this.animate(new CemFrame<>(entity, animationState, state));
    }

    /**
     * 把 CEM 公式写到骨骼上。
     */
    protected abstract void animate(CemFrame<T> frame);

    /**
     * 按名字取骨骼；模型里没有这个骨骼时返回 {@code null}。
     */
    @Nullable
    protected final GeoBone bone(String name) {
        return this.model.getAnimationProcessor().getBone(name);
    }

    /**
     * 写入 CEM 的 {@code rx/ry/rz}。
     */
    protected static void setRotation(GeoBone bone, float rx, float ry, float rz) {
        bone.setRotX(-rx);
        bone.setRotY(-ry);
        bone.setRotZ(rz);
    }

    /**
     * 写入 CEM 的 {@code tx/ty/tz}。
     * <p>参数同样适用于“相对静止姿势的偏移”：只把随动画变化的项减出来即可。
     */
    protected static void setTranslation(GeoBone bone, float tx, float ty, float tz) {
        bone.setPosX(tx);
        bone.setPosY(-ty);
        bone.setPosZ(tz);
    }

    /**
     * 写入 CEM 的 {@code sx/sy/sz}。
     * <p>GeoLib 的缩放是在旋转之后、绕骨骼自身 pivot 应用的，与 CEM 的语义一致，因此不需要换算符号。
     */
    protected static void setScale(GeoBone bone, float sx, float sy, float sz) {
        bone.setScaleX(sx);
        bone.setScaleY(sy);
        bone.setScaleZ(sz);
    }

    /**
     * CEM 的 {@code torad()}。
     */
    protected static float rad(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }

    /**
     * CEM 的 {@code random(id)}：把种子按 float 位模式哈希后归一化，
     * 同一个实体每帧都得到同一个值（与 OptiFine / EntityModelFeatures 的实现一致）。
     */
    protected static float random(int id) {
        int hash = intHash(Float.floatToIntBits(id));

        return Math.abs(hash) / 2.14748365E9F;
    }

    private static int intHash(int value) {
        int x = value;
        x = x ^ 61 ^ x >> 16;
        x += x << 3;
        x ^= x >> 4;
        x *= 668265261;

        return x ^ x >> 15;
    }

    /**
     * 单个实体跨帧保留的状态：{@code var.NAME} 以及上一次状态更新所在的 tick。
     */
    static final class State {
        private final Map<String, Float> variables = new HashMap<>();
        private int lastTick = Integer.MIN_VALUE;

        float get(String name) {
            Float value = this.variables.get(name);

            return value == null ? 0.0F : value;
        }

        void set(String name, float value) {
            this.variables.put(name, value);
        }

        int elapsedTicks(int currentTick) {
            if (this.lastTick == Integer.MIN_VALUE) {
                this.lastTick = currentTick;

                return 0;
            }

            int elapsed = Mth.clamp(currentTick - this.lastTick, 0, MAX_STEP_TICKS);

            if (elapsed > 0) this.lastTick = currentTick;

            return elapsed;
        }
    }
}
