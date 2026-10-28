package dev.hoyin1600p.vhaccelerator.compat;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/** Exact unremapped dependencies not expressible by checking the injector target alone. */
public final class CommonCompatibilityLayouts {
    private static final String BASE = "dev.hoyin1600p.vhaccelerator.mixin.compat.";
    private CommonCompatibilityLayouts() { }

    public static boolean isCraftTweakerRemovalMixin(String mixin) {
        return List.of("ScriptRunIndexSessionMixin", "ScriptRunManagerOutputContextMixin",
                "RecipeListIndexedOutputMixin", "AccessibleRecipeMapsMixin").stream()
                .anyMatch(name -> mixin.equals(BASE + "crafttweaker.removals." + name));
    }

    public static boolean isCommonGroup(String group) {
        if (dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.GROUP.equals(group)) return true;
        return VaultLootCdfLayouts.GROUP.equals(group) || VaultLootCdfLayouts.LAZY_GROUP.equals(group) || (BASE + "decocraft").equals(group) || (BASE + "ferritecore").equals(group)
                || (BASE + "crafttweaker.removals").equals(group)
                || (BASE + "buildinggadgets").equals(group)
                || (BASE + "ae2things").equals(group)
                || (BASE + "lootjs").equals(group)
                || dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.GROUP.equals(group)
                || dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.GROUP.equals(group);
    }

    public static boolean isCommonMixin(String mixin) {
        if (dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.MIXIN.equals(mixin)) return true;
        return VaultLootCdfLayouts.MIXIN.equals(mixin) || VaultLootCdfLayouts.LAZY_MIXIN.equals(mixin) || mixin.startsWith(BASE + "decocraft.") || mixin.startsWith(BASE + "ferritecore.")
                || isCraftTweakerRemovalMixin(mixin) || StartupAdapterLayouts.isMixin(mixin)
                || mixin.equals(BASE + "buildinggadgets.TemplateLookupMixin")
                || mixin.equals(BASE + "ae2things.StorageManagerLoadMixin")
                || mixin.equals(BASE + "lootjs.LootJsItemFilterMixin")
                || mixin.equals(dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.STIRLING_MIXIN)
                || mixin.equals(dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.GOURMAND_MIXIN)
                || mixin.equals(dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.MIXIN);
    }

