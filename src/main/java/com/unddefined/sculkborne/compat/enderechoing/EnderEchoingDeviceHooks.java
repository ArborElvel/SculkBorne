package com.unddefined.sculkborne.compat.enderechoing;

import com.unddefined.sculkborne.Config;
import com.unddefined.sculkborne.entities.WandererEntity;
import com.unddefined.sculkborne.server.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * EnderEchoing 可选兼容：徘徊者有非常小的几率出现在末影回响仪器上。
 *
 * <p>仪器（谐振器、调谐器、折跃平台、水晶）只要处于加载状态，就由它自己的 tick 每 20 刻
 * （一秒）调用一次 {@link #onDeviceTick(Level, BlockPos)}，在这里按
 * {@link Config#WANDERER_DEVICE_SPAWN_CHANCE} 判定一次；命中时在仪器上方出现一只徘徊者，
 * 并用末影人的传送特效把它送上来。
 *
 * <p>这样出现的个体只停留 {@link WandererEntity#DEVICE_APPEARANCE_TICKS} 刻：
 * 到点还没有攻击目标就瞬移消失（{@code EnderMan#teleport} 加 {@code discard}），
 * 有目标则留下来照常行动，见 {@link WandererEntity#markDeviceAppearance()}。
 * 和平难度下不会出现。
 *
 * <p>EnderEchoing 是可选 mod，不能有编译期依赖，所以由它那边的
 * {@code compat/sculkborne/SculkBorneBridge#deviceTick} 反射调用这里的
 * {@link #onDeviceTick(Level, BlockPos)}：本类的类名与方法签名是对外契约，改名要两边一起改。
 */
public final class EnderEchoingDeviceHooks {

    /** 出现时扬起的末影粒子数量。 */
    private static final int APPEARANCE_PARTICLES = 32;

    /**
     * 一次每秒判定：按概率在仪器上方生成一只只会短暂停留的徘徊者。
     *
     * <p>只有在仪器上方放得下（{@value WandererEntity#DEVICE_APPEARANCE_TICKS} 刻的停留与体型无关，
     * 这里按徘徊者 0.6×2.9 的碰撞箱检查三格高的空位）时才会出现，放不下就当作这次没命中。
     *
     * @param level 仪器所在维度
     * @param pos   仪器所在方块位置
     */
    public static void onDeviceTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 和平难度下不生成任何怪物
        if (serverLevel.getDifficulty() == Difficulty.PEACEFUL) return;

        if (serverLevel.getRandom().nextFloat() >= Config.WANDERER_DEVICE_SPAWN_CHANCE.get()) return;

        BlockPos spawnPos = pos.above();
        AABB spawnBox = EntityRegistry.WANDERER_ENTITY.get()
                .getSpawnAABB(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        if (!serverLevel.noCollision(spawnBox)) return;

        if (!(EntityRegistry.WANDERER_ENTITY.get().spawn(serverLevel, spawnPos, MobSpawnType.EVENT)
                instanceof WandererEntity wanderer)) return;

        wanderer.markDeviceAppearance();

        // 出现时自己放一次末影人传送特效；消失时由 EnderMan#teleport 自己放
        serverLevel.playSound(null, spawnPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
        serverLevel.sendParticles(ParticleTypes.PORTAL, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0,
                spawnPos.getZ() + 0.5, APPEARANCE_PARTICLES, 0.5, 1.0, 0.5, 0.2);
    }

    private EnderEchoingDeviceHooks() {}
}
