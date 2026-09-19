package com.unddefined.sculkborne.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.unddefined.sculkborne.client.SculkBorneClient.deepDarkVeilPostChain;
import static com.unddefined.sculkborne.client.SculkBorneClient.sculkVeilPostChain;

// 一个渲染器类，两个独立实例：BUFF（玩家影匿效果）和 DEEP_DARK（深暗之域）。
public class SculkVeilRenderer {
    private static final Minecraft mc = Minecraft.getInstance();

    public final Supplier<PostChain> chain;
    public float[] FOG_COLOR;
    public float DARKNESS_STRENGTH;
    public float fogRadius;
    public float fogDensity;
    public float marchRadius;
    public boolean useMask;
    public float fadeProgress = 0f;
    private int lastWidth = -1;
    private int lastHeight = -1;

    // 深暗之域掩码：64x64 texel，每 texel 16 格，覆盖玩家周围 ±512 格。
    public static final int MASK_SIZE = 64;
    public static final float MASK_SCALE = 16f;
    public int maskCenterX = Integer.MIN_VALUE;
    public int maskCenterZ = 0;
    public boolean hasDeepDark = false;
    private DynamicTexture deepDarkMask = null;
    private NativeImage maskPixels = null; // CPU 像素，tick 线程构建
    private boolean maskDirty = false;

    public SculkVeilRenderer(Supplier<PostChain> chain, float[] fogColor, float darknessStrength,
                             float fogRadius, float fogDensity, float marchRadius, boolean useMask) {
        this.chain = chain;
        this.FOG_COLOR = fogColor;
        this.DARKNESS_STRENGTH = darknessStrength;
        this.fogRadius = fogRadius;
        this.fogDensity = fogDensity;
        this.marchRadius = marchRadius;
        this.useMask = useMask;
    }

    // 玩家 buff 渲染器：影匿效果驱动，相机周围局域雾。
    public static final SculkVeilRenderer BUFF = new SculkVeilRenderer(
            () -> sculkVeilPostChain, new float[]{7f, 71f, 73f}, 1f, 12f, 0.15f, 12f, false);
    // 深暗之域渲染器：掩码范围内有深暗之域即渲染，雾锚定在深暗之域上空。
    public static final SculkVeilRenderer DEEP_DARK = new SculkVeilRenderer(
            () -> deepDarkVeilPostChain, new float[]{7f, 71f, 73f}, 0f, 12f, 0.06f, 12f, false);

    public void render(int tick, float PartialTicks, Matrix4f M, Matrix4f P) {
        if (tick < 0) fadeProgress = 0.001f;
        var Camera = mc.gameRenderer.getMainCamera();
        PostChain postChain = chain.get();
        if (postChain == null) return;
        safeResize(postChain);
        postChain.process(PartialTicks);
        applyUniforms(tick, Camera.getPosition(), M, P, postChain);
    }

    public void updateFadeProgress(boolean fadeIO, float delta) {
        float speed = 0.007f;
        if (fadeIO) fadeProgress += speed * delta;
        else fadeProgress -= speed * delta;

        fadeProgress = Mth.clamp(fadeProgress, 0.0f, 1.0f);
    }

    // 以玩家为中心重建深暗之域掩码（每 100 tick 或移动 64 格调用）。
    // 只写 CPU 像素，GPU 上传在渲染线程的 uploadMaskIfNeeded 中完成。
    public void updateDeepDarkMask(int centerX, int centerY, int centerZ) {
        if (!useMask || mc.level == null) return;
        maskCenterX = centerX;
        maskCenterZ = centerZ;
        if (maskPixels == null) maskPixels = new NativeImage(MASK_SIZE, MASK_SIZE, true);
        float extent = MASK_SIZE * MASK_SCALE;
        float originX = centerX - extent / 2f;
        float originZ = centerZ - extent / 2f;
        int count = 0;
        for (int x = 0; x < MASK_SIZE; x++) {
            int wx = Math.round(originX + (x + 0.5f) * MASK_SCALE);
            for (int z = 0; z < MASK_SIZE; z++) {
                int wz = Math.round(originZ + (z + 0.5f) * MASK_SCALE);
                // 深暗之域是洞穴生物群系，按玩家当前高度采样，避免采样到洞穴带之外。
                boolean deep = mc.level.getBiome(new BlockPos(wx, centerY, wz)).is(Biomes.DEEP_DARK);
                maskPixels.setPixelRGBA(x, z, deep ? 0xFFFFFFFF : 0xFF000000);
                if (deep) count++;
            }
        }
        maskDirty = true;
        hasDeepDark = count > 0;
    }

