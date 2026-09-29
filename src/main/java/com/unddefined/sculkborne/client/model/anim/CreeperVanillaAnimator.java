package com.unddefined.sculkborne.client.model.anim;

import com.unddefined.sculkborne.entities.CreesperEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版 {@code CreeperModel#setupAnim} 的移植。
 *
 * <h2>骨骼对应</h2>
 * <p>原版四条腿是 {@code right_hind_leg / left_hind_leg / right_front_leg / left_front_leg}
 * （x 为负的是 right），geo 里按 pivot 一一对应成 {@code leg1..leg4}：
 * {@code leg1}(x&lt;0,z&gt;0)、{@code leg2}(x&gt;0,z&gt;0)、{@code leg3}(x&lt;0,z&lt;0)、{@code leg4}(x&gt;0,z&lt;0)。
 * 原版代码里字段名与子节点名是反的（{@code rightHindLeg} 取的是 {@code left_hind_leg}），
 * 下面已经按“子节点名”整理过相位。
 */
public final class CreeperVanillaAnimator extends VanillaAnimator<CreesperEntity> {

    public CreeperVanillaAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void animate(CreesperEntity entity, VanillaFrame frame) {
        GeoBone head = bone("head");
        GeoBone leg1 = bone("leg1");
        GeoBone leg2 = bone("leg2");
        GeoBone leg3 = bone("leg3");
        GeoBone leg4 = bone("leg4");

        if (head == null || leg1 == null || leg2 == null || leg3 == null || leg4 == null) return;

        float swing = 1.4F * frame.limbSwingAmount();
        float phase = frame.limbSwing() * 0.6662F;

        hideFreshAnimationsEyelids();

        setRotation(head, rad(frame.headPitch()), rad(frame.headYaw()), 0.0F);

        setRotation(leg1, Mth.cos(phase + (float) Math.PI) * swing, 0.0F, 0.0F);
        setRotation(leg2, Mth.cos(phase) * swing, 0.0F, 0.0F);
        setRotation(leg3, Mth.cos(phase) * swing, 0.0F, 0.0F);
        setRotation(leg4, Mth.cos(phase + (float) Math.PI) * swing, 0.0F, 0.0F);
    }
}
