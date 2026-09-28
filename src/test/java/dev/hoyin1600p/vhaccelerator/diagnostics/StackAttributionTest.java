package dev.hoyin1600p.vhaccelerator.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StackAttributionTest {
    private static StackTraceElement frame(String className, String method) {
        return new StackTraceElement(className, method, null, -1);
    }

    @Test
    void pathNamesTheModThatCalledASharedHelper() {
        StackTraceElement[] stack = {
                frame("java.util.BitSet", "get"),
                frame("dev.hoyin1600p.vhaccelerator.client.shape.FastCoordinateMerger", "m_6200_"),
                frame("net.minecraft.world.phys.shapes.Shapes", "m_83882_"),
                frame("net.minecraft.world.phys.shapes.Shapes", "m_83113_"),
                frame("net.minecraft.world.phys.shapes.Shapes", "m_83110_"),
                frame("net.minecraft.world.level.block.Block", "<init>"),
                frame("com.mrcrayfish.furniture.block.ChairBlock", "<init>"),
                frame("net.minecraftforge.registries.DeferredRegister", "lambda$register$0")
        };
        assertEquals("dev.hoyin1600p.vhaccelerator", StackAttribution.owner(stack));
        assertEquals(
                "FastCoordinateMerger.m_6200_ <- Shapes.m_83882_ <- Shapes.m_83113_ <- Shapes.m_83110_"
                        + " <- ... <- com.mrcrayfish.furniture.block.ChairBlock.<init>",
                StackAttribution.path(stack)
        );
    }

    @Test
    void platformPathsStartAtTheLeaf() {
        StackTraceElement[] stack = {
                frame("jdk.internal.misc.Unsafe", "park"),
                frame("java.util.concurrent.locks.LockSupport", "parkNanos")
        };
        assertEquals("(waiting)", StackAttribution.owner(stack));
        assertEquals("Unsafe.park <- LockSupport.parkNanos", StackAttribution.path(stack));
    }

    @Test
    void bucketsReportOwnersWithPaths() {
        SampleBuckets buckets = new SampleBuckets();
        StackTraceElement[] stack = {
                frame("java.util.zip.Inflater", "inflate"),
                frame("team.chisel.ctm.client.Handler", "onStitch")
        };
        buckets.add("atlas", stack, 20_000_000L);
        buckets.add("atlas", stack, 10_000_000L);
        List<String> lines = new ArrayList<>();
        buckets.report("Test sampler", lines::add);
        assertEquals(2, lines.size(), "An owner line and a class-loading line");
        String line = lines.get(0);
        assertTrue(line.startsWith("[debug] Test sampler atlas: ~30 ms over 2 samples: team.chisel.ctm 30 ms (100%)"), line);
        assertTrue(line.contains("[paths: Handler.onStitch 30 ms]"), line);
    }

    @Test
    void classifiesClassLoadingWork() {
        assertEquals("class-load:read", StackAttribution.category(new StackTraceElement[] {
                frame("java.util.zip.Inflater", "inflateBytesBytes"),
                frame("java.util.zip.InflaterInputStream", "read"),
                frame("cpw.mods.cl.ModuleClassLoader", "readerToClass"),
                frame("cpw.mods.cl.ModuleClassLoader", "loadClass"),
                frame("iskallia.vault.init.ModBlocks", "<clinit>")
        }));
        assertEquals("class-load:mixin-transform", StackAttribution.category(new StackTraceElement[] {
                frame("org.objectweb.asm.ClassReader", "readCode"),
                frame("org.spongepowered.asm.mixin.transformer.MixinTransformer", "transformClass"),
                frame("cpw.mods.modlauncher.LaunchPluginHandler", "offerClassNodeToPlugins"),
                frame("cpw.mods.modlauncher.TransformingClassLoader", "maybeTransformClassBytes"),
                frame("cpw.mods.cl.ModuleClassLoader", "readerToClass"),
                frame("cpw.mods.cl.ModuleClassLoader", "loadClass")
        }));
        assertEquals("class-load:define", StackAttribution.category(new StackTraceElement[] {
                frame("java.lang.ClassLoader", "defineClass1"),
                frame("java.lang.ClassLoader", "defineClass"),
                frame("cpw.mods.cl.ModuleClassLoader", "loadClass")
        }));
        assertEquals("class-load:lock-wait", StackAttribution.category(new StackTraceElement[] {
                frame("jdk.internal.misc.Unsafe", "park"),
                frame("java.util.concurrent.locks.LockSupport", "park"),
                frame("cpw.mods.cl.ModuleClassLoader", "loadClass")
        }));
    }

    @Test
    void separatesStaticInitializersFromLoadingAndOtherWork() {
        assertEquals("static-init", StackAttribution.category(new StackTraceElement[] {
                frame("java.util.HashMap", "put"),
                frame("com.simibubi.create.AllBogeyStyles", "<clinit>"),
                frame("cpw.mods.cl.ModuleClassLoader", "loadClass")
        }));
        assertEquals("other", StackAttribution.category(new StackTraceElement[] {
                frame("net.minecraft.world.phys.shapes.Shapes", "join"),
                frame("com.mrcrayfish.furniture.block.ChairBlock", "<init>")
        }));
    }

    @Test
    void reportsClassLoadingSharesAndClassCounts() {
        SampleBuckets buckets = new SampleBuckets();
        buckets.add("main", new StackTraceElement[] {
                frame("java.lang.ClassLoader", "defineClass1"),
                frame("cpw.mods.cl.ModuleClassLoader", "loadClass")
        }, 30_000_000L);
        buckets.add("main", new StackTraceElement[] {
                frame("net.minecraft.server.Bootstrap", "bootStrap")
        }, 10_000_000L);
        buckets.addClassesLoaded("pre-game:main", 1200);
        buckets.addClassesLoaded("pre-game:main", 300);
        List<String> lines = new ArrayList<>();
        buckets.report("Test", lines::add);
        assertTrue(lines.stream().anyMatch(line -> line.contains(
                "class loading main: ~30 ms loading classes (75% of ~40 ms); class-load:define 30 ms; other 10 ms")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.endsWith("pre-game:main=1500")), lines.toString());
    }
}
