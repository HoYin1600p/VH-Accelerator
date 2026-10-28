package dev.hoyin1600p.vhaccelerator.registry;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Tells which registry a Forge 40 object-holder handler can act for.
 *
 * <p>Two handler shapes cover almost every handler in a large pack:
 * {@code ObjectHolderRef} (the {@code @ObjectHolder} fields) and the anonymous
 * handler {@code RegistryObject$2} that {@code RegistryObject.create(name,
 * registryKey, modid)} installs. Both do nothing unless the event names their
 * registry. A class is trusted only when its bytes match a reviewed Forge build
 * (SHA-256) and its declared fields are exactly the reviewed ones; any other
 * handler, class or layout is answered {@link HolderDispatchIndex#ALWAYS}, so
 * the original full pass still reaches it.</p>
 */
public final class ForgeHolderClassifier implements HolderDispatchIndex.Classifier<Object> {
    static final String REF_CLASS = "net.minecraftforge.registries.ObjectHolderRef";
    static final String OBJECT_CLASS = "net.minecraftforge.registries.RegistryObject";
    static final String KEYED_CLASS = "net.minecraftforge.registries.RegistryObject$2";
    static final String REGISTRY_CLASS = "net.minecraftforge.registries.ObjectHolderRegistry";

    /** ObjectHolderRegistry in Forge 1.18.2 40.0.52 to 40.2.1 and in 40.2.10 to 40.3.12: its applyObjectHolders loop. */
    static final Set<String> REVIEWED_REGISTRY = Set.of(
            "ea45c5fb0a795c8ad0be056a56a7ad47c731b88b68bad27f056a86827f8ece79",
            "486bc63870ca7ab7dd422ed38b8511c23f66f4b2275c9bfd092cd94488c0033d");

    /** ObjectHolderRef in Forge 1.18.2 40.0.52 to 40.2.1 and in 40.2.4 to 40.3.12. */
    static final Set<String> REVIEWED_REF = Set.of(
            "0692bc0e8e824308ea03fc80a0416b3ade42f203837d2631c67d3d50ea9d73cf",
            "6b484a4b222c5219480a49c53b36e0aee62f635d86388f54541097f9125fbc52");
    /** RegistryObject$2 in the same three compiler variants; the accept bodies are identical. */
    static final Set<String> REVIEWED_KEYED = Set.of(
            "d0955e3304db3d28f1fde804da9b5fa7244f57950c3613a3e440faa28c09aed8",
            "b3e9a6b7b055e35b485d6af557750cb5c1cc17d249f3de2a28b2f1c5bc4aafa9",
            "ab04ca76fa6bd0db8ce2c09ed47e5e4cd4ccde0ade7e9821e8af8330c11ed5ee");

    private final Class<?> refClass;
    private final Field refRegistry;
    private final Class<?> keyedClass;
    private final Field keyedExists;
    private final Field keyedInvalid;
    private final Field keyedName;
    private final Field keyedOuter;
    private final Field outerOptional;
    private final boolean registryReviewed;

    private ForgeHolderClassifier(Class<?> refClass, Field refRegistry, Class<?> keyedClass, Field keyedExists,
                                  Field keyedInvalid, Field keyedName, Field keyedOuter, Field outerOptional,
                                  boolean registryReviewed) {
        this.registryReviewed = registryReviewed;
        this.refClass = refClass;
        this.refRegistry = refRegistry;
        this.keyedClass = keyedClass;
        this.keyedExists = keyedExists;
        this.keyedInvalid = keyedInvalid;
        this.keyedName = keyedName;
        this.keyedOuter = keyedOuter;
        this.outerOptional = outerOptional;
    }

    /** Resolves the reviewed layout; whichever shape does not match is simply never indexed. */
    public static ForgeHolderClassifier resolve(ClassLoader loader) {
        return resolve(loader, REVIEWED_REF, REVIEWED_KEYED, REVIEWED_REGISTRY);
    }

    static ForgeHolderClassifier resolve(ClassLoader loader, Set<String> refHashes, Set<String> keyedHashes,
                                         Set<String> registryHashes) {
        boolean registryReviewed = false;
        try {
            registryReviewed = registryHashes.contains(hash(loader, Class.forName(REGISTRY_CLASS, false, loader)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            registryReviewed = false;
        }
        Class<?> ref = null;
        Field refRegistry = null;
        try {
            Class<?> c = Class.forName(REF_CLASS, false, loader);
            if (refHashes.contains(hash(loader, c)) && fieldsAre(c, "field", "injectedObject", "isValid", "registry")) {
                Field f = c.getDeclaredField("registry");
                if (f.getType() == ForgeRegistry.class && !Modifier.isStatic(f.getModifiers()) && f.trySetAccessible()) {
                    ref = c;
                    refRegistry = f;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            ref = null;
            refRegistry = null;
        }
        Class<?> keyed = null;
        Field exists = null;
        Field invalid = null;
        Field name = null;
        Field outer = null;
        Field optional = null;
        try {
            Class<?> c = Class.forName(KEYED_CLASS, false, loader);
            Class<?> objectClass = Class.forName(OBJECT_CLASS, false, loader);
            if (keyedHashes.contains(hash(loader, c))
                    && fieldsAre(c, "registryExists", "invalidRegistry", "val$registryName", "val$modid",
                    "val$callerStack", "this$0")) {
                Field e = c.getDeclaredField("registryExists");
                Field i = c.getDeclaredField("invalidRegistry");
                Field n = c.getDeclaredField("val$registryName");
                Field o = c.getDeclaredField("this$0");
                Field opt = objectClass.getDeclaredField("optionalRegistry");
                if (e.getType() == boolean.class && i.getType() == boolean.class
                        && n.getType() == ResourceLocation.class && o.getType() == objectClass
                        && opt.getType() == boolean.class
                        && e.trySetAccessible() && i.trySetAccessible() && n.trySetAccessible()
                        && o.trySetAccessible() && opt.trySetAccessible()) {
                    keyed = c;
                    exists = e;
                    invalid = i;
                    name = n;
                    outer = o;
                    optional = opt;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            keyed = null;
            exists = null;
            invalid = null;
            name = null;
            outer = null;
            optional = null;
        }
        return new ForgeHolderClassifier(ref, refRegistry, keyed, exists, invalid, name, outer, optional,
                registryReviewed);
    }

    /** True when ObjectHolderRegistry (the loop and the add/remove methods the index relies on) is a reviewed build. */
    public boolean registryClassReviewed() {
        return registryReviewed;
    }

    /** True when the layout of the object-holder reference shape matched. */
    public boolean indexesObjectHolderRefs() {
        return refClass != null;
    }

    /** True when the layout of the registry-key handler shape matched. */
    public boolean indexesRegistryKeyHandlers() {
        return keyedClass != null;
    }

    @Override
    public Object classify(Object handler) {
        if (handler == null) {
            return HolderDispatchIndex.ALWAYS;
        }
        Class<?> type = handler.getClass();
        try {
            if (type == refClass) {
                // accept(): registry == null || !filter.test(registry.getRegistryName()) -> return
                Object registry = refRegistry.get(handler);
                if (registry instanceof ForgeRegistry<?> forgeRegistry) {
                    ResourceLocation key = forgeRegistry.getRegistryName();
                    return key == null ? HolderDispatchIndex.ALWAYS : key;
                }
                return HolderDispatchIndex.ALWAYS;
            }
            if (type == keyedClass) {
                ResourceLocation key = (ResourceLocation) keyedName.get(handler);
                if (key == null) {
                    return HolderDispatchIndex.ALWAYS;
                }
                // accept(): invalidRegistry -> return. Then, unless the registry is optional or was
                // already confirmed, the registry must exist (one-time check that may throw) before
                // the filter is tested. Only a handler past that one-time step is a pure key match.
                if (keyedInvalid.getBoolean(handler) || keyedExists.getBoolean(handler)) {
                    return key;
                }
                Object outer = keyedOuter.get(handler);
                if (outer == null) {
                    return HolderDispatchIndex.ALWAYS;
                }
                return outerOptional.getBoolean(outer) ? key : HolderDispatchIndex.UNSETTLED;
            }
        } catch (IllegalAccessException | RuntimeException failure) {
            return HolderDispatchIndex.ALWAYS;
        }
        return HolderDispatchIndex.ALWAYS;
    }

    private static boolean fieldsAre(Class<?> type, String... names) {
        Set<String> expected = new TreeSet<>(Set.of(names));
        Set<String> actual = new TreeSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                actual.add(field.getName());
            }
        }
        return expected.equals(actual);
    }

    static String hash(ClassLoader loader, Class<?> type) {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                return "";
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        } catch (IOException | NoSuchAlgorithmException | RuntimeException failure) {
            return "";
        }
    }
}
