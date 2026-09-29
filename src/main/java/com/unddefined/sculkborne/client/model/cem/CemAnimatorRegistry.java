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
 * 实体类型到“整帧姿势动画器”的注册表，分两级：
 *
 * <ol>
 *   <li><b>附属实现</b>（{@link #register}）：CEM 附属 mod 提供，优先级最高，装了附属就由它接管。</li>
 *   <li><b>内置实现</b>（{@link #registerBuiltin}）：sculkborne 自带，把原版对应生物的动作
 *       （{@code net.minecraft.client.model} 里那些 mob 模型的 {@code setupAnim}）移植过来，
 *       没有附属时用它兜底。</li>
 * </ol>
 *
 * <p>查询按实体类型进行，实例按模型缓存：{@link GeoModel} 是共享的，所以每个模型只建一次动画器，
 * 里面按实体保存的跨帧状态由动画器自己负责隔离。两级都没有注册的生物返回 {@code false}，
 * 模型据此回退到 GeckoLib 的关键帧动画。
 *
 * <p>只在客户端渲染线程使用。
 */
public final class CemAnimatorRegistry {

    private static final Map<EntityType<?>, Function<GeoModel<?>, CemAnimatorHandle<?>>> OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, Function<GeoModel<?>, CemAnimatorHandle<?>>> BUILTINS = new ConcurrentHashMap<>();
    private static final Map<GeoModel<?>, CemAnimatorHandle<?>> HANDLES = new WeakHashMap<>();

    private CemAnimatorRegistry() {
    }

    /**
     * 注册某个生物类型的附属动画器工厂，覆盖内置实现。工厂拿到的模型就是该生物模型实例，供动画器查骨骼用。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends LivingEntity> void register(EntityType<T> entityType,
                                                         Function<GeoModel<?>, CemAnimatorHandle<T>> factory) {
        OVERRIDES.put(entityType, (Function) factory);
        HANDLES.clear();
    }

    /**
     * 注册某个生物类型的内置动画器工厂，只在没有附属实现时生效。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends LivingEntity> void registerBuiltin(EntityType<T> entityType,
                                                                Function<GeoModel<?>, CemAnimatorHandle<T>> factory) {
        BUILTINS.put(entityType, (Function) factory);
        HANDLES.clear();
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
            EntityType<?> type = entity.getType();
            Function<GeoModel<?>, CemAnimatorHandle<?>> factory = OVERRIDES.get(type);

            if (factory == null) factory = BUILTINS.get(type);

            if (factory == null) return false;

            handle = factory.apply(model);
            HANDLES.put(model, handle);
        }

        ((CemAnimatorHandle<T>) handle).apply(entity, animationState);

        return true;
    }
}
