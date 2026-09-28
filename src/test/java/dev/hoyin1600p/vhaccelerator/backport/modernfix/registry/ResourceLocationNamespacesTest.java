package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResourceLocationNamespacesTest {
    @AfterEach
    void reset() {
        ResourceLocationNamespaces.clearForTest();
    }

    @Test
    void equalNamespacesShareOneInstance() {
        String first = new String("the_vault");
        String second = new String("the_vault");
        assertSame(first, ResourceLocationNamespaces.canonical(first));
        assertSame(first, ResourceLocationNamespaces.canonical(second));
        assertEquals(1, ResourceLocationNamespaces.pooled());
    }

    @Test
    void countsDuplicatesOnlyWhenStatisticsAreEnabled() {
        ResourceLocationNamespaces.canonical(new String("minecraft"));
        ResourceLocationNamespaces.canonical(new String("minecraft"));
        assertEquals(0, ResourceLocationNamespaces.deduplicated());
        ResourceLocationNamespaces.enableStatistics();
        ResourceLocationNamespaces.canonical(new String("minecraft"));
        String pooled = ResourceLocationNamespaces.canonical("minecraft");
        ResourceLocationNamespaces.canonical(pooled);
        assertEquals(2, ResourceLocationNamespaces.deduplicated(),
                "Only a different instance is a replaced duplicate");
    }

    @Test
    void stopsGrowingAtTheBound() {
        for (int index = 0; index < ResourceLocationNamespaces.MAX_NAMESPACES; index++) {
            ResourceLocationNamespaces.canonical("ns" + index);
        }
        String overflow = new String("overflow");
        assertSame(overflow, ResourceLocationNamespaces.canonical(overflow));
        assertEquals(ResourceLocationNamespaces.MAX_NAMESPACES, ResourceLocationNamespaces.pooled());
        String known = new String("ns7");
        assertNotSame(known, ResourceLocationNamespaces.canonical(known),
                "Known namespaces still deduplicate once the table is full");
    }

    @Test
    void concurrentCallersAgreeOnOneInstance() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int task = 0; task < 64; task++) {
                results.add(pool.submit(() -> ResourceLocationNamespaces.canonical(new String("create"))));
            }
            String first = results.get(0).get();
            for (Future<String> result : results) {
                assertSame(first, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
