package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates a persisted JEI output plan against the current recipe result.
 *
 * <p>Some subtype interpreters accidentally include Java object identity in a
 * UID. Those hexadecimal identity values change on every JVM start even when
 * the represented ingredient is unchanged. A known example is Sophisticated
 * Storage's Minecraft {@code WoodType} subtype value. In that case the cached
 * UID must be rebound to the current process's UID before the plan is applied.
 * Merely treating both values as equal would leave JEI indexed under a stale
 * UID and make the recipe undiscoverable.</p>
 */
public final class CachedRecipeOutputReconciler {
    private static final Pattern TRANSIENT_WOOD_TYPE_IDENTITY = Pattern.compile(
            "net\\.minecraft\\.world\\.level\\.block\\.state\\.properties"
                    + "\\.WoodType@[0-9a-fA-F]+"
    );

    private CachedRecipeOutputReconciler() {
    }

    public static Result reconcile(
            Map<String, List<List<String>>> roleGroups,
            String outputRole,
            String liveUid
    ) {
        List<List<String>> outputGroups = roleGroups.get(outputRole);
        if (outputGroups == null || outputGroups.isEmpty()) {
            return new Result(Outcome.MISMATCH, roleGroups);
        }

        List<String> flattened = outputGroups.stream()
                .flatMap(List::stream)
                .toList();
        if (flattened.contains(liveUid)) {
            return new Result(Outcome.EXACT, roleGroups);
        }

        // A recipe's getResultItem() is not an authoritative representation
        // of a JEI category that intentionally exposes multiple output
        // variants. The full cached category plan remains protected by the
        // recipe/config/mod fingerprint and live category-ownership check.
        if (flattened.size() != 1) {
            return new Result(Outcome.TRUSTED_VARIANTS, roleGroups);
        }

        String cachedUid = flattened.get(0);
        if (!canonicalize(cachedUid).equals(canonicalize(liveUid))) {
            return new Result(Outcome.MISMATCH, roleGroups);
        }

        Map<String, List<List<String>>> rebound = new LinkedHashMap<>(
                roleGroups
        );
        List<List<String>> reboundOutputs = new ArrayList<>(
                outputGroups.size()
        );
        for (List<String> group : outputGroups) {
            List<String> reboundGroup = new ArrayList<>(group.size());
            for (String uid : group) {
                reboundGroup.add(uid.equals(cachedUid) ? liveUid : uid);
            }
            reboundOutputs.add(List.copyOf(reboundGroup));
        }
        rebound.put(outputRole, List.copyOf(reboundOutputs));
        return new Result(Outcome.REBOUND, Map.copyOf(rebound));
    }

    static String canonicalize(String uid) {
        return TRANSIENT_WOOD_TYPE_IDENTITY.matcher(uid)
                .replaceAll("net.minecraft.world.level.block.state.properties"
                        + ".WoodType@<runtime>");
    }

    public enum Outcome {
        EXACT,
        REBOUND,
        TRUSTED_VARIANTS,
        MISMATCH
    }

    public record Result(
            Outcome outcome,
            Map<String, List<List<String>>> roleGroups
    ) {
        public boolean accepted() {
            return outcome != Outcome.MISMATCH;
        }

        public boolean rebound() {
            return outcome == Outcome.REBOUND;
        }
    }
}
