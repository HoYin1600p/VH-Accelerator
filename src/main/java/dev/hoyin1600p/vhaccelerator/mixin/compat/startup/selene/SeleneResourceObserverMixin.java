package dev.hoyin1600p.vhaccelerator.mixin.compat.startup.selene;

import dev.hoyin1600p.vhaccelerator.startup.StartupWorkObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact-version dedicated diagnostics; target behaviour and arguments are unchanged. */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.selene.resourcepack.DynamicResourcePack", remap = false)
public abstract class SeleneResourceObserverMixin {
    @Inject(method = "registerPack(Lnet/minecraftforge/eventbus/api/IEventBus;)V", at = @At("HEAD"), require = 0)
    private void vha$observeStart0(CallbackInfo ci) {
        StartupWorkObserver.enter("selene.pack.register.listener", this);
    }

    @Inject(method = "registerPack(Lnet/minecraftforge/eventbus/api/IEventBus;)V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd0(CallbackInfo ci) {
        StartupWorkObserver.exit("selene.pack.register.listener");
    }

    @Inject(method = "register(Lnet/minecraftforge/event/AddPackFindersEvent;)V", at = @At("HEAD"), require = 0)
    private void vha$observeStart1(CallbackInfo ci) {
        StartupWorkObserver.enter("selene.pack.register.finder", this);
    }

    @Inject(method = "register(Lnet/minecraftforge/event/AddPackFindersEvent;)V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd1(CallbackInfo ci) {
        StartupWorkObserver.exit("selene.pack.register.finder");
    }

    @Inject(method = "addBytes(Lnet/minecraft/resources/ResourceLocation;[B)V", at = @At("HEAD"), require = 0)
    private void vha$observeStart2(net.minecraft.resources.ResourceLocation id, byte[] bytes, CallbackInfo ci) {
        StartupWorkObserver.enter("selene.resource.generated", this);
    }

    @Inject(method = "addBytes(Lnet/minecraft/resources/ResourceLocation;[B)V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd2(net.minecraft.resources.ResourceLocation id, byte[] bytes, CallbackInfo ci) {
        StartupWorkObserver.generated("selene.resource.generated", bytes);
    }

    @Inject(method = "addRootResource(Ljava/lang/String;[B)V", at = @At("HEAD"), require = 0)
    private void vha$observeStart3(String name, byte[] bytes, CallbackInfo ci) {
        StartupWorkObserver.enter("selene.root.generated", this);
    }

    @Inject(method = "addRootResource(Ljava/lang/String;[B)V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd3(String name, byte[] bytes, CallbackInfo ci) {
        StartupWorkObserver.generated("selene.root.generated", bytes);
    }

}
