package com.unddefined.sculkborne.compat.enderechoing;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

/**
 * EnderEchoing 可选兼容的接线入口。
 *
 * <p>本类刻意不引用 EnderEchoing 的任何类型，因此没有安装 EnderEchoing 时也能安全加载；
 * 真正引用它类型的监听类只在 {@code enderechoing} 已加载时才会被触碰。
 */
public final class EnderEchoingCompat {
    private static final String MOD_ID = "enderechoing";

    private EnderEchoingCompat() {}

    /** 在模组构造阶段调用；没有安装 EnderEchoing 时什么也不做。 */
    public static void registerIfPresent() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        NeoForge.EVENT_BUS.register(EnderEchoingCompatEvents.class);
    }
}
