package dev.hoyin1600p.vhaccelerator.diagnostics.blockshape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/** Reads registry metadata on the server thread, without enumerating block states. */
public final class NativeBlockShapeAdapter {
    private NativeBlockShapeAdapter() { }

    public static Iterable<BlockShapeDump.BlockShape> registry() {
        return () -> new Iterator<>() {
            private final Iterator<Block> blocks = Registry.BLOCK.iterator();
            private int index;
            @Override public boolean hasNext() { return blocks.hasNext(); }
            @Override public BlockShapeDump.BlockShape next() { return capture(blocks.next(), index++); }
        };
    }

    private static BlockShapeDump.BlockShape capture(Block block, int index) {
        List<BlockShapeDump.PropertyShape> properties = new ArrayList<>();
        List<BlockShapeDump.DefaultValue> defaults = new ArrayList<>();
        for (Property<?> property : block.getStateDefinition().getProperties()) captureProperty(block, property, properties, defaults);
        return new BlockShapeDump.BlockShape(index, Objects.requireNonNull(Registry.BLOCK.getKey(block)).toString(),
                block.getClass().getName(), block.getStateDefinition().getPossibleStates().size(), properties, defaults);
    }

    private static <T extends Comparable<T>> void captureProperty(Block block, Property<T> property,
            List<BlockShapeDump.PropertyShape> properties, List<BlockShapeDump.DefaultValue> defaults) {
        List<T> domain = new ArrayList<>(property.getPossibleValues());
        List<BlockShapeDump.Value> values = new ArrayList<>(domain.size());
        for (T value : domain) values.add(new BlockShapeDump.Value(values.size(), Objects.requireNonNull(property.getName(value)),
                Objects.requireNonNull(value.toString()), value.getClass().getName()));
        int selected = BlockShapeDump.defaultIndex(domain, block.defaultBlockState().getValue(property));
        properties.add(new BlockShapeDump.PropertyShape(properties.size(), property.getName(), property.getClass().getName(),
                property.getValueClass().getName(), values));
        BlockShapeDump.Value value = values.get(selected);
        defaults.add(new BlockShapeDump.DefaultValue(property.getName(), selected, value.name(), value.text(), value.valueClass()));
    }
}
