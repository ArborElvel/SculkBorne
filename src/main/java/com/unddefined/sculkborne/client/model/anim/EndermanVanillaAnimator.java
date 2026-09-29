package com.unddefined.sculkborne.client.model.anim;

import com.unddefined.sculkborne.entities.WandererEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 原版 {@code EndermanModel#setupAnim} 的移植：在 {@code HumanoidModel} 的基础上把四肢摆幅减半并夹到 ±0.4、
 * 搬方块时把双臂抬到身前，以及被注视暴怒时把头往下沉 5 像素。
 *
 * <p>原版 {@code EndermanModel} 里的 {@code head.y = -13}、{@code body.y = -14}、
 * {@code rightArm.setPos(-5, -12, 0)}、{@code legs.y = -5} 都是静止姿势常量（geo 的 pivot 已经烘焙过），
 * 所以下面只处理真正会变化的那一项。
 *
 * <p>原版是在拷贝 {@code hat} 之后才改 {@code head.y}，所以基类先拷一次 {@code hat}、
 * 这里再动头，外层头部方块留在原处，和原版一致。
 */
public final class EndermanVanillaAnimator extends HumanoidVanillaAnimator<WandererEntity> {

    /** 原版把四肢摆动夹到的上限（弧度）。 */
    private static final float LIMB_SWING_CLAMP = 0.4F;

    /**
     * 暴怒时 {@code head.y} 的位移，对应原版的 {@code head.y -= 5.0F}。
     *
     * <p>注意这里用的是原版 {@code ModelPart} 的 y（向下为正），geo 的 y 轴朝上，
     * 换算交给 {@link #setTranslation}。
     */
    private static final float CREEPY_HEAD_Y = -5.0F;

    public EndermanVanillaAnimator(GeoModel<?> model) {
        super(model);
    }

    @Override
    protected void poseFinal(WandererEntity entity, VanillaFrame frame, GeoBone head, GeoBone body,
                             GeoBone rightArm, GeoBone leftArm, GeoBone rightLeg, GeoBone leftLeg) {
        float rightArmX = Mth.clamp(rotX(rightArm) * 0.5F, -LIMB_SWING_CLAMP, LIMB_SWING_CLAMP);
        float leftArmX = Mth.clamp(rotX(leftArm) * 0.5F, -LIMB_SWING_CLAMP, LIMB_SWING_CLAMP);
        float rightLegX = Mth.clamp(rotX(rightLeg) * 0.5F, -LIMB_SWING_CLAMP, LIMB_SWING_CLAMP);
        float leftLegX = Mth.clamp(rotX(leftLeg) * 0.5F, -LIMB_SWING_CLAMP, LIMB_SWING_CLAMP);

        float rightArmZ = rotZ(rightArm);
        float leftArmZ = rotZ(leftArm);

        if (entity.getCarriedBlock() != null) {
            rightArmX = -0.5F;
            leftArmX = -0.5F;
            rightArmZ = 0.05F;
            leftArmZ = -0.05F;
        }

        setRotation(rightArm, rightArmX, rotY(rightArm), rightArmZ);
        setRotation(leftArm, leftArmX, rotY(leftArm), leftArmZ);
        setRotation(rightLeg, rightLegX, rotY(rightLeg), rotZ(rightLeg));
        setRotation(leftLeg, leftLegX, rotY(leftLeg), rotZ(leftLeg));

        hideFreshAnimationsEyelids();

        // 原版这一句在 hat 拷贝之后，所以只有头自己动，外层头部方块留在原处
        if (entity.isCreepy()) setTranslation(head, 0.0F, CREEPY_HEAD_Y, 0.0F);
    }
}
