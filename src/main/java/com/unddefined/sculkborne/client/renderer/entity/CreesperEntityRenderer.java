package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.CreesperEntityModel;
import com.unddefined.sculkborne.client.renderer.layer.CreesperWhisperLayer;
import com.unddefined.sculkborne.entities.CreesperEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 幽匿苦力怕的渲染器：身体走默认的 GeckoLib 流程，{@code whisper} 骨骼由
 * {@link CreesperWhisperLayer} 单独用自己的贴图渲染。
 */
public class CreesperEntityRenderer extends GeoEntityRenderer<CreesperEntity> {
    public CreesperEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new CreesperEntityModel<>());
        addRenderLayer(new CreesperWhisperLayer(this));
    }
}
