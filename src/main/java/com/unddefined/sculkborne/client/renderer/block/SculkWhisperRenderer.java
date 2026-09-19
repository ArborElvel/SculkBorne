package com.unddefined.sculkborne.client.renderer.block;

import com.unddefined.sculkborne.blocks.entity.SculkWhisperBlockEntity;
import com.unddefined.sculkborne.client.model.block.SculkWhisperModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SculkWhisperRenderer extends GeoBlockRenderer<SculkWhisperBlockEntity> {
    public SculkWhisperRenderer() {
        super(new SculkWhisperModel<>());
    }
}
