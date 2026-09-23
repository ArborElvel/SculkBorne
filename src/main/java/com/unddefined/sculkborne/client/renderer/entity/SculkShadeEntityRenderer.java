package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.SculkShadeEntityModel;
import com.unddefined.sculkborne.entities.SculkShadeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SculkShadeEntityRenderer extends GeoEntityRenderer<SculkShadeEntity> {
    public SculkShadeEntityRenderer(EntityRendererProvider.Context c) {
        super(c, new SculkShadeEntityModel<>());
    }
}
