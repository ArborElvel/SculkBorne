package com.unddefined.sculkborne.client.model.block;

import com.unddefined.sculkborne.blocks.entity.SculkWhisperBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

public class SculkWhisperModel<T extends SculkWhisperBlockEntity> extends DefaultedBlockGeoModel<T> {
    public SculkWhisperModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_whisper"));
    }
}