    public static List<String> problems(String group, MixinTargetPreflight.ClassLookup lookup) {
        if (dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.GROUP.equals(group)) {
            return dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.problems(lookup);
        }
        List<String> problems = new ArrayList<>();
        if (VaultLootCdfLayouts.GROUP.equals(group)) return VaultLootCdfLayouts.problems(lookup);
        if (VaultLootCdfLayouts.LAZY_GROUP.equals(group)) return VaultLootCdfLayouts.lazyProblems(lookup);
        if (group.equals(BASE + "buildinggadgets")) {
            return dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.BuildingGadgetsTemplateLayouts.problems(lookup);
        }
        if (group.equals(BASE + "ae2things")) {
            return dev.hoyin1600p.vhaccelerator.compat.ae2things.Ae2ThingsDiskLayouts.problems(lookup);
        }
        if (group.equals(BASE + "lootjs")) {
            return dev.hoyin1600p.vhaccelerator.compat.lootjs.LootJsRegexFilterLayouts.problems(lookup);
        }
        if (group.equals(dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.GROUP)) {
            return dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalFuelLayouts.problems(lookup);
        }
        if (group.equals(dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.GROUP)) {
            return dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.problems(lookup);
        }
        if (group.equals(BASE + "crafttweaker.removals")) {
            freshRecipeCopies(lookup, problems);
            call(lookup, problems, "com/blamejared/crafttweaker/impl/script/scriptrun/ScriptRun",
                    "executeRun", "()V",
                    "com/blamejared/crafttweaker/impl/script/scriptrun/runner/IScriptRunner", "run", "()V");
            call(lookup, problems, "com/blamejared/crafttweaker/impl/script/scriptrun/ScriptRunManager",
                    "applyActionInRun", "(Lcom/blamejared/crafttweaker/api/action/base/IAction;)V",
                    "com/blamejared/crafttweaker/api/action/base/IAction", "apply", "()V");
            declaredMethod(lookup, problems, "com/blamejared/crafttweaker/api/recipe/RecipeList",
                    "removeByRecipeTest", "(Ljava/util/function/Predicate;)V");
            declaredField(lookup, problems, "com/blamejared/crafttweaker/api/recipe/RecipeList", "recipes", "Ljava/util/Map;");
            declaredField(lookup, problems, "com/blamejared/crafttweaker/api/recipe/RecipeList", "byName", "Ljava/util/Map;");
            for (String owner : List.of("com/blamejared/crafttweaker/api/action/recipe/ActionRemoveRecipeByOutput",
                    "com/blamejared/crafttweaker/api/action/recipe/generic/ActionRemoveGenericRecipeByOutput")) {
                privateFinalField(lookup, problems, owner, "output",
                        "Lcom/blamejared/crafttweaker/api/ingredient/IIngredient;");
            }
            // A tag output is expanded to its items; the plain stacks it matches against use IItemStack.matches.
            String tagIngredient = "com/blamejared/crafttweaker/api/ingredient/type/TagIngredient";
            method(lookup, problems, tagIngredient, "getItems", "()[Lcom/blamejared/crafttweaker/api/item/IItemStack;");
            method(lookup, problems, tagIngredient, "matches", "(Lcom/blamejared/crafttweaker/api/item/IItemStack;Z)Z");
            for (String stack : List.of("com/blamejared/crafttweaker/api/item/MCItemStack",
                    "com/blamejared/crafttweaker/api/item/MCItemStackMutable")) {
                noDeclaredMethod(lookup, problems, stack, "matches");
            }
            method(lookup, problems, "com/blamejared/crafttweaker/api/item/IItemStack", "matches",
                    "(Lcom/blamejared/crafttweaker/api/item/IItemStack;Z)Z");
        } else if (group.equals(BASE + "decocraft")) {
            method(lookup, problems, "com/razz/decocraft/models/bbmodel/BBModelLoader", "loadModel",
                    "(Ljava/io/Reader;)Lcom/razz/decocraft/models/bbmodel/BBModel;");
            method(lookup, problems, "com/razz/decocraft/utils/JsonParser", "parseModel",
                    "(Ljava/lang/String;Ljava/util/zip/ZipFile;)Lcom/razz/decocraft/models/bbmodel/BBModel;");
            field(lookup, problems, "com/razz/decocraft/models/bbmodel/BBModel", "textures", "Ljava/util/List;");
            for (String name : List.of("source", "path", "relative_path")) {
                field(lookup, problems, "com/razz/decocraft/models/bbmodel/BBModelParts$Texture", name, "Ljava/lang/String;");
            }
        } else if (group.equals(BASE + "ferritecore")) {
            method(lookup, problems, "malte0811/ferritecore/ducks/FastMapStateHolder", "getStateMap",
                    "()Lmalte0811/ferritecore/fastmap/FastMap;");
            method(lookup, problems, "malte0811/ferritecore/ducks/FastMapStateHolder", "getStateIndex", "()I");
            method(lookup, problems, "malte0811/ferritecore/fastmap/FastMap", "getValue", "(ILjava/lang/Object;)Ljava/lang/Comparable;");
            method(lookup, problems, "malte0811/ferritecore/fastmap/FastMap", "getPropertySet", "()Lcom/google/common/collect/ImmutableSet;");
            method(lookup, problems, "malte0811/ferritecore/classloading/FastImmutableMapDefiner", "makeMap",
                    "(Lmalte0811/ferritecore/ducks/FastMapStateHolder;)Lcom/google/common/collect/ImmutableMap;");
        }
        return problems;
    }

