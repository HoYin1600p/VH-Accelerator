package dev.hoyin1600p.vhaccelerator.compat.supplementaries;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Vault Hunters' library room templates store Supplementaries book piles with
 * an empty book list ({@code Items:[]}). Loading one sets the pile's
 * {@code books} property from its book count; 0 is not a valid value
 * (1 to 4), so the load throws, and Vault replaces the pile with its magenta
 * "Missing: supplementaries:book_pile" error block. A pile saved without any
 * book now loads with as many plain books as its block state shows (at least
 * one). Piles that hold books, which is every pile a player can make, load
 * unchanged.
 */
public final class BookPileRepair {
    private static final String ITEMS = "Items";

    private BookPileRepair() {
    }

    /** {@code tag}, or a copy holding plain books when it has none. */
    public static CompoundTag withBooks(CompoundTag tag, BlockState state) {
        if (tag == null || hasBook(tag)) {
            return tag;
        }
        int books = Math.max(1, Math.min(4, bookCount(state)));
        CompoundTag repaired = tag.copy();
        ListTag items = new ListTag();
        for (int slot = 0; slot < books; slot++) {
            CompoundTag item = new CompoundTag();
            item.putByte("Slot", (byte) slot);
            item.putString("id", "minecraft:book");
            item.putByte("Count", (byte) 1);
            items.add(item);
        }
        repaired.put(ITEMS, items);
        return repaired;
    }

    private static boolean hasBook(CompoundTag tag) {
        ListTag items = tag.getList(ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            if (!ItemStack.of(items.getCompound(i)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int bookCount(BlockState state) {
        if (state != null) {
            for (Property<?> property : state.getProperties()) {
                if (property instanceof IntegerProperty integer && "books".equals(property.getName())) {
                    return state.getValue(integer);
                }
            }
        }
        return 1;
    }
}
