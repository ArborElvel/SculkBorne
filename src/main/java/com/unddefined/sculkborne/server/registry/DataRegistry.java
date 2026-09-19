package com.unddefined.sculkborne.server.registry;

import com.mojang.serialization.Codec;
import com.unddefined.sculkborne.server.SculkIntrusionSpreader;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class DataRegistry {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "sculkborne");

    public static final Supplier<AttachmentType<SculkIntrusionSpreader>> SCULK_SPREADER = ATTACHMENT_TYPES.register(
            "sculk_intrusion_spreader", () -> AttachmentType.builder(SculkIntrusionSpreader::new).build()
    );
    public static final Supplier<AttachmentType<Long>> SCULK_VEIL_START = ATTACHMENT_TYPES.register(
            "sculk_veil_start", () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG).build()
    );
    public static final Supplier<AttachmentType<Long>> SCULK_VEIL_LAST_TICK = ATTACHMENT_TYPES.register(
            "sculk_veil_last_tick", () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG).build()
    );
    public static final Supplier<AttachmentType<Long>> SCULK_VEIL_TOTAL = ATTACHMENT_TYPES.register(
            "sculk_veil_total", () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build()
    );
    public static final Supplier<AttachmentType<Long>> GLOWING_START = ATTACHMENT_TYPES.register(
            "glowing_start", () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG).build()
    );
    public static final Supplier<AttachmentType<Long>> GLOWING_LAST_TICK = ATTACHMENT_TYPES.register(
            "glowing_last_tick", () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG).build()
    );
    public static final Supplier<AttachmentType<Long>> GLOWING_TOTAL = ATTACHMENT_TYPES.register(
            "glowing_total", () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build()
    );
}
