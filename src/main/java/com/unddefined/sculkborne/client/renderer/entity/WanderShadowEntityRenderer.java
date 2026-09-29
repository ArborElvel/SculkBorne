package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.WanderShadowEntityModel;
import com.unddefined.sculkborne.entities.WanderShadowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 徘徊者残影的渲染器。
 *
 * <p>它没有眼睛相关的骨骼（也就没有眼珠），所以不挂 {@code WandererEyesLayer} 那种自发光眼睛层；
 * 它也不会搬方块，所以不像徘徊者那样挂一层搬运方块的渲染层。
 */
public class WanderShadowEntityRenderer extends GeoEntityRenderer<WanderShadowEntity> {

    public WanderShadowEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new WanderShadowEntityModel<>());
    }
}
