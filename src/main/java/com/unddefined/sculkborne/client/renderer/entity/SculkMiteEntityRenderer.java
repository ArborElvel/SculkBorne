package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.SculkMiteEntityModel;
import com.unddefined.sculkborne.entities.SculkMiteEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SculkMiteEntityRenderer extends GeoEntityRenderer<SculkMiteEntity> {
    public SculkMiteEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new SculkMiteEntityModel<>());
    }
}
