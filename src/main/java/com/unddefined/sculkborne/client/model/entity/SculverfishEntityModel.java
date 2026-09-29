package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CemEntityModel;
import com.unddefined.sculkborne.entities.SculverfishEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class SculverfishEntityModel<T extends SculverfishEntity> extends CemEntityModel<T> {

    public SculverfishEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculverfish"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
