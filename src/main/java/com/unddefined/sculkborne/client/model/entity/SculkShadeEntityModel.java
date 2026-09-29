package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CemEntityModel;
import com.unddefined.sculkborne.entities.SculkShadeEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class SculkShadeEntityModel<T extends SculkShadeEntity> extends CemEntityModel<T> {

    public SculkShadeEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_shade"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
