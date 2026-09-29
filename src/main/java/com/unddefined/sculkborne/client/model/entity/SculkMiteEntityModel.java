package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CemEntityModel;
import com.unddefined.sculkborne.entities.SculkMiteEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class SculkMiteEntityModel<T extends SculkMiteEntity> extends CemEntityModel<T> {

    public SculkMiteEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_mite"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
