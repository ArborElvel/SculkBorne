package com.unddefined.sculkborne.server.events;

import com.unddefined.sculkborne.SculkBorne;
import com.unddefined.sculkborne.effects.AttackScatteredEffect;
import com.unddefined.sculkborne.effects.StaggerEffect;
import com.unddefined.sculkborne.effects.TinnitusEffect;
import com.unddefined.sculkborne.entities.CreesperEntity;
import com.unddefined.sculkborne.entities.SculkMob;
import com.unddefined.sculkborne.entities.SculkSkeletonEntity;
import com.unddefined.sculkborne.entities.SculverfishEntity;
import com.unddefined.sculkborne.server.SculkBloom;
import com.unddefined.sculkborne.server.SculkIntrusionSpreader;
import com.unddefined.sculkborne.server.registry.ItemRegistry;
import com.unddefined.sculkborne.server.registry.PotionRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import static com.unddefined.sculkborne.Config.SCULK_VEIL_GLOWING_DURATION;
import static com.unddefined.sculkborne.server.registry.DataRegistry.GLOWING_LAST_TICK;
import static com.unddefined.sculkborne.server.registry.DataRegistry.GLOWING_START;
import static com.unddefined.sculkborne.server.registry.DataRegistry.GLOWING_TOTAL;
import static com.unddefined.sculkborne.server.registry.DataRegistry.SCULK_SPREADER;
import static com.unddefined.sculkborne.server.registry.DataRegistry.SCULK_VEIL_TOTAL;
import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.ATTACK_SCATTERED;
import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.SCULK_INTRUSION;
import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.SCULK_VEIL;
import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.STAGGER;
import static com.unddefined.sculkborne.server.registry.MobEffectRegistry.TINNITUS;
import static com.unddefined.sculkborne.server.registry.TagRegistry.SCULK_BLOCKS;
import static net.minecraft.world.effect.MobEffects.DARKNESS;
import static net.minecraft.world.effect.MobEffects.GLOWING;
import static net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED;
import static net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE;
import static net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;

