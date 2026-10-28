package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class CommonCompatibilityAuditTest {
    private static final String BASE = "dev/hoyin1600p/vhaccelerator/";

    @Test
    void auditedCommonGroupsHaveNoReachableClientLinkage() throws Exception {
        try (ZipFile ferrite = new ZipFile(Path.of("libs/ferritecore-4.2.2-forge.jar").toFile())) {
            MixinTargetPreflight.ClassLookup lookup = name -> {
                var entry = ferrite.getEntry(name + ".class");
                if (entry == null) return MixinTargetPreflightTest.read(name);
                try (var input = ferrite.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, 0);
                    return node;
                }
            };
            assertEquals(List.of(), CommonCompatibilityLayouts.problems("dev.hoyin1600p.vhaccelerator.mixin.compat.ferritecore", lookup));
            for (String group : List.of("decocraft.BBModelTextureDataMixin", "decocraft.JsonParserModelShareMixin",
                    "ferritecore.StateHolderPropertyMapMixin")) {
                var audit = CommonCompatibilityAudit.inspect(List.of(BASE + "mixin/compat/" + group.replace('.', '/')), lookup);
                assertEquals(List.of(), audit.problems(), group + " " + audit.helperClasses());
                assertTrue(audit.helperClasses().size() > 1);
            }
        }
    }

    @Test
    void craftTweakerRemovalHooksAreCommonSafeAndRequireTheExactRunnerLayout() throws Exception {
        String group = "dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals";
        try (ZipFile craftTweaker = new ZipFile("libs/CraftTweaker-forge-1.18.2-9.1.213.jar")) {
            MixinTargetPreflight.ClassLookup lookup = name -> {
                var entry = craftTweaker.getEntry(name + ".class");
                if (entry == null) return MixinTargetPreflightTest.read(name);
                try (var input = craftTweaker.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, 0);
                    return node;
                }
            };
            assertEquals(List.of(), CommonCompatibilityLayouts.problems(group, lookup));
            for (String hook : List.of("ScriptRunIndexSessionMixin", "ScriptRunManagerOutputContextMixin",
                    "RecipeListIndexedOutputMixin")) {
                String mixin = group + "." + hook;
                assertTrue(CommonCompatibilityLayouts.isCommonMixin(mixin));
                var audit = CommonCompatibilityAudit.inspect(List.of(mixin), lookup);
                assertEquals(List.of(), audit.problems(), mixin + " " + audit.helperClasses());
                assertTrue(audit.helperClasses().size() > 1);
            }
            assertFalse(CommonCompatibilityLayouts.isCommonMixin(group + ".FutureMixin"));
            assertFalse(CommonCompatibilityLayouts.isCommonGroup(group + ".future"));
            String runner = "com/blamejared/crafttweaker/impl/script/scriptrun/ScriptRun";
            ClassNode modified = lookup.find(runner);
            var method = modified.methods.stream().filter(value -> value.name.equals("executeRun")).findFirst().orElseThrow();
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("run")) {
                    method.instructions.remove(call);
                }
            }
            assertTrue(CommonCompatibilityLayouts.problems(group,
                    name -> name.equals(runner) ? modified : lookup.find(name)).stream()
                    .anyMatch(problem -> problem.contains("invocation count")));
            var contract = CommonCompatibilityLayouts.contract(group);
            assertEquals(List.of("9.1.213"), contract.get("supportedVersions"));
            assertEquals("optimizations.indexCraftTweakerRecipeRemovals", contract.get("setting"));
            assertEquals(List.of("CLIENT", "DEDICATED_SERVER"), contract.get("sides"));
        }
    }

    @Test
    void rejectsTransitiveStaticInitializersDescriptorsAndLambdaHandles() {
        ClassNode root = node(BASE + "fixture/Root");
        ClassNode helper = node(BASE + "fixture/Helper");
        MethodNode entry = method("run", "()V");
        entry.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, helper.name, "work", "()V", false));
        root.methods.add(entry);
        MethodNode initializer = method("<clinit>", "()V");
        initializer.instructions.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/client/Minecraft"));
        helper.methods.add(initializer);
        helper.methods.add(method("work", "([Lnet/minecraft/client/multiplayer/ClientLevel;)V"));
        helper.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "generic", "Ljava/util/List;",
                "Ljava/util/List<Lnet/minecraft/client/renderer/LevelRenderer;>;", null));
        MethodNode lambda = method("lambda", "()V");
        lambda.instructions.add(new LdcInsnNode(new Handle(Opcodes.H_INVOKESTATIC,
                "net/minecraftforge/client/ForgeHooksClient", "example", "()V", false)));
        root.methods.add(lambda);
        Map<String, ClassNode> classes = Map.of(root.name, root, helper.name, helper);
        var audit = CommonCompatibilityAudit.inspect(List.of(root.name), classes::get);
        assertTrue(audit.problems().stream().anyMatch(problem -> problem.contains("Minecraft")));
        assertTrue(audit.problems().stream().anyMatch(problem -> problem.contains("ClientLevel")));
        assertTrue(audit.problems().stream().anyMatch(problem -> problem.contains("ForgeHooksClient")));
        assertTrue(audit.problems().stream().anyMatch(problem -> problem.contains("LevelRenderer")));
    }

    @Test
    void rejectsMissingHelpersAndChangedReflectiveFieldLayouts() {
        ClassNode root = node(BASE + "fixture/Root");
        MethodNode entry = method("run", "()V");
        entry.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BASE + "missing/Helper", "work", "()V", false));
        root.methods.add(entry);
        assertFalse(CommonCompatibilityAudit.inspect(List.of(root.name),
                name -> name.equals(root.name) ? root : null).problems().isEmpty());
        Map<String, ClassNode> layouts = new HashMap<>();
        ClassNode model = node("com/razz/decocraft/models/bbmodel/BBModel");
        model.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "textures", "Ljava/util/List;", null, null));
        layouts.put(model.name, model);
        ClassNode texture = node("com/razz/decocraft/models/bbmodel/BBModelParts$Texture");
        for (String field : List.of("source", "path", "relative_path")) {
            texture.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, field, "Ljava/lang/String;", null, null));
        }
        layouts.put(texture.name, texture);
        ClassNode loader = node("com/razz/decocraft/models/bbmodel/BBModelLoader");
        loader.methods.add(method("loadModel", "(Ljava/io/Reader;)Lcom/razz/decocraft/models/bbmodel/BBModel;"));
        layouts.put(loader.name, loader);
        ClassNode parser = node("com/razz/decocraft/utils/JsonParser");
        parser.methods.add(method("parseModel", "(Ljava/lang/String;Ljava/util/zip/ZipFile;)Lcom/razz/decocraft/models/bbmodel/BBModel;"));
        layouts.put(parser.name, parser);
        assertEquals(List.of(), CommonCompatibilityLayouts.problems("dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft", layouts::get));
        texture.fields.get(0).desc = "I";
        assertEquals(1, CommonCompatibilityLayouts.problems("dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft", layouts::get).size());
    }

    @Test
    void commonAuditExceptionsDoNotCaptureIndependentClientCompanions() {
        for (String mod : List.of("decocraft", "ferritecore")) {
            String clientGroup = "dev.hoyin1600p.vhaccelerator.mixin.client.compat." + mod;
            assertFalse(CommonCompatibilityLayouts.isCommonGroup(clientGroup));
            assertFalse(CommonCompatibilityLayouts.isCommonMixin(clientGroup + ".ExampleMixin"));
            assertEquals(List.of(), CommonCompatibilityLayouts.problems(clientGroup,
                    name -> { fail("client companion must not acquire common dependency checks"); return null; }));
        }
    }

    @Test
    void commonActivationWaitsForTheEntireGroupAndCountsOnlyGuardedUse() throws Exception {
        String first = "dev.hoyin1600p.vhaccelerator.mixin.compat.ferritecore.StateHolderPropertyMapMixin";
        try (ZipFile ferrite = new ZipFile("libs/ferritecore-4.2.2-forge.jar")) {
            MixinTargetPreflight.ClassLookup lookup = name -> {
                var entry = ferrite.getEntry(name + ".class");
                if (entry == null) return MixinTargetPreflightTest.read(name);
                try (var input = ferrite.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, 0);
                    return node;
                }
            };
            CompatMixinGroups group = new CompatMixinGroups(List.of(first), (target, mixin) -> true, lookup);
            group.publishDiagnostics(Map.of("physicalSide", "DEDICATED_SERVER", "debug", true));
            assertFalse(CompatMixinGroups.activationAllowed(first));
            CompatMixinGroups.recordOptimizationUse(first);
            assertEquals(0L, group.snapshot().get(0).get("optimizationUseCount"));
            group.applied(first, "net.minecraft.world.level.block.state.StateHolder");
            assertTrue(CompatMixinGroups.activationAllowed(first));
            CompatMixinGroups.recordOptimizationUse(first);
            assertEquals(1L, group.snapshot().get(0).get("optimizationUseCount"));
            assertEquals(true, group.snapshot().get(0).get("eligibleForPerformanceCredit"));
            assertThrows(IllegalStateException.class, () -> group.applicationRejected(first,
                    "net.minecraft.world.level.block.state.StateHolder", List.of("late contradictory application")));
            assertFalse(CompatMixinGroups.activationAllowed(first));
        }
    }

    private static ClassNode node(String name) {
        ClassNode node = new ClassNode();
        node.name = name;
        node.superName = "java/lang/Object";
        return node;
    }

    private static MethodNode method(String name, String descriptor) {
        return new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, descriptor, null, null);
    }
}
