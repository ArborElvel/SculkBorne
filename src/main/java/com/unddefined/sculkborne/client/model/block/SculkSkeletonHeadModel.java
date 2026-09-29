package com.unddefined.sculkborne.client.model.block;

import com.unddefined.sculkborne.blocks.entity.SculkHeadBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * 幽匿骷髅的头的模型。
 *
 * <p>和幽匿僵尸的头一样复用实体的几何与贴图：{@code geo/block/sculk_skeleton_head.geo.json} 里的头
 * 与头顶的触须环就是实体模型头上那部分，贴图指向 {@code textures/entity/sculk_skeleton.png}。
 * 骷髅的触须画在实体贴图里，所以不需要额外的渲染层。
 */
public class SculkSkeletonHeadModel<T extends SculkHeadBlockEntity> extends DefaultedBlockGeoModel<T> {
    /** 幽匿骷髅实体的贴图，头的各个面与触须都从这里取样。 */
    private static final ResourceLocation ENTITY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("sculkborne", "textures/entity/sculk_skeleton.png");

    public SculkSkeletonHeadModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_skeleton_head"));
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return ENTITY_TEXTURE;
    }
}
