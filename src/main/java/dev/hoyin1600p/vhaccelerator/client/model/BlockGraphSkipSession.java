package dev.hoyin1600p.vhaccelerator.client.model;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.BlockGraphManifest;
import dev.hoyin1600p.vhaccelerator.client.cache.ClientAssetFingerprint;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Warm-launch skipping of whole block-state graphs (dynamic model stage S1).
 *
 * <p>On a cold launch, every eligible block whose every state is a plain
 * vanilla {@code MultiVariant}/{@code MultiPart} graph over ordinary JSON
 * models, with only block-atlas textures, is certified in a
 * {@link BlockGraphManifest} together with its model groups. On the next
 * initial launch with the same client asset fingerprint, those blocks'
 * {@code loadTopLevel} calls are skipped: their blockstate files and models
 * are not read or parsed. Their manifest textures are still stitched before
 * the atlas is built, their model groups are restored with vanilla's exact
 * equivalence classes, and their keys join the deferred block-state
 * registry. A skipped block's graph loads on the single
 * {@link ModelGraphLoader} thread the first time one of its states is
 * needed, is checked against the stitched textures, and then bakes.</p>
 *
 * <p>Anything doubtful keeps the eager path: no or stale manifest, Compare
 * Mode, CTM or ModernFix dynamic resources, a later reload, a block whose
 * state count changed, or a block any of whose states is already loaded. If
 * deferral turns out unavailable at bake time, every skipped graph is loaded
 * before the bake loop.</p>
 */
public final class BlockGraphSkipSession {
    private static final int MAX_WARNINGS = 16;
    private static volatile BlockGraphSkipSession current;

    private final ResourceManager resourceManager;
    private final CompletableFuture<BlockGraphManifest.Manifest> pending;
    private final Set<String> skippedBlocks = new LinkedHashSet<>();
    private Set<ResourceLocation> skippedKeys = new LinkedHashSet<>();
    private final Map<String, Set<String>> blockTextures = new HashMap<>();
    private final AtomicInteger warnings = new AtomicInteger();
    private final AtomicInteger firstUseLoads = new AtomicInteger();
    private final AtomicBoolean invalidated = new AtomicBoolean();
    private BlockGraphManifest.Manifest manifest;
    private String fingerprint;
    private boolean resolved;
    private boolean warm;
    private String lastNamespace;
    private String lastPath;
    private boolean lastDecision;
    private volatile boolean skipsClosed;
    private volatile boolean materialsAdded;
    private volatile boolean loadingClosed;

    /** Receives restored model groups; implemented by the bakery. */
    public interface GroupSink {
        void vhaccelerator$setModelGroup(BlockState state, int group);

        void vhaccelerator$registerModelGroup(List<BlockState> states);
    }

    private BlockGraphSkipSession(
            ResourceManager resourceManager,
            CompletableFuture<BlockGraphManifest.Manifest> pending
    ) {
        this.resourceManager = resourceManager;
        this.pending = pending;
    }