    public static java.util.Map<String, Object> contract(String group) {
        if (dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.GROUP.equals(group)) {
            return dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.contract();
        }
        if (StartupAdapterLayouts.isGroup(group)) return StartupAdapterLayouts.contract(group);
        if (VaultLootCdfLayouts.GROUP.equals(group)) return VaultLootCdfLayouts.contract();
        if (VaultLootCdfLayouts.LAZY_GROUP.equals(group)) return VaultLootCdfLayouts.lazyContract();
        if (dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.GROUP.equals(group)) {
            return dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalFuelLayouts.contract();
        }
        if (dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.GROUP.equals(group)) {
            return dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.contract();
        }
        if (group.equals(BASE + "buildinggadgets")) return java.util.Map.of(
                "contractVersion", 1, "modId", "buildinggadgets",
                "supportedVersions", List.of("3.13.2-build.21+mc1.18.2"),
                "setting", "compatibility.compactBuildingGadgetsTemplates", "settingDefault", true,
                "sides", List.of("DEDICATED_SERVER"),
                "selection", "known discovery, reviewed archive and Guava; common master and manual setting; Compare Mode off",
                "activation", "confirmed fresh-method return hooks; runtime reflection and coordinate semantics fail closed",
                "injectionSites", List.of("Template.deserialize/normalize/rotate(Axis,Rotation)/mirror RETURN"),
                "requiredDependencies", List.of("original Template.map identity and final layout",
                        "Guava 31.0.1 JdkBackedImmutableMap delegateMap and ordered entries",
                        "reviewed BlockPos/Vec3i coordinate equality and hashes"));
        if (group.equals(BASE + "ae2things")) return java.util.Map.of(
                "contractVersion", 1, "modId", "ae2things",
                "supportedVersions", List.of("any version with the reviewed StorageManager/DataStorage layout"),
                "setting", "compatibility.shareAe2ThingsDiskStrings", "settingDefault", true,
                "sides", List.of("CLIENT", "DEDICATED_SERVER"),
                "selection", "known discovery and mod presence; common master and setting; Compare Mode off",
                "activation", "readNbt RETURN hook confirmed; layout or reflection problems keep the loaded tree as built",
                "injectionSites", List.of("StorageManager.readNbt(CompoundTag) RETURN"),
                "requiredDependencies", List.of("StorageManager.disks:java.util.Map private final",
                        "DataStorage.stackKeys:net.minecraft.nbt.ListTag public",
                        "net.minecraft.nbt.StringTag immutable, copy() returns itself"));
        if (group.equals(BASE + "lootjs")) return java.util.Map.of(
                "contractVersion", 1, "modId", "lootjs",
                "supportedVersions", List.of("any LootJS/KubeJS pair with the reviewed ofItemFilter and IngredientJS layout (reviewed: LootJS 2.7.7, KubeJS 1802.5.5-build.569)"),
                "setting", "compatibility.shortCircuitLootJsRegexFilters", "settingDefault", true,
                "sides", List.of("CLIENT", "DEDICATED_SERVER"),
                "selection", "known discovery and LootJS plus KubeJS presence; common master and setting; Compare Mode off",
                "activation", "redirect confirmed; only an exact RegexIngredientJS takes the short-circuit, any exception reruns the original check",
                "injectionSites", List.of("LootJSPlugin.ofItemFilter(Object) IngredientJS.isEmpty() redirect"),
                "requiredDependencies", List.of("ofItemFilter calls IngredientJS.isEmpty() exactly once",
                        "IngredientJS.isEmpty = getFirst().isEmpty(); getFirst/getStacks default bodies as reviewed",
                        "RegexIngredientJS does not override isEmpty/getFirst/getStacks/getCount",
                        "ItemStackJS.getList() static"));
        if (group.equals(BASE + "crafttweaker.removals")) return java.util.Map.of(
                "contractVersion", 3, "modId", "crafttweaker", "supportedVersions", List.of("9.1.213"),
                "setting", "optimizations.indexCraftTweakerRecipeRemovals", "settingDefault", true,
                "sides", List.of("CLIENT", "DEDICATED_SERVER"),
                "selection", "known discovery and exact version; dedicated requires common master, setting and Compare Mode off; clients retain context-only hooks independently of optimization settings",
                "activation", "indexing requires common master, setting, Compare Mode off and confirmed whole-group application; context-only execution earns no optimization credit",
                "injectionSites", List.of("ScriptRun.executeRun IScriptRunner.run redirect",
                        "ScriptRunManager.applyActionInRun IAction.apply redirect",
                        "AccessibleElementsProvider.recipeManager fresh-copy publication HEAD",
                        "RecipeList.removeByRecipeTest candidate dispatch at HEAD"),
                "requiredDependencies", List.of("RecipeList.recipes/byName:java.util.Map",
                        "one IScriptRunner.run invocation", "one IAction.apply invocation",
                        "ActionRemoveRecipeByOutput.output:IIngredient private final",
                        "ActionRemoveGenericRecipeByOutput.output:IIngredient private final",
                        "TagIngredient.getItems()/matches(IItemStack,boolean) public",
                        "MCItemStack and MCItemStackMutable inherit IItemStack.matches unchanged",
                        "server and client fixRecipeManager fresh HashMap copies before provider publication"));
        if (group.equals(BASE + "decocraft")) return java.util.Map.of(
                "modId", "decocraft", "supportedVersions", List.of("3.0.4-1.18.2"),
                "setting", "compatibility.trimDecocraftModels", "settingDefault", true,
                "sides", List.of("CLIENT", "DEDICATED_SERVER"),
                "competingOwner", "none declared; unknown layouts reject",
                "injectionSites", List.of("BBModelLoader.loadModel(Reader):BBModel RETURN",
                        "JsonParser.parseModel(String,ZipFile):BBModel HEAD/RETURN"),
                "requiredFields", List.of("BBModel.textures:java.util.List",
                        "BBModelParts$Texture.source/path/relative_path:java.lang.String"));
        if (group.equals(BASE + "ferritecore")) return java.util.Map.of(
                "modId", "ferritecore", "supportedVersions", List.of("4.2.2"),
                "setting", "compatibility.compactFerriteCorePropertyMaps", "settingDefault", true,
                "sides", List.of("CLIENT", "DEDICATED_SERVER"),
                "competingOwner", "FerriteCore owns FastMap; VHA only removes its per-state view",
                "injectionSites", List.of("StateHolder.populateNeighbours TAIL", "StateHolder property getters/setter map reads"),
                "requiredDependencies", List.of("FastMapStateHolder.getStateMap/getStateIndex", "FastMap.getValue/getPropertySet",
                        "FastImmutableMapDefiner.makeMap"));
        return CompatibilityInventory.reference(group);
    }

