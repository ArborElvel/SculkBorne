package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CemEntityModel;
import com.unddefined.sculkborne.entities.CreesperEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class CreesperEntityModel<T extends CreesperEntity> extends CemEntityModel<T> {
    public CreesperEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "creesper"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
