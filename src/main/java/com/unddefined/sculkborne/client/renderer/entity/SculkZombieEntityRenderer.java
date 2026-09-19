package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.SculkZombieEntityModel;
import com.unddefined.sculkborne.client.renderer.layer.SculkZombieTendrilLayer;
import com.unddefined.sculkborne.entities.SculkZombieEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SculkZombieEntityRenderer extends GeoEntityRenderer<SculkZombieEntity> {
    public SculkZombieEntityRenderer(EntityRendererProvider.Context c) {
        super(c, new SculkZombieEntityModel<>());
        addRenderLayer(new SculkZombieTendrilLayer(this));
    }
}