    private static org.objectweb.asm.tree.MethodNode declaredMethod(MixinTargetPreflight.ClassLookup lookup,
            List<String> problems, String owner, String name, String descriptor) {
        ClassNode node = find(lookup, owner);
        org.objectweb.asm.tree.MethodNode method = node == null ? null : node.methods.stream()
                .filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor))
                .findFirst().orElse(null);
        if (method == null) problems.add("required method missing: " + owner + "." + name + descriptor);
        return method;
    }

    private static void freshRecipeCopies(MixinTargetPreflight.ClassLookup lookup, List<String> problems) {
        String descriptor = "(Lnet/minecraft/world/item/crafting/RecipeManager;)V";
        declaredMethod(lookup, problems, "com/blamejared/crafttweaker/impl/helper/AccessibleElementsProvider",
                "recipeManager", descriptor);
        for (String owner : List.of("com/blamejared/crafttweaker/impl/script/ScriptReloadListener",
                "com/blamejared/crafttweaker/impl/script/RecipeManagerScriptLoader")) {
            var method = declaredMethod(lookup, problems, owner, "fixRecipeManager", descriptor);
            var copier = declaredMethod(lookup, problems, owner, "lambda$fixRecipeManager$0",
                    "(Lcom/blamejared/crafttweaker/mixin/common/access/recipe/AccessRecipeManager;"
                            + "Lnet/minecraft/world/item/crafting/RecipeType;Ljava/util/Map;)Ljava/util/Map;");
            if (method == null || copier == null) continue;
            int constructors = 0;
            boolean published = false;
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                    if (call.owner.equals("java/util/HashMap") && call.name.equals("<init>")
                            && call.desc.equals("(Ljava/util/Map;)V")) constructors++;
                    if (call.owner.equals("com/blamejared/crafttweaker/platform/helper/IAccessibleElementsProvider")
                            && call.name.equals("recipeManager") && call.desc.equals(descriptor)) published = true;
                    else if (published) problems.add("code executes after fresh recipe-map publication: " + owner);
                }
            }
            if (constructors != 2 || !published) problems.add("fresh recipe-map copy boundary changed: " + owner);
            long innerCopies = java.util.stream.StreamSupport.stream(copier.instructions.spliterator(), false)
                    .filter(instruction -> instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.equals("java/util/HashMap") && call.name.equals("<init>")
                            && call.desc.equals("(Ljava/util/Map;)V")).count();
            if (innerCopies != 1) problems.add("fresh per-type map copy changed: " + owner);
        }
    }

    private static void declaredField(MixinTargetPreflight.ClassLookup lookup, List<String> problems,
            String owner, String name, String descriptor) {
        ClassNode node = find(lookup, owner);
        if (node == null || node.fields.stream().noneMatch(field -> field.name.equals(name)
                && field.desc.equals(descriptor))) {
            problems.add("required field missing: " + owner + "." + name + ":" + descriptor);
        }
    }

    private static void privateFinalField(MixinTargetPreflight.ClassLookup lookup, List<String> problems,
            String owner, String name, String descriptor) {
        ClassNode node = find(lookup, owner);
        int required = Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL;
        if (node == null || node.fields.stream().noneMatch(field -> field.name.equals(name)
                && field.desc.equals(descriptor) && (field.access & required) == required)) {
            problems.add("required private final field missing: " + owner + "." + name + ":" + descriptor);
        }
    }

    private static void call(MixinTargetPreflight.ClassLookup lookup, List<String> problems,
            String owner, String methodName, String methodDescriptor,
            String targetOwner, String targetName, String targetDescriptor) {
        var method = declaredMethod(lookup, problems, owner, methodName, methodDescriptor);
        if (method == null) return;
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && call.owner.equals(targetOwner) && call.name.equals(targetName)
                    && call.desc.equals(targetDescriptor)) count++;
        }
        if (count != 1) problems.add("required invocation count must be one: " + owner + "." + methodName
                + " -> " + targetOwner + "." + targetName + targetDescriptor + "; found " + count);
    }

    private static ClassNode find(MixinTargetPreflight.ClassLookup lookup, String owner) {
        try {
            return lookup.find(owner);
        } catch (Exception | LinkageError unavailable) {
            return null;
        }
    }

    private static void noDeclaredMethod(MixinTargetPreflight.ClassLookup lookup, List<String> problems,
            String owner, String name) {
        ClassNode node = find(lookup, owner);
        if (node == null) problems.add("class bytecode unavailable: " + owner);
        else if (node.methods.stream().anyMatch(method -> method.name.equals(name))) {
            problems.add("method must not be overridden: " + owner + "." + name);
        }
    }

    private static void field(MixinTargetPreflight.ClassLookup lookup, List<String> problems,
                              String owner, String name, String descriptor) {
        ClassNode node = find(lookup, owner);
        if (node == null || node.fields.stream().noneMatch(field -> field.name.equals(name)
                && field.desc.equals(descriptor) && (field.access & Opcodes.ACC_PUBLIC) != 0)) {
            problems.add("required public field missing: " + owner + "." + name + ":" + descriptor);
        }
    }

    private static void method(MixinTargetPreflight.ClassLookup lookup, List<String> problems,
                               String owner, String name, String descriptor) {
        ClassNode node = find(lookup, owner);
        if (node == null || node.methods.stream().noneMatch(method -> method.name.equals(name)
                && method.desc.equals(descriptor) && (method.access & Opcodes.ACC_PUBLIC) != 0)) {
            problems.add("required public method missing: " + owner + "." + name + descriptor);
        }
    }
}
