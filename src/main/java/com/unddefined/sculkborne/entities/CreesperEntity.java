package com.unddefined.sculkborne.entities;

import com.unddefined.sculkborne.server.InfrasoundDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import static com.unddefined.sculkborne.Config.*;

public class CreesperEntity extends Creeper implements GeoEntity, SculkMob {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int sculkHealCooldown;

    public CreesperEntity(EntityType<CreesperEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Creeper.createAttributes();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        applySunlightDebuffs();
        sculkHealCooldown = tickSculkRegeneration(sculkHealCooldown);
        tickSculkBlockBonus();
    }

    /**
     * 降低脚步声的音量。
     *
     * <p>原版 {@link net.minecraft.world.entity.Entity#playStepSound} 用 {@code 方块音量 × 0.15}
     * 播放所在方块的脚步声，这里保持音源与音调不变，只把音量乘上
     * {@link com.unddefined.sculkborne.Config#CREESPER_STEP_SOUND_VOLUME}；
     * 组合脚步声（例如地毯叠在方块上）走的是另一条路径，一并压低以保持一致。
     */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundType soundType = state.getSoundType(this.level(), pos, this);
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F * stepSoundVolume(), soundType.getPitch());
    }

    @Override
    protected void playCombinationStepSounds(BlockState primaryState, BlockState secondaryState, BlockPos primaryPos, BlockPos secondaryPos) {
        SoundType soundType = primaryState.getSoundType(this.level(), primaryPos, this);
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F * stepSoundVolume(), soundType.getPitch());
        this.playMuffledStepSound(secondaryState, secondaryPos);
    }

    /** 脚步声的音量倍率，取自配置 {@link com.unddefined.sculkborne.Config#CREESPER_STEP_SOUND_VOLUME}。 */
    private float stepSoundVolume() {
        return (float) CREESPER_STEP_SOUND_VOLUME.getAsDouble();
    }

    /**
     * 自爆时发出的次声波。
     *
     * <p>次声波苦力怕的自爆不做物理爆炸（由 {@code ServerEvents} 取消 ExplosionEvent.Start，
     * 原版爆炸的方块破坏、伤害、音效与粒子都不会发生），改为在自身位置结算一次次声波爆发：
     * 范围内生物受到真实伤害与次声波减益，范围与伤害取 {@link com.unddefined.sculkborne.Config} 中的配置值。
     *
     * <p>爆源自身不参与结算，与原版爆炸不伤害爆源生物一致。
     *
     * @param level 自爆所在的服务端维度
     */
    public void infrasoundExplode(ServerLevel level) {
        InfrasoundDamage.InfrasoundBurst(level, this.position(),
                CREESPER_INFRASOUND_HURT_RANGE.getAsInt(),
                CREESPER_INFRASOUND_AFFECT_RANGE.getAsInt(),
                CREESPER_INFRASOUND_HURT_DAMAGE.getAsInt(),
                this, this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 1,
                state -> state.isMoving()
                        ? state.setAndContinue(RawAnimation.begin().thenLoop("walk"))
                        : state.setAndContinue(RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
