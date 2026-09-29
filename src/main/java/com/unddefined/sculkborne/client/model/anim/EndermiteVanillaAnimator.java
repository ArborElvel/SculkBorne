package com.unddefined.sculkborne.client.model.anim;

import com.unddefined.sculkborne.entities.SculkMiteEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版 {@code EndermiteModel#setupAnim} 的移植：四个体节按 {@code ageInTicks} 做左右扭动。
 *
 * <p>原版 {@code segment0..segment3} 是四根平级的骨骼，FA 把模型改写成一条链之后，
 * 带方块的骨骼分别是 {@code head_s}(0)、{@code tail_s}(1)、{@code tail1_s}(2)、{@code tail2}(3)，
 * 对应关系见 {@code SculkMiteCemAnimator} 的类注释。
 *
 * <p>链式层级与平级层级在旋转/位移的叠加方式上不同：这里按原版逐节的数值写入，
 * 因为四根骨骼在 geo 里各自处在不同分支上（没有任何一节是另一节的祖先），叠加结果与原版一致。
 */
public final class EndermiteVanillaAnimator extends VanillaAnimator<SculkMiteEntity> {

    /** 原版 {@code segment0..segment3} 对应的骨骼名。 */
    private static final String[] SEGMENTS = {"head_s", "tail_s", "tail1_s", "tail2"};

    public EndermiteVanillaAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(SculkMiteEntity entity, VanillaFrame frame) {
        for (int i = 0; i < SEGMENTS.length; i++) {
            GeoBone segment = bone(SEGMENTS[i]);

            if (segment == null) return;

            float phase = frame.ageInTicks() * 0.9F + i * 0.15F * (float) Math.PI;
            float spread = Math.abs(i - 2);

            setRotation(segment, 0.0F, Mth.cos(phase) * (float) Math.PI * 0.01F * (1.0F + spread), 0.0F);
            setTranslation(segment, Mth.sin(phase) * (float) Math.PI * 0.1F * spread, 0.0F, 0.0F);
        }
    }
}
