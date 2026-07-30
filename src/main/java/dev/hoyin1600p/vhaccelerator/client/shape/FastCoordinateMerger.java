package dev.hoyin1600p.vhaccelerator.client.shape;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import net.minecraft.world.phys.shapes.IndexMerger;

/**
 * An allocation-compatible coordinate merger that reads flat arrays while
 * preserving {@link net.minecraft.world.phys.shapes.IndirectMerger}'s exact
 * epsilon and index semantics.
 */
public final class FastCoordinateMerger implements IndexMerger {
    private static final double EPSILON = 1.0E-7D;
    private static final DoubleList EMPTY = DoubleLists.unmodifiable(
            DoubleArrayList.wrap(new double[]{0.0D})
    );

    private final double[] result;
    private final int[] firstIndices;
    private final int[] secondIndices;
    private final int resultLength;

    public FastCoordinateMerger(
            DoubleList first,
            DoubleList second,
            boolean includeFirstOnly,
            boolean includeSecondOnly
    ) {
        int firstSize = first.size();
        int secondSize = second.size();
        int capacity = firstSize + secondSize;
        result = new double[capacity];
        firstIndices = new int[capacity];
        secondIndices = new int[capacity];

        double[] firstValues = values(first);
        double[] secondValues = values(second);
        boolean excludeFirstOnly = !includeFirstOnly;
        boolean excludeSecondOnly = !includeSecondOnly;
        double previous = Double.NaN;
        int merged = 0;
        int firstCursor = 0;
        int secondCursor = 0;

        while (true) {
            boolean selectedFirst;
            while (true) {
                boolean firstExhausted = firstCursor >= firstSize;
                boolean secondExhausted = secondCursor >= secondSize;
                if (firstExhausted && secondExhausted) {
                    resultLength = Math.max(1, merged);
                    return;
                }

                selectedFirst = !firstExhausted
                        && (secondExhausted
                        || firstValues[firstCursor]
                        < secondValues[secondCursor] + EPSILON);
                if (selectedFirst) {
                    firstCursor++;
                    if (!excludeFirstOnly
                            || secondCursor != 0 && !secondExhausted) {
                        break;
                    }
                } else {
                    secondCursor++;
                    if (!excludeSecondOnly
                            || firstCursor != 0 && !firstExhausted) {
                        break;
                    }
                }
            }

            int firstIndex = firstCursor - 1;
            int secondIndex = secondCursor - 1;
            double coordinate = selectedFirst
                    ? firstValues[firstIndex]
                    : secondValues[secondIndex];
            if (!(previous >= coordinate - EPSILON)) {
                firstIndices[merged] = firstIndex;
                secondIndices[merged] = secondIndex;
                result[merged] = coordinate;
                merged++;
                previous = coordinate;
            } else {
                firstIndices[merged - 1] = firstIndex;
                secondIndices[merged - 1] = secondIndex;
            }
        }
    }

    @Override
    public boolean forMergedIndexes(IndexConsumer consumer) {
        int segments = resultLength - 1;
        for (int index = 0; index < segments; index++) {
            if (!consumer.merge(
                    firstIndices[index],
                    secondIndices[index],
                    index
            )) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int size() {
        return resultLength;
    }

    @Override
    public DoubleList getList() {
        return resultLength <= 1
                ? EMPTY
                : DoubleArrayList.wrap(result, resultLength);
    }

    private static double[] values(DoubleList list) {
        if (list instanceof DoubleArrayList arrayList) {
            return arrayList.elements();
        }
        return list.toDoubleArray();
    }
}
