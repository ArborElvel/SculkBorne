package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.client.model.cem.CreesperCemAnimator;
import com.unddefined.sculkborne.entities.CreesperEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class CreesperEntityModel<T extends CreesperEntity> extends DefaultedEntityGeoModel<T> {
    private final CreesperCemAnimator cemAnimator = new CreesperCemAnimator(this);
    public CreesperEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "creesper"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        cemAnimator.apply(animatable, animationState);
    }
}
