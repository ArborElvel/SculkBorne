package com.unddefined.sculkborne.compat.enderechoing;

import com.unddefined.enderechoing.api.event.EnderEchoDeviceEvent;
import com.unddefined.enderechoing.api.event.EnderEchoTeleportEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * EnderEchoing 事件监听：取代原来的双向反射契约。
 *
 * <p>只在 {@code enderechoing} 已加载时由
 * {@link EnderEchoingCompat#registerIfPresent()} 注册，所以本类可以安全引用它的类型。
 */
public final class EnderEchoingCompatEvents {
    private EnderEchoingCompatEvents() {}

    /** 传送成功后，起点与终点各判定一次幽匿螨生成。 */
    @SubscribeEvent
    public static void onTeleport(EnderEchoTeleportEvent.Post event) {
        EnderEchoingTeleportHooks.onTeleport(event.getPlayer(), event.getFromLevel(), event.getFromPos());
    }

    /** 仪器每秒一次的判定：徘徊者有没有极小概率出现。 */
    @SubscribeEvent
    public static void onDeviceTick(EnderEchoDeviceEvent.Tick event) {
        EnderEchoingDeviceHooks.onDeviceTick(event.getLevel(), event.getBlockPos());
    }
}
