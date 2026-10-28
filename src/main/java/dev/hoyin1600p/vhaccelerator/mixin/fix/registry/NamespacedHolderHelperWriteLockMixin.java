package dev.hoyin1600p.vhaccelerator.mixin.fix.registry;

import dev.hoyin1600p.vhaccelerator.backport.fix.RegistryWriteLock;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Serializes Forge's separate intrusive-holder table during parallel mod construction. */
@Mixin(targets = "net.minecraftforge.registries.NamespacedHolderHelper", remap = false)
abstract class NamespacedHolderHelperWriteLockMixin {
    @Shadow @Final private Registry<?> self;

    @Redirect(method = "createIntrusiveHolder", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 0)
    private Object vha$createIntrusiveHolderUnderWriteLock(Map<Object, Object> holders, Object key,
                                                          Function<Object, Object> factory) {
        return RegistryWriteLock.computeHolder(this.self, holders, key, factory);
    }
}
