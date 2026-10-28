package dev.hoyin1600p.vhaccelerator.mixin.fix.registry;

import dev.hoyin1600p.vhaccelerator.backport.fix.RegistryWriteLock;
import java.util.Map;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code Registry.internalRegister} records each new registry's bootstrap in
 * the static {@code LOADERS} map, a plain {@code LinkedHashMap}. Mods that
 * create registries from parallel construction call it at the same time, so
 * the {@code put} runs under a shared lock. The write to the root registry that
 * follows is covered by {@link MappedRegistryWriteLockMixin}.
 */
@Mixin(Registry.class)
abstract class RegistryLoadersLockMixin {
    @Redirect(
            method = "internalRegister",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    remap = false
            ),
            require = 1
    )
    private static Object vha$putLoaderUnderLock(Map<Object, Object> loaders, Object key, Object loader) {
        synchronized (RegistryWriteLock.LOADERS) {
            return loaders.put(key, loader);
        }
    }
}
