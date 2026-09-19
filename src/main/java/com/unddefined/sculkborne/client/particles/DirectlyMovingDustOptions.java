package com.unddefined.sculkborne.client.particles;

import com.mojang.serialization.MapCodec;
import com.unddefined.sculkborne.server.registry.ParticlesRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

public record DirectlyMovingDustOptions(int lifetime, float red, float green, float blue,
                                        float size) implements ParticleOptions {
    public static final MapCodec<DirectlyMovingDustOptions> CODEC = MapCodec.unit(new DirectlyMovingDustOptions(0, 0f, 0f, 0f, 0f));

    public static final StreamCodec<ByteBuf, DirectlyMovingDustOptions> STREAM_CODEC = StreamCodec.of(
            (buf, options) -> {
                buf.writeInt(options.lifetime);
                buf.writeFloat(options.red);
                buf.writeFloat(options.green);
                buf.writeFloat(options.blue);
                buf.writeFloat(options.size);
            },
            buf -> new DirectlyMovingDustOptions(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())
    );

    @Override
    public @NotNull ParticleType<?> getType() {
        return ParticlesRegistry.DIRECT_MOVING_DUST.get();
    }

    // 添加getter方法
}