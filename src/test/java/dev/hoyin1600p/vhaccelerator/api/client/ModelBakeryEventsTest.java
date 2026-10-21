package dev.hoyin1600p.vhaccelerator.api.client;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.datafixers.util.Pair;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class ModelBakeryEventsTest {
    private static final ResourceLocation BLOCK = new ResourceLocation("buildscape", "family/slab");
    private static final ResourceLocation MODEL = new ResourceLocation("buildscape", "block/family/slab");

    private static BlockModelDefinition definition() {
        return new BlockModelDefinition(Map.of(), null);
    }

    private static final class Resources implements ResourceManager {
        private String override;
        public Set<String> getNamespaces() { return Set.of("buildscape", "minecraft"); }
        public boolean hasResource(ResourceLocation location) { return override != null; }
        public Resource getResource(ResourceLocation location) throws IOException {
            if (override == null) throw new FileNotFoundException(location.toString());
            return (Resource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Resource.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getSourceName" -> override;
                        case "getLocation" -> location;
                        case "getInputStream" -> new ByteArrayInputStream(new byte[0]);
                        default -> null;
                    });
        }
        public List<Resource> getResources(ResourceLocation location) throws IOException {
            return List.of(getResource(location));
        }
        public Collection<ResourceLocation> listResources(String path, Predicate<String> filter) { return List.of(); }
        public Stream<PackResources> listPacks() {
            return Stream.of("lower", "bundle", "higher").map(name -> (PackResources) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{PackResources.class},
                    (proxy, method, args) -> method.getName().equals("getName") ? name : null));
        }
    }

    @Test
    void rejectsInvalidVersionNamespaceAndDuplicateWithoutReplacingFirstListener() {
        var registry = new ModelBakeryEvents.Registry();
        var listener = new ModelBakeryEvents.Listener() { };
        assertEquals(ModelBakeryEvents.Registration.UNSUPPORTED_VERSION, registry.register("buildscape", 2, listener));
        assertEquals(ModelBakeryEvents.Registration.INVALID_ARGUMENT, registry.register("BAD NAME", 1, listener));
        assertEquals(ModelBakeryEvents.Registration.INVALID_ARGUMENT, registry.register(null, 1, listener));
        assertEquals(ModelBakeryEvents.Registration.INVALID_ARGUMENT, registry.register("buildscape", 1, null));
        assertEquals(ModelBakeryEvents.Registration.REGISTERED, registry.register("buildscape", 1, listener));
        assertEquals(ModelBakeryEvents.Registration.DUPLICATE_NAMESPACE, registry.register("buildscape", 1, listener));
    }

    @Test
    void eachReloadSnapshotsListenersAndClosesOnce() {
        var registry = new ModelBakeryEvents.Registry();
        var first = registry.begin(new Resources());
        AtomicInteger callbacks = new AtomicInteger();
        registry.register("buildscape", 1, new ModelBakeryEvents.Listener() {
            public void beforePreparation(ResourceManager resources) { callbacks.incrementAndGet(); }
            public void afterBaking(ResourceManager resources) { callbacks.incrementAndGet(); }
        });
        first.afterBaking();
        assertEquals(0, callbacks.get());
        var next = registry.begin(new Resources());
        next.afterBaking();
        next.afterBaking();
        next.beforeBaking();
        assertEquals(2, callbacks.get());
    }

    @Test
    void nativeDefinitionNeedsNoLooseJsonAndMergesBetweenResourcePacks() {
        var registry = new ModelBakeryEvents.Registry();
        AtomicInteger reads = new AtomicInteger();
        var nativeDefinition = definition();
        registry.register("buildscape", 1, new ModelBakeryEvents.Listener() {
            public ModelBakeryEvents.BlockStateDefinition blockState(ResourceManager resources, ResourceLocation id,
                    StateDefinition<Block, BlockState> states) {
                reads.incrementAndGet();
                return new ModelBakeryEvents.BlockStateDefinition("bundle", nativeDefinition);
            }
        });
        Resources resources = new Resources();
        var session = registry.begin(resources);
        assertThrows(FileNotFoundException.class, () -> resources.getResources(BLOCK));
        assertTrue(session.prepareBlockState(BLOCK, null));
        assertTrue(session.prepareBlockState(BLOCK, null));
        var lower = Pair.of("lower", definition());
        var higher = Pair.of("higher", definition());
        var original = List.of(lower, higher);
        var merged = session.mergeBlockState(BLOCK, original);
        assertEquals(List.of("lower", "bundle", "higher"), merged.stream().map(Pair::getFirst).toList());
        assertSame(nativeDefinition, merged.get(1).getSecond());
        assertEquals(2, original.size());
        assertEquals(1, reads.get());
        assertFalse(session.prepareBlockState(new ResourceLocation("minecraft", "stone"), null));
        var replacement = session.mergeBlockState(BLOCK, List.of(lower, Pair.of("bundle", definition()), higher));
        assertEquals(3, replacement.size());
        assertSame(nativeDefinition, replacement.get(1).getSecond());
        var unknownOverride = Pair.of("external-source", definition());
        assertEquals(List.of("bundle", "external-source"), session.mergeBlockState(BLOCK, List.of(unknownOverride))
                .stream().map(Pair::getFirst).toList());
    }

    @Test
    void unknownPackIsQuarantinedAndNeverTurnsAMissingResourceIntoSuccess() {
        var registry = new ModelBakeryEvents.Registry();
        AtomicInteger reads = new AtomicInteger();
        registry.register("buildscape", 1, new ModelBakeryEvents.Listener() {
            public ModelBakeryEvents.Model model(ResourceManager resources, ResourceLocation id) {
                reads.incrementAndGet();
                return new ModelBakeryEvents.Model("not-active", BlockModel.fromString("{}"));
            }
        });
        var session = registry.begin(new Resources());
        assertNull(session.model(MODEL));
        assertNull(session.model(MODEL));
        assertEquals(1, reads.get());
        assertEquals(List.of(), session.mergeBlockState(BLOCK, List.of()));
    }

    @Test
    void higherPriorityLooseModelWinsAndUnrelatedNamespacesDoNotInvokeProvider() {
        var registry = new ModelBakeryEvents.Registry();
        AtomicInteger calls = new AtomicInteger();
        var model = BlockModel.fromString("{}");
        registry.register("buildscape", 1, new ModelBakeryEvents.Listener() {
            public ModelBakeryEvents.Model model(ResourceManager resources, ResourceLocation id) {
                calls.incrementAndGet();
                return new ModelBakeryEvents.Model("bundle", model);
            }
        });
        Resources resources = new Resources();
        resources.override = "higher";
        var session = registry.begin(resources);
        assertNull(session.model(MODEL));
        resources.override = "lower";
        assertSame(model, session.model(MODEL));
        assertNull(session.model(new ResourceLocation("minecraft", "block/stone")));
        assertEquals(2, calls.get());
    }

    @Test
    void checkedRuntimeLinkageAndAssertionFailuresAreContainedAndRetriedNextReload() {
        for (Throwable failure : List.of(new IOException("read"), new IllegalStateException("provider"),
                new NoClassDefFoundError("optional integration"), new AssertionError("provider assertion"))) {
            var registry = new ModelBakeryEvents.Registry();
            AtomicInteger calls = new AtomicInteger();
            registry.register("buildscape", 1, new ModelBakeryEvents.Listener() {
                public ModelBakeryEvents.Model model(ResourceManager resources, ResourceLocation id) throws Exception {
                    calls.incrementAndGet();
                    if (failure instanceof Exception exception) throw exception;
                    throw (Error) failure;
                }
            });
            var session = registry.begin(new Resources());
            assertDoesNotThrow(() -> session.model(MODEL));
            assertNull(session.model(MODEL));
            assertEquals(1, calls.get());
            assertNull(registry.begin(new Resources()).model(MODEL));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void recursiveCallbackFallsBackWithoutStackOverflow() {
        var registry = new ModelBakeryEvents.Registry();
        ModelBakeryEvents.Session[] session = new ModelBakeryEvents.Session[1];
        registry.register("buildscape", 1, new ModelBakeryEvents.Listener() {
            public ModelBakeryEvents.Model model(ResourceManager resources, ResourceLocation id) {
                session[0].model(id);
                return null;
            }
        });
        session[0] = registry.begin(new Resources());
        assertNull(session[0].model(MODEL));
    }

    @Test
    void failureDuringStartDoesNotDisableOtherProviders() {
        var registry = new ModelBakeryEvents.Registry();
        AtomicInteger healthy = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        registry.register("broken", 1, new ModelBakeryEvents.Listener() {
            public void beforePreparation(ResourceManager resources) { throw new IllegalStateException("setup"); }
            public void afterBaking(ResourceManager resources) { cleanups.incrementAndGet(); }
        });
        registry.register("healthy", 1, new ModelBakeryEvents.Listener() {
            public void beforePreparation(ResourceManager resources) { healthy.incrementAndGet(); }
            public void afterPreparation(ResourceManager resources) { healthy.incrementAndGet(); }
        });
        var session = registry.begin(new Resources());
        session.afterPreparation();
        session.afterBaking();
        assertEquals(2, healthy.get());
        assertEquals(1, cleanups.get());
    }

    @Test
    void concurrentVariantsResolveOneNativeDefinitionPerReload() throws Exception {
        var registry = new ModelBakeryEvents.Registry();
        AtomicInteger reads = new AtomicInteger();
        registry.register("buildscape", 1, new ModelBakeryEvents.Listener() {
            public ModelBakeryEvents.BlockStateDefinition blockState(ResourceManager resources, ResourceLocation id,
                    StateDefinition<Block, BlockState> states) {
                reads.incrementAndGet();
                return new ModelBakeryEvents.BlockStateDefinition("bundle", definition());
            }
        });
        var session = registry.begin(new Resources());
        var workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> queries = IntStream.range(0, 32)
                    .mapToObj(index -> (Callable<Boolean>) () -> session.prepareBlockState(BLOCK, null)).toList();
            for (var result : workers.invokeAll(queries)) assertTrue(result.get());
        } finally {
            workers.shutdownNow();
        }
        assertEquals(1, reads.get());
    }

    @Test
    void nativeMergeTargetsTheCollectedDefinitionListInMinecraft1182() throws IOException {
        ClassNode target = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "net/minecraft/client/resources/model/ModelBakery.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(target, 0);
        }
        var loadModel = target.methods.stream().filter(method -> method.name.equals("loadModel")).findFirst().orElseThrow();
        var listSlots = loadModel.localVariables.stream().filter(local -> local.desc.equals("Ljava/util/List;"))
                .map(local -> local.index).distinct().sorted().toList();
        int collectors = 0;
        for (var instruction : loadModel.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/stream/Stream")
                    && call.name.equals("collect")) {
                var store = call.getNext();
                while (store != null && !(store instanceof VarInsnNode)) store = store.getNext();
                assertNotNull(store);
                assertEquals(Opcodes.ASTORE, store.getOpcode());
                assertEquals(listSlots.get(1).intValue(), ((VarInsnNode) store).var,
                        "@ModifyVariable ordinal 1 must be the collected blockstate definitions, not coloring properties");
                collectors++;
            }
        }
        assertEquals(1, collectors);
    }

    @Test
    void legacyRedirectIsOptionalAndLowerPriorityThanBuildscape() throws IOException {
        ClassNode mixin = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "dev/hoyin1600p/vhaccelerator/mixin/client/ModelBakeryBlockStateMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(mixin, 0);
        }
        var metadata = mixin.invisibleAnnotations.stream()
                .filter(annotation -> annotation.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        assertEquals(500, metadata.values.get(metadata.values.indexOf("priority") + 1));
        var redirect = mixin.methods.stream().filter(method -> method.name.equals("vhaccelerator$usePreparedBlockStates"))
                .findFirst().orElseThrow().visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        assertEquals(0, redirect.values.get(redirect.values.indexOf("require") + 1));
    }
}
