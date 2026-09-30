package com.unddefined.sculkborne.client.model.entity;

import com.unddefined.sculkborne.entities.SculkSpreaderEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

public class SculkSpreaderEntityModel<T extends SculkSpreaderEntity> extends DefaultedEntityGeoModel<T> {
    public SculkSpreaderEntityModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_spreader"));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture) {return RenderType.entityTranslucent(texture);}

    /**
     * 绽放期间整只生物改用 bloom 贴图，与幽匿催发体 {@code bloom=true} 时换成 bloom 模型是同一个做法。
     *
     * <p>{@code sculk_spreader_bloom.png} 是一张和 {@code sculk_spreader.png} 同尺寸、同布局的贴图，
     * 只有头部各面的像素不同（其余像素与本体贴图完全一致，所以身上看起来不变），
     * 头顶那部分带 {@code .mcmeta} 逐帧动画，由 GeckoLib 的 {@code AnimatableTexture} 按帧播放。
     */
    @Override
    public ResourceLocation getTextureResource(T animatable, @Nullable GeoRenderer<T> renderer) {
        return buildFormattedTexturePath(ResourceLocation.fromNamespaceAndPath("sculkborne",
                "sculk_spreader" + (animatable.isBlooming() ? "_bloom" : "")));
    }
}
