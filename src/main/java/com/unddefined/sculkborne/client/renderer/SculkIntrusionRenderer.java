package com.unddefined.sculkborne.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.util.Mth;

import java.util.Random;
import java.util.function.Supplier;

import static com.unddefined.sculkborne.client.SculkBorneClient.sculkIntrusionPostChain;

// SculkIntrusion 全屏后处理：把 sculk_intrusion 过程纹理按 fadeProgress
// 淡入淡出地叠加到主画面（shader 采样 DiffuseSampler 并与侵入纹理 mix）；
// 渲染时机由调用方决定（与 SculkVeil 一样建议放在 AFTER_LEVEL 阶段）。
public class SculkIntrusionRenderer {
    private static final Minecraft mc = Minecraft.getInstance();

    public final Supplier<PostChain> chain;
    private int lastWidth = -1;
    private int lastHeight = -1;
    public float fadeProgress = 0f;
    private float randomHash = 0f;
    private boolean wasFadingIn = false;
    private final Random rng = new Random();

    public static final SculkIntrusionRenderer INSTANCE = new SculkIntrusionRenderer(() -> sculkIntrusionPostChain);

    public SculkIntrusionRenderer(Supplier<PostChain> chain) {
        this.chain = chain;
    }

    public void render(float partialTicks) {
        PostChain postChain = chain.get();
        if (postChain == null || fadeProgress == 0f) return;
        safeResize(postChain);
        // GameTime 按世界时间（秒）推进：节点闪烁与轮廓蠕动与帧率无关。
        float timeSeconds = mc.level == null ? 0.0F
                : ((float) mc.level.getGameTime() + partialTicks) / 20.0F;
        postChain.setUniform("GameTime", timeSeconds);
        postChain.setUniform("fadeProgress", fadeProgress);
        postChain.setUniform("randomHash", randomHash);
        postChain.process(partialTicks);
    }

    // 与 SculkVeilRenderer 相同的淡入淡出节奏；delta 传每帧 PartialTicks。
    public void updateFadeProgress(boolean fadeIO, float delta) {
        // 每次重新进入淡入（新的效果触发）时更换随机种子；
        // 同一段淡入淡出期间 randomHash 保持不变，保证幽匿布局稳定。
        if (fadeIO && !wasFadingIn) randomHash = rng.nextFloat();
        wasFadingIn = fadeIO;

        float speed = 0.004f;
        if (fadeIO) fadeProgress += speed * delta;
        else fadeProgress -= speed * delta;

        fadeProgress = Mth.clamp(fadeProgress, 0.0f, 1.0f);
    }

    private void safeResize(PostChain postChain) {
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != lastWidth || h != lastHeight) {
            postChain.resize(w, h);
            lastWidth = w;
            lastHeight = h;
        }
    }
}
