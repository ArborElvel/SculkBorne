package com.unddefined.sculkborne;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SCULK_VEIL_DARKNESS_DURATION = BUILDER
            .comment("Duration of the Darkness effect applied by Sculk Veil, in seconds.")
            .defineInRange("sculk_veil_darkness_duration", 20, 20, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SCULK_VEIL_GLOWING_DURATION = BUILDER
            .comment("Duration of the Glowing effect applied by Sculk Veil, in seconds.")
            .defineInRange("sculk_veil_glowing_duration", 25, 20, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue ECHO_DRUSE_MAX_GROWTH_VALUE = BUILDER
            .comment("Maximum growth value of an Echo Druse.")
            .defineInRange("echo_druse_max_growth_value", 40000, 4, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue ECHO_DRUSE_GENERATION_PROBABILITY = BUILDER
            .comment("Chance for an Echo Druse to generate on top of a Sculk Catalyst, from 0.0 to 1.0.")
            .defineInRange("echo_druse_generation_probability", 0.3, 0, Double.MAX_VALUE);

    public static final ModConfigSpec.IntValue SCULK_WHISPER_COOLDOWN = BUILDER
            .comment("Cooldown of the Sculk Whisper's infrasound burst, in seconds.")
            .defineInRange("sculk_whisper_cooldown", 45, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SCULK_WHISPER_HURT_RANGE = BUILDER
            .comment("Range of the Sculk Whisper's damaging infrasound burst, in blocks.")
            .defineInRange("sculk_whisper_hurt_range", 8, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SCULK_WHISPER_AFFECT_RANGE = BUILDER
            .comment("Range of the Sculk Whisper's status-effect infrasound burst, in blocks.")
            .defineInRange("sculk_whisper_affect_range", 30, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SCULK_WHISPER_HURT_DAMAGE = BUILDER
            .comment("Damage dealt by the Sculk Whisper's damaging infrasound burst.")
            .defineInRange("sculk_whisper_hurt_damage", 15, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CREESPER_INFRASOUND_HURT_RANGE = BUILDER
            .comment("Range of the Creesper's damaging infrasound burst, in blocks.")
            .defineInRange("creesper_infrasound_hurt_range", 5, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CREESPER_INFRASOUND_AFFECT_RANGE = BUILDER
            .comment("Range of the Creesper's status-effect infrasound burst, in blocks.")
            .defineInRange("creesper_infrasound_affect_range", 15, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CREESPER_INFRASOUND_HURT_DAMAGE = BUILDER
            .comment("Damage dealt by the Creesper's damaging infrasound burst.")
            .defineInRange("creesper_infrasound_hurt_damage", 10, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue CREESPER_STEP_SOUND_VOLUME = BUILDER
            .comment("Volume multiplier for the Creesper's footstep sounds (1.0 = vanilla volume).")
            .defineInRange("creesper_step_sound_volume", 0.1D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue SCULK_SHRIEKER_CAN_SUMMON_CHANCE = BUILDER
            .comment("1 in N chance for a Sculk Shrieker to gain CAN_SUMMON when a nearby entity dies on a Sculk Catalyst.")
            .defineInRange("sculk_shrieker_can_summon_chance", 7, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue SCULK_MITE_TELEPORT_SPAWN_CHANCE = BUILDER
            .comment("Chance for a Sculk Mite to appear at each end (departure and destination) of an Ender Echoing"
                    + " teleport, from 0.0 to 1.0.")
            .defineInRange("sculk_mite_teleport_spawn_chance", 0.05D, 0.0D, 1.0D);

    static final ModConfigSpec SPEC = BUILDER.build();
}
