package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.WandererEntityModel;
import com.unddefined.sculkborne.client.renderer.layer.WandererEyesLayer;
import com.unddefined.sculkborne.entities.WandererEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WandererEntityRenderer extends GeoEntityRenderer<WandererEntity> {
    public WandererEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new WandererEntityModel<>());
        addRenderLayer(new WandererEyesLayer(this));
    }
}
