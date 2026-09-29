package com.unddefined.sculkborne.client.model.anim;

import com.unddefined.sculkborne.entities.SculverfishEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版 {@code SilverfishModel#setupAnim} 的移植：七个体节按 {@code ageInTicks} 扭动，
 * 三个外层（{@code layer0..2}）跟着第 3、5、2 节的旋转与位移走。
 *
 * <p>原版 {@code segment0..segment6} + {@code layer0..2} 都是平级骨骼，FA 把模型改写成一条链之后：
 * 体节对应 {@code head}(0)、{@code head1_s}(1)、{@code tail_s}(2)、{@code tail1_s}(3)、
 * {@code tail2}(4)、{@code tail3}(5)、{@code tail4}(6)；三层外壳被并进对应体节的方块列表，
 * geo 里保留成 {@code wing3 / wing1 / wing2}，作为 {@code head1_s / tail_s / tail2} 的子骨骼，
 * 因此它们会跟着宿主一起转，不需要单独写。
 *
 * <p>链式层级会让子骨骼叠加祖先的姿势，而原版七节是平级的，所以最后两节
 * （{@code tail3}、{@code tail4}，它们是 {@code tail2} 的后代）要减掉最近一节祖先的数值，
 * 才能让世界姿势与原版一致。
 */
public final class SilverfishVanillaAnimator extends VanillaAnimator<SculverfishEntity> {

    /** 原版 {@code segment0..segment6} 对应的骨骼名。 */
    private static final String[] SEGMENTS = {"head", "head1_s", "tail_s", "tail1_s", "tail2", "tail3", "tail4"};

    /** 每一节在 geo 链上最近的“体节祖先”下标，没有则填 {@code -1}。 */
    private static final int[] SEGMENT_ANCESTORS = {-1, -1, -1, -1, -1, 4, 5};

    public SilverfishVanillaAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(SculverfishEntity entity, VanillaFrame frame) {
        float[] yaw = new float[SEGMENTS.length];
        float[] shift = new float[SEGMENTS.length];

        for (int i = 0; i < SEGMENTS.length; i++) {
            float phase = frame.ageInTicks() * 0.9F + i * 0.15F * (float) Math.PI;
            float spread = Math.abs(i - 2);

            yaw[i] = Mth.cos(phase) * (float) Math.PI * 0.05F * (1.0F + spread);
            shift[i] = Mth.sin(phase) * (float) Math.PI * 0.2F * spread;
        }

        for (int i = 0; i < SEGMENTS.length; i++) {
            GeoBone segment = bone(SEGMENTS[i]);

            if (segment == null) return;

            int ancestor = SEGMENT_ANCESTORS[i];
            float parentYaw = ancestor < 0 ? 0.0F : yaw[ancestor];
            float parentShift = ancestor < 0 ? 0.0F : shift[ancestor];

            setRotation(segment, 0.0F, yaw[i] - parentYaw, 0.0F);
            setTranslation(segment, shift[i] - parentShift, 0.0F, 0.0F);
        }
    }
}
