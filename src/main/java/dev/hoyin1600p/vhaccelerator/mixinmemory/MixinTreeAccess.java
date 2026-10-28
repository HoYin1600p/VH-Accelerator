package dev.hoyin1600p.vhaccelerator.mixinmemory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The few places where VH Accelerator touches Mixin 0.8.5 internals, behind one interface so the
 * release policy can be tested without a Mixin environment.
 */
public interface MixinTreeAccess {
    /** The monitor Mixin holds while it applies mixins to a class; trees are only read inside it. */
    Object lock();

    /** The mixins of every active config. Call while holding {@link #lock()}. */
    List<Object> mixins();

    /** Dotted target class names; empty when none resolved. */
    List<String> targets(Object mixin);

    boolean pseudo(Object mixin);

    /** The tree the mixin keeps, or null when it has no validated state. */
    ClassNode tree(Object mixin);

    void setTree(Object mixin, ClassNode tree);

    /** Opens the live Mixin, or explains in the failure why it must stay untouched. */
    static Result open(String mixinVersion, boolean hotSwap, Object activeTransformer) {
        if (!"0.8.5".equals(mixinVersion)) {
            return Result.failure("Mixin version is " + mixinVersion + ", not the reviewed 0.8.5");
        }
        if (hotSwap) {
            return Result.failure("Mixin hot-swap is enabled");
        }
        if (activeTransformer == null) {
            return Result.failure("Mixin has no active transformer");
        }
        try {
            return Result.success(Reflective.open(activeTransformer));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return Result.failure("Mixin layout differs from 0.8.5: " + exception);
        }
    }

    record Result(MixinTreeAccess access, String failure) {
        static Result success(MixinTreeAccess access) {
            return new Result(access, null);
        }

        static Result failure(String reason) {
            return new Result(null, reason);
        }
    }

    /** Reads and replaces {@code MixinInfo.state.classNode} by reflection after checking every field. */
    final class Reflective implements MixinTreeAccess {
        private static final String PACKAGE = "org.spongepowered.asm.mixin.transformer.";

        private final Object processor;
        private final Field configs;
        private final Field mixinsOfConfig;
        private final Field state;
        private final Field classNode;
        private final Field virtual;
        private final List<?> fixedMixins;

        private Reflective(Object processor, Field configs, Field mixinsOfConfig, Field state, Field classNode,
                           Field virtual, List<?> fixedMixins) {
            this.processor = processor;
            this.configs = configs;
            this.mixinsOfConfig = mixinsOfConfig;
            this.state = state;
            this.classNode = classNode;
            this.virtual = virtual;
            this.fixedMixins = fixedMixins;
        }

        static Reflective open(Object transformer) throws ReflectiveOperationException {
            if (!transformer.getClass().getName().equals(PACKAGE + "MixinTransformer")) {
                throw new ClassNotFoundException("active transformer is " + transformer.getClass().getName());
            }
            ClassLoader loader = transformer.getClass().getClassLoader();
            Class<?> processorClass = Class.forName(PACKAGE + "MixinProcessor", false, loader);
            Class<?> configClass = Class.forName(PACKAGE + "MixinConfig", false, loader);
            Class<?> infoClass = Class.forName(PACKAGE + "MixinInfo", false, loader);
            Class<?> stateClass = Class.forName(PACKAGE + "MixinInfo$State", false, loader);
            Field processorField = field(transformer.getClass(), "processor", processorClass);
            Field configsField = field(processorClass, "configs", List.class);
            Field mixinsField = field(configClass, "mixins", List.class);
            Field stateField = field(infoClass, "state", stateClass);
            Field classNodeField = field(stateClass, "classNode", ClassNode.class);
            Field virtualField = field(infoClass, "virtual", boolean.class);
            if (!IMixinInfo.class.isAssignableFrom(infoClass)) {
                throw new ClassNotFoundException("MixinInfo is not an IMixinInfo");
            }
            Object processor = processorField.get(transformer);
            if (processor == null) {
                throw new IllegalStateException("MixinTransformer.processor is null");
            }
            return new Reflective(processor, configsField, mixinsField, stateField, classNodeField, virtualField, null);
        }

        /** An instance of the real Mixin classes, for tests that supply their own list of mixins. */
        static Reflective forTest(Object processor, List<?> mixins) throws ReflectiveOperationException {
            ClassLoader loader = IMixinInfo.class.getClassLoader();
            Class<?> infoClass = Class.forName(PACKAGE + "MixinInfo", false, loader);
            Class<?> stateClass = Class.forName(PACKAGE + "MixinInfo$State", false, loader);
            return new Reflective(
                    processor,
                    null,
                    null,
                    field(infoClass, "state", stateClass),
                    field(stateClass, "classNode", ClassNode.class),
                    field(infoClass, "virtual", boolean.class),
                    mixins
            );
        }

        private static Field field(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(name);
            if (Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) {
                throw new NoSuchFieldException(owner.getName() + "." + name + " has an unexpected shape");
            }
            field.setAccessible(true);
            return field;
        }

        @Override
        public Object lock() {
            return this.processor;
        }

        @Override
        public List<Object> mixins() {
            List<Object> result = new ArrayList<>();
            if (this.fixedMixins != null) {
                result.addAll(this.fixedMixins);
                return result;
            }
            try {
                for (Object config : (List<?>) this.configs.get(this.processor)) {
                    result.addAll((List<?>) this.mixinsOfConfig.get(config));
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
            return result;
        }

        @Override
        public List<String> targets(Object mixin) {
            List<String> names = new ArrayList<>();
            for (String name : ((IMixinInfo) mixin).getTargetClasses()) {
                names.add(name.replace('/', '.'));
            }
            return names;
        }

        @Override
        public boolean pseudo(Object mixin) {
            try {
                return this.virtual.getBoolean(mixin);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public ClassNode tree(Object mixin) {
            try {
                Object current = this.state.get(mixin);
                return current == null ? null : (ClassNode) this.classNode.get(current);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void setTree(Object mixin, ClassNode tree) {
            try {
                this.classNode.set(this.state.get(mixin), tree);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
