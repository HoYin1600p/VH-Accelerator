package dev.hoyin1600p.vhaccelerator.mixinmemory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.objectweb.asm.tree.ClassNode;

/**
 * Replaces the class trees Mixin keeps for mixins that are done with, by {@link ReleasedMixinTree}.
 *
 * <p>A mixin qualifies when it is not pseudo, has at least one resolved target, and every one of its
 * targets has already been loaded: Mixin reads a mixin's tree only to apply it to a target, so such a
 * mixin is not read again unless something re-transforms a loaded class. If that happens the
 * replacement rebuilds the tree from its stored bytes, so the worst case is a little extra work, never
 * a different result. Trees that do not survive the byte round trip stay as they are.
 */
public final class MixinTreeReleaser {
    /** Mixins handled per hold of Mixin's lock, so class loading elsewhere waits only briefly. */
    static final int BATCH = 32;

    private MixinTreeReleaser() {
    }

    public record Statistics(
            int mixins,
            int released,
            int alreadyReleased,
            int keptUnloadedTargets,
            int keptPseudoOrUntargeted,
            int keptNoState,
            int keptUnstable,
            int failed,
            long classBytes,
            long storedBytes,
            long millis
    ) {
    }

    public static Statistics sweep(MixinTreeAccess access, Predicate<String> targetLoaded) {
        long started = System.nanoTime();
        List<Object> all;
        synchronized (access.lock()) {
            all = new ArrayList<>(access.mixins());
        }
        int released = 0;
        int already = 0;
        int unloaded = 0;
        int pseudo = 0;
        int noState = 0;
        int unstable = 0;
        int failed = 0;
        long classBytes = 0;
        long storedBytes = 0;
        for (int from = 0; from < all.size(); from += BATCH) {
            List<Object> batch = all.subList(from, Math.min(all.size(), from + BATCH));
            synchronized (access.lock()) {
                for (Object mixin : batch) {
                    try {
                        ClassNode tree = access.tree(mixin);
                        if (tree == null) {
                            noState++;
                            continue;
                        }
                        if (tree instanceof ReleasedMixinTree) {
                            already++;
                            continue;
                        }
                        List<String> targets = access.targets(mixin);
                        if (access.pseudo(mixin) || targets.isEmpty()) {
                            pseudo++;
                            continue;
                        }
                        if (!allLoaded(targets, targetLoaded)) {
                            unloaded++;
                            continue;
                        }
                        ReleasedMixinTree replacement = ReleasedMixinTree.capture(tree);
                        if (replacement == null) {
                            unstable++;
                            continue;
                        }
                        access.setTree(mixin, replacement);
                        released++;
                        classBytes += replacement.classSize();
                        storedBytes += replacement.storedSize();
                    } catch (RuntimeException | LinkageError exception) {
                        failed++;
                    }
                }
            }
        }
        return new Statistics(all.size(), released, already, unloaded, pseudo, noState, unstable, failed,
                classBytes, storedBytes, (System.nanoTime() - started) / 1_000_000L);
    }

    private static boolean allLoaded(List<String> targets, Predicate<String> targetLoaded) {
        for (String target : targets) {
            if (!targetLoaded.test(target)) {
                return false;
            }
        }
        return true;
    }
}
