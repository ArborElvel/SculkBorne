package com.unddefined.sculkborne.blocks;

import net.minecraft.world.level.block.SkullBlock;

/**
 * 本模组各个头颅的 {@link SkullBlock.Type}，写法与原版 {@link SkullBlock.Types} 一致。
 *
 * <p>原版 {@code Types} 是写死的枚举，幽匿头颅如果借它的值（僵尸头借 {@code ZOMBIE}、骷髅头借
 * {@code SKELETON}），戴上头时原版 {@code SkullBlockRenderer} 就会按僵尸头/骷髅头的模型与贴图去画，
 * 和本模组的头长得不一样。所以每个幽匿头颅在这里各有一个自己的值，客户端再把本模组的模型与贴图
 * 填进原版那两张表（见 {@code SculkBorneClient}）。
 */
public enum SculkHeadTypes implements SkullBlock.Type {
    SCULK_ZOMBIE("sculk_zombie"),
    SCULK_SKELETON("sculk_skeleton");

    private final String name;

    SculkHeadTypes(String name) {
        this.name = name;
        TYPES.put(name, this);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
