package com.unddefined.sculkborne.server.events;

import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.server.registry.BlockRegistry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = SculkBorne.MODID)
public class EchoDrusePlacer {
    @SubscribeEvent
    public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
        // 检查是否是右键点击方块
        ItemStack stack = event.getItemStack();
        // 检查玩家是否使用回响碎片
        if (stack.getItem() != Items.ECHO_SHARD) return;
        // 获取被点击的方块状态
        BlockState blockState = event.getLevel().getBlockState(event.getPos());
        // 检查被点击的方块是否是Sculk Catalyst
        if (blockState.getBlock() != Blocks.SCULK_CATALYST) return;
        if (event.getLevel().isClientSide()) return;
        // 消耗一个回响碎片（创造模式不消耗）
        if (!event.getEntity().isCreative()) stack.shrink(1);

        event.getLevel().setBlock(event.getPos().atY(event.getPos().getY() + 1), BlockRegistry.ECHO_DRUSE.get().defaultBlockState(), 3);

        // 设置交互结果为成功
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        //播放Sculk音效
        event.getLevel().playSound(null, event.getPos(), SoundEvents.SCULK_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        event.getLevel().gameEvent(event.getEntity(), GameEvent.BLOCK_PLACE, event.getPos());
    }
}
