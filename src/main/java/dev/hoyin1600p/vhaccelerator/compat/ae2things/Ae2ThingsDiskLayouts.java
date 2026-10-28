package dev.hoyin1600p.vhaccelerator.compat.ae2things;

import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/** The exact AE2Things members the string sharing relies on; any other layout keeps the original behavior. */
public final class Ae2ThingsDiskLayouts {
    public static final String MANAGER = "io/github/projectet/ae2things/util/StorageManager";
    public static final String STORAGE = "io/github/projectet/ae2things/util/DataStorage";
    public static final String READ_NBT_DESCRIPTOR = "(Lnet/minecraft/nbt/CompoundTag;)L" + MANAGER + ";";

    private Ae2ThingsDiskLayouts() { }

    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        List<String> problems = new ArrayList<>();
        ClassNode manager = find(lookup, MANAGER);
        ClassNode storage = find(lookup, STORAGE);
        if (manager == null) {
            problems.add("AE2Things StorageManager bytecode unavailable");
        } else {
            int publicStatic = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
            if (manager.methods.stream().noneMatch(method -> method.name.equals("readNbt")
                    && method.desc.equals(READ_NBT_DESCRIPTOR) && (method.access & publicStatic) == publicStatic)) {
                problems.add("required public static method missing: " + MANAGER + ".readNbt" + READ_NBT_DESCRIPTOR);
            }
            int privateFinal = Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL;
            if (manager.fields.stream().noneMatch(field -> field.name.equals("disks")
                    && field.desc.equals("Ljava/util/Map;")
                    && (field.access & (privateFinal | Opcodes.ACC_STATIC)) == privateFinal)) {
                problems.add("required private final field missing: " + MANAGER + ".disks:Ljava/util/Map;");
            }
        }
        if (storage == null) {
            problems.add("AE2Things DataStorage bytecode unavailable");
        } else if (storage.fields.stream().noneMatch(field -> field.name.equals("stackKeys")
                && field.desc.equals("Lnet/minecraft/nbt/ListTag;")
                && (field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC)) {
            problems.add("required public field missing: " + STORAGE + ".stackKeys:Lnet/minecraft/nbt/ListTag;");
        }
        return problems;
    }

    private static ClassNode find(MixinTargetPreflight.ClassLookup lookup, String owner) {
        try {
            return lookup.find(owner);
        } catch (Exception | LinkageError unavailable) {
            return null;
        }
    }
}
