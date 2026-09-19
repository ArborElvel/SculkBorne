package com.unddefined.sculkborne.client.model.block;

import com.unddefined.sculkborne.blocks.entity.CalibratedSculkShriekerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

public class CalibratedSculkShriekerModel<T extends CalibratedSculkShriekerBlockEntity> extends DefaultedBlockGeoModel<T> {
    public CalibratedSculkShriekerModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "calibrated_sculk_shrieker"));
    }
}
