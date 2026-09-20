package com.unddefined.sculkborne.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.client.renderer.SculkIntrusionRenderer;
import com.unddefined.sculkborne.client.renderer.SculkVeilRenderer;
import com.unddefined.sculkborne.compat.enderechoing.EnderEchoingScreenEffects;
import com.unddefined.sculkborne.server.events.ShadowNight;
import com.unddefined.sculkborne.server.registry.MobEffectRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 影匿（Sculk Veil）与幽匿侵扰（Sculk Intrusion）的全屏后处理驱动。
 *
 * <p>{@link SculkVeilRenderer} 与 {@link SculkIntrusionRenderer} 只负责把 PostChain 画出来，
 * 何时淡入淡出由这里按效果状态驱动：玩家身上的影匿雾跟随 {@link MobEffectRegistry#SCULK_VEIL}，
 * 幽匿侵扰的蔓延纹理跟随 {@link MobEffectRegistry#SCULK_INTRUSION}，
 * 深暗之域/幽影之夜的影匿雾跟随所在生物群系与 {@link ShadowNight}。</p>
 */
@EventBusSubscriber(modid = SculkBorne.MODID, value = Dist.CLIENT)
public final class SculkScreenEffectEvents {
    private static final Minecraft mc = Minecraft.getInstance();
    /**
     * 影匿雾的 GameTime（游戏刻）：着色器在 GameTime 为负时直接输出原画面，
     * 用一段负值区间让淡入开始时先有一段延迟，再真正显形。
     */
    private static final int VEIL_TICKS_START = -21;
    /**
     * 影匿雾的 GameTime 每游戏刻推进的刻数：2 表示雾的流动、起显延迟都比原始快一倍。
     */
    private static final int VEIL_GAMETIME_STEP = 2;
    /**
     * 淡入淡出速度倍率：{@code updateFadeProgress} 的 delta 以游戏刻为单位，乘 2 即淡入淡出快一倍。
     */
    private static final float FADE_SPEED_MULTIPLIER = 2f;

    private static int veilTicks = VEIL_TICKS_START;
    private static int deepDarkTicks = VEIL_TICKS_START;
    private static long lastTickGameTime = -1;

    @SubscribeEvent
    public static void renderSculkScreenEffects(RenderLevelStageEvent event) {
        if (mc.level == null || mc.player == null) return;
        // 事件会按渲染阶段逐个触发，只在 AFTER_LEVEL 推进一帧的淡入淡出，避免同一帧叠加多次。
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        float partialTicks = event.getPartialTick().getGameTimeDeltaTicks();
        float fadeDelta = partialTicks * FADE_SPEED_MULTIPLIER;
        boolean hasVeil = mc.player.hasEffect(MobEffectRegistry.SCULK_VEIL);
        boolean inDeepDark = mc.level.getBiome(mc.player.blockPosition()).is(Biomes.DEEP_DARK)
                || ShadowNight.isActive(mc.level);
        boolean hasIntrusion = mc.player.hasEffect(MobEffectRegistry.SCULK_INTRUSION);

        // GameTime 以游戏刻为单位，每刻只推进一次；淡入淡出本身仍按帧推进，不受此影响。
        long gameTime = mc.level.getGameTime();
        if (gameTime != lastTickGameTime) {
            lastTickGameTime = gameTime;
            advanceTicks(VEIL_GAMETIME_STEP);
        }
        SculkVeilRenderer.BUFF.updateFadeProgress(hasVeil && !inDeepDark, fadeDelta);
        SculkVeilRenderer.DEEP_DARK.DARKNESS_STRENGTH = hasVeil ? 1f : 0f;
        SculkVeilRenderer.DEEP_DARK.fogDensity = hasVeil ? 0.15f : 0.06f;
        SculkVeilRenderer.DEEP_DARK.updateFadeProgress(inDeepDark, fadeDelta);
        SculkIntrusionRenderer.INSTANCE.updateFadeProgress(hasIntrusion, fadeDelta);

        if (SculkVeilRenderer.BUFF.fadeProgress != 0f
                || SculkVeilRenderer.DEEP_DARK.fadeProgress != 0f
                || SculkIntrusionRenderer.INSTANCE.fadeProgress != 0f)
            renderScreenEffectChains(event, partialTicks);

        // 幽匿雾画完之后再让 EnderEchoing 的回响波叠上去，两个 mod 同时加载时波不会被雾盖住。
        EnderEchoingScreenEffects.renderEchoWaveAfterVeil(event);
    }

    private static void renderScreenEffectChains(RenderLevelStageEvent event, float partialTicks) {
        Matrix4f modelView = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(modelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.disableDepthTest();
            // 先铺幽匿侵扰的蔓延纹理，再叠影匿雾：影匿的遮蔽理应盖在最上层。
            if (SculkIntrusionRenderer.INSTANCE.fadeProgress != 0f)
                SculkIntrusionRenderer.INSTANCE.render(partialTicks);
            if (SculkVeilRenderer.BUFF.fadeProgress != 0f)
                SculkVeilRenderer.BUFF.render(veilTicks, partialTicks, modelView, projection);
            if (SculkVeilRenderer.DEEP_DARK.fadeProgress != 0f)
                SculkVeilRenderer.DEEP_DARK.render(deepDarkTicks, partialTicks, modelView, projection);
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.restoreProjectionMatrix();
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** 影匿雾的 GameTime 随游戏刻前进，保证淡入节奏与帧率、多人环境无关。 */
    private static void advanceTicks(int step) {
        veilTicks = SculkVeilRenderer.BUFF.fadeProgress != 0f ? veilTicks + step : VEIL_TICKS_START;
        deepDarkTicks = SculkVeilRenderer.DEEP_DARK.fadeProgress != 0f ? deepDarkTicks + step : VEIL_TICKS_START;
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        veilTicks = VEIL_TICKS_START;
        deepDarkTicks = VEIL_TICKS_START;
        lastTickGameTime = -1;
        SculkVeilRenderer.BUFF.fadeProgress = 0f;
        SculkVeilRenderer.DEEP_DARK.fadeProgress = 0f;
        SculkIntrusionRenderer.INSTANCE.fadeProgress = 0f;
    }

    private SculkScreenEffectEvents() {}
}
