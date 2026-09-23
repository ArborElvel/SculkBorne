package com.unddefined.sculkborne.client.renderer.entity;

import com.unddefined.sculkborne.client.model.entity.SculkShadeEntityModel;
import com.unddefined.sculkborne.entities.SculkShadeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * 幽影的渲染器，负责「按光照等级隐身／显形」这条外观规则。
 *
 * <p>光照判断在服务端做（服务端每刻都刷新天空光衰减，判出来的亮度才准），结果按每刻一档的速度同步成
 * {@link SculkShadeEntity#getVisibilityAlpha() 当前不透明度}，这里只负责按它画。判断依据是方块光与白天
 * 阳光（夜里没有阳光时算暗）：亮度不超过 8 完全隐身、不低于 12 完全显形，中间线性过渡，进入完全隐身
 * 还会先延迟一段 {@link SculkShadeEntity} 里定好的时长再淡出，淡入淡出也各占一段固定的时长
 * （阈值、延迟与时长都是那边的常量）：
 *
 * <ul>
 *     <li>不透明度为 0（完全隐身）时本体不进渲染管线，名牌也不显示；</li>
 *     <li>不透明度为满时完全不透明；</li>
 *     <li>中间值按同步过来的不透明度画，于是举着火把靠近、日出日落、灯被移走时都是渐渐显隐。</li>
 * </ul>
 *
 * <p>完全隐身时依然会走一遍渲染流程（动画等照常），只是不产生本体的几何；如果此时它正在发光
 * （Glowing 效果、声波发光），按原版隐身与幽匿蠹虫的做法改用 {@link RenderType#outline} 只画剪影——
 * 本体仍然看不见，但声波、发光效果能把它照出来。
 *
 * <p>透明度走 GeckoLib 的 {@link #getRenderColor}（它会被写进顶点色），而不是
 * {@code RenderSystem.setShaderColor}：实体渲染是按渲染类型批量提交的，全局 shader 颜色会把同一批次里
 * 其它实体一起改掉。
 *
 * <p>这条规则只影响外观：索敌、碰撞箱、掉落与幽匿生物的共用规则都不受影响。
 */
public class SculkShadeEntityRenderer extends GeoEntityRenderer<SculkShadeEntity> {

    public SculkShadeEntityRenderer(EntityRendererProvider.Context c) {
        super(c, new SculkShadeEntityModel<>());
    }

    /** 服务端同步过来的当前不透明度，换算成 0~1，见 {@link SculkShadeEntity#getVisibilityAlpha()}。 */
    private static float visibilityAlpha(SculkShadeEntity shade) {
        return shade.getVisibilityAlpha() / 255.0F;
    }

    /**
     * 完全隐身时不画本体：返回 {@code null} 让模型跳过渲染，连深度都不写，避免 0 透明度挡住身后的东西；
     * 但正在发光时改用 outline 通道，只留下剪影。其余情况交给 GeckoLib 自己处理——它已经实现了原版隐身
     * 的 15% 幽灵渲染与隐身发光时的 outline。
     */
    @Nullable
    @Override
    public RenderType getRenderType(SculkShadeEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        if (visibilityAlpha(animatable) <= 0.0F) {
            return Minecraft.getInstance().shouldEntityAppearGlowing(animatable) ? RenderType.outline(texture) : null;
        }

        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    /**
     * 把同步过来的不透明度叠到 GeckoLib 给出的渲染颜色上。
     *
     * <p>两头都直接交给 {@code super}：完全不透明那一档没什么可改；完全隐身那一档的不透明度由
     * {@link #getRenderType} 决定（要么不画、要么只画剪影），这里保持原来的颜色，剪影才不会被压成 0。
     */
    @Override
    public Color getRenderColor(SculkShadeEntity animatable, float partialTick, int packedLight) {
        Color base = super.getRenderColor(animatable, partialTick, packedLight);
        float alpha = visibilityAlpha(animatable);
        if (alpha >= 1.0F || alpha <= 0.0F) return base;

        return Color.ofARGB(Mth.ceil(base.getAlpha() * alpha), base.getRed(), base.getGreen(), base.getBlue());
    }

    /** 完全隐身时连名牌一起隐藏，与幽匿蠹虫藏在幽匿块里时的处理一致。 */
    @Override
    public boolean shouldShowName(SculkShadeEntity animatable) {
        return visibilityAlpha(animatable) > 0.0F && super.shouldShowName(animatable);
    }
}
