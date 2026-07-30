package dev.hoyin1600p.vhaccelerator.client.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.IndirectMerger;
import org.junit.jupiter.api.Test;

final class FastCoordinateMergerTest {
    @Test
    void matchesVanillaAcrossRandomCoordinateLists() {
        Random random = new Random(0x564841L);
        for (int iteration = 0; iteration < 25_000; iteration++) {
            DoubleList first = coordinates(random);
            DoubleList second = coordinates(random);
            for (boolean includeFirstOnly : new boolean[]{false, true}) {
                for (boolean includeSecondOnly
                        : new boolean[]{false, true}) {
                    assertEquivalent(
                            new IndirectMerger(
                                    first,
                                    second,
                                    includeFirstOnly,
                                    includeSecondOnly
                            ),
                            new FastCoordinateMerger(
                                    first,
                                    second,
                                    includeFirstOnly,
                                    includeSecondOnly
                            )
                    );
                }
            }
        }
    }

    private static DoubleList coordinates(Random random) {
        int size = 1 + random.nextInt(12);
        double[] values = new double[size];
        double value = random.nextDouble() * 2.0D - 1.0D;
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                value += random.nextDouble() < 0.2D
                        ? random.nextDouble() * 1.5E-7D
                        : random.nextDouble() * 0.75D;
            }
            values[index] = value;
        }
        return DoubleArrayList.wrap(values);
    }

    private static void assertEquivalent(
            IndexMerger expected,
            IndexMerger actual
    ) {
        assertEquals(expected.size(), actual.size());
        assertEquals(expected.getList().size(), actual.getList().size());
        for (int index = 0; index < expected.getList().size(); index++) {
            assertEquals(
                    Double.doubleToLongBits(
                            expected.getList().getDouble(index)
                    ),
                    Double.doubleToLongBits(
                            actual.getList().getDouble(index)
                    )
            );
        }
        assertEquals(indexes(expected), indexes(actual));
    }

    private static List<String> indexes(IndexMerger merger) {
        List<String> indexes = new ArrayList<>();
        merger.forMergedIndexes((first, second, merged) -> {
            indexes.add(first + ":" + second + ":" + merged);
            return true;
        });
        return indexes;
    }
}
