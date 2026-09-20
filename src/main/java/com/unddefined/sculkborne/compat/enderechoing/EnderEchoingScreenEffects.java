package com.unddefined.sculkborne.compat.enderechoing;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * EnderEchoing 可选兼容：回响波交给影匿后处理统一驱动（仅客户端）。
 *
 * <p>两个 mod 都会在 AFTER_LEVEL 阶段往主渲染目标上画东西，谁在上面取决于监听器注册顺序
 * （随 mod 加载顺序变化）。这里在幽匿雾画完之后主动回调 EnderEchoing 的回响波渲染，
 * 让波固定叠在雾之上。EnderEchoing 是可选 mod，不能有编译期依赖，
 * 因此按 {@code compat/iris/IrisCompat} 的做法用反射调用，缺 mod 或方法时静默跳过。</p>
 */
public final class EnderEchoingScreenEffects {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "enderechoing";
    private static final String RENDERER_CLASS = "com.unddefined.enderechoing.client.renderer.EchoRenderer";
    private static final String RENDER_METHOD = "renderEchoAfterSculkVeil";

    private static Method renderEcho;
    private static boolean unavailable = false;

    /** 在幽匿雾之后绘制回响波；EnderEchoing 未安装或版本不匹配时什么也不做。 */
    public static void renderEchoWaveAfterVeil(RenderLevelStageEvent event) {
        if (unavailable || !ModList.get().isLoaded(MOD_ID)) return;
        Method method = renderEcho;
        if (method == null) {
            method = resolve();
            if (method == null) return;
        }
        try {
            method.invoke(null, event);
        } catch (ReflectiveOperationException e) {
            unavailable = true;
            LOGGER.error("Failed to render the EnderEchoing echo wave after the sculk veil", e);
        }
    }

    private static Method resolve() {
        try {
            renderEcho = Class.forName(RENDERER_CLASS).getMethod(RENDER_METHOD, RenderLevelStageEvent.class);
            return renderEcho;
        } catch (ReflectiveOperationException | LinkageError e) {
            unavailable = true;
            return null;
        }
    }

    private EnderEchoingScreenEffects() {}
}
