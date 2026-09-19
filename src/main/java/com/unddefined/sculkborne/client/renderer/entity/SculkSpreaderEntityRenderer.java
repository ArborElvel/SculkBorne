package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.SculkSpreaderEntityModel;
import com.unddefined.sculkborne.entities.SculkSpreaderEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SculkSpreaderEntityRenderer  extends GeoEntityRenderer<SculkSpreaderEntity> {
    public SculkSpreaderEntityRenderer(EntityRendererProvider.Context c) {
        super(c, new SculkSpreaderEntityModel<>());
    }
}
