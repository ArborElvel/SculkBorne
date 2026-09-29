package com.unddefined.sculkborne.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取经验球实体的 {@code count}：原版里一个球实体装着 {@code count} 份面值 {@code value} 的经验，
 * 但 {@code ExperienceOrb} 没有对应的 getter。
 */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccess {

    @Accessor("count")
    int sculkborne$getCount();
}
