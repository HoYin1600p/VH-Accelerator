package dev.hoyin1600p.vhaccelerator.diagnostics;

/**
 * Attributes a sampled stack to the mod doing the work, for debug-only
 * launch samplers. Stacks are innermost frame first.
 */
public final class StackAttribution {
    private static final String[] PLATFORM_PREFIXES = {
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "net.minecraft.", "com.mojang.", "net.minecraftforge.",
            "cpw.mods.", "org.spongepowered.", "com.google.",
            "it.unimi.", "org.apache.", "org.lwjgl.", "io.netty.",
            "org.objectweb.", "com.electronwill.",
            // Launcher entry points sit under every main/render-thread frame.
            "io.github.zekerzhayard.forgewrapper.", "org.prismlauncher.",
            "org.multimc.", "net.fabricmc.devlaunchinjector."
    };
    private static final int PATH_CALLERS = 3;

    private StackAttribution() {
    }

    /**
     * The first frame outside platform code, as a three-segment package,
     * or a platform label when only Minecraft, Forge or the JDK is running.
     */
    public static String owner(StackTraceElement[] stack) {
        int index = ownerIndex(stack);
        if (index >= 0) {
            return ownerOf(stack[index]);
        }
        String top = stack[0].getClassName() + "." + stack[0].getMethodName();
        if (top.startsWith("jdk.internal.misc.Unsafe.park")
                || top.startsWith("java.lang.Object.wait")
                || top.startsWith("java.lang.Thread.sleep")) {
            return "(waiting)";
        }
        return "(minecraft/forge/jdk)";
    }

    /**
     * A short call path: the owner frame, its next callers, and the first
     * caller outside platform code that belongs to a different owner, so
     * shared helpers show who invoked them.
     */
    public static String path(StackTraceElement[] stack) {
        int index = ownerIndex(stack);
        int start = index >= 0 ? index : 0;
        StringBuilder path = new StringBuilder(simple(stack[start]));
        int end = Math.min(stack.length, start + 1 + PATH_CALLERS);
        for (int caller = start + 1; caller < end; caller++) {
            path.append(" <- ").append(simple(stack[caller]));
        }
        String owner = index >= 0 ? ownerOf(stack[index]) : null;
        for (int caller = end; caller < stack.length; caller++) {
            StackTraceElement frame = stack[caller];
            if (!isPlatform(frame.getClassName())
                    && !ownerOf(frame).equals(owner)) {
                path.append(" <- ... <- ").append(frame.getClassName())
                        .append('.').append(frame.getMethodName());
                break;
            }
        }
        return path.toString();
    }

    /**
     * What kind of work a sample is doing, for class-loading measurement:
     * {@code class-load:<kind>}, {@code static-init} or {@code other}.
     * Loading triggered inside a static initializer counts as loading.
     */
    public static String category(StackTraceElement[] stack) {
        int loader = -1;
        for (int index = 0; index < stack.length; index++) {
            StackTraceElement frame = stack[index];
            if (isLoaderFrame(frame)) {
                loader = index;
                break;
            }
            if ("<clinit>".equals(frame.getMethodName())) {
                return "static-init";
            }
        }
        if (loader < 0) {
            return "other";
        }
        String leaf = stack[0].getClassName() + "." + stack[0].getMethodName();
        if (leaf.startsWith("jdk.internal.misc.Unsafe.park")
                || leaf.startsWith("java.lang.Object.wait")) {
            return "class-load:lock-wait";
        }
        boolean mixin = false;
        boolean transform = false;
        boolean read = false;
        boolean define = false;
        for (int index = 0; index <= loader; index++) {
            String className = stack[index].getClassName();
            String method = stack[index].getMethodName();
            if (className.startsWith("org.spongepowered.asm.")) {
                mixin = true;
            } else if (className.startsWith("cpw.mods.modlauncher.ClassTransformer")
                    || className.startsWith("cpw.mods.modlauncher.LaunchPluginHandler")
                    || className.startsWith("cpw.mods.modlauncher.TransformationServiceDecorator")
                    || className.startsWith("net.minecraftforge.coremod.")
                    || className.startsWith("org.objectweb.asm.")) {
                transform = true;
            } else if (className.startsWith("java.util.zip.")
                    || className.startsWith("sun.nio.")
                    || className.startsWith("jdk.nio.zipfs.")
                    || className.startsWith("cpw.mods.niofs.")
                    || className.startsWith("java.io.")) {
                read = true;
            } else if (method.startsWith("defineClass")) {
                define = true;
            }
        }
        if (mixin) {
            return "class-load:mixin-transform";
        }
        if (transform) {
            return "class-load:transform";
        }
        if (read) {
            return "class-load:read";
        }
        return define ? "class-load:define" : "class-load:other";
    }

    private static boolean isLoaderFrame(StackTraceElement frame) {
        String className = frame.getClassName();
        String method = frame.getMethodName();
        boolean loaderClass = className.equals("java.lang.ClassLoader")
                || className.startsWith("cpw.mods.cl.")
                || className.equals("cpw.mods.modlauncher.TransformingClassLoader")
                || className.startsWith("jdk.internal.loader.");
        return loaderClass && (method.equals("loadClass")
                || method.equals("findClass")
                || method.equals("readerToClass")
                || method.startsWith("defineClass")
                || method.equals("loadFromModule"));
    }

    private static int ownerIndex(StackTraceElement[] stack) {
        for (int index = 0; index < stack.length; index++) {
            StackTraceElement element = stack[index];
            if (!isPlatform(element.getClassName())) {
                return index;
            }
            String method = element.getMethodName();
            if (method.startsWith("vhaccelerator$") || method.startsWith("vha$")) {
                return index;
            }
            if (method.indexOf('$') > 0 && !method.startsWith("lambda$")
                    && !method.startsWith("access$")) {
                return index;
            }
        }
        return -1;
    }

    private static String ownerOf(StackTraceElement element) {
        String className = element.getClassName();
        if (!isPlatform(className)) {
            return packagePrefix(className);
        }
        String method = element.getMethodName();
        if (method.startsWith("vhaccelerator$") || method.startsWith("vha$")) {
            return "dev.hoyin1600p.vhaccelerator";
        }
        // A mixin handler merged into platform code; the path names it.
        return "(mixin handler in " + packagePrefix(className) + ")";
    }

    public static boolean isPlatform(String className) {
        for (String prefix : PLATFORM_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String packagePrefix(String className) {
        int dots = 0;
        for (int index = 0; index < className.length(); index++) {
            if (className.charAt(index) == '.' && ++dots == 3) {
                return className.substring(0, index);
            }
        }
        int last = className.lastIndexOf('.');
        return last < 0 ? className : className.substring(0, last);
    }

    private static String simple(StackTraceElement frame) {
        String className = frame.getClassName();
        return className.substring(className.lastIndexOf('.') + 1)
                + "." + frame.getMethodName();
    }
}
