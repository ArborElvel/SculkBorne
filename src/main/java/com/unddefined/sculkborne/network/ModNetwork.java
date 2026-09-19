package com.unddefined.sculkborne.network;

import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.network.packet.InfrasoundParticlePacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Supplier;

@EventBusSubscriber(modid = SculkBorne.MODID)
public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1.0";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registerClientPayload(registrar,
                InfrasoundParticlePacket.TYPE,
                InfrasoundParticlePacket.STREAM_CODEC,
                () -> InfrasoundParticlePacket::handle
        );
    }

    private static <T extends CustomPacketPayload> void registerClientPayload(
            PayloadRegistrar registrar, CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Supplier<IPayloadHandler<T>> clientHandler) {
        if (FMLEnvironment.dist.isClient()) registrar.playToClient(type, codec, clientHandler.get());
        else registrar.playToClient(type, codec, (payload, context) -> {});
    }
}
