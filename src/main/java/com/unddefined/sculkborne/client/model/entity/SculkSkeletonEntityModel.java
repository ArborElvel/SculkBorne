package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CemEntityModel;
import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class SculkSkeletonEntityModel<T extends SculkSkeletonEntity> extends CemEntityModel<T> {

    public SculkSkeletonEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_skeleton"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
