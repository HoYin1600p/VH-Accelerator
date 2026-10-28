package dev.hoyin1600p.vhaccelerator.compat;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Adds a straight-line first-use observer only to this config's confirmed merged handlers. */
public final class CompatibilityApplicationObserver {
    private CompatibilityApplicationObserver() { }

    /** Common ports require every declared injector to have an actual call edge, even require=0. */
    public static List<String> missingCommonInjectors(ClassNode target, String mixin, ClassNode source) {
        if (!CommonCompatibilityLayouts.isCommonMixin(mixin)) return List.of();
        List<String> missing = new ArrayList<>();
        for (var original : source.methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (original.visibleAnnotations != null) annotations.addAll(original.visibleAnnotations);
            if (original.invisibleAnnotations != null) annotations.addAll(original.invisibleAnnotations);
            if (annotations.stream().noneMatch(annotation -> annotation.desc.startsWith(
                    "Lorg/spongepowered/asm/mixin/injection/"))) continue;
            boolean invoked = false;
            for (var merged : target.methods) {
                if (!merged.name.contains(original.name) || !merged.desc.equals(original.desc)
                        || !mergedFrom(merged.visibleAnnotations, merged.invisibleAnnotations, mixin)) continue;
                for (var caller : target.methods) {
                    for (var instruction : caller.instructions) {
                        if (instruction instanceof MethodInsnNode call && call.owner.equals(target.name)
                                && call.name.equals(merged.name) && call.desc.equals(merged.desc)) invoked = true;
                    }
                }
            }
            if (!invoked) missing.add("unconfirmed injector call edge: " + original.name + original.desc);
        }
        return missing;
    }

    private static boolean mergedFrom(List<AnnotationNode> visible, List<AnnotationNode> invisible, String mixin) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (visible != null) annotations.addAll(visible);
        if (invisible != null) annotations.addAll(invisible);
        return annotations.stream().anyMatch(annotation -> {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                    || annotation.values == null) return false;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals(mixin)) return true;
            }
            return false;
        });
    }

    public static int instrument(ClassNode target, String mixin) {
        if (CompatMixinGroups.groupOf(mixin) == null) {
            return 0;
        }
        int observed = 0;
        for (var method : target.methods) {
            if (method.name.startsWith("<") || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                continue;
            }
            boolean merged = mergedFrom(method.visibleAnnotations, method.invisibleAnnotations, mixin);
            if (!merged) continue;
            InsnList observer = new InsnList();
            observer.add(new LdcInsnNode(mixin));
            observer.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "dev/hoyin1600p/vhaccelerator/compat/CompatMixinGroups", "recordFirstUse",
                    "(Ljava/lang/String;)V", false));
            method.instructions.insert(observer);
            method.maxStack = Math.max(method.maxStack, 1);
            observed++;
        }
        return observed;
    }
}