    // 只在渲染线程调用：把 CPU 像素上传成 GPU 纹理。
    private void uploadMaskIfNeeded() {
        if (!maskDirty || maskPixels == null) return;
        if (deepDarkMask == null) deepDarkMask = new DynamicTexture(maskPixels);
        else deepDarkMask.upload();

        maskDirty = false;
    }

    private void applyUniforms(int tick, Vec3 cameraPos, Matrix4f M, Matrix4f P, PostChain postChain) {
        List<PostPass> passes = getPasses(postChain);
        if (!passes.isEmpty()) {
            uploadMaskIfNeeded();
            Iterator<PostPass> var16 = passes.iterator();

            while (true) {
                if (!var16.hasNext()) return;
                var pass = var16.next();
                var effect = pass.getEffect();

                effect.safeGetUniform("ModelViewMat").set(M);
                effect.safeGetUniform("InverseProjectionMatrix").set(P.invert());
                effect.safeGetUniform("InverseModelViewMatrix").set(M.invert());
                effect.safeGetUniform("CameraPos").set(cameraPos.toVector3f());
                effect.safeGetUniform("GameTime").set((float) tick);
                effect.safeGetUniform("fadeProgress").set(fadeProgress);
                effect.safeGetUniform("fogRadius").set(fogRadius);
                effect.safeGetUniform("fogSteps").set(3);
                effect.safeGetUniform("fogDensityStrength").set(fogDensity);
                effect.safeGetUniform("fogColor").set(FOG_COLOR[0] / 255f, FOG_COLOR[1] / 255f, FOG_COLOR[2] / 255f);
                effect.safeGetUniform("darknessStrength").set(DARKNESS_STRENGTH);
                effect.safeGetUniform("marchRadius").set(marchRadius);
                effect.safeGetUniform("useMask").set(useMask ? 1f : 0f);
                if (useMask) {
                    effect.safeGetUniform("maskOrigin").set(
                            maskCenterX - MASK_SIZE * MASK_SCALE / 2f,
                            maskCenterZ - MASK_SIZE * MASK_SCALE / 2f);
                    effect.safeGetUniform("maskScale").set(MASK_SCALE);
                    effect.safeGetUniform("maskSize").set((float) MASK_SIZE);
                    if (deepDarkMask != null) effect.setSampler("DeepDarkMask", deepDarkMask::getId);
                }
                effect.setSampler("DepthSampler", pass.inTarget::getDepthTextureId);
            }
        }
    }

    private static Field findPassesField() {
        try {
            return ObfuscationReflectionHelper.findField(PostChain.class, "passes");
        } catch (ObfuscationReflectionHelper.UnableToFindFieldException var3) {
            try {
                return ObfuscationReflectionHelper.findField(PostChain.class, "passes");
            } catch (ObfuscationReflectionHelper.UnableToFindFieldException var2) {
                LogUtils.getLogger().info("Unable to find passes field on PostChain using Mojmap or SRG identifiers", var2);
                return null;
            }
        }
    }

    private List<PostPass> getPasses(PostChain postChain) {
        if (postChain == null) {
            return Collections.emptyList();
        } else if (findPassesField() == null) {
            return Collections.emptyList();
        } else {
            try {
                Object value = Objects.requireNonNull(findPassesField()).get(postChain);
                if (value instanceof List) {
                    return (List<PostPass>) value;
                }
                LogUtils.getLogger().error("Sculk veil post chain passes had unexpected type: {}", value == null ? "null" : value.getClass().getName());
            } catch (IllegalAccessException var3) {
                LogUtils.getLogger().error("Failed to access sculk veil post chain passes", var3);
                return Collections.emptyList();
            }

            return Collections.emptyList();
        }
    }

    public void safeResize(PostChain postChain) {
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != lastWidth || h != lastHeight) {
            postChain.resize(w, h);
            lastWidth = w;
            lastHeight = h;
        }
    }
}
