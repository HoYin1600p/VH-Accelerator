package dev.hoyin1600p.vhaccelerator.mixin.fix.registry;

import com.mojang.serialization.Lifecycle;
import dev.hoyin1600p.vhaccelerator.backport.fix.RegistryWriteLock;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceKey;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Serialises the write paths of vanilla's {@code MappedRegistry}, which has no
 * locking, so mods registering from parallel construction cannot corrupt its
 * hash maps or hand two entries the same id. Each overwritten method keeps its
 * Forge 40.3.11 body and only runs it inside the registry's monitor, with
 * {@code nextId} read inside the lock.
 *
 * <p>The private five-argument {@code registerMapping} is left alone so the
 * registry growth redirects (VHA's and ModernFix's) keep their target. Priority
 * 500 matches the tag backport, so later injectors still apply on top.
 */
@Mixin(value = MappedRegistry.class, priority = 500)
abstract class MappedRegistryWriteLockMixin<T> {
    @Shadow
    @Final
    private Map<ResourceKey<T>, Holder.Reference<T>> byKey;

    @Shadow
    @Final
    private Object2IntMap<T> toId;

    @Shadow
    @Final
    private Map<T, Holder.Reference<T>> byValue;

    @Shadow
    @Final
    private Map<T, Lifecycle> lifecycles;

    @Shadow
    @Final
    @Nullable
    private Function<T, Holder.Reference<T>> customHolderProvider;

    @Shadow
    private int nextId;

    @Shadow
    private void validateWrite(ResourceKey<T> key) {
        throw new AssertionError();
    }

    @Shadow
    private Holder<T> registerMapping(int id, ResourceKey<T> key, T value, Lifecycle lifecycle, boolean logDuplicate) {
        throw new AssertionError();
    }

    /**
     * @author HoYin1600p
     * @reason Vanilla's body, run under the registry's write lock.
     */
    @Overwrite
    public Holder<T> register(ResourceKey<T> key, T value, Lifecycle lifecycle) {
        synchronized (RegistryWriteLock.monitor(this)) {
            return this.registerMapping(this.nextId, key, value, lifecycle);
        }
    }

    /**
     * @author HoYin1600p
     * @reason Vanilla's body, run under the registry's write lock.
     */
    @Overwrite
    public Holder<T> registerMapping(int id, ResourceKey<T> key, T value, Lifecycle lifecycle) {
        synchronized (RegistryWriteLock.monitor(this)) {
            return this.registerMapping(id, key, value, lifecycle, true);
        }
    }

    /**
     * @author HoYin1600p
     * @reason Vanilla's body, run under the registry's write lock.
     */
    @Overwrite
    public Holder<T> registerOrOverride(OptionalInt requestedId, ResourceKey<T> key, T value, Lifecycle lifecycle) {
        synchronized (RegistryWriteLock.monitor(this)) {
            this.validateWrite(key);
            Validate.notNull(key);
            Validate.notNull(value);
            Holder<T> holder = this.byKey.get(key);
            T previous = holder != null && holder.isBound() ? holder.value() : null;
            int id;
            if (previous == null) {
                id = requestedId.orElse(this.nextId);
            } else {
                id = this.toId.getInt(previous);
                if (requestedId.isPresent() && requestedId.getAsInt() != id) {
                    throw new IllegalStateException("ID mismatch");
                }

                this.lifecycles.remove(previous);
                this.toId.removeInt(previous);
                this.byValue.remove(previous);
            }

            return this.registerMapping(id, key, value, lifecycle, false);
        }
    }

    /**
     * Vanilla is {@code byKey.computeIfAbsent(key, k -> { check; create })}.
     * This is the same operation without the lambda (a null mapping counts as
     * absent in {@code computeIfAbsent}), so no synthetic method has to be
     * merged into the registry class.
     *
     * @author HoYin1600p
     * @reason Vanilla's body, run under the registry's write lock.
     */
    @Overwrite
    public Holder<T> getOrCreateHolder(ResourceKey<T> key) {
        synchronized (RegistryWriteLock.monitor(this)) {
            Holder.Reference<T> existing = this.byKey.get(key);
            if (existing != null) {
                return existing;
            }
            if (this.customHolderProvider != null) {
                throw new IllegalStateException("This registry can't create new holders without value");
            }
            this.validateWrite(key);
            Holder.Reference<T> created = Holder.Reference.createStandAlone((MappedRegistry<T>) (Object) this, key);
            this.byKey.put(key, created);
            return created;
        }
    }
}
