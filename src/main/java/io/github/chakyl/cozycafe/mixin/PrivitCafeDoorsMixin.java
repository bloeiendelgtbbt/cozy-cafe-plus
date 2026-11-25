package io.github.chakyl.cozycafe.mixin;

import dev.slarrties.privit.common.region.rule.Rule;
import io.github.chakyl.cozycafe.util.CafeLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Doors and fence gates inside a cafe's claim are always usable, by guests and by NPC customers alike,
 * whatever the claim's own rules say. Every Privit door/gate check (players, mobs) ends in this method.
 * Trapdoors stay under Privit's rules. Both descriptors are listed so the target is found in the dev
 * (Mojang names) and in the production (intermediary) environment.
 */
@Mixin(targets = "dev.slarrties.privit.server.region.protection.RegionPermissionChecker", remap = false)
public abstract class PrivitCafeDoorsMixin {
    @Inject(method = {
            "isAllowed(Ljava/util/UUID;Ldev/slarrties/privit/common/region/rule/Rule;Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerLevel;)Z",
            "isAllowed(Ljava/util/UUID;Ldev/slarrties/privit/common/region/rule/Rule;Lnet/minecraft/class_2338;Lnet/minecraft/class_3218;)Z"
    }, at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void cozyrestaurant$cafeDoorsAlwaysOpen(UUID player, Rule rule, BlockPos pos, ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (rule != Rule.INTERACT_WITH_DOORS && rule != Rule.INTERACT_WITH_FENCE_GATES) return;
        if (level != null && pos != null && CafeLookup.cafeAt(level, pos) != null) cir.setReturnValue(true);
    }
}
