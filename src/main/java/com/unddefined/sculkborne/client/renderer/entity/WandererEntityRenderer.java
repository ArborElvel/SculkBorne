package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.WandererEntityModel;
import com.unddefined.sculkborne.client.renderer.layer.WandererCarriedBlockLayer;
import com.unddefined.sculkborne.client.renderer.layer.WandererEyesLayer;
import com.unddefined.sculkborne.entities.WandererEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WandererEntityRenderer extends GeoEntityRenderer<WandererEntity> {
    public WandererEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new WandererEntityModel<>());
        addRenderLayer(new WandererEyesLayer(this));
        // 原版的 CarriedBlockLayer 挂不到 GeckoLib 渲染器上，搬起来的方块要自己画
        addRenderLayer(new WandererCarriedBlockLayer(this));
    }
}
