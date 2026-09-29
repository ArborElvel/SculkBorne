package com.unddefined.sculkborne.mixin;

import com.unddefined.sculkborne.entities.PickupDelayed;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拾取的兜底拦截：见 {@link PickupDelayed}。
 *
 * <p>原版的拾取延迟只在 {@code ExperienceOrb#playerTouch} 里判定，而 Clumps 之类的 mod 会把
 * playerTouch 整个换掉（它的 HEAD 注入直接 cancel，不看 {@code takeXpDelay}），
 * 于是徘徊者刚剥落的经验球会被玩家当场捡回去。这里改成在 {@code Player#touch} 这一步拦住：
 * 球还在延迟里就不把这次触碰交给它（{@code playerTouch} 不会被调用），
 * 所以任何接管 playerTouch 的 mod 都绕不过这道延迟。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "touch", at = @At("HEAD"), cancellable = true)
    private void sculkborne$keepDelayedOrbs(Entity entity, CallbackInfo ci) {
        if (entity instanceof PickupDelayed orb && orb.sculkborne$isPickupDelayed()) ci.cancel();
    }
}
