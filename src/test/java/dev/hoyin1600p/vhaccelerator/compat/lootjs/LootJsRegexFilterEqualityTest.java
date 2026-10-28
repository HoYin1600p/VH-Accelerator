package dev.hoyin1600p.vhaccelerator.compat.lootjs;

import static org.junit.jupiter.api.Assertions.*;

import dev.latvian.mods.kubejs.item.ItemStackJS;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import dev.latvian.mods.kubejs.item.ingredient.RegexIngredientJS;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * KubeJS's real {@code IngredientJS.isEmpty()} (default {@code getFirst().isEmpty()} over the real default
 * {@code getStacks()}, with a real {@code RegexIngredientJS}) is the oracle. Only the item stacks are stand-ins,
 * because Minecraft's item registry cannot be bootstrapped here; they implement what the code under test reads
 * (id, emptiness, copy, withCount) the way {@code ItemStackJS} does for a non-empty stack.
 */
class LootJsRegexFilterEqualityTest {
    private static final String[] MODS = {"minecraft", "botania", "pneumaticcraft", "the_vault", "create", "mekanism",
            "ae2", "thermal", "powah", "refinedstorage", "quark", "twigs"};
    private static final String[] NAMES = {"stone", "enchanted_book", "mana_pool", "air_canister", "gold_ingot",
            "botania_flower", "pneumatic_tube", "cable", "vault_gem", "dust", "enchanted_apple", "plate"};

    /** ItemStackJS's static initializer builds ItemStack's codec, which only needs the bootstrap guard to be set. */
    @BeforeAll
    static void guardBootstrap() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field guard = Bootstrap.class.getDeclaredField("isBootstrapped");
        guard.setAccessible(true);
        guard.setBoolean(null, true);
    }

    @AfterEach
    void restoreItemList() throws Exception {
        setList(null);
    }

    @Test
    void shortCircuitAnswersExactlyWhatTheOriginalIsEmptyAnswers() throws Exception {
        String[] regexes = {".*botania.*", ".*pneumaticcraft.*", ".*enchanted_book.*", "^minecraft:.*", ".*nothing_like_this.*",
                "twigs:plate", "[", ".*", "", "^$", "(?i).*MANA.*", ".*:cable$", ".*(dust|plate)$", "the_vault:vault_gem"};
        for (long seed = 1; seed <= 30; seed++) {
            Random random = new Random(seed);
            int size = random.nextInt(8) == 0 ? random.nextInt(4) : 200 + random.nextInt(3000);
            List<ItemStackJS> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                // Several stacks share an id, like the creative-tab fill of potions and enchanted books.
                list.add(new FakeStack(MODS[random.nextInt(MODS.length)] + ":" + NAMES[random.nextInt(NAMES.length)], false));
            }
            setList(list);
            for (String regex : regexes) {
                Pattern pattern;
                try {
                    pattern = Pattern.compile(regex);
                } catch (RuntimeException invalid) {
                    continue;
                }
                IngredientJS ingredient = new RegexIngredientJS(pattern);
                assertEquals(ingredient.isEmpty(), LootJsRegexFilters.isEmpty(ingredient), "seed " + seed + " regex " + regex);
            }
        }
    }

    @Test
    void anEmptyListAndAListWithoutMatchesAreEmpty() throws Exception {
        setList(List.of());
        assertTrue(LootJsRegexFilters.isEmpty(new RegexIngredientJS(Pattern.compile(".*"))));
        setList(List.of(new FakeStack("minecraft:stone", false), new FakeStack("create:cable", false)));
        assertTrue(LootJsRegexFilters.isEmpty(new RegexIngredientJS(Pattern.compile(".*botania.*"))));
        assertFalse(LootJsRegexFilters.isEmpty(new RegexIngredientJS(Pattern.compile(".*cable.*"))));
    }

    @Test
    void theWalkStopsAtTheFirstMatchInsteadOfTestingEveryItem() throws Exception {
        List<ItemStackJS> list = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) list.add(new FakeStack(i == 10 ? "botania:mana_pool" : "minecraft:stone" + i, false));
        setList(list);
        AtomicInteger tests = new AtomicInteger();
        FakeStack.TESTS = tests;
        try {
            assertFalse(LootJsRegexFilters.isEmpty(new RegexIngredientJS(Pattern.compile(".*botania.*"))));
            assertEquals(11, tests.get());
            tests.set(0);
            assertFalse(new RegexIngredientJS(Pattern.compile(".*botania.*")).isEmpty());
            assertEquals(5_000, tests.get(), "the original walks the whole list");
        } finally {
            FakeStack.TESTS = null;
        }
    }

    @Test
    void anyOtherIngredientKeepsTheOriginalCheck() throws Exception {
        setList(List.of(new FakeStack("minecraft:stone", false)));
        AtomicInteger originalCalls = new AtomicInteger();
        IngredientJS custom = new IngredientJS() {
            @Override public boolean test(ItemStackJS stack) { return true; }
            @Override public boolean isEmpty() { originalCalls.incrementAndGet(); return false; }
        };
        assertFalse(LootJsRegexFilters.isEmpty(custom));
        assertEquals(1, originalCalls.get());
        // A subclass of the regex ingredient is not the reviewed class either.
        class Loud extends RegexIngredientJS {
            Loud() { super(Pattern.compile(".*")); }
            @Override public boolean isEmpty() { originalCalls.incrementAndGet(); return true; }
        }
        assertTrue(LootJsRegexFilters.isEmpty(new Loud()));
        assertEquals(2, originalCalls.get());
    }

    @Test
    void aFailureInTheWalkFallsBackToTheOriginalCheck() throws Exception {
        List<ItemStackJS> list = new ArrayList<>();
        for (int i = 0; i < 20; i++) list.add(new FakeStack("minecraft:item" + i, i == 3));
        setList(list);
        RegexIngredientJS ingredient = new RegexIngredientJS(Pattern.compile(".*botania.*"));
        RuntimeException original = assertThrows(IllegalStateException.class, ingredient::isEmpty);
        RuntimeException shortCircuit = assertThrows(IllegalStateException.class, () -> LootJsRegexFilters.isEmpty(ingredient));
        assertEquals(original.getMessage(), shortCircuit.getMessage());
    }

    private static void setList(List<ItemStackJS> list) throws Exception {
        Field field = ItemStackJS.class.getDeclaredField("cachedItemList");
        field.setAccessible(true);
        field.set(null, list);
    }

    /** What the tested code reads of a non-empty {@code ItemStackJS}. */
    private static final class FakeStack extends ItemStackJS {
        static volatile AtomicInteger TESTS;
        private final String id;
        private final boolean failing;
        private int count = 1;

        FakeStack(String id, boolean failing) {
            super(null);
            this.id = id;
            this.failing = failing;
        }

        @Override public String getId() {
            AtomicInteger tests = TESTS;
            if (tests != null) tests.incrementAndGet();
            if (failing) throw new IllegalStateException("registry lookup failed for " + id);
            return id;
        }
        @Override public boolean isEmpty() { return count <= 0; }
        @Override public ItemStackJS copy() { FakeStack copy = new FakeStack(id, failing); copy.count = count; return copy; }
        @Override public ItemStackJS withCount(int count) {
            if (count <= 0) return ItemStackJS.EMPTY;
            FakeStack copy = (FakeStack) copy();
            copy.count = count;
            return copy;
        }

        @Override public boolean equals(Object other) { return other instanceof FakeStack stack && stack.id.equals(id) && stack.count == count; }
        @Override public int hashCode() { return id.hashCode() * 31 + count; }
    }
}
