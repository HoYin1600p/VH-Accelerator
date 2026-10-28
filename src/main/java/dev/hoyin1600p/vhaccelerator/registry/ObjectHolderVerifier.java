package dev.hoyin1600p.vhaccelerator.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.ObjectHolderRegistry;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.registries.RegistryObject;

/**
 * Debug-only check, run once at load-complete, that holder fields and registry objects hold what their
 * registries contain. It reads Forge's handler set by reflection, never changes anything and never throws.
 */
public final class ObjectHolderVerifier {
    private static final int MAX_DETAILS = 20;

    private ObjectHolderVerifier() {
    }

    public record Result(int refsChecked, int registryObjectsChecked, int skipped, int mismatches,
                         List<String> details) {
        public String summaryLine() {
            return "Object-holder verifier: checked " + refsChecked + " ObjectHolderRef fields and "
                    + registryObjectsChecked + " RegistryObject handlers, " + mismatches + " mismatches, "
                    + skipped + " skipped";
        }
    }

    /** Runs the check on Forge's live handler set and logs the summary line plus up to 20 details. */
    public static void runAndLog(Consumer<String> info, Consumer<String> warn) {
        try {
            Result result = verify(liveHandlers());
            info.accept(result.summaryLine());
            result.details().forEach(warn);
        } catch (Throwable failure) {
            try {
                warn.accept("Object-holder verifier failed: " + failure);
            } catch (Throwable ignored) {
                // nothing more to do
            }
        }
    }

    private static Collection<?> liveHandlers() throws ReflectiveOperationException {
        Field field = ObjectHolderRegistry.class.getDeclaredField("objectHolders");
        if (!field.trySetAccessible()) {
            throw new IllegalAccessException("objectHolders");
        }
        synchronized (ObjectHolderRegistry.class) {
            return new ArrayList<>((Set<?>) field.get(null));
        }
    }

    /** Checks the given handlers; handlers of any other shape are skipped. Never throws. */
    public static Result verify(Collection<?> handlers) {
        int refs = 0;
        int objects = 0;
        int skipped = 0;
        int mismatches = 0;
        List<String> details = new ArrayList<>();
        try {
            Method isDummied = ForgeRegistry.class.getDeclaredMethod("isDummied", ResourceLocation.class);
            isDummied.trySetAccessible();
            for (Object handler : handlers) {
                try {
                    if (handler == null) {
                        skipped++;
                        continue;
                    }
                    String type = handler.getClass().getName();
                    if (type.equals(ForgeHolderClassifier.REF_CLASS)) {
                        Object registry = read(handler, "registry");
                        ResourceLocation key = (ResourceLocation) read(handler, "injectedObject");
                        Field target = (Field) read(handler, "field");
                        if (!(registry instanceof ForgeRegistry<?> forge) || key == null || target == null
                                || !(Boolean) read(handler, "isValid") || !target.trySetAccessible()
                                || !forge.containsKey(key) || (Boolean) isDummied.invoke(forge, key)) {
                            skipped++;
                            continue;
                        }
                        refs++;
                        Object expected = forge.getValue(key);
                        Object actual = target.get(null);
                        if (actual != expected) {
                            mismatches++;
                            addDetail(details, "holder " + target.getDeclaringClass().getName() + "."
                                    + target.getName() + " for " + key + " in " + forge.getRegistryName()
                                    + " holds " + actual + ", registry has " + expected);
                        }
                    } else if (type.equals(ForgeHolderClassifier.KEYED_CLASS)) {
                        Object outer = read(handler, "this$0");
                        ResourceLocation registryName = (ResourceLocation) read(handler, "val$registryName");
                        if (!(outer instanceof RegistryObject<?> object) || registryName == null
                                || object.getId() == null) {
                            skipped++;
                            continue;
                        }
                        ForgeRegistry<?> forge = RegistryManager.ACTIVE.getRegistry(registryName);
                        if (forge == null) {
                            skipped++; // vanilla or data-driven registry: no Forge registry to compare with
                            continue;
                        }
                        objects++;
                        ResourceLocation id = object.getId();
                        boolean shouldBePresent = forge.containsKey(id) && !(Boolean) isDummied.invoke(forge, id);
                        if (object.isPresent() != shouldBePresent) {
                            mismatches++;
                            addDetail(details, "registry object " + id + " in " + registryName + " is "
                                    + (object.isPresent() ? "present" : "absent") + ", registry "
                                    + (shouldBePresent ? "contains" : "lacks") + " it");
                        }
                    } else {
                        skipped++;
                    }
                } catch (Throwable one) {
                    skipped++;
                    addDetail(details, "could not check a handler: " + one);
                }
            }
        } catch (Throwable failure) {
            addDetail(details, "verifier stopped early: " + failure);
        }
        return new Result(refs, objects, skipped, mismatches, details);
    }

    private static void addDetail(List<String> details, String text) {
        if (details.size() < MAX_DETAILS) {
            details.add("Object-holder verifier: " + text);
        }
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
