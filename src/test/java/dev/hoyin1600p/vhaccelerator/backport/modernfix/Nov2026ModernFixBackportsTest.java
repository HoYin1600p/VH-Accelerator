package dev.hoyin1600p.vhaccelerator.backport.modernfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwner;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipResolver;
import dev.hoyin1600p.vhaccelerator.backport.ModernFixOwnership;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.architectury.ArchitecturyEventInvokers;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.systeminfo.SharedSystemInfo;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import oshi.SystemInfo;

final class Nov2026ModernFixBackportsTest {
    public interface SampleListener {
        String concat(String left, int right);

        String joinVarargs(String prefix, String... parts);

        void boom();
    }

    @Test
    void architecturyInvokerCachesAndPreservesArgumentsVarargsAndExceptions() throws Throwable {
        SampleListener listener = new SampleListener() {
            @Override
            public String concat(String left, int right) {
                return left + ":" + right;
            }

            @Override
            public String joinVarargs(String prefix, String... parts) {
                return prefix + "=" + String.join(",", parts);
            }

            @Override
            public void boom() {
                throw new IllegalStateException("expected");
            }
        };

        Method concat = SampleListener.class.getMethod("concat", String.class, int.class);
        Method varargs = SampleListener.class.getMethod("joinVarargs", String.class, String[].class);
        Method boom = SampleListener.class.getMethod("boom");
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        assertEquals("vh:42", ArchitecturyEventInvokers.invoke(lookup, listener, concat, new Object[]{"vh", 42}));
        assertEquals("vh:99", ArchitecturyEventInvokers.invoke(lookup, listener, concat, new Object[]{"vh", 99}));
        assertEquals(
                "tags=a,b",
                ArchitecturyEventInvokers.invoke(
                        lookup,
                        listener,
                        varargs,
                        new Object[]{"tags", new String[]{"a", "b"}}
                )
        );
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> ArchitecturyEventInvokers.invoke(lookup, listener, boom, new Object[0])
        );
        assertEquals("expected", thrown.getMessage());
    }

    @Test
    void sharedSystemInfoReturnsSameInstance() {
        SystemInfo first = SharedSystemInfo.get();
        SystemInfo second = SharedSystemInfo.get();
        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    void ownershipYieldsToModernFixAndCompareMode() {
        for (BackportFeature feature : new BackportFeature[]{
                BackportFeature.FASTER_BIOME_LOOKUPS,
                BackportFeature.ARCHITECTURY_EVENT_DISPATCH,
                BackportFeature.SHARED_OSHI_SYSTEM_INFO
        }) {
            assertEquals(
                    BackportOwner.VHA,
                    BackportOwnershipResolver.resolve(feature, false, false, true, ModernFixOwnership.INACTIVE).owner()
            );
            assertEquals(
                    BackportOwner.MODERNFIX,
                    BackportOwnershipResolver.resolve(feature, true, false, true, ModernFixOwnership.ACTIVE).owner()
            );
            assertEquals(
                    BackportOwner.DISABLED,
                    BackportOwnershipResolver.resolve(feature, true, true, true, ModernFixOwnership.INACTIVE).owner()
            );
            assertTrue(feature.defaultEnabled());
        }
    }
}
