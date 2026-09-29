package com.unddefined.sculkborne.client.model.cem;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 实体类型到 CEM 动画器的注册表，由 CEM 附属 mod 在客户端初始化时填充。
 *
 * <p>查询按实体类型进行，实例按模型缓存：{@link GeoModel} 是共享的，所以每个模型只建一次动画器，
 * 里面按实体保存的跨帧状态由动画器自己负责隔离。没有注册动画器的生物返回 {@code false}，
 * 模型据此回退到 GeckoLib 的关键帧动画。
 *
 * <p>只在客户端渲染线程使用。
 */
public final class CemAnimatorRegistry {

    private static final Map<EntityType<?>, Function<GeoModel<?>, CemAnimatorHandle<?>>> FACTORIES = new ConcurrentHashMap<>();
    private static final Map<GeoModel<?>, CemAnimatorHandle<?>> HANDLES = new WeakHashMap<>();

    private CemAnimatorRegistry() {
    }

    /**
     * 注册某个生物类型的动画器工厂。工厂拿到的模型就是该生物模型实例，供动画器查骨骼用。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends LivingEntity> void register(EntityType<T> entityType,
                                                         Function<GeoModel<?>, CemAnimatorHandle<T>> factory) {
        FACTORIES.put(entityType, (Function) factory);
    }

    /**
     * 尝试让注册的动画器接管本帧姿势。
     *
     * @return {@code true} 表示已经由 CEM 动画器写完整帧姿势（调用方不要再走关键帧动画）；
     *         {@code false} 表示该生物没有注册动画器，调用方应回退到 {@code super.setCustomAnimations}
     */
    @SuppressWarnings("unchecked")
    public static <T extends LivingEntity> boolean apply(GeoModel<?> model, T entity, AnimationState<?> animationState) {
        CemAnimatorHandle<?> handle = HANDLES.get(model);

        if (handle == null) {
            Function<GeoModel<?>, CemAnimatorHandle<?>> factory = FACTORIES.get(entity.getType());

            if (factory == null) return false;

            handle = factory.apply(model);
            HANDLES.put(model, handle);
        }

        ((CemAnimatorHandle<T>) handle).apply(entity, animationState);

        return true;
    }
}
