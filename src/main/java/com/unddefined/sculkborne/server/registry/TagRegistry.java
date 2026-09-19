package com.unddefined.sculkborne.server.registry;

import com.unddefined.sculkborne.SculkBorne;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 自定义方块标签，标签内容由数据包提供，文件位于 {@code data/sculkborne/tags/block/} 下，
 * 运行时通过 {@code BlockState#is(TagKey)} 判定。
 */
public class TagRegistry {
    /** 幽匿系方块：幽匿生物站在其上回血、在其附近生成等规则共用。 */
    public static final TagKey<Block> SCULK_BLOCKS = create("sculk_blocks");

    private static TagKey<Block> create(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, name));
    }
}
