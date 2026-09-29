package com.unddefined.sculkborne.client.model.block;

import com.unddefined.sculkborne.blocks.entity.SculkHeadBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * 幽匿僵尸的头的模型。
 *
 * <p>几何与贴图都直接复用幽匿僵尸实体：{@code geo/block/sculk_zombie_head.geo.json} 里的头与触须
 * 就是实体模型头上那部分的坐标，贴图也指向 {@code textures/entity/sculk_zombie.png}，
 * 所以方块上的头和生物头上的头长得一模一样。
 */
public class SculkZombieHeadModel<T extends SculkHeadBlockEntity> extends DefaultedBlockGeoModel<T> {
    /** 幽匿僵尸实体的贴图，头的各个面都从这里取样。 */
    private static final ResourceLocation ENTITY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("sculkborne", "textures/entity/sculk_zombie.png");

    public SculkZombieHeadModel() {
        super(ResourceLocation.fromNamespaceAndPath("sculkborne", "sculk_zombie_head"));
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return ENTITY_TEXTURE;
    }
}
