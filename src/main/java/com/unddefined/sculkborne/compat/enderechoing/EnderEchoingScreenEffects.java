package com.unddefined.sculkborne.compat.enderechoing;

import com.unddefined.enderechoing.client.api.render.EnderEchoClientRender;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * EnderEchoing 可选兼容：回响波交给影匿后处理统一驱动（仅客户端）。
 *
 * <p>两个 mod 都会在 AFTER_LEVEL 阶段往主渲染目标上画东西，谁在上面取决于监听器注册顺序
 * （随 mod 加载顺序变化）。这里在幽匿雾画完之后通过 EnderEchoing 的客户端 API 触发回响波渲染，
 * 让波固定叠在雾之上；EnderEchoing 未安装时什么也不做。
 */
public final class EnderEchoingScreenEffects {
    private static final String MOD_ID = "enderechoing";

    private EnderEchoingScreenEffects() {}

    /** 在幽匿雾之后绘制回响波；EnderEchoing 未安装时什么也不做。 */
    public static void renderEchoWaveAfterVeil(RenderLevelStageEvent event) {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        EnderEchoClientRender.renderEchoWaveAfterVeil(event);
    }
}