@EventBusSubscriber(modid = SculkBorne.MODID)
public class ServerEvents {
    private static final float SCULK_MATTER_BLOCK_DROP_CHANCE = 0.10F;
    private static final float FORTUNE_BLOCK_DROP_CHANCE = 0.10F;

    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(Potions.AWKWARD, Items.SCULK_VEIN, PotionRegistry.SCULK_INTRUSION);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof SculverfishEntity sculverfish) {
            sculverfish.initializeSpawnIfNeeded();
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(VanillaGameEvent event) {
        if (!event.getVanillaEvent().is(GameEvent.ENTITY_DIE)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getCause() instanceof LivingEntity dead)) return;
        if (!dead.shouldDropExperience() || dead.wasExperienceConsumed()) return;

        var damageSource = dead.getLastDamageSource();
        int xp = dead.getExperienceReward(level, damageSource == null ? null : damageSource.getEntity());
        if (xp <= 0) return;

        Vec3 deathPos = event.getEventPosition();
        double radius = SculkIntrusionSpreader.FOLLOW_RADIUS;
        var host = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(deathPos, radius * 2, radius * 2, radius * 2),
                h -> h.isAlive() && (h.hasEffect(SCULK_INTRUSION) || h instanceof Warden)
                        && SculkIntrusionSpreader.followBox(h).contains(deathPos)).stream().findFirst().orElse(null);
        if (host == null) return;
        if (SculkIntrusionSpreader.hasUsableCatalystNearby(level, deathPos)) return;
        if (level.getRandom().nextFloat() >= ((host instanceof Warden) ? 0 : SculkIntrusionSpreader.TRIGGER_CHANCE)) return;
        host.getData(SCULK_SPREADER).absorbEntityDeath(level, host, deathPos, xp);
        dead.skipDropExperience();
    }

    @SubscribeEvent
    public static void onSculkMobDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof SculkMob sculkMob) sculkMob.triggerSculkBloomOnDeath();
    }

    @SubscribeEvent
    public static void onSculkMobDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof SculkMob sculkMob)) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return;
        sculkMob.dropSculkMobLoot(level, event.getSource(), event.getDrops());
    }

    @SubscribeEvent
    public static void onCreesperExplosion(ExplosionEvent.Start event) {
        if (!(event.getExplosion().getDirectSourceEntity() instanceof CreesperEntity creesper)) return;
        if (event.getLevel() instanceof ServerLevel level) creesper.infrasoundExplode(level);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSculkBlockDrops(BlockDropsEvent event) {
        if (!event.getState().is(SCULK_BLOCKS)) return;

        var fortune = event.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(fortune, event.getTool());
        float chance = Math.min(1.0F, SCULK_MATTER_BLOCK_DROP_CHANCE + fortuneLevel * FORTUNE_BLOCK_DROP_CHANCE);
        if (event.getLevel().getRandom().nextFloat() >= chance) return;

        ItemEntity drop = new ItemEntity(event.getLevel(), event.getPos().getX() + 0.5,
                event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                new ItemStack(ItemRegistry.SCULK_MATTER.get()));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) SculkBloom.serverTick(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) SculkBloom.discard(level);
    }

    @SubscribeEvent
    public static void onWardenAttack(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Warden)) return;
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        event.getEntity().addEffect(new MobEffectInstance(SCULK_INTRUSION, 20 * 60));
    }

    @SubscribeEvent
    public static void onSculkMobAttack(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof SculkMob sculkMob)) return;
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        sculkMob.tryApplyIntrusionOnAttack(event.getEntity());
    }

    @SubscribeEvent
    public static void onSculkSkeletonAttack(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof SculkSkeletonEntity skeleton)) return;
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        skeleton.applyBlindnessAndDeafnessOnHit(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Warden warden)) return;
        if (!(warden.level() instanceof ServerLevel level)) return;
        if (!warden.isAlive()) return;
        if (warden.hasEffect(SCULK_INTRUSION)) return;
        warden.getData(SCULK_SPREADER).serverTick(level, warden);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        updateGlowingTime(player, now);
        long glowingInProgress = player.getData(GLOWING_START) >= 0 ? now - player.getData(GLOWING_START) + 1 : 0;
        if (player.getData(SCULK_VEIL_TOTAL) - player.getData(GLOWING_TOTAL) - glowingInProgress >= 6000
                && !player.hasEffect(DARKNESS))
            player.addEffect(new MobEffectInstance(DARKNESS, Integer.MAX_VALUE, 1, false, true));
    }

    private static void updateGlowingTime(ServerPlayer player, long now) {
        long start = player.getData(GLOWING_START);
        if (player.hasEffect(GLOWING)) {
            if (start < 0) player.setData(GLOWING_START, now);
            player.setData(GLOWING_LAST_TICK, now);
        } else if (start >= 0) {
            long lastTick = player.getData(GLOWING_LAST_TICK);
            player.setData(GLOWING_TOTAL, player.getData(GLOWING_TOTAL) + Math.max(0, lastTick - start + 1));
            player.setData(GLOWING_START, -1L);
            player.setData(GLOWING_LAST_TICK, -1L);
        }
    }

    @SubscribeEvent
    public static void onExpireEffect(MobEffectEvent.Expired event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null) return;
        clearEffect(effect, event.getEntity());
        if (effect.is(SCULK_VEIL)) {
            event.getEntity().addEffect(new MobEffectInstance(GLOWING, SCULK_VEIL_GLOWING_DURATION.get() * 20));
        }
    }

    private static void clearEffect(MobEffectInstance effect, LivingEntity entity) {
        if (effect.is(SCULK_INTRUSION)) entity.getData(SCULK_SPREADER).clear();
        if (effect.is(TINNITUS) && entity.getAttribute(FOLLOW_RANGE) != null && entity instanceof Monster monster) {
            monster.getAttribute(FOLLOW_RANGE).removeModifier(TinnitusEffect.tinnitus_modifier_id);
        }
        if (effect.is(STAGGER) && entity.getAttribute(MOVEMENT_SPEED) != null) {
            entity.getAttribute(MOVEMENT_SPEED).removeModifier(StaggerEffect.stagger_modifier_id);
        }
        if (effect.is(ATTACK_SCATTERED) && entity.getAttribute(ATTACK_SPEED) != null) {
            entity.getAttribute(ATTACK_SPEED).removeModifier(AttackScatteredEffect.attack_scattered_modifier_id);
        }
    }

    @SubscribeEvent
    public static void onRemoveEffect(MobEffectEvent.Remove event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (effect != null) clearEffect(effect, event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(ATTACK_SCATTERED)) return;
        MobEffectInstance effect = entity.getEffect(ATTACK_SCATTERED);
        if (effect == null) return;
        RandomSource random = entity.getRandom();
        if (random.nextFloat() < 0.3F * (effect.getAmplifier() + 1)) event.setCanceled(true);
    }
}
