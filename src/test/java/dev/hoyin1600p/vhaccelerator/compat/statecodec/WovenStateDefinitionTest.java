package dev.hoyin1600p.vhaccelerator.compat.statecodec;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecWorld.Owner;
import dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecWorld.State;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Runs the real 1.18.2 {@code StateDefinition} constructor with its one {@code appendPropertyCodec} call rewritten
 * to the mixin's handler, the way an instance {@code @Redirect} handler rewrites it, in a class loader of its own. This is not
 * Sponge Mixin; it checks that the hook has the call shape the constructor needs and that the states a
 * redirected constructor creates carry a lazy codec equal to the eager one.
 */
class WovenStateDefinitionTest {
    private static final String DEFINITION = StateCodecLayouts.STATE_DEFINITION.replace('/', '.');
    private static final String HANDLER = "redirect$zzz000$vhaccelerator$lazyAppend";

    /** Loads the three StateDefinition classes itself (the outer one rewritten); everything else comes from the parent. */
    private static final class WovenLoader extends ClassLoader {
        WovenLoader() {
            super(WovenStateDefinitionTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(DEFINITION) || name.startsWith(DEFINITION + "$")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        byte[] bytes = bytes(name);
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        private byte[] bytes(String name) throws ClassNotFoundException {
            try (InputStream stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                byte[] original = stream.readAllBytes();
                return name.equals(DEFINITION) ? weave(original) : original;
            } catch (Exception failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }
    }

    static byte[] weave(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        MethodNode append = node.methods.stream().filter(method -> method.desc.equals(StateCodecLayouts.APPEND_DESC))
                .findFirst().orElseThrow();
        MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, HANDLER,
                StateCodecLayouts.APPEND_DESC, null, null);
        for (int slot = 1; slot <= 4; slot++) handler.instructions.add(new VarInsnNode(Opcodes.ALOAD, slot));
        handler.instructions.add(new InsnNode(Opcodes.ICONST_1));
        handler.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "dev/hoyin1600p/vhaccelerator/compat/statecodec/LazyStateCodec", "append",
                "(Lcom/mojang/serialization/MapCodec;Ljava/util/function/Supplier;Ljava/lang/String;"
                        + "Lnet/minecraft/world/level/block/state/properties/Property;Z)Lcom/mojang/serialization/MapCodec;", false));
        handler.instructions.add(new InsnNode(Opcodes.ARETURN));
        handler.maxStack = 5;
        handler.maxLocals = 5;
        node.methods.add(handler);
        int redirected = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>")) continue;
            MethodInsnNode target = null;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals(append.name) && call.desc.equals(append.desc)) {
                    target = call;
                }
            }
            if (target == null) continue;
            // The glue Mixin emits for an instance handler: save the four arguments, push this, reload them.
            int base = Math.max(method.maxLocals, 10);
            InsnList glue = new InsnList();
            for (int index = 3; index >= 0; index--) glue.add(new VarInsnNode(Opcodes.ASTORE, base + index));
            glue.add(new VarInsnNode(Opcodes.ALOAD, 0));
            for (int index = 0; index < 4; index++) glue.add(new VarInsnNode(Opcodes.ALOAD, base + index));
            glue.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, HANDLER, StateCodecLayouts.APPEND_DESC, false));
            method.instructions.insertBefore(target, glue);
            method.instructions.remove(target);
            redirected++;
        }
        assertEquals(1, redirected);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    @Test
    void the_rewritten_constructor_installs_a_lazy_codec_equal_to_the_eager_chain() throws Exception {
        WovenLoader loader = new WovenLoader();
        Random random = new Random(40_3_11L);
        int lazyCount = 0;
        for (int index = 0; index < 120; index++) {
            List<Property<?>> properties = new ArrayList<>();
            int count = random.nextInt(6);
            for (int p = 0; p < count; p++) {
                String name = "w" + index + "_" + p;
                properties.add(switch (random.nextInt(3)) {
                    case 0 -> BooleanProperty.create(name);
                    case 1 -> IntegerProperty.create(name, 0, 1 + random.nextInt(4));
                    default -> EnumProperty.create(name, LazyStateCodecEqualityTest.Shape.class);
                });
            }
            if (properties.stream().mapToInt(property -> property.getPossibleValues().size()).reduce(1, (a, b) -> a * b) > 400) {
                properties = properties.subList(0, Math.min(2, properties.size()));
            }
            StateCodecWorld eagerWorld = StateCodecWorld.build("test:woven_" + index, properties, false);

            // The woven constructor needs its owner's default state for the supplier; build the owner around the proxy.
            Owner owner = new Owner("test:woven_" + index);
            Object[] woven = wovenWithOwner(loader, owner, properties);
            MapCodec<State> installed = (MapCodec<State>) woven[1];
            if (properties.isEmpty()) {
                assertFalse(installed instanceof LazyStateCodec, "no properties, nothing to defer");
            } else {
                assertTrue(installed instanceof LazyStateCodec, "the redirected constructor installs the lazy codec");
                assertFalse(((LazyStateCodec<State>) installed).isBuilt(), "nothing is built by the constructor");
                lazyCount++;
            }
            List<State> wovenStates = (List<State>) woven[2];
            List<State> eagerStates = eagerWorld.definition.getPossibleStates();
            assertEquals(eagerStates.size(), wovenStates.size());
            for (int state = 0; state < eagerStates.size(); state++) {
                assertEquals(eagerStates.get(state).toString(), wovenStates.get(state).toString());
                Tag eager = eagerWorld.vanilla.encode(eagerStates.get(state), NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
                        .build(NbtOps.INSTANCE.empty()).result().orElseThrow();
                Tag lazy = installed.encode(wovenStates.get(state), NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
                        .build(NbtOps.INSTANCE.empty()).result().orElseThrow();
                assertEquals(eager, lazy);
                DataResult<State> decoded = installed.compressedDecode(NbtOps.INSTANCE, lazy);
                assertSame(wovenStates.get(state), decoded.result().orElseThrow());
            }
            CompoundTag unknown = new CompoundTag();
            unknown.putString("not_a_property", "x");
            assertEquals(LazyStateCodecEqualityTest.describe(eagerWorld.vanilla.compressedDecode(NbtOps.INSTANCE, unknown))
                            .replace(eagerStates.get(0).toString(), "STATE"),
                    LazyStateCodecEqualityTest.describe(installed.compressedDecode(NbtOps.INSTANCE, unknown))
                            .replace(wovenStates.get(0).toString(), "STATE"));
        }
        assertTrue(lazyCount > 80);
    }

    /** As {@link #wovenDefinition} but wires the owner's holder through reflection on the woven class. */
    @SuppressWarnings("unchecked")
    private static Object[] wovenWithOwner(WovenLoader loader, Owner owner, List<Property<?>> properties) throws Exception {
        Class<?> builderClass = loader.loadClass(DEFINITION + "$Builder");
        Class<?> factoryClass = loader.loadClass(DEFINITION + "$Factory");
        Object builder = builderClass.getConstructor(Object.class).newInstance(owner);
        builderClass.getMethod("add", Property[].class).invoke(builder, (Object) properties.toArray(new Property<?>[0]));
        MapCodec<State>[] seen = new MapCodec[1];
        Object[] definitionHolder = new Object[1];
        // The supplier captured by the constructor calls Owner::defaultState, which reads owner.definition; the
        // woven definition is another class, so give the owner a view of its first state instead.
        State[] first = new State[1];
        Object factory = Proxy.newProxyInstance(loader, new Class<?>[]{factoryClass}, (proxy, method, args) -> {
            seen[0] = (MapCodec<State>) args[2];
            State state = new State((Owner) args[0], (ImmutableMap<Property<?>, Comparable<?>>) args[1], (MapCodec<State>) args[2]);
            if (first[0] == null) first[0] = state;
            return state;
        });
        Function<Owner, State> defaults = ignored -> first[0];
        Object definition = builderClass.getMethod("create", Function.class, factoryClass).invoke(builder, defaults, factory);
        List<State> states = (List<State>) definition.getClass().getMethod("getPossibleStates").invoke(definition);
        return new Object[]{definition, seen[0], states};
    }
}
