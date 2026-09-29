package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CemEntityModel;
import com.unddefined.sculkborne.entities.WandererEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class WandererEntityModel<T extends WandererEntity> extends CemEntityModel<T> {

    public WandererEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "wanderer"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
