package com.unddefined.sculkborne.mixin;

import com.unddefined.sculkborne.entities.Overhealable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 见 {@link Overhealable}：给所有生物补一个绕过血量上限的治疗入口。
 *
 * <p>{@code LivingEntity#setHealth} 会把生命值夹在 0 ~ 血量上限之间，而承载生命值的同步数据 id 是私有的，
 * 所以这里直接持有它，供「回血可以超过上限」使用。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements Overhealable {

    /** 承载生命值的同步数据，原版私有。 */
    @Final
    @Shadow
    private static EntityDataAccessor<Float> DATA_HEALTH_ID;

    @Override
    public void sculkborne$setHealthAboveMax(float health) {
        ((LivingEntity) (Object) this).getEntityData().set(DATA_HEALTH_ID, health);
    }
}
