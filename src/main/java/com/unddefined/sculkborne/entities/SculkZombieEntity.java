package com.unddefined.sculkborne.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

public class SculkZombieEntity extends Zombie implements GeoEntity, SculkMob, VibrationSystem {
    private static final ThreadLocal<Boolean> RELAYING_VIBRATION = ThreadLocal.withInitial(() -> false);
    private static final EntityDataAccessor<Boolean> VIBRATION_ACTIVE =
            SynchedEntityData.defineId(SculkZombieEntity.class, EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser = new SculkZombieVibrationUser();
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicVibrationListener =
            new DynamicGameEventListener<>(new VibrationSystem.Listener(this));
    /** 幽匿系方块上的回血剩余计时（tick），见 {@link SculkMob#tickSculkRegeneration(int)}。 */
    private int sculkHealCooldown;
    /** 振动激活与冷却剩余时间：30 tick 激活期 + 10 tick 冷却期。 */
    private int vibrationCooldown;

    public SculkZombieEntity(EntityType<SculkZombieEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, 20.0F)
                .add(Attributes.MOVEMENT_SPEED, 0.23F)
                .add(Attributes.ATTACK_DAMAGE, 3.0F)
                .add(Attributes.ARMOR, 2.0F)
                .add(Attributes.KNOCKBACK_RESISTANCE, -4.0F)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 与同类保持至少约 6 格距离；攻击目标与基础移动 Goal 仍由 Zombie 保留。
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, SculkZombieEntity.class, 6.0F, 1.0D, 1.2D));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        applySunlightDebuffs();
        sculkHealCooldown = tickSculkRegeneration(sculkHealCooldown);
        tickSculkBlockBonus();
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel S) {
            if (vibrationCooldown > 0) {
                vibrationCooldown--;
                if (vibrationCooldown == 10) {
                    entityData.set(VIBRATION_ACTIVE, false);
                    playSound(SoundEvents.SCULK_CLICKING_STOP, 1.0F, random.nextFloat() * 0.2F + 0.8F);
                }
            }
            VibrationSystem.Ticker.tick(S, vibrationData, vibrationUser);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VIBRATION_ACTIVE, false);
    }

    public boolean isVibrationActive() {
        return entityData.get(VIBRATION_ACTIVE);
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> listenerConsumer) {
        if (level() instanceof ServerLevel S) listenerConsumer.accept(dynamicVibrationListener, S);
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return vibrationUser;
    }

    @Override
    public boolean dampensVibrations() {
        return false;
    }

    private class SculkZombieVibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource = new net.minecraft.world.level.gameevent.EntityPositionSource(
                SculkZombieEntity.this, SculkZombieEntity.this.getEyeHeight());

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return positionSource;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent,
                                           GameEvent.Context context) {
            return !isNoAi() && isAlive() && vibrationCooldown <= 0;
        }

        @Override
        public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> gameEvent,
                                       @Nullable Entity entity, @Nullable Entity playerEntity, float distance) {
            getNavigation().stop();
            setTarget(null);
            vibrationCooldown = 60;
            entityData.set(VIBRATION_ACTIVE, true);
            playSound(SoundEvents.SCULK_CLICKING, 1.0F, random.nextFloat() * 0.2F + 0.8F);
            int frequency = VibrationSystem.getGameEventFrequency(gameEvent);
            tryResonateVibration(level, frequency);

            // 保留原始来源，使 Warden 能定位最初产生振动的玩家；同步保护避免同一次事件递归转发。
            if (!RELAYING_VIBRATION.get()) {
                RELAYING_VIBRATION.set(true);
                try {
                    level.gameEvent(gameEvent, position(), GameEvent.Context.of(entity));
                } finally {
                    RELAYING_VIBRATION.set(false);
                }
            }
        }

        /** 将接收到的振动频率传递给周围的振动共鸣方块。 */
        private void tryResonateVibration(ServerLevel level, int frequency) {
            if (frequency < 1 || frequency > VibrationSystem.RESONANCE_EVENTS.size()) return;

            for (Direction direction : Direction.values()) {
                BlockPos resonatorPos = blockPosition().relative(direction);
                if (level.getBlockState(resonatorPos).is(BlockTags.VIBRATION_RESONATORS)) {
                    level.gameEvent(VibrationSystem.getResonanceEventByFrequency(frequency), resonatorPos,
                            GameEvent.Context.of(SculkZombieEntity.this, level.getBlockState(resonatorPos)));
                }
            }
        }
    }

    /** 被阳光直射时只获得虚弱与缓慢，不像普通僵尸那样燃烧。 */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 姿势完全由客户端的 SculkZombieCemAnimator 计算（CEM 公式本身包含待机/行走/攻击/受伤），
        // 因此不注册关键帧动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
