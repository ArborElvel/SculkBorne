package com.unddefined.sculkborne.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

public class ParticleDirectlyMovingDust extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final double xStart;
    private final double yStart;
    private final double zStart;
    private final double xEnd;
    private final double yEnd;
    private final double zEnd;

    protected ParticleDirectlyMovingDust(DirectlyMovingDustOptions options, ClientLevel level, double from_x, double from_y, double from_z,
                                         double to_x, double to_y, double to_z, SpriteSet sprites) {
        super(level, from_x, from_y, from_z);
        this.xStart = from_x;
        this.yStart = from_y;
        this.zStart = from_z;
        this.xEnd = to_x;
        this.yEnd = to_y;
        this.zEnd = to_z;
        this.sprites = sprites;

        this.gravity = 0;
        this.quadSize = options.size();
        this.hasPhysics = false;
        this.setLifetime(options.lifetime());
        this.setColor(options.red(), options.green(), options.blue());
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
        } else if(this.quadSize >= 1F){
            // 计算移动进度
            float progress = (float)this.age / (float)this.lifetime * 10;
            
            // 根据进度计算当前位置 (从起点移动到终点)
            this.x = this.xStart + (this.xEnd - this.xStart) * progress;
            this.y = this.yStart + (this.yEnd - this.yStart) * progress;
            this.z = this.zStart + (this.zEnd - this.zStart) * progress;
            
            // 更新精灵帧
            this.setSpriteFromAge(this.sprites);

            // 随着粒子变老，逐渐减小透明度 (从不透明到透明)
            this.alpha = (float) (this.lifetime - this.age) /200;
        }else {
            // 更新精灵帧
            this.setSpriteFromAge(this.sprites);

            // 随着粒子变老，逐渐减小透明度 (从不透明到透明)
            this.alpha = (float) (this.lifetime - this.age);
        }
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
        public record Provider(SpriteSet sprites) implements ParticleProvider<DirectlyMovingDustOptions> {

        @Override
            public Particle createParticle(
                    DirectlyMovingDustOptions options, ClientLevel level,
                    double from_x, double from_y, double from_z, double to_x, double to_y, double to_z) {
                return new ParticleDirectlyMovingDust(options, level, from_x, from_y, from_z, to_x, to_y, to_z, this.sprites);
            }
        }
}