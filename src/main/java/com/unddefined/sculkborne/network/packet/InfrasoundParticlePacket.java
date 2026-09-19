package com.unddefined.sculkborne.network.packet;

import com.unddefined.sculkborne.SculkBorne;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.unddefined.sculkborne.client.particles.ParticleMethods.spawnInfrasoundParticles;

public record InfrasoundParticlePacket(Vec3 center, float radius, boolean isStatic) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, "infrasound_particle");
    public static final Type<InfrasoundParticlePacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, InfrasoundParticlePacket> STREAM_CODEC = StreamCodec.ofMember(
            ( msg, buf) -> {
                buf.writeVec3(msg.center);
                buf.writeFloat(msg.radius);
                buf.writeBoolean(msg.isStatic);
            },
             buf -> new InfrasoundParticlePacket(buf.readVec3(), buf.readFloat(), buf.readBoolean())
    );

    @OnlyIn(Dist.CLIENT)
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level != null) spawnInfrasoundParticles(Minecraft.getInstance().level, center, radius, isStatic);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
