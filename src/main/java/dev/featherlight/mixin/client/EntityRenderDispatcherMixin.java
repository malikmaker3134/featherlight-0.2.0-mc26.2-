package dev.featherlight.mixin.client;

import dev.featherlight.Featherlight;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fail-soft hook: a descriptor that does not exist in this Minecraft version is skipped, not fatal. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    // Minecraft 26.2 and older
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void featherlight$cull(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (Featherlight.shouldCull(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }

    // Minecraft 26.3 (extra float parameter)
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void featherlight$cullWithTick(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                           float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (Featherlight.shouldCull(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}
