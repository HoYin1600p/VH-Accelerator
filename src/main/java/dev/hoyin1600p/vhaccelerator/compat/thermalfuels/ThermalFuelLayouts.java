package dev.hoyin1600p.vhaccelerator.compat.thermalfuels;

import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The exact Thermal bytecode the deferred converted-fuel list relies on. For each of the two managers
 * ({@code StirlingFuelManager}, {@code GourmandFuelManager}) the ordered calls and the field accesses of
 * {@code createConvertedRecipes} are pinned, so the method is known to read the registry, test items through the
 * manager's own pure lookups and write nothing but {@code convertedFuels}. Anything else keeps the eager scan.
 */
public final class ThermalFuelLayouts {
    private static final String MANAGERS = "cofh/thermal/core/util/managers/dynamo/";
    private static final String RECIPES = "cofh/thermal/core/util/recipes/dynamo/";
    private static final String SUPER = "cofh/thermal/lib/util/managers/SingleItemFuelManager";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String STACK_DESC = "L" + STACK + ";";
    private static final String RECIPE_MANAGER = "(Lnet/minecraft/world/item/crafting/RecipeManager;)V";
    private static final String DYNAMO_FUEL = "Lcofh/thermal/lib/util/recipes/internal/IDynamoFuel;";
    private static final String LIST = "Ljava/util/List;";

    private ThermalFuelLayouts() { }

    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        List<String> problems = new ArrayList<>();
        check(lookup, problems, "StirlingFuelManager", "StirlingFuel");
        check(lookup, problems, "GourmandFuelManager", "GourmandFuel");
        return problems;
    }

    private static void check(MixinTargetPreflight.ClassLookup lookup, List<String> problems, String manager,
            String fuel) {
        String owner = MANAGERS + manager;
        ClassNode node = find(lookup, owner);
        if (node == null) {
            problems.add("Thermal bytecode unavailable: " + owner);
            return;
        }
        if (!SUPER.equals(node.superName)) problems.add(owner + " must extend " + SUPER);
        if (node.fields.stream().noneMatch(field -> field.name.equals("convertedFuels") && field.desc.equals(LIST)
                && (field.access & Opcodes.ACC_STATIC) == 0)) {
            problems.add("required instance field missing: " + owner + ".convertedFuels" + LIST);
        }
        for (String[] required : new String[][] {{"clear", "()V"}, {"getConvertedFuels", "()" + LIST}}) {
            MethodNode method = method(node, required[0], required[1]);
            if (method == null || (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != 0) {
                problems.add("required instance method missing: " + owner + "." + required[0] + required[1]);
            }
        }
        MethodNode scan = method(node, "createConvertedRecipes", RECIPE_MANAGER);
        if (scan == null || (scan.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != 0) {
            problems.add("required instance method missing: " + owner + ".createConvertedRecipes" + RECIPE_MANAGER);
            return;
        }
        List<String> expected = List.of(
                Opcodes.INVOKEINTERFACE + " net/minecraftforge/registries/IForgeRegistry.iterator()Ljava/util/Iterator;",
                Opcodes.INVOKEINTERFACE + " java/util/Iterator.hasNext()Z",
                Opcodes.INVOKEINTERFACE + " java/util/Iterator.next()Ljava/lang/Object;",
                Opcodes.INVOKESPECIAL + " " + STACK + ".<init>(Lnet/minecraft/world/level/ItemLike;)V",
                Opcodes.INVOKEVIRTUAL + " " + owner + ".getFuel(" + STACK_DESC + ")" + DYNAMO_FUEL,
                Opcodes.INVOKEVIRTUAL + " " + owner + ".validFuel(" + STACK_DESC + ")Z",
                Opcodes.INVOKEVIRTUAL + " " + owner + ".getEnergy(" + STACK_DESC + ")I",
                Opcodes.INVOKEVIRTUAL + " " + owner + ".convert(" + STACK_DESC + "I)L" + RECIPES + fuel + ";",
                Opcodes.INVOKEINTERFACE + " java/util/List.add(Ljava/lang/Object;)Z",
                Opcodes.INVOKEVIRTUAL + " " + STACK + ".m_41720_()Lnet/minecraft/world/item/Item;",
                Opcodes.INVOKEVIRTUAL + " net/minecraft/world/item/Item.getRegistryName()Lnet/minecraft/resources/ResourceLocation;",
                Opcodes.INVOKEINTERFACE + " org/apache/logging/log4j/Logger.error(Ljava/lang/String;)V");
        List<String> actual = new ArrayList<>();
        TreeSet<String> fields = new TreeSet<>();
        for (AbstractInsnNode instruction : scan.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                actual.add(call.getOpcode() + " " + call.owner + "." + call.name + call.desc);
            } else if (instruction instanceof FieldInsnNode access) {
                fields.add(access.getOpcode() + " " + access.owner + "." + access.name);
            }
        }
        if (!actual.equals(expected)) {
            problems.add("reviewed body changed: " + owner + ".createConvertedRecipes calls " + actual);
        }
        TreeSet<String> expectedFields = new TreeSet<>(List.of(
                Opcodes.GETSTATIC + " net/minecraftforge/registries/ForgeRegistries.ITEMS",
                Opcodes.GETFIELD + " " + owner + ".convertedFuels",
                Opcodes.GETSTATIC + " cofh/thermal/core/ThermalCore.LOG"));
        if (!fields.equals(expectedFields)) {
            problems.add("reviewed body changed: " + owner + ".createConvertedRecipes field accesses " + fields);
        }
    }

    public static Map<String, Object> contract() {
        return Map.of(
                "contractVersion", 1, "modId", "thermal_foundation",
                "supportedVersions", List.of("any Thermal Foundation with the reviewed Stirling and Gourmand fuel manager layout (reviewed: thermal_foundation 1.18.2-9.2.2.58)"),
                "setting", "compatibility.deferThermalConvertedFuels", "settingDefault", true,
                "sides", List.of("DEDICATED_SERVER"),
                "selection", "known discovery and Thermal Foundation presence; dedicated server only; common master and setting; Compare Mode off",
                "activation", "whole-group layout preflight; the original createConvertedRecipes runs unchanged on the first getConvertedFuels() call and is dropped by the next refresh or clear()",
                "injectionSites", List.of("StirlingFuelManager.createConvertedRecipes(RecipeManager) HEAD",
                        "StirlingFuelManager.clear() HEAD", "StirlingFuelManager.getConvertedFuels() HEAD",
                        "GourmandFuelManager.createConvertedRecipes(RecipeManager) HEAD",
                        "GourmandFuelManager.clear() HEAD", "GourmandFuelManager.getConvertedFuels() HEAD"),
                "requiredDependencies", List.of("both managers extend SingleItemFuelManager and keep the instance field convertedFuels:java.util.List",
                        "createConvertedRecipes pinned to its reviewed ordered calls and field accesses; it writes only convertedFuels",
                        "no other reviewed mod reads getConvertedFuels() on a server; Thermal Expansion's JEI plugin is the only reader"));
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElse(null);
    }

    private static ClassNode find(MixinTargetPreflight.ClassLookup lookup, String owner) {
        try {
            return lookup.find(owner);
        } catch (Exception | LinkageError unavailable) {
            return null;
        }
    }
}