    /** Starts a session for the initial model load, or returns null. */
    public static BlockGraphSkipSession begin(ResourceManager resourceManager) {
        if (resourceManager == null
                || LaunchTimer.isFinished()
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || VHAcceleratorConfig.compareModeEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.skipBlockStateGraphLoading)
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.deferBlockStateModelBaking)
                || !DeferredModelCompatibility.allowsBlockStateDeferral()) {
            return null;
        }
        return new BlockGraphSkipSession(resourceManager, BlockGraphManifest.readAsync());
    }

    /**
     * Makes this session the live generation once its deferred registry is
     * installed. That happens in {@code ModelManager#apply} after the
     * previous generation was retired, so retirement and the reload-start
     * hook only ever close an older session, never the one being applied.
     * A launch may run several client reloads, each with its own session.
     */
    public void activate() {
        current = this;
    }

    /** A session with no manifest, for lifecycle tests. */
    static BlockGraphSkipSession forTest() {
        return new BlockGraphSkipSession(null, CompletableFuture.completedFuture(null));
    }

    boolean loadingClosed() {
        return loadingClosed;
    }

    /**
     * A client resource reload is starting and is about to close the packs
     * this generation reads; later first uses show the missing model until
     * the reload rebuilds every chunk.
     */
    public static void closeLoadingForReload() {
        BlockGraphSkipSession session = current;
        if (session != null) {
            session.loadingClosed = true;
        }
    }

    /** Debug: whether the current session skipped this key's block. */
    static boolean wasSkippedBlock(ResourceLocation location) {
        BlockGraphSkipSession session = current;
        return session != null
                && session.skippedBlocks.contains(location.getNamespace() + ":" + location.getPath());
    }

    /** Debug: first-use graph loads by the first caller frame outside the model stack. */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>
            callers = new java.util.concurrent.ConcurrentHashMap<>();

    private void recordCaller() {
        String caller = StackWalker.getInstance().walk(frames -> frames
                .map(frame -> frame.getClassName() + "." + frame.getMethodName())
                .filter(name -> !name.startsWith("dev.hoyin1600p.")
                        && !name.startsWith("java.")
                        && !name.startsWith("jdk.")
                        && !name.startsWith("com.google.")
                        && !name.startsWith("it.unimi.")
                        && !name.startsWith("net.minecraft.client.resources.model.")
                        && !name.startsWith("net.minecraftforge.client.model."))
                .limit(3)
                .reduce((a, b) -> a + " <- " + b)
                .orElse("?"));
        callers.computeIfAbsent(Thread.currentThread().getName().replaceAll("-?\\d+$", "")
                + " | " + caller, key -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
    }

    /** Debug: skipped blocks and first-use graph loads so far, with top callers. */
    static String describeCurrent() {
        BlockGraphSkipSession session = current;
        if (session == null) {
            return "no skip session";
        }
        StringBuilder top = new StringBuilder();
        session.callers.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                .limit(6)
                .forEach(entry -> top.append("\n      caller ").append(entry.getValue().get())
                        .append("  ").append(entry.getKey()));
        return session.skippedBlocks.size() + " skipped blocks, " + session.firstUseLoads.get()
                + " loaded on first use" + top;
    }

    /** Stops first-use loading once the next reload retires this generation. */
    public static void closeCurrent() {
        BlockGraphSkipSession session = current;
        current = null;
        if (session != null) {
            session.loadingClosed = true;
            if (!session.skippedBlocks.isEmpty()) {
                VHAccelerator.LOGGER.info(
                        "Retired skipped block-state graphs: {} of {} skipped blocks were loaded on first use",
                        session.firstUseLoads.get(), session.skippedBlocks.size());
            }
        }
    }

    private boolean resolve() {
        if (resolved) {
            return warm;
        }
        resolved = true;
        try {
            manifest = pending.join();
        } catch (RuntimeException failure) {
            manifest = null;
        }
        fingerprint = ClientAssetFingerprint.current(resourceManager);
        if (fingerprint == null) {
            return false;
        }
        if (manifest == null) {
            VHAccelerator.LOGGER.info("No block graph manifest; loading every block-state graph and recording one");
            return false;
        }
        if (!manifest.matches(fingerprint)) {
            ClientAssetFingerprint.reportMismatch("Block graph", manifest.fingerprint(), fingerprint);
            return false;
        }
        warm = true;
        VHAccelerator.LOGGER.info("Validated block graph manifest: {} certified blocks, {} textures",
                manifest.blocks().size(), manifest.textureCount());
        return true;
    }

    /** True when {@code loadTopLevel} may be skipped for this block-state key. */
    public boolean skip(
            ModelResourceLocation location,
            Map<ResourceLocation, UnbakedModel> topLevelModels,
            Map<ResourceLocation, UnbakedModel> unbakedCache
    ) {
        if (location == null || skipsClosed || loadingClosed
                || !DeferredBlockStateBaking.eligibleKey(location) || !resolve()) {
            return false;
        }
        String namespace = location.getNamespace();
        String path = location.getPath();
        // Vanilla loads every state of one block consecutively.
        if (!namespace.equals(lastNamespace) || !path.equals(lastPath)) {
            lastNamespace = namespace;
            lastPath = path;
            lastDecision = decideBlock(namespace, path, location, unbakedCache);
        }
        if (!lastDecision || topLevelModels.containsKey(location) || unbakedCache.containsKey(location)) {
            return false;
        }
        skippedKeys.add(location);
        return true;
    }

    private boolean decideBlock(
            String namespace,
            String path,
            ModelResourceLocation first,
            Map<ResourceLocation, UnbakedModel> unbakedCache
    ) {
        String blockId = namespace + ":" + path;
        BlockGraphManifest.Block certified = manifest.block(blockId);
        if (certified == null || unbakedCache.containsKey(first)) {
            return false;
        }
        ResourceLocation id = new ResourceLocation(namespace, path);
        if (!Registry.BLOCK.containsKey(id)) {
            return false;
        }
        Block block = Registry.BLOCK.get(id);
        if (block.getStateDefinition().getPossibleStates().size() != certified.states()) {
            return false;
        }
        skippedBlocks.add(blockId);
        return true;
    }

    /**
     * Called once by material collection, before atlas stitching: closes
     * skipping, restores the skipped blocks' model groups and returns a node
     * that contributes their certified textures, or null when nothing was
     * skipped.
     */
    public UnbakedModel materialNode(GroupSink groups) {
        skipsClosed = true;
        materialsAdded = true;
        if (skippedBlocks.isEmpty()) {
            return null;
        }
        Set<Material> union = new LinkedHashSet<>();
        Map<Integer, ResourceLocation> textures = new HashMap<>();
        for (String blockId : skippedBlocks) {
            BlockGraphManifest.Block certified = manifest.block(blockId);
            for (int index : certified.textures()) {
                union.add(new Material(TextureAtlas.LOCATION_BLOCKS, textures.computeIfAbsent(
                        index, ignored -> new ResourceLocation(manifest.texture(index)))));
            }
            List<BlockState> states = Registry.BLOCK.get(new ResourceLocation(blockId))
                    .getStateDefinition().getPossibleStates();
            restoreGroups(certified.groups(), states, groups::vhaccelerator$setModelGroup,
                    groups::vhaccelerator$registerModelGroup);
        }
        VHAccelerator.LOGGER.info(
                "Skipped loading {} block-state graphs of {} certified blocks; added {} manifest textures "
                        + "before atlas stitching",
                skippedKeys.size(), skippedBlocks.size(), union.size());
        return new ManifestMaterials(List.copyOf(union));
    }

    /**
     * Applies certified group labels to a block's states, reproducing
     * vanilla's equivalence classes: non-{@code MODEL} states get 0 and each
     * positive label with more than one state becomes one registered group.
     */
    static <S> void restoreGroups(
            int[] labels,
            List<S> states,
            BiConsumer<S, Integer> setGroup,
            Consumer<List<S>> registerGroup
    ) {
        Map<Integer, List<S>> byLabel = new java.util.LinkedHashMap<>();
        for (int index = 0; index < labels.length && index < states.size(); index++) {
            int label = labels[index];
            if (label == BlockGraphManifest.NON_MODEL_GROUP) {
                setGroup.accept(states.get(index), 0);
            } else if (label > BlockGraphManifest.NON_MODEL_GROUP) {
                byLabel.computeIfAbsent(label, ignored -> new ArrayList<>()).add(states.get(index));
            }
        }
        for (List<S> group : byLabel.values()) {
            if (group.size() > 1) {
                registerGroup.accept(group);
            }
        }
    }

    public boolean materialsAdded() {
        return materialsAdded;
    }

    public Set<ResourceLocation> skippedKeys() {
        return Collections.unmodifiableSet(skippedKeys);
    }

    /** Skipped blocks whose whole graph finished loading through {@link #loadAndBake}. */
    private final Set<String> completedBlocks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Whether a deferred key still needs its skipped block's graph loaded:
     * its block was skipped and no first-use load of it has completed. A
     * block's state keys enter the unbaked cache before their child models
     * finish loading, so "key present" is not "graph loaded"; completion is
     * recorded only after the loader-thread load returns. Needs no per-key
     * set, so it keeps working after {@link #releaseSkippedKeys()}.
     */
    public boolean needsGraph(ResourceLocation location) {
        String blockId = location.getNamespace() + ":" + location.getPath();
        return skippedBlocks.contains(blockId) && !completedBlocks.contains(blockId);
    }

    /**
     * Drops the per-key skip set once the deferred registry is installed; the
     * registry holds the keys and {@link #needsGraph} answers from the much
     * smaller per-block set. Replaced, not cleared, so the table is freed.
     */
    public void releaseSkippedKeys() {
        skippedKeys = new LinkedHashSet<>();
    }

    public boolean isSkipped(ResourceLocation location) {
        return skippedKeys.contains(location);
    }

    /**
     * Fallback when deferral is unavailable at bake time: loads every skipped
     * graph now, exactly as {@code loadTopLevel} would have.
     */
    public void restoreEagerly(
            Map<ResourceLocation, UnbakedModel> topLevelModels,
            Function<ResourceLocation, UnbakedModel> getter
    ) {
        if (skippedKeys.isEmpty()) {
            return;
        }
        VHAccelerator.LOGGER.warn(
                "Deferred block-state baking is unavailable at bake time; loading {} skipped block-state "
                        + "graphs eagerly", skippedKeys.size());
        for (ResourceLocation location : skippedKeys) {
            UnbakedModel model = getter.apply(location);
            topLevelModels.put(location, model);
            model.getMaterials(getter, new HashSet<>());
        }
        skippedKeys.clear();
        skippedBlocks.clear();
    }

    /**
     * First use of a skipped key: loads its block's graph on the loader
     * thread, checks its textures were stitched, then bakes through
     * {@code baker}. Null (the missing model) once a reload retired this
     * generation or when the graph no longer matches its certification.
     */
    public BakedModel loadAndBake(
            ResourceLocation location,
            Function<ResourceLocation, UnbakedModel> getter,
            Map<ResourceLocation, UnbakedModel> unbakedCache,
            Function<ResourceLocation, BakedModel> baker
    ) {
        if (loadingClosed) {
            return null;
        }
        if (dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            recordCaller();
        }
        // The whole block counts as loaded afterwards (needsGraph), so every
        // one of its states is loaded and parent-bound here, as vanilla's
        // loadTopLevel + getMaterials would have done. Binding only the
        // requested state left the block's other variant models (a log's
        // horizontal model, a slab's top/double models) unbound; they then
        // baked without their parent's elements: zero quads, an invisible
        // block, and no error.
        boolean covered = ModelGraphLoader.call(() -> {
            boolean all = true;
            for (ResourceLocation key : blockStateKeys(location)) {
                UnbakedModel model = getter.apply(key);
                Collection<Material> materials = model.getMaterials(getter, new HashSet<>());
                boolean plain = DeferredBlockStateBaking.select(Map.of(key, model), unbakedCache)
                        .contains(key);
                all &= verify(key, plain, materials);
            }
            return all;
        });
        if (!covered) {
            return null;
        }
        firstUseLoads.incrementAndGet();
        completedBlocks.add(location.getNamespace() + ":" + location.getPath());
        return baker.apply(location);
    }

    /**
     * Every state key of {@code location}'s block, the requested key first;
     * just that key if the block cannot be resolved.
     */
    static List<ResourceLocation> blockStateKeys(ResourceLocation location) {
        List<ResourceLocation> keys = new ArrayList<>();
        keys.add(location);
        ResourceLocation blockId = new ResourceLocation(location.getNamespace(), location.getPath());
        Block block = Registry.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) {
            return keys;
        }
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation key = BlockModelShaper.stateToModelLocation(blockId, state);
            if (!key.equals(location)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private boolean verify(ResourceLocation location, boolean plain, Collection<Material> materials) {
        String blockId = location.getNamespace() + ":" + location.getPath();
        BlockGraphManifest.Block certified = manifest.block(blockId);
        Set<String> stitched;
        synchronized (blockTextures) {
            stitched = blockTextures.computeIfAbsent(blockId, ignored -> {
                Set<String> set = new HashSet<>();
                if (certified != null) {
                    for (int index : certified.textures()) {
                        set.add(manifest.texture(index));
                    }
                }
                return set;
            });
        }
        boolean covered = certified != null && plain;
        if (covered) {
            for (Material material : materials) {
                if (!TextureAtlas.LOCATION_BLOCKS.equals(material.atlasLocation())
                        || !stitched.contains(material.texture().toString())) {
                    covered = false;
                    break;
                }
            }
        }
        if (covered) {
            return true;
        }
        if (invalidated.compareAndSet(false, true)) {
            BlockGraphManifest.invalidateAsync();
        }
        if (warnings.getAndIncrement() < MAX_WARNINGS) {
            VHAccelerator.LOGGER.warn(
                    "Skipped block-state graph {} no longer matches its certification; showing the missing "
                            + "model and discarding the block graph manifest so the next launch re-records it",
                    location);
        }
        return false;
    }

    /**
     * On a launch that skipped nothing, certifies every eligible block whose
     * every state is a plain graph with complete block-atlas textures, and
     * writes the manifest asynchronously for the next launch.
     */
    public void recordIfCold(
            Map<ResourceLocation, UnbakedModel> topLevelModels,
            Map<ResourceLocation, UnbakedModel> unbakedCache,
            Function<ResourceLocation, UnbakedModel> getter,
            Object2IntMap<BlockState> modelGroups
    ) {
        skipsClosed = true;
        resolve();
        if (warm || fingerprint == null || loadingClosed || modelGroups == null
                || modelGroups.defaultReturnValue() != BlockGraphManifest.UNGROUPED) {
            return;
        }
        long started = System.nanoTime();
        Set<ResourceLocation> plain = DeferredBlockStateBaking.select(topLevelModels, unbakedCache);
        Map<UnbakedModel, List<String>> texturesByModel = new IdentityHashMap<>();
        BlockGraphManifest.Builder builder = new BlockGraphManifest.Builder();
        int eligibleBlocks = 0;
        for (Block block : Registry.BLOCK) {
            ResourceLocation id = Registry.BLOCK.getKey(block);
            List<BlockState> states = block.getStateDefinition().getPossibleStates();
            if (states.isEmpty()
                    || !DeferredBlockStateBaking.eligibleKey(BlockModelShaper.stateToModelLocation(states.get(0)))) {
                continue;
            }
            eligibleBlocks++;
            int[] groups = new int[states.size()];
            Set<String> textures = new LinkedHashSet<>();
            boolean certified = true;
            for (int index = 0; index < states.size() && certified; index++) {
                BlockState state = states.get(index);
                ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                UnbakedModel model = topLevelModels.get(key);
                if (model == null || !plain.contains(key)) {
                    certified = false;
                    break;
                }
                List<String> modelTextures = texturesByModel.computeIfAbsent(model, unbaked -> {
                    try {
                        List<String> collected = new ArrayList<>();
                        for (Material material : unbaked.getMaterials(getter, new HashSet<>())) {
                            if (!TextureAtlas.LOCATION_BLOCKS.equals(material.atlasLocation())
                                    || material.texture().equals(net.minecraft.client.renderer.texture
                                            .MissingTextureAtlasSprite.getLocation())) {
                                return null;
                            }
                            if (dev.hoyin1600p.vhaccelerator.client.compat.ctm.CtmModelBakeOptimizer.textureUsesCtm(material.texture())) {
                                // CTM wraps it in the bake event, which needs its graph.
                                return null;
                            }
                            collected.add(material.texture().toString());
                        }
                        return collected;
                    } catch (RuntimeException | LinkageError failure) {
                        return null;
                    }
                });
                if (modelTextures == null) {
                    certified = false;
                    break;
                }
                textures.addAll(modelTextures);
                groups[index] = modelGroups.getInt(state);
            }
            if (certified) {
                builder.add(id.toString(), groups, new ArrayList<>(textures));
            }
        }
        BlockGraphManifest.Manifest built = builder.build(fingerprint);
        VHAccelerator.LOGGER.info(
                "Certified {} of {} eligible blocks for warm block-state graph skipping in {} ms",
                builder.size(), eligibleBlocks, (System.nanoTime() - started) / 1_000_000L);
        if (built != null) {
            BlockGraphManifest.writeAsync(built);
        }
    }

    /** Contributes certified textures to the stitch set; never baked. */
    private static final class ManifestMaterials implements UnbakedModel {
        private final List<Material> materials;

        private ManifestMaterials(List<Material> materials) {
            this.materials = materials;
        }

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return List.of();
        }

        @Override
        public Collection<Material> getMaterials(
                Function<ResourceLocation, UnbakedModel> modelGetter,
                Set<Pair<String, String>> missingTextures
        ) {
            return materials;
        }

        @Override
        public BakedModel bake(
                ModelBakery bakery,
                Function<Material, TextureAtlasSprite> spriteGetter,
                ModelState state,
                ResourceLocation location
        ) {
            return null;
        }
    }
}
