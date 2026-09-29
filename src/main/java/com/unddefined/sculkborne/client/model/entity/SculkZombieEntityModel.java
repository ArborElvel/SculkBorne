package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CemEntityModel;
import com.unddefined.sculkborne.entities.SculkZombieEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoRenderer;

public class SculkZombieEntityModel<T extends SculkZombieEntity> extends CemEntityModel<T> {

    public SculkZombieEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_zombie"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {return RenderType.entityTranslucent(texture);}

    @Override
    public ResourceLocation getTextureResource(T animatable, @Nullable GeoRenderer<T> renderer) {
        return buildFormattedTexturePath(ResourceLocation.fromNamespaceAndPath("sculkborne",
                "sculk_zombie" + (animatable.isVibrationActive() ? "_active" : "")));
    }
}
