package com.unddefined.sculkborne.server.registry;

import com.unddefined.sculkborne.blocks.CalibratedSculkShriekerBlock;
import com.unddefined.sculkborne.blocks.EchoDruseBlock;
import com.unddefined.sculkborne.blocks.SculkWhisperBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("sculkborne");

    public static final DeferredBlock<CalibratedSculkShriekerBlock> CALIBRATED_SCULK_SHRIEKER =
            BLOCKS.register("calibrated_sculk_shrieker", CalibratedSculkShriekerBlock::new);
    public static final DeferredBlock<EchoDruseBlock> ECHO_DRUSE =
            BLOCKS.register("echo_druse_block", EchoDruseBlock::new);
    public static final DeferredBlock<SculkWhisperBlock> SCULK_WHISPER =
            BLOCKS.register("sculk_whisper", SculkWhisperBlock::new);
}
