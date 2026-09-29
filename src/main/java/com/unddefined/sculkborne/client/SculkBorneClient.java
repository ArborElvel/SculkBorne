package com.unddefined.sculkborne.client;

import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.client.model.anim.CreeperVanillaAnimator;
import com.unddefined.sculkborne.client.model.anim.EndermanVanillaAnimator;
import com.unddefined.sculkborne.client.model.anim.EndermiteVanillaAnimator;
import com.unddefined.sculkborne.client.model.anim.SilverfishVanillaAnimator;
import com.unddefined.sculkborne.client.model.anim.SkeletonVanillaAnimator;
import com.unddefined.sculkborne.client.model.anim.VexVanillaAnimator;
import com.unddefined.sculkborne.client.model.anim.ZombieVanillaAnimator;
import com.unddefined.sculkborne.client.model.cem.CemAnimatorRegistry;
import com.unddefined.sculkborne.client.particles.ParticleDirectlyMovingDust;
import com.unddefined.sculkborne.client.renderer.block.CalibratedSculkShriekerRenderer;
import com.unddefined.sculkborne.client.renderer.block.SculkWhisperRenderer;
import com.unddefined.sculkborne.client.renderer.block.SculkZombieHeadRenderer;
import com.unddefined.sculkborne.client.renderer.entity.CreesperEntityRenderer;
import com.unddefined.sculkborne.client.renderer.entity.SculkMiteEntityRenderer;
import com.unddefined.sculkborne.client.renderer.entity.SculkShadeEntityRenderer;
import com.unddefined.sculkborne.client.renderer.entity.SculkSkeletonEntityRenderer;
import com.unddefined.sculkborne.client.renderer.entity.SculkSpreaderEntityRenderer;
import com.unddefined.sculkborne.client.renderer.entity.SculkZombieEntityRenderer;
import com.unddefined.sculkborne.client.renderer.entity.SculverfishEntityRenderer;
import com.unddefined.sculkborne.client.renderer.entity.WandererEntityRenderer;
import com.unddefined.sculkborne.client.renderer.entity.WanderShadowEntityRenderer;
import com.unddefined.sculkborne.server.registry.BlockEntityRegistry;
import com.unddefined.sculkborne.server.registry.EntityRegistry;
import com.unddefined.sculkborne.server.registry.ParticlesRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.io.IOException;

@Mod(value = SculkBorne.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SculkBorne.MODID, value = Dist.CLIENT)
public class SculkBorneClient {
    private static final Minecraft mc = Minecraft.getInstance();
    public static PostChain sculkVeilPostChain = null;
    public static PostChain deepDarkVeilPostChain = null;
    public static PostChain sculkIntrusionPostChain = null;

    public SculkBorneClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                ResourceLocation veil = ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, "shaders/post/sculk_veil.json");
                sculkVeilPostChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), veil);
                deepDarkVeilPostChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), veil);
                sculkIntrusionPostChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                        ResourceLocation.fromNamespaceAndPath(SculkBorne.MODID, "shaders/post/sculk_intrusion.json"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            BlockEntityRenderers.register(BlockEntityRegistry.CALIBRATED_SCULK_SHRIEKER.get(),
                    context -> new CalibratedSculkShriekerRenderer());
            BlockEntityRenderers.register(BlockEntityRegistry.SCULK_WHISPER.get(),
                    context -> new SculkWhisperRenderer());
            BlockEntityRenderers.register(BlockEntityRegistry.SCULK_ZOMBIE_HEAD.get(),
                    context -> new SculkZombieHeadRenderer());

            registerVanillaAnimators();
        });
    }

    /**
     * 注册 per mob 的原版动作移植（内置兜底）：没有安装 CEM 附属时由它们接管整帧姿势，
     * 装了之后附属在 {@code CemAnimatorRegistry} 里的注册会覆盖这些内置实现。
     */
    private static void registerVanillaAnimators() {
        CemAnimatorRegistry.registerBuiltin(EntityRegistry.CREESPER_ENTITY.get(), CreeperVanillaAnimator::new);
        CemAnimatorRegistry.registerBuiltin(EntityRegistry.SCULK_MITE_ENTITY.get(), EndermiteVanillaAnimator::new);
        CemAnimatorRegistry.registerBuiltin(EntityRegistry.SCULK_SHADE_ENTITY.get(), VexVanillaAnimator::new);
        CemAnimatorRegistry.registerBuiltin(EntityRegistry.SCULK_SKELETON_ENTITY.get(), SkeletonVanillaAnimator::new);
        CemAnimatorRegistry.registerBuiltin(EntityRegistry.SCULK_ZOMBIE_ENTITY.get(), ZombieVanillaAnimator::new);
        CemAnimatorRegistry.registerBuiltin(EntityRegistry.SCULVERFISH_ENTITY.get(), SilverfishVanillaAnimator::new);
        CemAnimatorRegistry.registerBuiltin(EntityRegistry.WANDERER_ENTITY.get(), EndermanVanillaAnimator::new);
        CemAnimatorRegistry.registerBuiltin(EntityRegistry.WANDER_SHADOW_ENTITY.get(), EndermanVanillaAnimator::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegistry.SCULK_SPREADER_ENTITY.get(), SculkSpreaderEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SCULK_ZOMBIE_ENTITY.get(), SculkZombieEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.CREESPER_ENTITY.get(), CreesperEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SCULK_SKELETON_ENTITY.get(), SculkSkeletonEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SCULVERFISH_ENTITY.get(), SculverfishEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SCULK_SHADE_ENTITY.get(), SculkShadeEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SCULK_MITE_ENTITY.get(), SculkMiteEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.WANDERER_ENTITY.get(), WandererEntityRenderer::new);
        event.registerEntityRenderer(EntityRegistry.WANDER_SHADOW_ENTITY.get(), WanderShadowEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ParticlesRegistry.DIRECT_MOVING_DUST.get(), ParticleDirectlyMovingDust.Provider::new);
    }
}
