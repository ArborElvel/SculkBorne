package com.unddefined.sculkborne.blocks;

import com.unddefined.sculkborne.blocks.entity.SculkHeadBlockEntity;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * 幽匿僵尸的头。
 *
 * <p>方块本身沿用原版头颅，行为见 {@link SculkHeadBlock}：接收振动、输出红石，也会像原版感测体那样
 * 共鸣相邻的振动共鸣方块。
 */
public class SculkZombieHeadBlock extends SculkHeadBlock {
    public SculkZombieHeadBlock() {
        super(SculkHeadTypes.SCULK_ZOMBIE, Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .instrument(NoteBlockInstrument.ZOMBIE)
                .strength(1.0F)
                .sound(SoundType.SCULK_SENSOR)
                .lightLevel(state -> 1)
                .pushReaction(PushReaction.DESTROY), true);
    }

    @Override
    public BlockEntityType<SculkHeadBlockEntity> blockEntityType() {
        return BlockEntityRegistry.SCULK_ZOMBIE_HEAD.get();
    }
}
