package com.unddefined.sculkborne.client.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class DirectlyMovingDustParticleType extends ParticleType<DirectlyMovingDustOptions> {
    public DirectlyMovingDustParticleType(boolean overrideLimiter) {
        super(overrideLimiter);
    }
    
    @Override
    public MapCodec<DirectlyMovingDustOptions> codec() {
        return DirectlyMovingDustOptions.CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, DirectlyMovingDustOptions> streamCodec() {
        return DirectlyMovingDustOptions.STREAM_CODEC;
    }
}