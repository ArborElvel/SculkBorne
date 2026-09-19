package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.entities.SculkSpreaderEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class SculkSpreaderEntityModel<T extends SculkSpreaderEntity> extends DefaultedEntityGeoModel<T> {
    public SculkSpreaderEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_spreader"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {return RenderType.entityTranslucent(texture);}

}
